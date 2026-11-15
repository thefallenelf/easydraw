package com.zhangchuang.nianchu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 图层操作的种类 */
enum class OpKind { PEN, MARKER, ERASE, FILL, LINE, RECT, OVAL, ARROW }

/** 马克笔不透明度（叠加式半透明） */
const val OP_ALPHA_MARKER = 0x66

/**
 * 一笔（可回放）操作。图层位图 == 所有操作按序画进一张透明图的结果。
 * points 为平面坐标 [x0,y0,...]；pressures 与点数一一对应（null = 满压）。
 *
 * 绘制平滑：铅笔用「中点二次贝塞尔」把折线转成平滑曲线，再沿曲线按压力叠印圆片，
 * 避免快速运笔时出现一颗颗点。
 */
class LayerOp(
    val kind: OpKind,
    val color: Int,
    val width: Float,
    val points: FloatArray,
    val pressures: FloatArray? = null,
    val tolerance: Int = 32,
    val filled: Boolean = false,
    /** 橡皮笔头是否为方形 */
    val square: Boolean = false
) {
    val ox: Float get() = points.getOrElse(0) { 0f }
    val oy: Float get() = points.getOrElse(1) { 0f }
    val pointCount: Int get() = points.size / 2

    private fun pressure(i: Int): Float = pressures?.getOrNull(i)?.coerceIn(0.05f, 1f) ?: 1f

    fun scaled(sx: Float, sy: Float): LayerOp {
        val pts = FloatArray(points.size) { i -> if ((i and 1) == 0) points[i] * sx else points[i] * sy }
        return LayerOp(kind, color, width * ((sx + sy) / 2f), pts,
            pressures?.copyOf(), tolerance, filled, square)
    }

    /**
     * 旋转 90° + 缩放到新尺寸（与 Layer.rotateTo 位图变换保持一致）。
     * clockwise=true：竖屏→横屏。
     */
    fun rotateAndScale(oldW: Int, oldH: Int, newW: Int, newH: Int, clockwise: Boolean): LayerOp {
        val sx = newW.toFloat() / oldH
        val sy = newH.toFloat() / oldW
        val pts = FloatArray(points.size) { i ->
            val x = points[(i / 2) * 2]
            val y = points[(i / 2) * 2 + 1]
            if (i % 2 == 0) {
                if (clockwise) (oldH - y) * sx else y * sx
            } else {
                if (clockwise) x * sy else (oldW - x) * sy
            }
        }
        return LayerOp(kind, color, width * ((sx + sy) / 2f), pts,
            pressures?.copyOf(), tolerance, filled, square)
    }

    /**
     * 把操作坐标顺时针旋转 steps×90°（正方形画布，steps=1/2/3）。
     */
    fun rotate90cw(side: Float, steps: Int): LayerOp {
        val pts = FloatArray(points.size) { i ->
            val x = points[(i / 2) * 2]
            val y = points[(i / 2) * 2 + 1]
            when (steps % 4) {
                1 -> if (i % 2 == 0) side - y else x
                2 -> if (i % 2 == 0) side - x else side - y
                3 -> if (i % 2 == 0) y else side - x
                else -> if (i % 2 == 0) x else y
            }
        }
        return LayerOp(kind, color, width, pts,
            pressures?.copyOf(), tolerance, filled, square)
    }

    fun copyDeep(): LayerOp =
        LayerOp(kind, color, width, points.copyOf(), pressures?.copyOf(), tolerance, filled, square)

    fun drawInto(canvas: Canvas, target: Bitmap) {
        when (kind) {
            OpKind.PEN -> drawPen(canvas)
            OpKind.MARKER -> drawMarker(canvas)
            OpKind.ERASE -> drawErase(canvas)
            OpKind.FILL -> floodFill(target, ox.toInt(), oy.toInt(), color, tolerance)
            else -> drawShape(canvas)
        }
    }

    fun drawPreview(canvas: Canvas) {
        when (kind) {
            OpKind.PEN -> drawPen(canvas)
            OpKind.MARKER -> drawMarker(canvas)
            OpKind.ERASE -> drawErasePreview(canvas)
            else -> drawShape(canvas)
        }
    }

    // ---------- 平滑曲线 ----------

    /** 用二次贝塞尔通过中点，得到平滑路径 */
    private fun smoothPath(): Path {
        val path = Path()
        val n = pointCount
        if (n == 0) return path
        if (n == 1) {
            path.moveTo(points[0], points[1])
            return path
        }
        path.moveTo(points[0], points[1])
        if (n == 2) {
            path.lineTo(points[2], points[3])
            return path
        }
        var i = 1
        while (i < n - 1) {
            val cx = points[i * 2]; val cy = points[i * 2 + 1]
            val nx = points[(i + 1) * 2]; val ny = points[(i + 1) * 2 + 1]
            val mx = (cx + nx) / 2f; val my = (cy + ny) / 2f
            path.quadTo(cx, cy, mx, my)
            i++
        }
        path.lineTo(points[(n - 1) * 2], points[(n - 1) * 2 + 1])
        return path
    }

    private fun hasPressureVariation(): Boolean {
        val pr = pressures ?: return false
        if (pr.size < 2) return false
        var lo = 1f; var hi = 0f
        for (v in pr) { if (v < lo) lo = v; if (v > hi) hi = v }
        return (hi - lo) > 0.18f
    }

    private fun drawPen(canvas: Canvas) {
        // 恒定宽度平滑描边：预览与提交走同一函数、同一宽度，所见即所得。
        if (pointCount == 0) return
        if (pointCount == 1) {
            canvas.drawCircle(points[0], points[1], max(0.8f, width / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@LayerOp.color })
            return
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@LayerOp.color
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(smoothPath(), p)
    }

    /**
     * 沿「平滑后折线」以细步长叠印圆形，半径按对应采样点压力插值。
     * 步长取 min(2px, 半径*0.5)，远小于像素视觉直径，因此看不到颗粒。
     */
    /**
     * 沿「平滑后折线」以细步长叠印圆形。
     * （保留给马克笔压感；铅笔已改为恒定宽度直接描边）
     */
    private fun stampAlongSmoothedPath(canvas: Canvas, radiusAt: (Int) -> Float) {
        val n = pointCount
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        for (i in 0 until n - 1) {
            val x0 = points[i * 2]; val y0 = points[i * 2 + 1]
            val x1 = points[(i + 1) * 2]; val y1 = points[(i + 1) * 2 + 1]
            val r0 = radiusAt(i); val r1 = radiusAt(i + 1)
            val dist = hypot(x1 - x0, y1 - y0)
            val step = max(0.6f, min(2.2f, min(r0, r1) * 0.45f))
            val steps = max(1, (dist / step).toInt())
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                canvas.drawCircle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t,
                    max(0.8f, r0 + (r1 - r0) * t), paint)
            }
        }
    }

    private fun drawMarker(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@LayerOp.color
            alpha = OP_ALPHA_MARKER
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (pointCount == 1) {
            canvas.drawCircle(points[0], points[1], max(0.8f, width / 2f), p)
        } else {
            canvas.drawPath(smoothPath(), p)
        }
    }

    private fun drawErase(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        if (square) {
            if (pointCount == 1) {
                val r = width / 2f
                canvas.drawRect(RectF(points[0] - r, points[1] - r,
                    points[0] + r, points[1] + r), p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = width
                p.strokeCap = Paint.Cap.SQUARE
                p.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(smoothPath(), p)
            }
        } else {
            if (pointCount == 1) {
                canvas.drawCircle(points[0], points[1], max(1f, width / 2f), p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = width
                p.strokeCap = Paint.Cap.ROUND
                p.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(smoothPath(), p)
            }
        }
    }

    private fun drawErasePreview(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = 0x99000000.toInt()
        }
        if (pointCount == 0) return
        if (square) {
            if (pointCount == 1) {
                canvas.drawRect(RectF(points[0] - width / 2f, points[1] - width / 2f,
                    points[0] + width / 2f, points[1] + width / 2f), p)
            } else {
                canvas.drawPath(smoothPath(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = width
                    strokeCap = Paint.Cap.SQUARE
                    color = 0x33000000
                    strokeJoin = Paint.Join.ROUND
                })
                canvas.drawRect(0f, 0f, 0f, 0f, p)
            }
        } else {
            if (pointCount == 1) {
                canvas.drawCircle(points[0], points[1], width / 2f, p)
            } else {
                canvas.drawPath(smoothPath(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = width
                    strokeCap = Paint.Cap.ROUND
                    color = 0x33000000
                    strokeJoin = Paint.Join.ROUND
                })
            }
        }
    }

    private fun drawShape(canvas: Canvas) {
        if (kind == OpKind.FILL) return
        if (pointCount < 2) return
        val x0 = points[0]; val y0 = points[1]
        val x1 = points[2]; val y1 = points[3]
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@LayerOp.color
            style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when (kind) {
            OpKind.LINE -> canvas.drawLine(x0, y0, x1, y1, p)
            OpKind.RECT -> canvas.drawRect(
                RectF(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)), p)
            OpKind.OVAL -> canvas.drawOval(
                RectF(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)), p)
            OpKind.ARROW -> {
                canvas.drawLine(x0, y0, x1, y1, p)
                val a = atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
                val hl = max(16f, width * 3.2f)
                val spread = 0.52
                for (side in doubleArrayOf(1.0, -1.0)) {
                    val ang = a + side * spread
                    canvas.drawLine(x1, y1,
                        (x1 - cos(ang) * hl).toFloat(), (y1 - sin(ang) * hl).toFloat(), p)
                }
            }
            else -> {}
        }
    }
}

