package app.vidwall

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size

/** Чтение видео из галереи через MediaStore. Ошибки доступа дают пустой результат, а не вылет. */
object VideoRepository {
    private const val TAG = "VidWall"
    private val collection: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    private val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )

    fun uriOf(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    private fun read(c: Cursor): VideoInfo = VideoInfo(
        id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)),
        name = c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "",
        durationMs = c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
    )

    fun queryAll(context: Context): List<VideoInfo> {
        val result = ArrayList<VideoInfo>()
        try {
            val selection = "${MediaStore.Video.Media.DURATION} > 0"
            val order = "${MediaStore.Video.Media.DATE_ADDED} DESC"
            context.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
                while (c.moveToNext()) result.add(read(c))
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryAll failed", e)
        }
        return result
    }

    fun info(context: Context, id: Long): VideoInfo? {
        try {
            context.contentResolver.query(
                collection, projection, "${MediaStore.Video.Media._ID} = ?", arrayOf(id.toString()), null
            )?.use { c ->
                if (c.moveToFirst()) return read(c)
            }
        } catch (e: Exception) {
            Log.w(TAG, "info failed", e)
        }
        return null
    }

    fun loadThumbnail(context: Context, id: Long, width: Int, height: Int): Bitmap? = try {
        context.contentResolver.loadThumbnail(uriOf(id), Size(width, height), null)
    } catch (e: Exception) {
        null
    }
}
