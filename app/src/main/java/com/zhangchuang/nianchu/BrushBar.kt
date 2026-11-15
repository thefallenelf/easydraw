package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 笔刷属性条：
 *  左：大小滑杆（对数手感，1dp~60dp 直径）+ 实时直径数字
 *  右：形状切换（圆形/方形，主要给橡皮用）
 *      填充切换（空心/实心，主要给形状用）
 *      橡皮模式（像素擦除 / 整笔删除）
 */
class BrushBar(
    context: Context,
    private val onSize: (dpDiameter: Float) -> Unit,
    private val onShape: (square: Boolean) -> Unit,
    private val onFillChanged: (Boolean) -> Unit,
    private val onEraserMode: (Boolean) -> Unit
) : View(context) {

    var diameterDp = 6f
        private set
    var squareHead = false
        private set
    var shapeFilled = false
        private set
    var eraserPixel = true
        private set

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val filledTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slider = RectF()

    // 右侧 chips
    private val chipLabels = arrayOf("圆形", "空心", "像素")
    private var chipRects = ArrayList<RectF>()

    fun setState(diameterDp: Float, square: Boolean, filled: Boolean, eraserPixel: Boolean) {
        this.diameterDp = diameterDp
        this.squareHead = square
        this.shapeFilled = filled
        this.eraserPixel = eraserPixel
        invalidate()
    }

    init { setWillNotDraw(false) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            min(MeasureSpec.getSize(widthMeasureSpec), (700f * d()).toInt()),
            resolveSize((54f * d()).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val dark = isDark()
        bg.color = if (dark) 0xFF2A2E35.toInt() else 0xFFFAF7EF.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
            20f * d(), 20f * d(), bg)
        val on = if (dark) 0xFFE8EAEE.toInt() else 0xFF2F3A48.toInt()
        val dim = if (dark) 0xFF717C8A.toInt() else 0xFF8A93A0.toInt()

        val chipsW = 3 * 62f * d() + 8f * d()
        val left = 56f * d()
        val right = width - chipsW - 16f * d()
        val cy = height / 2f
        slider.set(left, cy - 2f * d(), right, cy + 2f * d())

        // 滑杆
        track.color = 0x668A93A0.toInt()
        canvas.drawRoundRect(slider, 2f * d(), 2f * d(), track)
        val t = (diameterDp - MIN_D).toFloat() / (MAX_D - MIN_D)
        val fillX = left + (right - left) * t
        filledTrack.color = on
        canvas.drawRoundRect(RectF(left, cy - 2f * d(), fillX, cy + 2f * d()),
            2f * d(), 2f * d(), filledTrack)
        knob.color = on
        canvas.drawCircle(fillX, cy, 11f * d(), knob)
        knob.color = if (dark) 0xFF2A2E35.toInt() else 0xFFFAF7EF.toInt()
        canvas.drawCircle(fillX, cy, 5f * d(), knob)

        // 左侧：当前尺寸预览圆 + 数字
        val previewR = min(height * 0.30f, (diameterDp * d()) / 2f).coerceAtLeast(2f * d())
        val pvx = 26f * d()
        label.color = dim
        label.textSize = 10f * d()
        strokeFor(on).also { sp ->
            sp.strokeWidth = 1.6f * d()
            if (squareHead) {
                canvas.drawRect(RectF(pvx - previewR, cy - previewR, pvx + previewR, cy + previewR), sp)
            } else {
                canvas.drawCircle(pvx, cy, previewR, sp)
            }
        }
        label.color = on
        label.textSize = 11f * d()
        canvas.drawText("${diameterDp.toInt()}dp", pvx, height - 4f * d(), label)

        // 右侧 chips
        chipRects.clear()
        var cx = width - chipsW
        val cw = 60f * d()
        val chH = 32f * d()
        for (i in 0..2) {
            val r = RectF(cx, cy - chH / 2f, cx + cw, cy + chH / 2f)
            chipRects.add(r)
            val active = when (i) {
                0 -> squareHead
                1 -> shapeFilled
                else -> !eraserPixel
            }
            chip.color = if (active) {
                if (dark) 0xFF3F4A5C.toInt() else 0xFFDDE3EE.toInt()
            } else {
                if (dark) 0xFF343A45.toInt() else 0xFFEDE8DE.toInt()
            }
            canvas.drawRoundRect(r, 9f * d(), 9f * d(), chip)
            label.color = if (active) on else dim
            label.textSize = 12f * d()
            val txt = when (i) {
                0 -> if (squareHead) "方形" else "圆形"
                1 -> if (shapeFilled) "实心" else "空心"
                else -> if (eraserPixel) "像素擦" else "整笔删"
            }
            canvas.drawText(txt, r.centerX(), r.centerY() + 4f * d(), label)
            cx += cw + 4f * d()
        }
    }

    private fun strokeFor(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
    }

    private fun isDark(): Boolean =
        (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun d(): Float = resources.displayMetrics.density

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // chips
                for (i in chipRects.indices) {
                    if (chipRects[i].contains(event.x, event.y)) {
                        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
                        when (i) {
                            0 -> { squareHead = !squareHead; onShape(squareHead) }
                            1 -> { shapeFilled = !shapeFilled; onFillChanged(shapeFilled) }
                            2 -> { eraserPixel = !eraserPixel; onEraserMode(eraserPixel) }
                        }
                        invalidate()
                        return true
                    }
                }
                // slider
                if (slider.width() > 0f && event.x <= slider.right + 8f * d()) {
                    val v = ((event.x - slider.left) / slider.width()).coerceIn(0f, 1f)
                    // 对数映射：1~60dp
                    diameterDp = (MIN_D * Math.pow((MAX_D / MIN_D).toDouble(), v.toDouble())).toFloat()
                        .coerceIn(MIN_D, MAX_D)
                    onSize(diameterDp)
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val MIN_D = 1f
        const val MAX_D = 60f
    }
}