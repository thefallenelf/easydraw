package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 「标准」颜色页：仿 Windows 颜色对话框的标准色 ——
 *  · 蜂窝六边形色轮（色相按角度环绕，外圈饱和度更高）
 *  · 底部一行灰阶六边形 + 当前色六边形
 * 点任意色块即选中。
 */
class HexWheelView(
    context: Context,
    initial: Int
) : View(context) {

    var onColor: ((Int) -> Unit)? = null

    private var currentColor: Int = initial

    private data class Cell(val cx: Float, val cy: Float, val r: Float, val color: Int)
    private val cells = ArrayList<Cell>()
    private val grayCells = ArrayList<Cell>()
    private var currentHex = Cell(0f, 0f, 0f, initial)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22000000
        strokeWidth = 1f
    }
    private val selRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF1A73E8.toInt()
        strokeWidth = 2.6f
    }

    init { setWillNotDraw(false) }

    fun setColorSilently(c: Int) {
        currentColor = c
        currentHex = Cell(currentHex.cx, currentHex.cy, currentHex.r, c)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val h = (270f * d).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(h, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildLayout(w.toFloat(), h.toFloat())
    }

    private fun buildLayout(w: Float, h: Float) {
        cells.clear(); grayCells.clear()
        val d = resources.displayMetrics.density
        val pad = 12f * d
        val stripH = 54f * d
        val wheelH = h - stripH - pad * 2
        val R = 3
        // 点顶六边形：宽度 = sqrt(3)*r*(2R+1)，高度 = 3*r*R
        val rByW = (w - pad * 2) / (sqrt(3f) * (2 * R + 1))
        val rByH = wheelH / (3f * R + 0.6f)
        val rr = min(rByW, rByH) * 0.96f
        val cx0 = w / 2f
        val cy0 = pad + wheelH / 2f

        for (q in -R..R) {
            for (s2 in -R..R) {
                val ring = (kotlin.math.abs(q) + kotlin.math.abs(s2) +
                    kotlin.math.abs(q + s2)) / 2
                if (ring > R) continue
                val x = cx0 + sqrt(3f) * rr * (q + s2 / 2f)
                val y = cy0 + 1.5f * rr * s2
                val dx = x - cx0
                val dy = y - cy0
                val ang = Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat()
                val hue = (240f + ang + 360f) % 360f
                val sat = (0.30f + 0.68f * (ring.toFloat() / R)).coerceAtMost(1f)
                val col = Color.HSVToColor(floatArrayOf(hue, sat, 1f))
                cells.add(Cell(x, y, rr, col))
            }
        }

        // 灰阶行 + 当前色六边形
        val gy = h - stripH / 2f - pad * 0.5f
        val n = 9
        val stepX = (w - pad * 2) / n
        val gr = min(stepX * 0.42f, stripH * 0.40f)
        for (i in 0 until n - 1) {
            val v = 1f - i / (n - 2f)
            val c = Color.HSVToColor(floatArrayOf(0f, 0f, v))
            grayCells.add(Cell(pad + stepX * i + stepX / 2f, gy, gr, c))
        }
        currentHex = Cell(pad + stepX * (n - 1) + stepX / 2f, gy, gr * 1.25f, currentColor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (c in cells) drawHex(canvas, c, c.color, 1f)
        for (c in grayCells) drawHex(canvas, c, c.color, 1f)
        // 当前色六边形（黑白描边）
        drawHex(canvas, currentHex, currentColor, 1f)
        edge.strokeWidth = 2f
        canvas.drawPath(hexPath(currentHex.cx, currentHex.cy, currentHex.r), edge)
    }

    private fun drawHex(canvas: Canvas, c: Cell, color: Int, scale: Float) {
        fill.color = color
        val path = hexPath(c.cx, c.cy, c.r * scale)
        canvas.drawPath(path, fill)
        edge.color = 0x1F000000
        edge.strokeWidth = 1f
        canvas.drawPath(path, edge)
    }

    private fun hexPath(cx: Float, cy: Float, r: Float): Path {
        val p = Path()
        for (k in 0 until 6) {
            val a = Math.toRadians((60.0 * k + 30.0))
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_MOVE) return super.onTouchEvent(event)
        val x = event.x; val y = event.y
        for (c in cells + grayCells) {
            if (kotlin.math.hypot(x - c.cx, y - c.cy) <= c.r * 0.95f) {
                currentColor = c.color
                currentHex = Cell(currentHex.cx, currentHex.cy, currentHex.r, c.color)
                onColor?.invoke(c.color)
                invalidate()
                return true
            }
        }
        return true
    }
}