/** 点到线段距离 */
fun pointSegDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val l2 = dx * dx + dy * dy
    val t = if (l2 < 1e-6f) 0f else ((px - ax) * dx + (py - ay) * dy) / l2
    val tc = t.coerceIn(0f, 1f)
    return hypot(px - (ax + dx * tc), py - (ay + dy * tc))
}

private fun samplePoints(op: LayerOp): List<Pair<Float, Float>> {
    if (op.pointCount <= 40) return (0 until op.pointCount).map { op.points[it * 2] to op.points[it * 2 + 1] }
    val out = ArrayList<Pair<Float, Float>>()
    for (k in 0..40) {
        val i = min((k.toDouble() * op.pointCount / 40.0).toInt(), op.pointCount - 1)
        out.add(op.points[i * 2] to op.points[i * 2 + 1])
    }
    return out
}

private fun segSegDist(x1: Float, y1: Float, x2: Float, y2: Float,
                       x3: Float, y3: Float, x4: Float, y4: Float): Float {
    var d = pointSegDist(x3, y3, x1, y1, x2, y2)
    d = min(d, pointSegDist(x4, y4, x1, y1, x2, y2))
    d = min(d, pointSegDist(x1, y1, x3, y3, x4, y4))
    d = min(d, pointSegDist(x2, y2, x3, y3, x4, y4))
    return d
}

