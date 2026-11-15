package com.zhangchuang.nianchu

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * 极简细线图标库。所有图标都按 24×24 视口设计，画在 (cx, cy) 为中心、边长 s 的正方形内。
 * 与文字标签配合使用，保证「一目了然」。
 */
object Ico {

    const val PEN = 0
    const val MARKER = 1
    const val ERASER = 2
    const val FILL = 3
    const val LINE = 4
    const val RECT = 5
    const val OVAL = 6
    const val ARROW = 7
    const val DROPPER = 8
    const val GALLERY = 9
    const val PIN = 10
    const val UNDO = 11
    const val REDO = 12
    const val TRASH = 13
    const val LAYERS = 14
    const val EXPORT = 15
    const val SHARE = 16
    const val GEAR = 17
    const val NEWDOC = 18
    const val SAVE = 19
    const val BACK = 20
    const val MENU = 21
    const val EYE = 22

    fun draw(canvas: Canvas, kind: Int, color: Int, s: Float, cx: Float, cy: Float) {
        val k = s / 24f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.9f * k
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        fun x(v: Float) = cx + v * k
        fun y(v: Float) = cy + v * k

        when (kind) {
            PEN -> {
                // 笔杆
                canvas.drawLine(x(-8f), y(8f), x(5f), y(-5f), p)
                canvas.drawLine(x(-5f), y(5f), x(8f), y(-8f), p)
                // 笔尖
                canvas.drawLine(x(-8f), y(8f), x(-5f), y(5f), p)
                canvas.drawLine(x(-8f), y(8f), x(-6f), y(4.4f), p)
                // 顶部笔帽
                canvas.drawLine(x(4f), y(-4f), x(8f), y(-8f), p)
            }
            MARKER -> {
                // 粗笔身（斜矩形）
                val body = Path().apply {
                    moveTo(x(-6f), y(4f)); lineTo(x(-2f), y(8f))
                    lineTo(x(7f), y(-1f)); lineTo(x(3f), y(-5f)); close()
                }
                canvas.drawPath(body, p)
                // 笔头（凿形）
                canvas.drawLine(x(-6f), y(4f), x(-8f), y(6f), p)
                canvas.drawLine(x(-8f), y(6f), x(-9f), y(7.4f), p)
                canvas.drawLine(x(-2f), y(8f), x(-4f), y(9.4f), p)
            }
            ERASER -> {
                // 斜放的橡皮
                val r = Path().apply {
                    moveTo(x(-7f), y(3f)); lineTo(x(-1f), y(-6f))
                    lineTo(x(7f), y(-1f)); lineTo(x(1f), y(8f)); close()
                }
                canvas.drawPath(r, p)
                canvas.drawLine(x(-4f), y(-1.5f), x(4f), y(3.5f), p)
            }
            FILL -> {
                // 倾斜的油漆桶
                val b = Path().apply {
                    moveTo(x(-7f), y(-2f)); lineTo(x(1f), y(-8f))
                    lineTo(x(8f), y(-1f)); lineTo(x(0f), y(5f)); close()
                }
                canvas.drawPath(b, p)
                canvas.drawLine(x(-7f), y(-2f), x(-9f), y(0f), p)
                // 滴落的漆
                canvas.drawLine(x(8f), y(4f), x(8f), y(7f), p)
                canvas.drawLine(x(8f), y(7f), x(9.5f), y(9f), p)
            }
            LINE -> {
                canvas.drawLine(x(-7f), y(7f), x(7f), y(-7f), p)
                canvas.drawCircle(x(-7f), y(7f), 1.8f * k, f)
                canvas.drawCircle(x(7f), y(-7f), 1.8f * k, f)
            }
            RECT -> canvas.drawRect(RectF(x(-8f), y(-6f), x(8f), y(6f)), p)
            OVAL -> canvas.drawOval(RectF(x(-9f), y(-6f), x(9f), y(6f)), p)
            ARROW -> {
                canvas.drawLine(x(-8f), y(7f), x(6f), y(-7f), p)
                canvas.drawLine(x(6f), y(-7f), x(0.5f), y(-7f), p)
                canvas.drawLine(x(6f), y(-7f), x(6f), y(-1.5f), p)
            }
            DROPPER -> {
                // 滴管：吸头 + 管身 + 尖
                canvas.drawLine(x(-8f), y(8f), x(-4f), y(4f), p)
                canvas.drawLine(x(-8f), y(8f), x(-6.2f), y(8f), p)
                canvas.drawLine(x(-8f), y(8f), x(-8f), y(6.2f), p)
                canvas.drawLine(x(-4f), y(4f), x(2f), y(-2f), p)
                canvas.drawLine(x(-1.5f), y(0.5f), x(4.5f), y(5.5f), p)
                canvas.drawCircle(x(6f), y(-6f), 3.4f * k, p)
            }
            GALLERY -> {
                val gap = 1.4f
                for (rx in intArrayOf(-1, 1)) for (ry in intArrayOf(-1, 1)) {
                    canvas.drawRect(
                        RectF(x(rx * 7.5f - if (rx > 0) 7f else 0f),
                            y(ry * 7.5f - if (ry > 0) 7f else 0f),
                            x(rx * 7.5f - if (rx > 0) 7f else 0f) + 5.6f * k,
                            y(ry * 7.5f - if (ry > 0) 7f else 0f) + 5.6f * k), p)
                }
            }
            PIN -> {
                // 图钉：头、颈、针
                canvas.drawLine(x(-4f), y(-6f), x(4f), y(-6f), p)
                canvas.drawLine(x(-3f), y(-6f), x(-1.5f), y(2f), p)
                canvas.drawLine(x(3f), y(-6f), x(1.5f), y(2f), p)
                canvas.drawLine(x(-3.5f), y(2f), x(3.5f), y(2f), p)
                canvas.drawLine(x(0f), y(2f), x(0f), y(8f), p)
            }
            UNDO -> {
                canvas.drawArc(RectF(x(-7f), y(-7f), x(7f), y(5f)), 200f, -160f, false, p)
                canvas.drawLine(x(-7f), y(-2f), x(-7f), y(-7f), p)
                canvas.drawLine(x(-7f), y(-7f), x(-2f), y(-7f), p)
            }
            REDO -> {
                canvas.drawArc(RectF(x(-7f), y(-7f), x(7f), y(5f)), 340f, 160f, false, p)
                canvas.drawLine(x(7f), y(-2f), x(7f), y(-7f), p)
                canvas.drawLine(x(7f), y(-7f), x(2f), y(-7f), p)
            }
            TRASH -> {
                canvas.drawRect(RectF(x(-5f), y(-4f), x(5f), y(8f)), p)
                canvas.drawLine(x(-7f), y(-6f), x(7f), y(-6f), p)
                canvas.drawLine(x(-3f), y(-6f), x(-2f), y(-8f), p)
                canvas.drawLine(x(3f), y(-6f), x(2f), y(-8f), p)
                canvas.drawLine(x(-2f), y(-1f), x(-2f), y(5f), p)
                canvas.drawLine(x(2f), y(-1f), x(2f), y(5f), p)
            }
            LAYERS -> {
                fun diamond(off: Float) {
                    val d = Path().apply {
                        moveTo(x(0f), y(-8f + off)); lineTo(x(9f), y(-3f + off))
                        lineTo(x(0f), y(2f + off)); lineTo(x(-9f), y(-3f + off)); close()
                    }
                    canvas.drawPath(d, p)
                }
                diamond(0f); diamond(4.5f); diamond(9f)
            }
            EXPORT -> {
                canvas.drawLine(x(0f), y(-8f), x(0f), y(3f), p)
                canvas.drawLine(x(0f), y(3f), x(-4f), y(-1f), p)
                canvas.drawLine(x(0f), y(3f), x(4f), y(-1f), p)
                canvas.drawLine(x(-8f), y(3f), x(-8f), y(8f), p)
                canvas.drawLine(x(8f), y(3f), x(8f), y(8f), p)
                canvas.drawLine(x(-8f), y(8f), x(8f), y(8f), p)
            }
            SHARE -> {
                canvas.drawCircle(x(-6f), y(-5f), 2.2f * k, p)
                canvas.drawCircle(x(6f), y(1f), 2.2f * k, p)
                canvas.drawCircle(x(-2f), y(7f), 2.2f * k, p)
                canvas.drawLine(x(-4.4f), y(-3.8f), x(4.4f), y(-0.2f), p)
                canvas.drawLine(x(-4.7f), y(5.8f), x(4.7f), y(2.2f), p)
            }
            GEAR -> {
                canvas.drawCircle(x(0f), y(0f), 4.2f * k, p)
                canvas.drawCircle(x(0f), y(0f), 1.6f * k, p)
                for (ang in 0 until 360 step 45) {
                    val r = Math.toRadians(ang.toDouble())
                    canvas.drawLine(
                        x((6.2f * cos(r)).toFloat()), y((6.2f * sin(r)).toFloat()),
                        x((8.4f * cos(r)).toFloat()), y((8.4f * sin(r)).toFloat()), p)
                }
            }
            NEWDOC -> {
                canvas.drawLine(x(-6f), y(-8f), x(4f), y(-8f), p)
                canvas.drawLine(x(4f), y(-8f), x(7f), y(-5f), p)
                canvas.drawLine(x(7f), y(-5f), x(7f), y(8f), p)
                canvas.drawLine(x(7f), y(8f), x(-6f), y(8f), p)
                canvas.drawLine(x(-6f), y(8f), x(-6f), y(-8f), p)
                canvas.drawLine(x(0.5f), y(-2f), x(0.5f), y(5f), p)
                canvas.drawLine(x(-3f), y(1.5f), x(4f), y(1.5f), p)
            }
            SAVE -> {
                canvas.drawLine(x(-7f), y(-8f), x(5f), y(-8f), p)
                canvas.drawLine(x(5f), y(-8f), x(8f), y(-5f), p)
                canvas.drawLine(x(8f), y(-5f), x(8f), y(8f), p)
                canvas.drawLine(x(8f), y(8f), x(-7f), y(8f), p)
                canvas.drawLine(x(-7f), y(8f), x(-7f), y(-8f), p)
                canvas.drawRect(RectF(x(-3f), y(-8f), x(3f), y(-3f)), p)
                canvas.drawRect(RectF(x(-4f), y(3f), x(4f), y(8f)), p)
            }
            BACK -> {
                canvas.drawLine(x(2f), y(-8f), x(-6f), y(0f), p)
                canvas.drawLine(x(-6f), y(0f), x(2f), y(8f), p)
                canvas.drawLine(x(-6f), y(0f), x(8f), y(0f), p)
            }
            MENU -> {
                for (dy in floatArrayOf(-6f, 0f, 6f)) canvas.drawLine(x(-7f), y(dy), x(7f), y(dy), p)
            }
            EYE -> {
                canvas.drawOval(RectF(x(-9f), y(-5f), x(9f), y(5f)), p)
                canvas.drawCircle(x(0f), y(0f), 2.6f * k, p)
            }
        }
    }
}