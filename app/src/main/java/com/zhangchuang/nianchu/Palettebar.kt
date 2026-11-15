package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 调色板：24 个预设色 + 独立的「+」自定义取色按钮（不重叠）。
 * 色点右侧留出专门的一格给「+」，中间有分隔线。
 */
class Palettebar(
    context: Context,
    private val onColor: (Int) -> Unit
) : View(context) {

    private val colors = PALETTE_24.clone()
    private var selected = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val leadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    var onCustomTap: () -> Unit = {}

    init { setWillNotDraw(false) }

    fun setSelected(c: Int) {
        val idx = colors.indexOf(c)
        selected = if (idx >= 0) idx else -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec).coerceAtMost((700f * d()).toInt()),
            resolveSize((56f * d()).toInt(), heightMeasureSpec))
    }

    // 布局：左侧 24 色等分；右侧专门一格「+」
    private fun plusZoneW(): Float = 46f * d()

    private fun colorZoneW(): Float = width - plusZoneW()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dark = isDark()
        bgPaint.color = if (dark) 0xFF2A2E35.toInt() else 0xFFFAF7EF.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
            20f * d(), 20f * d(), bgPaint)

        val n = colors.size
        val zone = colorZoneW()
        val pad = 8f * d()
        val cell = (zone - pad * 2) / n
        val r = min(cell * 0.38f, 17f * d())
        val cy = height / 2f

        for (i in 0 until n) {
            val cx = pad + cell * i + cell / 2f
            paint.color = colors[i]
            canvas.drawCircle(cx, cy, r, paint)
            strokePaint.color = 0x1F000000.toInt()
            strokePaint.strokeWidth = 1f
            canvas.drawCircle(cx, cy, r, strokePaint)
            if (i == selected) {
                strokePaint.color = if (dark) 0xFFFFFFFF.toInt() else 0xFF3D4757.toInt()
                strokePaint.strokeWidth = 2.5f * d()
                canvas.drawCircle(cx, cy, r + 4f * d(), strokePaint)
            }
        }

        // 分隔线
        val sepX = zone
        strokePaint.color = 0x22000000.toInt()
        strokePaint.strokeWidth = 1f
        canvas.drawLine(sepX, 10f * d(), sepX, height - 10f * d(), strokePaint)

        // 右侧「+」自定义色按钮（独立区域，不再与色点重叠）
        val px = sepX + plusZoneW() / 2f
        plusPaint.color = if (dark) 0xFFE8EAEE.toInt() else 0xFF2F3A48.toInt()
        plusPaint.strokeWidth = 2.2f * d()
        canvas.drawCircle(px, cy, r * 0.95f, strokePaint.apply {
            color = if (dark) 0xFF4A5462.toInt() else 0xFFDCD5C6.toInt()
        })
        val arm = r * 0.48f
        canvas.drawLine(px - arm, cy, px + arm, cy, plusPaint)
        canvas.drawLine(px, cy - arm, px, cy + arm, plusPaint)
    }

    private fun isDark(): Boolean =
        (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun d(): Float = resources.displayMetrics.density

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        val zone = colorZoneW()
        if (event.x >= zone) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onCustomTap()
            return true
        }
        val n = colors.size
        val pad = 8f * d()
        val cell = (zone - pad * 2) / n
        val idx = ((event.x - pad) / cell).toInt().coerceIn(0, n - 1)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        selected = idx
        onColor(colors[idx])
        invalidate()
        return true
    }
}