/** 判断两条折线笔触是否相交（用于按笔画擦除） */
fun strokeIntersects(a: LayerOp, b: LayerOp, margin: Float): Boolean {
    val asamp = samplePoints(a)
    val bsamp = samplePoints(b)
    for (p in bsamp) {
        var best = Float.MAX_VALUE
        for (q in asamp) {
            val d = hypot(p.first - q.first, p.second - q.second)
            if (d < best) best = d
        }
        if (best <= (a.width + b.width) / 2f + margin) return true
    }
    val pa = a.points; val pb = b.points
    for (i in 0 until a.pointCount - 1) {
        for (j in 0 until b.pointCount - 1) {
            val d = segSegDist(
                pa[i * 2], pa[i * 2 + 1], pa[i * 2 + 2], pa[i * 2 + 3],
                pb[j * 2], pb[j * 2 + 1], pb[j * 2 + 2], pb[j * 2 + 3])
            if (d <= (a.width + b.width) / 2f + margin) return true
        }
    }
    return false
}

/** RGBA 四通道颜色距离 */
fun colorDist(a: Int, b: Int): Float {
    val da = (a shr 24 and 0xFF) - (b shr 24 and 0xFF)
    val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
    val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return sqrt((da * da + dr * dr + dg * dg + db * db).toFloat())
}

/** 扫描线泛洪填充 */
fun floodFill(bmp: Bitmap, seedX: Int, seedY: Int, fillColor: Int, tolerance: Int): Int {
    if (seedX < 0 || seedY < 0 || seedX >= bmp.width || seedY >= bmp.height) return 0
    val w = bmp.width; val h = bmp.height
    if (w <= 0 || h <= 0) return 0
    var changed = 0
    try {
        val pix = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)
        val seed = seedY * w + seedX
        val target = pix[seed]
        if (colorDist(target, fillColor) < 0.5f) return 0
        val tolF = tolerance.coerceAtLeast(1).toFloat()
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun push(i: Int) {
            if (sp < stack.size && !visited[i]) { visited[i] = true; stack[sp++] = i }
        }
        push(seed)
        while (sp > 0) {
            val idx = stack[--sp]
            if (colorDist(pix[idx], target) > tolF) continue
            pix[idx] = fillColor
            changed++
            val x = idx % w
            if (idx >= w) push(idx - w)
            if (idx < (h - 1) * w) push(idx + w)
            if (x > 0) push(idx - 1)
            if (x < w - 1) push(idx + 1)
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h)
    } catch (_: OutOfMemoryError) {
    }
    return changed
}