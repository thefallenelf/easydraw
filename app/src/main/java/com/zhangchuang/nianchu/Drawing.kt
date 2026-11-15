package com.zhangchuang.nianchu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * 画布文档（图层为「seed 栅格 + 操作回放」双源模型）。
 *
 * 不变式：**层的最终内容永远 = seed + 有序回放 ops**。
 */
class Drawing(var width: Int, var height: Int) {

    /** 图层列表：下标 0 = 最底层，列表尾 = 最顶层 */
    val layers = ArrayList<Layer>()

    /** 画纸底色（浅色纸白 / 深色跟随主题） */
    var paperColor = 0xFFFCFAF4.toInt()

    private var basePaper: Bitmap? = null

    /** 当前选中层下标 */
    var activeIndex = 0

    val activeLayer: Layer? get() = layers.getOrNull(activeIndex)

    init {
        if (layers.isEmpty()) addLayer("画布")
    }

    fun baseBitmap(): Bitmap {
        val b = basePaper
        if (b != null && !b.isRecycled && b.width == width && b.height == height) return b
        val n = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        n.eraseColor(paperColor)
        basePaper = n
        return n
    }

    fun changePaperColor(c: Int) {
        if (paperColor == c) return
        paperColor = c
        basePaper?.recycle(); basePaper = null
    }

    fun addLayer(name: String? = null): Layer {
        val l = Layer(name ?: "图层${layers.size + 1}", width, height)
        layers.add(l)
        activeIndex = layers.size - 1
        return l
    }

    fun removeLayer(index: Int): Layer? {
        if (layers.size <= 1) return null
        val l = layers.removeAt(index)
        if (activeIndex > index) activeIndex--
        if (activeIndex >= layers.size) activeIndex = layers.size - 1
        l.recycle()
        return l
    }

    fun moveLayer(from: Int, to: Int) {
        if (from !in layers.indices || to !in layers.indices || from == to) return
        val l = layers.removeAt(from)
        val t = if (to > from) to - 1 else to
        layers.add(t, l)
        activeIndex = t
    }

    fun layerUp(index: Int): Boolean {
        if (index >= layers.size - 1) return false
        moveLayer(index, index + 1)
        return true
    }

    fun layerDown(index: Int): Boolean {
        if (index <= 0) return false
        moveLayer(index, index - 1)
        return true
    }

    fun repaintLayer(index: Int) {
        layers.getOrNull(index)?.markDirty()
    }

    fun repaintAll() {
        for (i in layers.indices) repaintLayer(i)
    }

    /**
     * 仅在「新建画布」时设定画布尺寸（之后屏幕旋转不再改动画布）。
     */
    fun resizeTo(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val sx = if (width > 0) w.toFloat() / width else 1f
        val sy = if (height > 0) h.toFloat() / height else 1f
        width = w
        height = h
        basePaper?.recycle()
        basePaper = null
        for (l in layers) l.resizeTo(w, h, sx, sy)
    }

    /**
     * 屏幕旋转适配（恢复 1.4.0 逻辑）：
     * 把画布内容整体旋转 90° 并缩放到新屏幕尺寸，使内容保持设备绝对方向。
     * clockwise = true 表示竖屏→横屏。
     */
    fun rotateTo(w: Int, h: Int, clockwise: Boolean) {
        val oldW = width
        val oldH = height
        if (oldW <= 0 || oldH <= 0 || w <= 0 || h <= 0) return
        for (l in layers) l.rotateTo(oldW, oldH, w, h, clockwise)
        width = w
        height = h
        basePaper?.recycle()
        basePaper = null
    }

    /** 合成整张成品（paper → 各可见层按序叠加）。调用方负责 recycle。 */
    fun composite(): Bitmap {
        synchronized(this) {
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawBitmap(baseBitmap(), 0f, 0f, null)
            for (l in layers) {
                if (!l.visible) continue
                val rb = l.resultBitmap()
                if (rb.width != width || rb.height != height) continue
                if (l.opacity >= 1f) {
                    c.drawBitmap(rb, 0f, 0f, null)
                } else {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.alpha = (l.opacity * 255).toInt().coerceIn(0, 255)
                    c.drawBitmap(rb, 0f, 0f, p)
                }
            }
            return out
        }
    }

    fun recycle() {
        basePaper?.recycle(); basePaper = null
        for (l in layers) l.recycle()
        layers.clear()
    }
}

/**
 * 图层：
 * - seed：栅格基底（越层后为 null，载入/合并后为位图）
 * - ops：叠加操作（回放顺序即加入顺序）
 * - result：最近一次合成的结果缓存
 */
class Layer(
    var name: String,
    var width: Int,
    var height: Int
) {
    var seed: Bitmap? = null
    val ops = ArrayList<LayerOp>()

    var visible = true
    var opacity = 1f
    var edited = false

    private var result: Bitmap? = null

    fun markDirty() {
        result?.recycle()
        result = null
    }

    /** 层结果位图（seed + ops 回放） */
    fun resultBitmap(): Bitmap {
        val r = result
        if (r != null && !r.isRecycled) return r
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        seed?.let { c.drawBitmap(it, 0f, 0f, null) }
        for (op in ops) op.drawInto(c, b)
        result = b
        return b
    }

    /** 等比适配（保留兼容） */
    fun resizeTo(w: Int, h: Int, sx: Float, sy: Float) {
        val sd = seed
        if (sd != null && !sd.isRecycled && (sd.width != w || sd.height != h)) {
            val ns = Bitmap.createScaledBitmap(sd, w, h, true)
            seed = ns
            if (sd !== ns) sd.recycle()
        }
        val newOps = ArrayList<LayerOp>(ops.size)
        for (op in ops) newOps.add(op.scaled(sx, sy))
        ops.clear()
        ops.addAll(newOps)
        width = w
        height = h
        markDirty()
        resultBitmap()
    }

    /**
     * 旋转 90° + 缩放到新尺寸（位图 + 全部 op 同步变换）。
     */
    fun rotateTo(oldW: Int, oldH: Int, nw: Int, nh: Int, clockwise: Boolean) {
        val sx = nw.toFloat() / oldH
        val sy = nh.toFloat() / oldW
        val sd = seed
        if (sd != null && !sd.isRecycled) {
            val m = Matrix()
            if (clockwise) {
                m.postRotate(90f)
                m.postTranslate(oldH.toFloat(), 0f)
            } else {
                m.postRotate(-90f)
                m.postTranslate(0f, oldW.toFloat())
            }
            m.postScale(sx, sy)
            val nb = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
            val c = Canvas(nb)
            c.drawBitmap(sd, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            seed = nb
            sd.recycle()
        }
        val transformed = ArrayList<LayerOp>(ops.size)
        for (op in ops) transformed.add(op.rotateAndScale(oldW, oldH, nw, nh, clockwise))
        ops.clear()
        ops.addAll(transformed)
        width = nw
        height = nh
        markDirty()
        resultBitmap()
    }

    fun recycle() {
        seed?.recycle(); seed = null
        ops.clear()
        markDirty()
    }
}

object LayerUtil {
    fun transparent(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    /** 画纸底色：浅色纸白 / 深色（跟随系统） */
    const val PAPER_LIGHT = 0xFFFCFAF4.toInt()
    const val PAPER_DARK = 0xFF20242A.toInt()
}