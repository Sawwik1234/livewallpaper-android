package app.vidwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Видеообои: ExoPlayer рисует прямо на поверхность обоев.
 * Пауза, когда обои не видны или включено энергосбережение; плеер освобождается через 30 с невидимости.
 * Ошибки воспроизведения перехватываются: сбойное видео пропускается.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    private class Slot(val key: String, val item: MediaItem, val speed: Float, val fit: Boolean)

    private inner class VideoEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var player: ExoPlayer? = null
        private var currentHolder: SurfaceHolder? = null
        private var isVisibleNow = false
        private var errorStreak = 0
        private var lastKeys: List<String> = emptyList()
        private var slotById: Map<String, Slot> = emptyMap()
        private var appliedQuality = -1
        private var receiverRegistered = false

        private val powerManager: PowerManager by lazy {
            getSystemService(Context.POWER_SERVICE) as PowerManager
        }

        private val releaseRunnable = Runnable { releasePlayer() }
        private val reloadRunnable = Runnable {
            applyQuality()
            if (isVisibleNow) {
                refreshPlaylist()
                updatePlaying()
            }
        }

        private val mediaObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = scheduleReload()
        }
        private val prefsListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> scheduleReload() }
        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = updatePlaying()
        }

        private fun scheduleReload() {
            handler.removeCallbacks(reloadRunnable)
            handler.postDelayed(reloadRunnable, RELOAD_DELAY_MS)
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            allowFixedSize()
            applyQuality()
            try {
                contentResolver.registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
                )
            } catch (e: Exception) {
                Log.w(TAG, "registerContentObserver failed", e)
            }
            try {
                registerReceiver(powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
                receiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "registerReceiver failed", e)
            }
            Prefs.get(applicationContext).registerOnSharedPreferenceChangeListener(prefsListener)
        }

        private fun allowFixedSize() {
            try {
                Engine::class.java
                    .getDeclaredMethod("setFixedSizeAllowed", Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                    .invoke(this, true)
            } catch (e: Exception) {
                Log.w(TAG, "setFixedSizeAllowed unavailable", e)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
            if (isVisibleNow) startPlayback()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
            currentHolder = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleNow = visible
            handler.removeCallbacks(releaseRunnable)
            if (visible) {
                startPlayback()
            } else {
                player?.pause()
                handler.postDelayed(releaseRunnable, RELEASE_DELAY_MS)
            }
        }

        override fun onDestroy() {
            handler.removeCallbacksAndMessages(null)
            try {
                contentResolver.unregisterContentObserver(mediaObserver)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterContentObserver failed", e)
            }
            if (receiverRegistered) {
                try {
                    unregisterReceiver(powerReceiver)
                } catch (e: Exception) {
                    Log.w(TAG, "unregisterReceiver failed", e)
                }
            }
            Prefs.get(applicationContext).unregisterOnSharedPreferenceChangeListener(prefsListener)
            releasePlayer()
            super.onDestroy()
        }

        private fun shouldPlay(): Boolean {
            if (!isVisibleNow) return false
            if (Prefs.pauseOnPowerSave(applicationContext) && powerManager.isPowerSaveMode) return false
            return true
        }

        private fun updatePlaying() {
            val p = player ?: return
            try {
                if (shouldPlay()) {
                    if (p.mediaItemCount > 0 && p.playbackState == Player.STATE_IDLE) p.prepare()
                    p.playWhenReady = true
                } else {
                    p.pause()
                }
            } catch (e: Exception) {
                Log.w(TAG, "updatePlaying failed", e)
            }
        }

        private fun startPlayback() {
            val holder = currentHolder ?: return
            try {
                errorStreak = 0
                val p = player ?: createPlayer().also { player = it }
                p.setVideoSurfaceHolder(holder)
                refreshPlaylist()
                updatePlaying()
            } catch (e: Exception) {
                Log.e(TAG, "startPlayback failed", e)
                releasePlayer()
            }
        }

        private fun createPlayer(): ExoPlayer {
            val p = ExoPlayer.Builder(applicationContext).build()
            p.volume = 0f
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            p.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    errorStreak = 0
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    applyItemSettings(p, mediaItem)
                }

                override fun onPlayerError(error: PlaybackException) {
                    handleError(p, error)
                }
            })
            return p
        }

        private fun applyItemSettings(p: ExoPlayer, item: MediaItem?) {
            val slot = item?.let { slotById[it.mediaId] }
            try {
                p.setPlaybackSpeed(slot?.speed ?: 1f)
                p.videoScalingMode = if (slot?.fit == true) {
                    C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                } else {
                    C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                }
            } catch (e: Exception) {
                Log.w(TAG, "applyItemSettings failed", e)
            }
        }

        private fun handleError(p: ExoPlayer, error: PlaybackException) {
            Log.w(TAG, "Playback error", error)
            errorStreak++
            if (errorStreak > p.mediaItemCount.coerceAtLeast(1) + 1) {
                Log.e(TAG, "Too many consecutive errors, giving up until next show")
                return
            }
            try {
                if (p.mediaItemCount > 1) p.seekToNextMediaItem()
                p.prepare()
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed", e)
            }
        }

        private fun slotOf(e: VideoEntry): Slot {
            val b = MediaItem.Builder()
                .setUri(VideoRepository.uriOf(e.id))
                .setMediaId(e.id.toString())
            if (e.startMs > 0 || e.endMs > 0) {
                val c = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(e.startMs)
                if (e.endMs > e.startMs) c.setEndPositionMs(e.endMs)
                b.setClippingConfiguration(c.build())
            }
            return Slot("${e.id}:${e.startMs}:${e.endMs}:${e.speed}:${e.fit}", b.build(), e.speed, e.fit)
        }

        /** Пересобирает плейлист, только если набор или настройки видео изменились. */
        private fun refreshPlaylist() {
            val p = player ?: return
            val ctx = applicationContext
            val entries = Prefs.entries(ctx)
            val slots = if (Prefs.useSelected(ctx) && entries.isNotEmpty()) {
                entries.map { slotOf(it) }
            } else {
                VideoRepository.queryAll(ctx).map { slotOf(VideoEntry(it.id)) }
            }
            p.shuffleModeEnabled = Prefs.shuffle(ctx)
            slotById = slots.associateBy { it.item.mediaId }
            if (slots.isEmpty()) {
                lastKeys = emptyList()
                p.stop()
                p.clearMediaItems()
                return
            }
            p.repeatMode = if (slots.size == 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL

            val keys = slots.map { it.key }
            if (keys == lastKeys && p.mediaItemCount == slots.size) {
                applyItemSettings(p, p.currentMediaItem)
                return
            }
            val playingId = p.currentMediaItem?.mediaId
            val index = slots.indexOfFirst { it.item.mediaId == playingId }
            lastKeys = keys
            val items = slots.map { it.item }
            if (index >= 0) {
                p.setMediaItems(items, index, p.currentPosition)
            } else {
                p.setMediaItems(items)
            }
            p.prepare()
        }

        /** Качество рендеринга: поверхность обоев меньше экрана и растягивается системой. */
        private fun applyQuality() {
            val q = Prefs.quality(applicationContext).coerceIn(30, 100)
            if (q == appliedQuality) return
            appliedQuality = q
            try {
                val holder = surfaceHolder
                if (q >= 100) {
                    holder.setSizeFromLayout()
                } else {
                    val dm = resources.displayMetrics
                    holder.setFixedSize(
                        maxOf(2, dm.widthPixels * q / 100),
                        maxOf(2, dm.heightPixels * q / 100)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "applyQuality failed", e)
            }
        }

        private fun releasePlayer() {
            val p = player ?: return
            player = null
            lastKeys = emptyList()
            try {
                p.clearVideoSurface()
                p.release()
            } catch (e: Exception) {
                Log.w(TAG, "release failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "VidWall"
        const val RELOAD_DELAY_MS = 1500L
        const val RELEASE_DELAY_MS = 30_000L
    }
}
