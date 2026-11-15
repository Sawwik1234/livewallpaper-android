package app.vidwall

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val permission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAll() }

    private lateinit var pageHome: View
    private lateinit var pageVideos: View
    private lateinit var pageSettings: View

    private lateinit var cardStatus: View
    private lateinit var cardPerm: View
    private lateinit var cardBattery: View
    private lateinit var listEmpty: TextView
    private lateinit var qualityTitle: TextView

    private val entryAdapter = EntryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pageHome = findViewById(R.id.pageHome)
        pageVideos = findViewById(R.id.pageVideos)
        pageSettings = findViewById(R.id.pageSettings)

        setupHome()
        setupVideos()
        setupSettings()

        val nav = findViewById<BottomNavigationView>(R.id.nav)
        nav.setOnItemSelectedListener {
            showPage(it.itemId)
            true
        }
        showPage(R.id.nav_home)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    // ---------- общие помощники ----------

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun setupCard(card: View, icon: Int, title: String, subtitle: String?) {
        card.findViewById<ImageView>(R.id.cardIcon).setImageResource(icon)
        card.findViewById<TextView>(R.id.cardTitle).text = title
        val sub = card.findViewById<TextView>(R.id.cardSubtitle)
        if (subtitle.isNullOrEmpty()) {
            sub.visibility = View.GONE
        } else {
            sub.visibility = View.VISIBLE
            sub.text = subtitle
        }
    }

    private fun switchOf(card: View): MaterialSwitch {
        val sw = card.findViewById<MaterialSwitch>(R.id.cardSwitch)
        sw.visibility = View.VISIBLE
        card.setOnClickListener { sw.toggle() }
        return sw
    }

    private fun showPage(id: Int) {
        pageHome.visibility = if (id == R.id.nav_home) View.VISIBLE else View.GONE
        pageVideos.visibility = if (id == R.id.nav_videos) View.VISIBLE else View.GONE
        pageSettings.visibility = if (id == R.id.nav_settings) View.VISIBLE else View.GONE
        refreshAll()
    }

    private fun refreshAll() {
        refreshHome()
        refreshVideos()
        refreshSettings()
    }

    // ---------- Главная ----------

    private fun setupHome() {
        cardStatus = pageHome.findViewById(R.id.cardStatus)
        cardStatus.isClickable = false
        cardPerm = pageHome.findViewById(R.id.cardPerm)
        setupCard(cardPerm, R.drawable.ic_lock, getString(R.string.perm_title), getString(R.string.perm_sub))
        cardPerm.setOnClickListener { permissionLauncher.launch(permission) }

        val cardSet = pageHome.findViewById<View>(R.id.cardSet)
        setupCard(cardSet, R.drawable.ic_play, getString(R.string.set_title), getString(R.string.set_sub))
        cardSet.setOnClickListener { setWallpaper() }
    }

    private fun setStatus(text: String) {
        setupCard(cardStatus, R.drawable.ic_video, text, null)
    }

    private fun refreshHome() {
        if (!hasPermission()) {
            cardPerm.visibility = View.VISIBLE
            setStatus(getString(R.string.status_no_access))
            return
        }
        cardPerm.visibility = View.GONE
        val app = applicationContext
        thread {
            val total = VideoRepository.queryAll(app).size
            val sel = Prefs.entries(app).size
            val useSel = Prefs.useSelected(app) && sel > 0
            runOnUiThread {
                setStatus(
                    if (useSel) getString(R.string.status_selected, sel)
                    else getString(R.string.status_all, total)
                )
            }
        }
    }

    private fun setWallpaper() {
        val component = ComponentName(this, VideoWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, "Не удалось открыть выбор обоев", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- Видео ----------

    private fun setupVideos() {
        val cardUse = pageVideos.findViewById<View>(R.id.cardUseSelected)
        setupCard(cardUse, R.drawable.ic_video, getString(R.string.use_selected_title), getString(R.string.use_selected_sub))
        val useSwitch = switchOf(cardUse)
        useSwitch.isChecked = Prefs.useSelected(this)
        useSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setUseSelected(this, checked)
            refreshHome()
        }

        val cardShuffle = pageVideos.findViewById<View>(R.id.cardShuffle)
        setupCard(cardShuffle, R.drawable.ic_shuffle, getString(R.string.shuffle_title), getString(R.string.shuffle_sub))
        val shuffleSwitch = switchOf(cardShuffle)
        shuffleSwitch.isChecked = Prefs.shuffle(this)
        shuffleSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setShuffle(this, checked) }

        val cardAdd = pageVideos.findViewById<View>(R.id.cardAdd)
        setupCard(cardAdd, R.drawable.ic_add, getString(R.string.add_title), getString(R.string.add_sub))
        cardAdd.setOnClickListener {
            if (hasPermission()) {
                startActivity(Intent(this, VideoPickerActivity::class.java))
            } else {
                permissionLauncher.launch(permission)
            }
        }

        listEmpty = pageVideos.findViewById(R.id.listEmpty)
        val list = pageVideos.findViewById<RecyclerView>(R.id.entryList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = entryAdapter
    }

    private fun refreshVideos() {
        // Переключатель мог измениться из другого экрана (выбор видео включает режим).
        val sw = pageVideos.findViewById<View>(R.id.cardUseSelected).findViewById<MaterialSwitch>(R.id.cardSwitch)
        if (sw.isChecked != Prefs.useSelected(this)) sw.isChecked = Prefs.useSelected(this)

        val app = applicationContext
        thread {
            val rows = Prefs.entries(app).map { Row(it, VideoRepository.info(app, it.id)) }
            runOnUiThread {
                entryAdapter.rows = rows
                entryAdapter.notifyDataSetChanged()
                listEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private data class Row(val entry: VideoEntry, val info: VideoInfo?)

    private class EntryHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val name: TextView = view.findViewById(R.id.entryName)
        val infoText: TextView = view.findViewById(R.id.entryInfo)
        val edit: ImageButton = view.findViewById(R.id.btnEdit)
        val delete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    private inner class EntryAdapter : RecyclerView.Adapter<EntryHolder>() {
        var rows: List<Row> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EntryHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_video_entry, parent, false)
        )

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: EntryHolder, position: Int) {
            val row = rows[position]
            val e = row.entry
            val info = row.info
            if (info == null) {
                holder.name.text = "#${e.id}"
                holder.infoText.setText(R.string.entry_deleted)
                holder.thumb.setImageDrawable(null)
                holder.edit.visibility = View.GONE
            } else {
                holder.edit.visibility = View.VISIBLE
                holder.name.text = info.name
                holder.infoText.text = describe(e, info)
                Thumbs.load(this@MainActivity, e.id, 160, 160, holder.thumb)
            }
            holder.edit.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, VideoEditorActivity::class.java)
                        .putExtra(VideoEditorActivity.EXTRA_ID, e.id)
                )
            }
            holder.delete.setOnClickListener {
                Prefs.setEntries(this@MainActivity, Prefs.entries(this@MainActivity).filter { it.id != e.id })
                refreshAll()
            }
        }

        private fun describe(e: VideoEntry, info: VideoInfo): String {
            val parts = ArrayList<String>()
            if (e.startMs > 0 || e.endMs > 0) {
                val end = if (e.endMs > 0) e.endMs else info.durationMs
                parts.add("${formatTime(e.startMs)}–${formatTime(end)}")
            } else {
                parts.add(getString(R.string.entry_whole) + " " + formatTime(info.durationMs))
            }
            if (e.speed != 1f) parts.add("${e.speed}×")
            if (e.fit) parts.add(getString(R.string.entry_fit))
            return parts.joinToString(" · ")
        }
    }

    // ---------- Настройки ----------

    private fun setupSettings() {
        cardBattery = pageSettings.findViewById(R.id.cardBattery)
        cardBattery.setOnClickListener {
            val pm = (getSystemService(POWER_SERVICE) as PowerManager)
            try {
                if (pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } else {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть настройки батареи", Toast.LENGTH_LONG).show()
            }
        }

        val cardPower = pageSettings.findViewById<View>(R.id.cardPowerSave)
        setupCard(cardPower, R.drawable.ic_pause, getString(R.string.powersave_title), getString(R.string.powersave_sub))
        val powerSwitch = switchOf(cardPower)
        powerSwitch.isChecked = Prefs.pauseOnPowerSave(this)
        powerSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setPauseOnPowerSave(this, checked) }

        qualityTitle = pageSettings.findViewById(R.id.qualityTitle)
        val slider = pageSettings.findViewById<Slider>(R.id.qualitySlider)
        val q = (Math.round(Prefs.quality(this) / 10f) * 10).coerceIn(30, 100)
        slider.value = q.toFloat()
        qualityTitle.text = getString(R.string.quality_title, q)
        slider.addOnChangeListener { _, value, fromUser ->
            qualityTitle.text = getString(R.string.quality_title, value.toInt())
            if (fromUser) Prefs.setQuality(this, value.toInt())
        }
    }

    private fun refreshSettings() {
        val pm = (getSystemService(POWER_SERVICE) as PowerManager)
        val ok = pm.isIgnoringBatteryOptimizations(packageName)
        setupCard(
            cardBattery,
            R.drawable.ic_bolt,
            getString(if (ok) R.string.battery_ok else R.string.battery_need),
            getString(R.string.battery_sub)
        )
        // Иконки карточек с переключателями
    }
}
