package app.vidwall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlin.concurrent.thread

/** Выбор одного или нескольких видео из галереи (сетка с превью). */
class VideoPickerActivity : AppCompatActivity() {

    private val videos = ArrayList<VideoInfo>()
    private val selected = LinkedHashSet<Long>()
    private lateinit var done: MaterialButton
    private val adapter = CellAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        done = findViewById(R.id.btnDone)
        val grid = findViewById<RecyclerView>(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, 3)
        grid.adapter = adapter

        selected.addAll(Prefs.entries(this).map { it.id })
        updateButton()

        val app = applicationContext
        thread {
            val list = VideoRepository.queryAll(app)
            runOnUiThread {
                videos.addAll(list)
                selected.retainAll(list.map { it.id }.toSet())
                adapter.notifyDataSetChanged()
                updateButton()
            }
        }

        done.setOnClickListener {
            save()
            finish()
        }
    }

    private fun updateButton() {
        done.text = getString(R.string.done_count, selected.size)
    }

    private fun save() {
        val old = Prefs.entries(this)
        val oldIds = old.map { it.id }.toSet()
        val result = ArrayList<VideoEntry>()
        old.filter { it.id in selected }.forEach { result.add(it) }
        selected.filter { it !in oldIds }.forEach { result.add(VideoEntry(it)) }
        Prefs.setEntries(this, result)
        if (result.isNotEmpty()) Prefs.setUseSelected(this, true)
    }

    private class Cell(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val dim: View = view.findViewById(R.id.dim)
        val duration: TextView = view.findViewById(R.id.duration)
        val check: ImageView = view.findViewById(R.id.check)
    }

    private inner class CellAdapter : RecyclerView.Adapter<Cell>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Cell =
            Cell(LayoutInflater.from(parent.context).inflate(R.layout.item_picker_cell, parent, false))

        override fun getItemCount(): Int = videos.size

        override fun onBindViewHolder(holder: Cell, position: Int) {
            val v = videos[position]
            Thumbs.load(this@VideoPickerActivity, v.id, 240, 240, holder.image)
            holder.duration.text = formatTime(v.durationMs)
            val isSelected = v.id in selected
            holder.dim.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val id = videos[pos].id
                if (!selected.remove(id)) selected.add(id)
                notifyItemChanged(pos)
                updateButton()
            }
        }
    }
}
