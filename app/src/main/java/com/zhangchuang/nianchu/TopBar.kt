package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 顶部工具条（自绘）：画廊 / 撤销 / 重做 / 清空 / 图层 | 导出 / 分享 / 设置
 * 每个按钮都有图标 + 中文小字标签。点按后由外层弹出气泡提示。
 * 本组件左上角内嵌「图钉」按钮，用于锁定常驻。
 */
class TopBar(
    context: Context,
    private val onAction: (TopAction) -> Unit
) : View(context) {

    data class Item(val action: TopAction, val icon: Int, val label: String)

    val items = listOf(
        Item(TopAction.GALLERY, Ico.GALLERY, "画廊"),
        Item(TopAction.UNDO, Ico.UNDO, "撤销"),
        Item(TopAction.REDO, Ico.REDO, "重做"),
        Item(TopAction.CLEAR, Ico.TRASH, "清空"),
        Item(TopAction.LAYERS, Ico.LAYERS, "图层"),
        Item(TopAction.EXPORT, Ico.EXPORT, "导出"),
        Item(TopAction.SHARE, Ico.SHARE, "分享"),
        Item(TopAction.SETTINGS, Ico.GEAR, "设置")
    )

    var canUndo = false
    var canRedo = false

    var onHint: ((String) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setWillNotDraw(false) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val want = min(MeasureSpec.getSize(widthMeasureSpec), (560f * d()).toInt())
        setMeasuredDimension(want, resolveSize((50f * d()).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dark = isDark()
        bgPaint.color = if (dark) 0xFF2A2E35.toInt() else 0xFFFAF7EF.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
            20f * d(), 20f * d(), bgPaint)

        val on = if (dark) 0xFFE8EAEE.toInt() else 0xFF2F3A48.toInt()
        val off = if (dark) 0xFF5D6773.toInt() else 0xFFB4BAC4.toInt()

        val cell = width / items.size.toFloat()
        val iconS = min(cell * 0.46f, height * 0.44f)
        textPaint.textSize = min(cell * 0.22f, 9.5f * d()).coerceAtLeast(7.5f * d())

        for (i in items.indices) {
            val cx = cell * i + cell / 2f
            val enabled = when (items[i].action) {
                TopAction.UNDO -> canUndo
                TopAction.REDO -> canRedo
                else -> true
            }
            val c = if (enabled) on else off
            Ico.draw(canvas, items[i].icon, c, iconS, cx, height * 0.40f)
            textPaint.color = c
            canvas.drawText(items[i].label, cx, height - 5f * d(), textPaint)
        }
    }

    private fun isDark(): Boolean =
        (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun d(): Float = resources.displayMetrics.density

    private var downIdx = -1

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cell = width / items.size.toFloat()
        val idx = (event.x / cell).toInt().coerceIn(0, items.size - 1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downIdx = idx; return true }
            MotionEvent.ACTION_MOVE -> { if (idx != downIdx) downIdx = idx; return true }
            MotionEvent.ACTION_UP -> {
                if (idx == downIdx) {
                    val it = items[idx]
                    val enabled = when (it.action) {
                        TopAction.UNDO -> canUndo
                        TopAction.REDO -> canRedo
                        else -> true
                    }
                    if (enabled) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onHint?.invoke(it.label)
                        onAction(it.action)
                    }
                }
                downIdx = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> { downIdx = -1; return true }
        }
        return super.onTouchEvent(event)
    }
}

enum class TopAction {
    GALLERY, UNDO, REDO, CLEAR, LAYERS, EXPORT, SHARE, SETTINGS
}