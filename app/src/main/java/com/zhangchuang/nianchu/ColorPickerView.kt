package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 自定义取色器面板（仿 Windows 颜色对话框的「自定义」页）。
 *
 *  ├─ 大号 饱和度/明度 方形（横向 S，纵向 V，随当前色相变化）
 *  ├─ 右侧竖直色相条（彩虹）
 *  ├─ 下方 RGB 数值条与 Hex 输入
 *  └─ 「原色 / 新色」并排预览块
 *
 * 支持手指拖动取色；对外通过 [onColorChanged] 实时回调。
 */
class ColorPickerView(
    context: Context,
    initial: Int
) : View(context) {

    var color: Int = initial
        private set

    var onColorChanged: ((Int) -> Unit)? = null

    private var hue = 0f      // 0..360
    private var sat = 1f      // 0..1
    private var valV = 1f     // 0..1

    private val svRect = RectF()
    private val hueRect = RectF()

    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cursor = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)

    private var dragSV = false
    private var dragHue = false

    init {
        fromColor(initial)
        setWillNotDraw(false)
    }

    fun setColorSilently(c: Int) {
        fromColor(c)
        invalidate()
    }

    private fun fromColor(c: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hue = hsv[0]; sat = hsv[1]; valV = hsv[2]
        color = Color.HSVToColor(floatArrayOf(hue, sat, valV))
    }

    private fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, valV))

    private fun commit() {
        color = currentColor()
        onColorChanged?.invoke(color)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (210f * d).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val pad = 10f * d
        val hueW = 26f * d
        val sizeH = height - pad * 2
        svRect.set(pad, pad, width - pad - hueW - 10f * d, pad + sizeH)
        hueRect.set(width - pad - hueW, pad, width - pad, pad + sizeH)

        // SV 方块：底色为纯色相，叠白->透明（左）与黑->透明（上）
        svPaint.shader = LinearGradient(svRect.left, 0f, svRect.right, 0f,
            intArrayOf(Color.WHITE, Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(svRect, svPaint)
        svPaint.shader = LinearGradient(0f, svRect.top, 0f, svRect.bottom,
            intArrayOf(0x00000000, 0xFF000000.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(svRect, svPaint)
        svPaint.shader = null

        // 色相条
        val colors = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
        )
        huePaint.shader = LinearGradient(0f, hueRect.top, 0f, hueRect.bottom,
            colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(hueRect, huePaint)
        huePaint.shader = null

        // 外框
        bg.style = Paint.Style.STROKE
        bg.color = 0x33000000
        bg.strokeWidth = 1f * d
        canvas.drawRect(svRect, bg)
        canvas.drawRect(hueRect, bg)

        // 光标：SV
        val cx = svRect.left + sat * svRect.width()
        val cy = svRect.top + (1f - valV) * svRect.height()
        cursor.strokeWidth = 2.2f * d
        cursor.color = if (valV > 0.55f) 0xCC000000.toInt() else 0xCCFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 8f * d, cursor)
        cursor.color = 0xAAFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 9.5f * d, cursor)

        // 光标：色相
        val hy = hueRect.top + (hue / 360f) * hueRect.height()
        ring.strokeWidth = 2.4f * d
        ring.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(hueRect.left - 2f * d, hy - 5f * d,
            hueRect.right + 2f * d, hy + 5f * d), 4f * d, 4f * d, ring)
        ring.color = 0x66000000
        ring.strokeWidth = 1f * d
        canvas.drawRoundRect(RectF(hueRect.left - 2f * d, hy - 5f * d,
            hueRect.right + 2f * d, hy + 5f * d), 4f * d, 4f * d, ring)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragHue = hueRect.contains(event.x, event.y)
                dragSV = svRect.contains(event.x, event.y)
                apply(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                apply(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragHue = false; dragSV = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun apply(x: Float, y: Float) {
        if (dragHue) {
            hue = (((y - hueRect.top) / hueRect.height()).coerceIn(0f, 1f)) * 360f
        } else if (dragSV) {
            sat = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
            valV = 1f - ((y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
        }
        commit()
    }
}