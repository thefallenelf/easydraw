package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * 画布视图 —— 「平板就是一张纸」。最终方案。
 *
 * 三层模型：
 *  - 数据层：画布 = 新生儿窗口尺寸（W×H），尺寸永久不变，永不缩放/旋转/重采样。
 *  - 显示层：系统把 Activity（工具）转到正立。本视图用矩阵把画布反向旋转
 *    「当前方向 − 出生方向」来抵消系统补偿 → 画布像纸一样保持出生朝向贴玻璃，
 *    而工具照常正立。旋转 90°/270° 后 W×H 恰好铺满 H×W 窗口：无灰边、无拉伸。
 *  - 触摸层：用同一矩阵的逆把窗口坐标映射回画布坐标，落笔精确。
 *
 * 方向：出生方向 = 首次调用 setDisplayRotation 时（画布建立时）的设备方向。
 */
class PaintView(
    context: Context,
    val controller: PaintController
) : View(context) {

    var onDrawingState: ((Boolean) -> Unit)? = null

    /** 画布出生时的设备方向（0/90/180/270） */
    private var bornRotation = -1

    /** 当前设备方向 */
    private var displayRotation = 0

    private val toWindow = Matrix()
    private val toCanvas = Matrix()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        rebuild()
    }

    /** 设备方向更新时调用（首次调用记录画布出生方向） */
    fun setDisplayRotation(curdeg: Int) {
        val c = ((curdeg % 360) + 360) % 360
        if (bornRotation < 0) {
            bornRotation = c
            displayRotation = c
            rebuild()
            invalidate()
            return
        }
        if (displayRotation == c) return
        displayRotation = c
        rebuild()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    fun updateFit() { rebuild(); invalidate() }
    fun resetView() { rebuild(); invalidate() }

    /** 画布相对其出生方向的转角（设备实际偏离角度） */
    private fun relativeRotation(): Int {
        if (bornRotation < 0) return 0
        var r = displayRotation - bornRotation
        r = ((r % 360) + 360) % 360
        return r
    }

    private fun rebuild() {
        toWindow.reset()
        val cw = controller.drawing.width.toFloat()
        val ch = controller.drawing.height.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (cw <= 0 || ch <= 0 || vw <= 0 || vh <= 0) {
            toCanvas.set(toWindow)
            return
        }
        val rel = relativeRotation()
        if (rel == 0) {
            toWindow.setValues(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
        } else {
            toWindow.postTranslate(-cw / 2f, -ch / 2f)
            toWindow.postRotate(-rel.toFloat())   // 方向已按实测取反
            toWindow.postTranslate(vw / 2f, vh / 2f)
        }
        if (!toWindow.invert(toCanvas)) {
            toCanvas.set(toWindow)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val d = controller.drawing
        if (d.width <= 0 || d.height <= 0) return

        canvas.drawColor(d.paperColor)

        canvas.save()
        canvas.concat(toWindow)
        canvas.clipRect(0f, 0f, d.width.toFloat(), d.height.toFloat())

        canvas.drawBitmap(d.baseBitmap(), 0f, 0f, null)
        for (l in d.layers) {
            if (!l.visible) continue
            val rb = l.resultBitmap()
            if (rb.width == 0 || rb.height == 0) continue
            if (l.opacity >= 1f) {
                canvas.drawBitmap(rb, 0f, 0f, null)
            } else {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.alpha = (l.opacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(rb, 0f, 0f, p)
            }
        }
        controller.previewOp?.drawPreview(canvas)
        canvas.restore()
    }

    private fun windowToCanvasPoint(x: Float, y: Float): FloatArray {
        val out = floatArrayOf(0f, 0f)
        toCanvas.mapPoints(out, floatArrayOf(x, y))
        return out
    }

    private fun inside(x: Float, y: Float): Boolean {
        val p = windowToCanvasPoint(x, y)
        val pad = 1.5f * resources.displayMetrics.density   // 边缘容差
        return p[0] >= -pad && p[1] >= -pad &&
            p[0] <= controller.drawing.width + pad && p[1] <= controller.drawing.height + pad
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val toolType = event.getToolType(0)

        if (action == MotionEvent.ACTION_DOWN && !inside(event.x, event.y)) return true

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                onDrawingState?.invoke(true)
                val p = windowToCanvasPoint(event.x, event.y)
                controller.handleActionDown(p[0], p[1], event.getPressure(0), toolType)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val h = event.historySize
                for (i in 0 until h) {
                    val p = windowToCanvasPoint(event.getHistoricalX(0, i), event.getHistoricalY(0, i))
                    controller.handleActionMove(
                        p[0], p[1],
                        event.getHistoricalPressure(0, i).coerceAtLeast(0.05f), toolType)
                }
                val p = windowToCanvasPoint(event.x, event.y)
                controller.handleActionMove(p[0], p[1], event.getPressure(0), toolType)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val p = windowToCanvasPoint(event.x, event.y)
                controller.handleActionUp(p[0], p[1], event.getPressure(0), toolType)
                onDrawingState?.invoke(false)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount > 1) {
                    controller.cancelGesture()
                    onDrawingState?.invoke(false)
                }
            }
        }
        invalidate()
        return true
    }
}