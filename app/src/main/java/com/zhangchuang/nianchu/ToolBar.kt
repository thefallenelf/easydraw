package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 底部工具栏（自绘）：图标 + 中文小字标签，选中项高亮。
 * 点按即出现工具名气泡提示，长按有震动反馈。
 */
class ToolBar(
    context: Context,
    val onTool: (Int) -> Unit
) : View(context) {

    data class Item(val tool: Int, val icon: Int, val label: String)

    val items = listOf(
        Item(Tool.PEN, Ico.PEN, "铅笔"),
        Item(Tool.MARKER, Ico.MARKER, "马克笔"),
        Item(Tool.ERASER, Ico.ERASER, "橡皮"),
        Item(Tool.FILL, Ico.FILL, "填充"),
        Item(Tool.LINE, Ico.LINE, "直线"),
        Item(Tool.RECT, Ico.RECT, "矩形"),
        Item(Tool.OVAL, Ico.OVAL, "椭圆"),
        Item(Tool.ARROW, Ico.ARROW, "箭头"),
        Item(Tool.EYEDROPPER, Ico.DROPPER, "取色")
    )

    var selected = Tool.PEN

    /** 点按后显示的提示文字（屏幕中央上方气泡，由 MainActivity 负责渲染） */
    var onHint: ((String) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    init { setWillNotDraw(false) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val want = min(MeasureSpec.getSize(widthMeasureSpec), (620f * d()).toInt())
        setMeasuredDimension(want, resolveSize((54f * d()).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dark = isDark()
        bgPaint.color = if (dark) 0xFF2A2E35.toInt() else 0xFFFAF7EF.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
            20f * d(), 20f * d(), bgPaint)

        val on = if (dark) 0xFFE8EAEE.toInt() else 0xFF2F3A48.toInt()
        val dim = if (dark) 0xFF717C8A.toInt() else 0xFF8A93A0.toInt()
        val cell = width / items.size.toFloat()
        val iconS = min(cell * 0.48f, height * 0.44f)
        textPaint.textSize = min(cell * 0.18f, 9.5f * d()).coerceAtLeast(7.5f * d())

        for (i in items.indices) {
            val cx = cell * i + cell / 2f
            val active = items[i].tool == selected
            if (active) {
                selPaint.color = if (dark) 0xFF3A4351.toInt() else 0xFFE4E9F0.toInt()
                canvas.drawRoundRect(
                    cx - cell / 2f + 3f * d(), 5f * d(),
                    cx + cell / 2f - 3f * d(), height - 5f * d(),
                    12f * d(), 12f * d(), selPaint)
            }
            Ico.draw(canvas, items[i].icon, if (active) on else dim,
                iconS, cx, height * 0.40f)
            textPaint.color = if (active) on else dim
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
            MotionEvent.ACTION_MOVE -> {
                if (idx != downIdx) downIdx = idx
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (idx == downIdx) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val it = items[idx]
                    selected = it.tool
                    onHint?.invoke(it.label)
                    onTool(it.tool)
                    invalidate()
                }
                downIdx = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> { downIdx = -1; return true }
        }
        return super.onTouchEvent(event)
    }
}