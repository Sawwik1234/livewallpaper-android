package app.vidwall

import java.util.Locale

data class VideoInfo(val id: Long, val name: String, val durationMs: Long)

/** Настройки одного выбранного видео. endMs = 0 означает «до конца». */
data class VideoEntry(
    val id: Long,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val speed: Float = 1f,
    val fit: Boolean = false
)

fun formatTime(ms: Long): String {
    val total = ms / 1000
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}
