package app.vidwall

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlin.concurrent.thread

/** Редактирование одного видео: фрагмент (начало и конец), скорость, вписать целиком. */
class VideoEditorActivity : AppCompatActivity() {

    private lateinit var info: VideoInfo
    private lateinit var trimLabel: TextView
    private lateinit var speedLabel: TextView
    private lateinit var trimSlider: RangeSlider
    private lateinit var speedSlider: Slider
    private lateinit var fitSwitch: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val loadedInfo = VideoRepository.info(this, id)
        val entry = Prefs.entries(this).firstOrNull { it.id == id }
        if (loadedInfo == null || entry == null) {
            finish()
            return
        }
        info = loadedInfo

        trimLabel = findViewById(R.id.trimLabel)
        speedLabel = findViewById(R.id.speedLabel)
        trimSlider = findViewById(R.id.trimSlider)
        speedSlider = findViewById(R.id.speedSlider)
        findViewById<TextView>(R.id.videoName).text = info.name

        // Превью
        val thumb = findViewById<ImageView>(R.id.thumb)
        val app = applicationContext
        thread {
            val bmp = VideoRepository.loadThumbnail(app, id, 640, 360)
            runOnUiThread { if (bmp != null) thumb.setImageBitmap(bmp) }
        }

        // Переключатель «вписать целиком»
        val cardFit = findViewById<android.view.View>(R.id.cardFit)
        cardFit.findViewById<ImageView>(R.id.cardIcon).setImageResource(R.drawable.ic_video)
        cardFit.findViewById<TextView>(R.id.cardTitle).setText(R.string.fit_title)
        cardFit.findViewById<TextView>(R.id.cardSubtitle).setText(R.string.fit_sub)
        fitSwitch = cardFit.findViewById(R.id.cardSwitch)
        fitSwitch.visibility = android.view.View.VISIBLE
        fitSwitch.isChecked = entry.fit
        cardFit.setOnClickListener { fitSwitch.toggle() }

        // Фрагмент
        val durationSec = info.durationMs / 1000f
        if (durationSec >= 0.5f) {
            val endSec = if (entry.endMs > 0) minOf(entry.endMs / 1000f, durationSec) else durationSec
            val startSec = (entry.startMs / 1000f).coerceIn(0f, endSec)
            trimSlider.valueFrom = 0f
            trimSlider.valueTo = durationSec
            trimSlider.setValues(startSec, endSec)
            trimSlider.addOnChangeListener { _, _, _ -> updateTrimLabel() }
        } else {
            trimSlider.isEnabled = false
        }
        updateTrimLabel()

        // Скорость
        speedSlider.value = snapSpeed(entry.speed)
        speedSlider.setLabelFormatter { String.format(Locale.ROOT, "%.2f×", it) }
        speedSlider.addOnChangeListener { _, _, _ -> updateSpeedLabel() }
        updateSpeedLabel()

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save(entry) }
        findViewById<MaterialButton>(R.id.btnReset).setOnClickListener { reset() }
    }

    private fun snapSpeed(s: Float): Float {
        val snapped = Math.round(s * 4f) / 4f
        return snapped.coerceIn(0.25f, 2.0f)
    }

    private fun currentRange(): Pair<Long, Long> {
        if (!trimSlider.isEnabled) return 0L to 0L
        val v = trimSlider.values
        return (v[0] * 1000).toLong() to (v[1] * 1000).toLong()
    }

    private fun updateTrimLabel() {
        val (s, e) = currentRange()
        val end = if (e > 0) e else info.durationMs
        trimLabel.text = getString(R.string.trim_label, formatTime(s), formatTime(end))
    }

    private fun updateSpeedLabel() {
        speedLabel.text = getString(
            R.string.speed_label, String.format(Locale.ROOT, "%.2f", speedSlider.value)
        )
    }

    private fun reset() {
        if (trimSlider.isEnabled) trimSlider.setValues(0f, info.durationMs / 1000f)
        speedSlider.value = 1.0f
        fitSwitch.isChecked = false
        updateTrimLabel()
        updateSpeedLabel()
    }

    private fun save(old: VideoEntry) {
        var (start, end) = currentRange()
        if (start < 50) start = 0L
        if (end >= info.durationMs - 50) end = 0L
        if (end > 0 && end - start < 300) {
            Toast.makeText(this, R.string.too_short, Toast.LENGTH_SHORT).show()
            return
        }
        val updated = old.copy(startMs = start, endMs = end, speed = speedSlider.value, fit = fitSwitch.isChecked)
        Prefs.setEntries(this, Prefs.entries(this).map { if (it.id == old.id) updated else it })
        finish()
    }

    companion object {
        const val EXTRA_ID = "video_id"
    }
}
