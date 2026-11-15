package app.vidwall

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val KEY_ENTRIES = "entries"
    private const val KEY_USE_SELECTED = "use_selected"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_POWER_SAVE = "pause_power_save"
    private const val KEY_QUALITY = "quality"

    fun get(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("vidwall", Context.MODE_PRIVATE)

    fun entries(context: Context): List<VideoEntry> {
        val raw = get(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                VideoEntry(
                    id = o.getLong("id"),
                    startMs = o.optLong("start", 0L),
                    endMs = o.optLong("end", 0L),
                    speed = o.optDouble("speed", 1.0).toFloat(),
                    fit = o.optBoolean("fit", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setEntries(context: Context, list: List<VideoEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("start", it.startMs)
                    .put("end", it.endMs)
                    .put("speed", it.speed.toDouble())
                    .put("fit", it.fit)
            )
        }
        get(context).edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    fun useSelected(context: Context) = get(context).getBoolean(KEY_USE_SELECTED, false)
    fun setUseSelected(context: Context, v: Boolean) =
        get(context).edit().putBoolean(KEY_USE_SELECTED, v).apply()

    fun shuffle(context: Context) = get(context).getBoolean(KEY_SHUFFLE, false)
    fun setShuffle(context: Context, v: Boolean) =
        get(context).edit().putBoolean(KEY_SHUFFLE, v).apply()

    fun pauseOnPowerSave(context: Context) = get(context).getBoolean(KEY_POWER_SAVE, true)
    fun setPauseOnPowerSave(context: Context, v: Boolean) =
        get(context).edit().putBoolean(KEY_POWER_SAVE, v).apply()

    fun quality(context: Context) = get(context).getInt(KEY_QUALITY, 80)
    fun setQuality(context: Context, v: Int) =
        get(context).edit().putInt(KEY_QUALITY, v).apply()
}
