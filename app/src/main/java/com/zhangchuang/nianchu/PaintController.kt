package com.zhangchuang.nianchu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 绘制状态机：把触摸事件翻译成 [LayerOp] 并提交到 [Drawing] 的活动图层。
 *
 * 手势生命周期：
 *  - [onTouchStart] → 按下（铅笔/马克笔直接开始记录；形状类按下起点；填充类立即填充）
 *  - [onTouchMove] → 移动（缩放采样、形状类拖拽、像素橡皮擦画到预览）
 *  - [onTouchEnd] → 抬起，生成 [LayerOp] 提交到图层并重画
 *
 * 撤销模型（op 重放）：
 *  - 一层位图 == 该层所有 op 按序重放。
 *  - undo = 弹出一个 op → 重画该层；redo 同理。FILL/形状都走同一条路径。
 *  - 像素橡皮 = 追加 ERASE op；按笔画橡皮 = 删除相交的笔触 op。
 */
class PaintController(
    val drawing: Drawing,
    val onChange: () -> Unit = {}
) {

    // ---- 当前工具状态 ----
    var tool = Tool.PEN
    var color = 0xFF212121.toInt()

    /** 笔头直径（dp 单位，1~60）。按密度换算成 px 后作为操作线宽。 */
    var brushDiameterDp = 6f

    /** 橡皮笔头是否为方形 */
    var eraserSquare = false

    /** 形状是否实心 */
    var shapeFilled = false

    var eraserModePixel = false   // false = 按笔画擦除；true = 像素橡皮

    var palmRejection = true

    // 记号笔线性宽度（px @ mdpi，随密度缩放由调用方设置）
    var densityScale: Float = 1f

    // ---- 手势瞬时状态 ----
    private var active = false
    private val pts = ArrayList<Float>()
    private val prs = ArrayList<Float>()
    private var lastX = 0f
    private var lastY = 0f
    private var moveScaled = false
    private var touchingWithStylus = -1

    // 形状类拖拽预览：起点 / 当前点
    private var shapeStartX = 0f
    private var shapeStartY = 0f
    private var shapeCurX = 0f
    private var shapeCurY = 0f

    // 撤销 / 重做栈（每个 Swing 里存 op）
    private val undoStack = CopyOnWriteArrayList<LayerOp>()
    private val redoStack = CopyOnWriteArrayList<LayerOp>()

    /** 最近一次使用的笔触（用于按笔画橡皮） */
    var lastStroke: LayerOp? = null
        private set

    /** 当前手势中的预览 op（供 PaintView 画覆盖层，形状类用） */
    var previewOp: LayerOp? = null
        private set

    /** 是否正在绘制（用于隐藏工具栏） */
    var isDrawing: Boolean = false
        private set

    /** 取色回调（EYEDROPPER），由外层设置 */
    var onPickColor: ((Int) -> Unit)? = null

    /** 填充完成回调（用于提示，可选） */
    var onFillDone: ((Int) -> Unit)? = null

    val editingLayer: Layer? get() = drawing.activeLayer

    // 每一层最大 op 数（超出后合并为“栅格+单op”来控内存，极简化：仅提示，不强制）
    val maxOpsPerLayer = 2400

    /** 线宽（像素）：直径 dp → px */
    fun brushWidthPx(): Float {
        val px = brushDiameterDp * densityScale
        // 橡皮至少 12dp，避免“太细不好用”
        return if (tool == Tool.ERASER) px.coerceAtLeast(12f * densityScale) else px
    }

    fun isSquareHead(): Boolean = tool == Tool.ERASER && eraserSquare

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    interface Listener {
        fun onUndoRedoChanged()
        fun onLayerChanged()
        fun onExtraChanged()
    }
    private val listeners = CopyOnWriteArrayList<Listener>()
    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }

    private fun dirtyListeners() {
        for (l in listeners) l.onExtraChanged()
    }

    private fun notifyUndoRedo() {
        for (l in listeners) l.onUndoRedoChanged()
    }

    // 取消正在进行的输入
    fun cancelGesture() {
        active = false
        pts.clear(); prs.clear()
        previewOp = null
        isDrawing = false
    }

    fun clearRedo() = redoStack.clear()

    fun undo() {
        val l = drawing.activeLayer ?: return
        val op = l.ops.removeLastOrNull() ?: return
        redoStack.add(op)
        if (redoStack.size > 64) redoStack.removeAt(0)
        drawing.repaintLayer(drawing.activeIndex)
        lastStroke = l.ops.lastOrNull()
        notifyUndoRedo()
        onChange()
    }

    fun redo() {
        val l = drawing.activeLayer ?: return
        val op = redoStack.removeLastOrNull() ?: return
        l.ops.add(op)
        drawing.repaintLayer(drawing.activeIndex)
        lastStroke = op
        notifyUndoRedo()
        onChange()
    }

    /*
     * 触摸事件由 PaintView 转发（坐标为视图坐标）。
     */
    fun handleActionDown(x: Float, y: Float, pressure: Float, toolType: Int) {
        val l = drawing.activeLayer ?: return
        clearRedo()
        active = true
        isDrawing = true
        touchingWithStylus = if (toolType == MotionEvent.TOOL_TYPE_STYLUS) 1 else 0

        when (tool) {
            Tool.PEN, Tool.MARKER -> {
                pts.clear(); prs.clear()
                addPoint(x, y, pressure)
                previewOp = buildStrokeOp(fromPreview = true)
            }
            Tool.ERASER -> {
                pts.clear(); prs.clear()
                if (eraserModePixel) {
                    addPoint(x, y, 1f)
                    previewOp = buildStrokeOp(fromPreview = true)
                } else {
                    // 按笔画擦除：记录普查路径，等抬起时删除相交 op
                    pts.clear(); prs.clear()
                    addPoint(x, y, 1f)
                }
            }
            Tool.LINE, Tool.RECT, Tool.OVAL, Tool.ARROW -> {
                shapeStartX = x; shapeStartY = y
                shapeCurX = x; shapeCurY = y
                previewOp = buildShapeOp(shapeStartX, shapeStartY, x, y)
            }
            Tool.FILL -> {
                // 在该层执行一次泛洪填充（立即提交）
                floodFillAt(x, y)
            }
            Tool.EYEDROPPER -> {
                onPickColor?.invoke(pickColorAt(x, y))
            }
        }
        onChange()
    }

    fun handleActionMove(x: Float, y: Float, pressure: Float, toolType: Int) {
        if (!active) return
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS) touchingWithStylus = 1

        // 手掌防误触：当手（非笔、非手指）与笔并存时忽略手指；若当前在用手（手指）时遇到笔，允许笔接管
        // 简化：仅当“工具类型包含手指（TOOL_TYPE_UNKNOWN/FINGER）且另一只笔/手指已按下”时忽略
        val isPalming = palmRejection &&
            touchingWithStylus == 1 &&
            (toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_UNKNOWN) &&
            pts.isNotEmpty()
        // 我们只在“本手势已有笔”后，不再接收新增的手掌
        when (tool) {
            Tool.PEN, Tool.MARKER, Tool.ERASER -> {
                if (isPalming) return
                addPoint(x, y, pressure)
                // 采样合并：距离过近的点合并
                previewOp = buildStrokeOp(fromPreview = true)
            }
            Tool.LINE, Tool.RECT, Tool.OVAL, Tool.ARROW -> {
                shapeCurX = x; shapeCurY = y
                previewOp = buildShapeOp(shapeStartX, shapeStartY, x, y)
            }
            Tool.FILL -> {}
            Tool.EYEDROPPER -> {}
        }
        onChange()
    }

    fun handleActionUp(x: Float, y: Float, pressure: Float, toolType: Int) {
        if (!active) return
        val l = drawing.activeLayer ?: run { active = false; return }
        when (tool) {
            Tool.PEN, Tool.MARKER, Tool.ERASER -> {
                val stroke = buildStrokeOp(fromPreview = false)
                if (tool == Tool.ERASER && !eraserModePixel) {
                    eraseStrokesTouching(stroke)
                } else if (stroke.pointCount > 0) {
                    commitOp(stroke)
                }
                lastStroke = stroke
                previewOp = null
            }
            Tool.LINE, Tool.RECT, Tool.OVAL, Tool.ARROW -> {
                shapeCurX = x; shapeCurY = y
                if (hypot(shapeCurX - shapeStartX, shapeCurY - shapeStartY) < 3f * densityScale) {
                    previewOp = null
                } else {
                    val op = buildShapeOp(shapeStartX, shapeStartY, x, y)
                    commitOp(op)
                    lastStroke = op
                    previewOp = null
                }
            }
            Tool.FILL -> {}
            Tool.EYEDROPPER -> {
                // 取色后可从任意位置抬起，什么都不做
            }
        }
        active = false
        isDrawing = false
        pts.clear(); prs.clear()
        cancelGesture()
        notifyUndoRedo()
        onChange()
    }

    private fun addPoint(x: Float, y: Float, pressure: Float) {
        // 太近的点跳过（0.6px 内视为重复），保留尽可能多的轨迹点以保证平滑
        if (pts.size >= 2) {
            val lx = pts[pts.size - 2]; val ly = pts[pts.size - 1]
            if (Math.abs(x - lx) < 0.6f && Math.abs(y - ly) < 0.6f) {
                if (prs.isNotEmpty()) prs[prs.size - 1] = pressure
                return
            }
        }
        pts.add(x); pts.add(y)
        prs.add(pressure.coerceIn(0.05f, 1f))
    }

    private fun buildStrokeOp(fromPreview: Boolean): LayerOp {
        if (pts.size < 2) {
            val x = pts.getOrElse(0) { 0f }; val y = pts.getOrElse(1) { 0f }
            return singlePointStroke(x, y)
        }
        val kind = when (tool) {
            Tool.PEN -> OpKind.PEN
            Tool.MARKER -> OpKind.MARKER
            Tool.ERASER -> OpKind.ERASE
            else -> OpKind.PEN
        }
        return LayerOp(kind, color, brushWidthPx().coerceAtLeast(1f),
            FloatArray(pts.size) { pts[it] }, prs.toFloatArray(),
            square = isSquareHead())
    }

    private fun singlePointStroke(x: Float, y: Float): LayerOp {
        val kind = when (tool) {
            Tool.PEN -> OpKind.PEN
            Tool.MARKER -> OpKind.MARKER
            Tool.ERASER -> OpKind.ERASE
            else -> OpKind.PEN
        }
        return LayerOp(kind, color, brushWidthPx().coerceAtLeast(1f),
            floatArrayOf(x, y), floatArrayOf(1f), square = isSquareHead())
    }

    private fun buildShapeOp(x0: Float, y0: Float, x1: Float, y1: Float): LayerOp {
        val kind = when (tool) {
            Tool.LINE -> OpKind.LINE
            Tool.RECT -> OpKind.RECT
            Tool.OVAL -> OpKind.OVAL
            Tool.ARROW -> OpKind.ARROW
            else -> OpKind.RECT
        }
        return LayerOp(kind, color, brushWidthPx().coerceAtLeast(1f),
            floatArrayOf(x0, y0, x1, y1), filled = shapeFilled)
    }

    /** 提交一个 op 到当前图层并重画 */
    fun commitOp(op: LayerOp) {
        val l = drawing.activeLayer ?: return
        val copy = op.copyDeep()
        l.ops.add(copy)
        l.edited = true
        // 防爆：超过上限时合并成像素位图并清空 op（保留最近 1/4）
        if (l.ops.size > maxOpsPerLayer) {
            mergeOps(l)
        }
        drawing.repaintLayer(drawing.activeIndex)
        lastStroke = copy
    }

    private fun mergeOps(l: Layer) {
        // 保留最多 maxOpsPerLayer 的 1/4，前面全部栅格化进位图
        val keep = (maxOpsPerLayer / 4).coerceAtLeast(64)
        if (l.ops.size <= keep) return
        val bmp = l.resultBitmap()
        val c = Canvas(bmp)
        val all = l.ops
        // 全部重画（位图是重放结果，直接覆盖即可）
        val old = all.toList()
        l.ops.clear()
        for (op in old) {
            if (op.kind != OpKind.FILL) op.drawInto(c, bmp)
        }
        // 重画后保留最近 keep 个 op，作为“历史行为”的替代（近似）
        // ——这里以整层栅格为唯一事实，op 列表仅保留最近以支持后续撤销到“合并点”。
        val keepOps = old.takeLast(keep).map { it.copyDeep() }
        l.ops.addAll(keepOps)
    }

    /** 按笔画擦除：从当前层删除与擦除笔触相交的笔触 op */
    private fun eraseStrokesTouching(eraseOp: LayerOp) {
        val l = drawing.activeLayer ?: return
        val margin = 6f * densityScale
        val toRemove = l.ops.filter {
            it.kind != OpKind.FILL && strokeIntersects(it, eraseOp, margin)
        }
        if (toRemove.isEmpty()) return
        l.ops.removeAll(toRemove.toSet())
        l.edited = true
        drawing.repaintLayer(drawing.activeIndex)
        lastStroke = l.ops.lastOrNull { it.kind != OpKind.FILL && it.kind != OpKind.ERASE }
    }

    /** 像素橡皮擦：直接追加一个 ERASE op */
    private fun erasePixel(x: Float, y: Float, pressure: Float) {
        val l = drawing.activeLayer ?: return
        val op = singlePointStroke(x, y)
        commitOp(op)
    }

    /** 泛洪填充（立即） */
    private fun floodFillAt(x: Float, y: Float) {
        val l = drawing.activeLayer ?: return
        val op = LayerOp(OpKind.FILL, color, 1f,
            floatArrayOf(x, y), tolerance = 32)
        commitOp(op)
        onFillDone?.invoke(color)
    }

    /** 取色：在当前合成图像素 */
    private fun pickColorAt(x: Float, y: Float): Int {
        val comp = drawing.composite()
        val c = try {
            comp.getPixel(x.toInt().coerceIn(0, comp.width - 1),
                y.toInt().coerceIn(0, comp.height - 1))
        } catch (e: Exception) { comp.getPixel(0, 0) }
        comp.recycle()
        return c
    }

    /** 旋转/尺寸变化后的缩放重放：由外层调用 drawing.resizeTo 后调用 */
    fun onResizePlayback() {
        drawing.repaintAll()
        onChange()
    }

    /** 屏幕层面缓存合成（PaintView 调用） */
    fun snapshotComposite(): Bitmap = drawing.composite()
}