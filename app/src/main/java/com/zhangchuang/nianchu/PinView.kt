package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 图钉按钮。
 * 未钉住：**浅色半透明**（几乎不抢视觉），钉住：实心深色 + 白色图标。
 */
class PinView(context: Context) : View(context) {

    var pinned = true
        set(v) { field = v; invalidate() }

    var onToggle: (() -> Unit)? = null

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (pinned) {
            // 钉住（实体）：浅色实心 + 深色图钉
            bg.color = if (dark) 0xFFE8EAEE.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawCircle(width / 2f, height / 2f, r, bg)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * resources.displayMetrics.density
                color = if (dark) 0x33000000 else 0x1F000000
            }
            canvas.drawCircle(width / 2f, height / 2f, r - ring.strokeWidth / 2f, ring)
            Ico.draw(canvas, Ico.PIN,
                if (dark) 0xFF2F3A48.toInt() else 0xFF3A4552.toInt(),
                min(width, height) * 0.58f, width / 2f, height / 2f)
        } else {
            // 未钉住：浅色半透明，几乎不抢视觉
            bg.color = if (dark) 0x1FFFFFFF else 0x66FFFFFF
            canvas.drawCircle(width / 2f, height / 2f, r, bg)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * resources.displayMetrics.density
                color = if (dark) 0x33FFFFFF else 0x26000000
            }
            canvas.drawCircle(width / 2f, height / 2f, r - ring.strokeWidth / 2f, ring)
            Ico.draw(canvas, Ico.PIN,
                if (dark) 0x80FFFFFF.toInt() else 0x803A4552.toInt(),
                min(width, height) * 0.58f, width / 2f, height / 2f)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            pinned = !pinned
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            onToggle?.invoke()
        }
        return true
    }
}