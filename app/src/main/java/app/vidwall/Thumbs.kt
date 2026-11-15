package app.vidwall

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Загрузка превью видео в фоне с кэшем. */
object Thumbs {
    private val executor = Executors.newFixedThreadPool(3)
    private val cache = LruCache<String, Bitmap>(60)
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, id: Long, width: Int, height: Int, view: ImageView) {
        val key = "$id@${width}x$height"
        view.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null)
        val app = context.applicationContext
        executor.execute {
            val bmp = VideoRepository.loadThumbnail(app, id, width, height) ?: return@execute
            cache.put(key, bmp)
            main.post { if (view.tag == key) view.setImageBitmap(bmp) }
        }
    }
}
