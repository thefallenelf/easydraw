package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 图层面板（右侧抽屉，竖排）。
 *
 * ┌ 图层                      × ┐
 * │ [＋新建][复制][删除][上移][下移] │
 * │ 最上层  👁 名称  100%          │
 * │ ...                            │
 * └──────────────────────────────┘
 *
 * 点选 = 切换当前绘制层；长按 = 重命名/显隐/复制/移动/删除。
 */
class LayerPanel(
    context: Context,
    private val controller: PaintController,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val listBox: LinearLayout
    private val dark get() = (resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        setBackgroundColor(if (dark) 0xF72A2E35.toInt() else 0xF7FAF7EF.toInt())

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 标题行
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14f * d()).toInt(), (10f * d()).toInt(), (6f * d()).toInt(), (6f * d()).toInt())
        }
        head.addView(TextView(context).apply {
            text = "图层"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(txt())
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(context).apply {
            text = "×"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(txtDim())
            layoutParams = LinearLayout.LayoutParams((40f * d()).toInt(), (40f * d()).toInt())
            setOnClickListener { onClose() }
        })
        col.addView(head)

        // 操作按钮行（自动换行不是必须；宽度不够时按钮可两行）
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((10f * d()).toInt(), 0, (10f * d()).toInt(), (6f * d()).toInt())
        }
        row.addView(chip("＋ 新建") { addLayer() })
        row.addView(chip("复制") { replicateActive() })
        row.addView(chip("删除") { deleteActive() })
        col.addView(row)

        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((10f * d()).toInt(), 0, (10f * d()).toInt(), (8f * d()).toInt())
        }
        row2.addView(chip("上移") { moveActive(1) })
        row2.addView(chip("下移") { moveActive(-1) })
        row2.addView(chip("显/隐") { toggleActiveVisible() })
        col.addView(row2)

        // 列表
        listBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply {
            addView(listBox)
            isVerticalScrollBarEnabled = true
        }
        col.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        refresh()
    }

    private fun d(): Float = resources.displayMetrics.density
    private fun txt() = if (dark) 0xFFE6E8EC.toInt() else 0xFF2F3A48.toInt()
    private fun txtDim() = if (dark) 0xFF9AA3AE.toInt() else 0xFF8A93A0.toInt()

    private fun chip(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(txt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14f * d()
                setColor(if (dark) 0xFF3A4351.toInt() else 0xFFE4E9F0.toInt())
            }
            setPadding((14f * d()).toInt(), (8f * d()).toInt(), (14f * d()).toInt(), (8f * d()).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = (8f * d()).toInt()
            }
            setOnClickListener { onClick(); refresh() }
        }

    /** 重建列表（层数少，直接全量刷新最简单最可靠） */
    fun refresh() {
        listBox.removeAllViews()
        val drawing = controller.drawing
        // 顶层显示在最上面：倒序
        for (i in drawing.layers.indices.reversed()) {
            val layer = drawing.layers[i]
            val active = i == drawing.activeIndex
            val row = TextView(context).apply {
                val vis = if (layer.visible) "👁" else "◌"
                val pos = when (i) {
                    drawing.layers.size - 1 -> "顶"
                    0 -> "底"
                    else -> "${i + 1}"
                }
                text = "$pos  $vis  ${layer.name}   ${(layer.opacity * 100).toInt()}%"
                textSize = 15f
                setTextColor(txt())
                setPadding((16f * d()).toInt(), (14f * d()).toInt(),
                    (16f * d()).toInt(), (14f * d()).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 10f * d()
                    setColor(if (active) {
                        if (dark) 0xFF3B4E6B.toInt() else 0xFFCFE0FA.toInt()
                    } else 0x00000000)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = (10f * d()).toInt()
                    rightMargin = (10f * d()).toInt()
                    bottomMargin = (4f * d()).toInt()
                }
                setOnClickListener {
                    drawing.activeIndex = i
                    refresh()
                }
                setOnLongClickListener {
                    showMenu(i)
                    true
                }
            }
            listBox.addView(row)
        }
        // 底部提示
        listBox.addView(TextView(context).apply {
            text = "点一下选择当前层，长按管理"
            textSize = 11f
            setTextColor(txtDim())
            setPadding((16f * d()).toInt(), (10f * d()).toInt(), (16f * d()).toInt(), (10f * d()).toInt())
        })
    }

    private fun showMenu(index: Int) {
        val drawing = controller.drawing
        val layer = drawing.layers.getOrNull(index) ?: return
        val items = arrayOf("重命名", "显示 / 隐藏", "复制这一层", "上移", "下移", "删除")
        MaterialAlertDialogBuilder(context)
            .setTitle(layer.name)
            .setItems(items) { _, w ->
                when (w) {
                    0 -> rename(index, layer)
                    1 -> { layer.visible = !layer.visible; refresh() }
                    2 -> { drawing.activeIndex = index; replicateActive() }
                    3 -> { drawing.activeIndex = index; moveActive(1) }
                    4 -> { drawing.activeIndex = index; moveActive(-1) }
                    5 -> { drawing.activeIndex = index; deleteActive() }
                }
            }.show()
    }

    private fun rename(index: Int, layer: Layer) {
        val input = android.widget.EditText(context).apply {
            setText(layer.name)
            setSelection(layer.name.length)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("重命名图层")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                input.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { layer.name = it }
                refresh()
            }.show()
    }

    private fun addLayer() {
        controller.drawing.addLayer()
    }

    private fun replicateActive() {
        val dr = controller.drawing
        val src = dr.activeLayer ?: return
        val copy = Layer(src.name + " 副本", src.width, src.height)
        src.seed?.let { copy.seed = it.copy(Bitmap.Config.ARGB_8888, true) }
        copy.ops.addAll(src.ops.map { it.copyDeep() })
        copy.visible = src.visible
        copy.opacity = src.opacity
        dr.layers.add(copy)
        dr.activeIndex = dr.layers.size - 1
    }

    private fun deleteActive() {
        controller.drawing.removeLayer(controller.drawing.activeIndex)
    }

    private fun moveActive(dir: Int) {
        val dr = controller.drawing
        if (dir > 0) dr.layerUp(dr.activeIndex) else dr.layerDown(dr.activeIndex)
    }

    private fun toggleActiveVisible() {
        controller.drawing.activeLayer?.let { it.visible = !it.visible }
    }
}