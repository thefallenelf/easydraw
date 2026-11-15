package com.zhangchuang.nianchu

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.roundToInt

/**
 * 画廊：网格展示所有工程（缩略图 + 标题 + 时间）。
 * 点击 = 打开继续画；长按 = 重命名/删除；右上/第一个卡 = 新建。
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private val items = ArrayList<ProjectIO.ProjectMeta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF9F7F1.toInt())
        }
        // 顶栏：标题 + 新建
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(24.dp(), 24.dp(), 24.dp(), 8.dp())
        }
        top.addView(TextView(this).apply {
            text = "念初的画画板"
            textSize = 22f
            setTextColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = "+ 新建"
            textSize = 16f
            setTextColor(0xFF4A7DBB.toInt())
            setPadding(0, 8.dp(), 0, 8.dp())
            setOnClickListener { openNew() }
        })
        root.addView(top)

        list = RecyclerView(this)
        list.layoutManager = GridLayoutManager(this, 2)
        list.setPadding(0, 0, 0, 0)
        list.adapter = GalleryAdapter()
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh() {
        items.clear()
        items.addAll(ProjectIO.listProjects(this))
        list.adapter?.notifyDataSetChanged()
    }

    private fun openNew() {
        val id = ProjectIO.newId()
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PROJECT_ID, id))
    }

    private fun open(id: String) {
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PROJECT_ID, id))
    }

    private fun delete(id: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除画作")
            .setMessage("确定删除这张画作？该操作不可恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                ProjectIO.deleteProject(this, id)
                refresh()
            }.show()
    }

    private fun rename(meta: ProjectIO.ProjectMeta) {
        val input = android.widget.EditText(this).apply {
            setText(meta.title)
            setSelection(meta.title.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "未命名" }
                ProjectIO.updateTitle(this, meta.id, name)
                ProjectIO.saveProject(this, metaToDrawingDummy(), meta.id, name)
                refresh()
            }.show()
    }

    /** 重命名时仅更新元数据标题（不重写层） */
    private fun metaToDrawingDummy(): Drawing = Drawing(1, 1)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private inner class GalleryAdapter : RecyclerView.Adapter<GalleryVH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryVH {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val img = ImageView(parent.context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (170.dp()).apply { })
            lp.height = 170.dp()
            card.addView(img, lp)
            val title = TextView(parent.context).apply {
                textSize = 15f
                setTextColor(0xFF333333.toInt())
                setPadding(4.dp(), 8.dp(), 4.dp(), 0)
            }
            card.addView(title)
            val time = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(0xFF888888.toInt())
                setPadding(4.dp(), 2.dp(), 4.dp(), 0)
            }
            card.addView(time)
            return GalleryVH(card, img, title, time)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: GalleryVH, position: Int) {
            val meta = items[position]
            val bmp = Thumbs.load(this@GalleryActivity, meta.id)
            if (bmp != null) {
                holder.img.setImageBitmap(bmp)
            } else {
                holder.img.setImageResource(android.R.color.transparent)
            }
            holder.img.setBackgroundColor(0xFFE8E4DA.toInt())
            holder.title.text = meta.title
            holder.time.text = ProjectIO.timeLabel(meta.updatedAt)
            holder.itemView.setOnClickListener { open(meta.id) }
            holder.itemView.setOnLongClickListener {
                val actions = arrayOf("重命名", "删除")
                MaterialAlertDialogBuilder(this@GalleryActivity)
                    .setTitle(meta.title)
                    .setItems(actions) { _, which ->
                        if (which == 0) rename(meta) else delete(meta.id)
                    }.show()
                true
            }
            holder.card.setBackgroundColor(
                if (position == 0) 0x66DCE4F0.toInt() else 0xFFFFFFFF.toInt())
        }
    }

    private class GalleryVH(
        val card: View,
        val img: ImageView,
        val title: TextView,
        val time: TextView
    ) : RecyclerView.ViewHolder(card)
}