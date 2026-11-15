package com.zhangchuang.nianchu

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 主绘画界面。
 *
 * 布局（FrameLayout，画布铺满全屏，其余为覆盖层）：
 *   - PaintView         画布
 *   - topHolder         顶部工具条 + 左侧图钉
 *   - bottomHolder      底部工具栏 + 调色板 + 笔刷条 + 图钉
 *   - capsule           浮动胶囊（新建/保存/返回，可长按拖动）
 *   - hintView          点按控件时的文字气泡提示
 *
 * 可见性：默认全部常驻。每个区域都有独立的「图钉」按钮，钉住 = 常驻；
 * 取消钉住则自动隐藏（落笔即隐），再由对应图钉/胶囊唤出。
 */
class MainActivity : Activity() {

    lateinit var controller: PaintController
    private lateinit var paintView: PaintView
    private lateinit var root: FrameLayout

    private lateinit var topBar: TopBar
    private lateinit var brushBar: BrushBar
    private lateinit var paletteBar: Palettebar
    private lateinit var toolBar: ToolBar

    private lateinit var topPin: PinView
    private lateinit var bottomPin: PinView
    private lateinit var palettePin: PinView

    private lateinit var capsule: FloatingCapsule
    private lateinit var hintView: TextView

    private var projectId: String = ""
    private var projectTitle: String = "未命名"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sysTop = 0
    private var sysBottom = 0

    private var topPinned = true
    private var bottomPinned = true
    private var palettePinned = true

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        private const val TAG = "MainActivity"
        private const val VERSION = "1.10.0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setUpEdgeToEdge()

        // 目标工程
        val id = intent.getStringExtra(EXTRA_PROJECT_ID)
        var meta: ProjectIO.ProjectMeta? = null
        if (id != null) meta = ProjectIO.loadProject(this, id)
        if (meta == null && id == null) {
            val recent = ProjectIO.listProjects(this)
            if (recent.isNotEmpty()) {
                val top = recent.first()
                projectId = top.id; projectTitle = top.title; meta = top
            }
        }
        if (meta != null) {
            projectId = meta.id; projectTitle = meta.title
        } else {
            projectId = ProjectIO.newId(); projectTitle = "未命名"
        }

        // 文档与控制器
        val drawing = Drawing(1, 1)
        drawing.changePaperColor(paperForTheme())
        controller = PaintController(drawing) { onDrawingChanged() }
        controller.densityScale = resources.displayMetrics.density
        controller.tool = Tool.PEN
        controller.color = Prefs.getColor(this)
        controller.brushDiameterDp = Prefs.getBrushDiameterDp(this)
        controller.eraserSquare = Prefs.getEraserSquare(this)
        controller.shapeFilled = Prefs.getShapeFilled(this)
        controller.eraserModePixel = Prefs.getEraserPixel(this)
        controller.palmRejection = Prefs.getPalmRejection(this)
        controller.onPickColor = { c -> onPickedColor(c) }

        root = FrameLayout(this)
        setContentView(root)
        paintView = PaintView(this, controller)
        paintView.onDrawingState = { d -> if (d) onStrokeBegan() else onStrokeEnded() }
        root.addView(paintView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        buildOverlays()
        applyInsets()

        if (meta != null) ProjectIO.restoreInto(this, meta, drawing)

        root.doOnLayoutOnce {
            resizeToWindow()
            restoreState()
            layoutBars()
            capsule.setPositionRatio(Prefs.getCapsuleX(this), Prefs.getCapsuleY(this))
        }
    }

    /** 画布尺寸只在首次建立时确定，之后永久不变 */
    private var canvasSized = false

    private fun resizeToWindow() {
        // 画布数据仅在首次建立时确定，之后永久不变；
        // 旋转由 PaintView 的显示层矩阵补偿（画布像纸钉在玻璃上，工具正立）。
        if (!canvasSized) {
            val w = root.width.coerceAtLeast(1)
            val h = root.height.coerceAtLeast(1)
            if (w > 1 && h > 1) {
                controller.drawing.resizeTo(w, h)
                controller.drawing.changePaperColor(paperForTheme())
                canvasSized = true
            }
        }
        paintView.setDisplayRotation(displayRotationDeg())
        paintView.invalidate()
    }

    /** 屏幕旋转后：显示层补偿（画布不动、工具正立）+ 工具栏重排 */
    private fun onOrientationChanged() {
        mainHandler.post {
            resizeToWindow()
            layoutBars()
        }
    }

    /** 设备相对自然方向的转角（0/90/180/270，顺时针） */
    private fun displayRotationDeg(): Int {
        return try {
            when (windowManager.defaultDisplay.rotation) {
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun paperForTheme(): Int {
        val dark = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (dark) LayerUtil.PAPER_DARK else LayerUtil.PAPER_LIGHT
    }

    private fun setUpEdgeToEdge() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    // ---------------- 覆盖层 ----------------

    private fun buildOverlays() {
        // ===== 顶部条 + 左图钉 =====
        topBar = TopBar(this) { a -> onTopAction(a) }
        topBar.onHint = { showHint(it) }
        val topParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, (50f * d()).toInt(),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        topParams.topMargin = sysTop + 10.dp()
        root.addView(topBar, topParams)

        topPin = PinView(this).apply {
            pinned = Prefs.getTopPinned(this@MainActivity)
            onToggle = { toggleTopPin() }
        }
        val tp = FrameLayout.LayoutParams((36f * d()).toInt(), (36f * d()).toInt(),
            Gravity.TOP or Gravity.START)
        root.addView(topPin, tp)

        // ===== 底部：工具条 / 调色板 / 笔刷条 =====
        brushBar = BrushBar(this,
            { dp -> controller.brushDiameterDp = dp; Prefs.setBrushDiameterDp(this, dp)
                     keepAlive() },
            { square -> controller.eraserSquare = square; Prefs.setEraserSquare(this, square)
                        keepAlive() },
            { filled -> controller.shapeFilled = filled; Prefs.setShapeFilled(this, filled)
                        keepAlive() },
            { pixel -> controller.eraserModePixel = pixel; Prefs.setEraserPixel(this, pixel)
                       keepAlive() })
        val bbP = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        root.addView(brushBar, bbP)
        brushBar.visibility = View.GONE

        paletteBar = Palettebar(this) { c -> onColorPicked(c) }
        paletteBar.onCustomTap = { showColorPicker() }
        val pp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, (56f * d()).toInt(),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        root.addView(paletteBar, pp)

        toolBar = ToolBar(this) { t -> onToolSelected(t) }
        toolBar.onHint = { showHint(it) }
        val tP = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, (54f * d()).toInt(),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        root.addView(toolBar, tP)

        bottomPin = PinView(this).apply {
            pinned = Prefs.getBottomPinned(this@MainActivity)
            onToggle = { toggleBottomPin() }
        }
        val bp = FrameLayout.LayoutParams((36f * d()).toInt(), (36f * d()).toInt(),
            Gravity.BOTTOM or Gravity.END)
        root.addView(bottomPin, bp)

        palettePin = PinView(this).apply {
            pinned = Prefs.getPalettePinned(this@MainActivity)
            onToggle = { togglePalettePin() }
        }
        val ppin = FrameLayout.LayoutParams((32f * d()).toInt(), (32f * d()).toInt(),
            Gravity.BOTTOM or Gravity.END)
        root.addView(palettePin, ppin)

        // ===== 浮动胶囊 =====
        capsule = FloatingCapsule(this,
            onNew = { openNewProject() },
            onSave = { saveNow() },
            onBack = { backToGallery() })
        capsule.onMoved = { x, y -> Prefs.setCapsulePos(this, x, y) }
        root.addView(capsule, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START))

        // ===== 文字气泡提示 =====
        hintView = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
            val bg = GradientDrawable()
            bg.cornerRadius = 14f * d()
            bg.setColor(0xE6212121.toInt())
            setBackgroundDrawable(bg)
            alpha = 0f
            visibility = View.GONE
        }
        root.addView(hintView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))
    }

    private fun d(): Float = resources.displayMetrics.density

    private fun updatePinLook(v: PinView, pinned: Boolean) {
        v.pinned = pinned
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val s = insets.systemWindowInsets
            sysTop = s.top
            sysBottom = s.bottom
            layoutBars()
            insets
        }
    }

    /** 计算所有底部/顶部覆盖层的位置，保证互不重叠 */
    private fun layoutBars() {
        if (!this::topBar.isInitialized) return
        topBar.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = sysTop + 10.dp()
        }
        topPin.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = sysTop + 22.dp()
            leftMargin = 12.dp()
        }

        val toolH = toolBar.measuredHeight.takeIf { it > 0 } ?: (54f * d()).toInt()
        val palH = paletteBar.measuredHeight.takeIf { it > 0 } ?: (56f * d()).toInt()
        val brH = brushBar.measuredHeight.takeIf { it > 0 } ?: (54f * d()).toInt()

        var y = sysBottom + 10.dp()
        toolBar.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = y }
        y += toolH + 8.dp()
        if (Prefs.getShowPalette(this) && paletteBar.visibility == View.VISIBLE) {
            paletteBar.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = y }
            y += palH + 8.dp()
        }
        if (brushBar.visibility == View.VISIBLE) {
            brushBar.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = y }
        }

        bottomPin.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = sysBottom + 10.dp()
            rightMargin = 10.dp()
        }
        palettePin.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = sysBottom + toolH + 18.dp()
            rightMargin = 10.dp()
        }

        updatePinLook(topPin, topPinned)
        updatePinLook(bottomPin, bottomPinned)
        updatePinLook(palettePin, palettePinned)
        palettePin.visibility = if (Prefs.getShowPalette(this)) View.VISIBLE else View.GONE
    }

    private fun restoreState() {
        val c = Prefs.getColor(this)
        paletteBar.setSelected(c)
        toolBar.selected = Tool.PEN
        controller.tool = Tool.PEN
        brushBar.setState(controller.brushDiameterDp, controller.eraserSquare,
            controller.shapeFilled, controller.eraserModePixel)
        topPinned = Prefs.getTopPinned(this)
        bottomPinned = Prefs.getBottomPinned(this)
        palettePinned = Prefs.getPalettePinned(this)
        paletteBar.visibility = if (Prefs.getShowPalette(this)) View.VISIBLE else View.GONE
        setBarsVisible(true)
        syncTopBarState()
        layoutBars()
    }

    // ---------------- 图钉与显隐 ----------------

    private fun toggleTopPin() {
        topPinned = !topPinned
        Prefs.setTopPinned(this, topPinned)
        if (topPinned) fadeIn(topBar) else fadeOut(topBar)
        showHint(if (topPinned) "顶部工具栏：钉住" else "顶部工具栏：可隐藏")
    }

    private fun toggleBottomPin() {
        bottomPinned = !bottomPinned
        Prefs.setBottomPinned(this, bottomPinned)
        if (bottomPinned) fadeIn(toolBar) else fadeOut(toolBar)
        showHint(if (bottomPinned) "底部工具栏：钉住" else "底部工具栏：可隐藏")
    }

    private fun togglePalettePin() {
        palettePinned = !palettePinned
        Prefs.setPalettePinned(this, palettePinned)
        if (palettePinned) fadeIn(paletteBar) else fadeOut(paletteBar)
        showHint(if (palettePinned) "调色板：钉住" else "调色板：可隐藏")
    }

    private fun fadeIn(v: View) { v.animate().alpha(1f).setDuration(140).start() }
    private fun fadeOut(v: View) { v.animate().alpha(0f).setDuration(140).start() }

    /** keepAlive：任何按钮交互后，若对应区域钉住则保持可见 */
    private fun keepAlive() {
        if (topPinned) fadeIn(topBar)
        if (bottomPinned) fadeIn(toolBar)
        if (palettePinned && Prefs.getShowPalette(this)) fadeIn(paletteBar)
    }

    private fun setBarsVisible(show: Boolean) {
        val a = if (show) 1f else 0f
        listOf(topBar, toolBar, paletteBar, bottomPin, topPin, palettePin)
            .forEach { it.alpha = a }
        if (Prefs.getShowPalette(this)) paletteBar.alpha = a else paletteBar.alpha = 0f
    }

    private fun onStrokeBegan() {
        // 钉住的区域保持可见；未钉住的隐藏（落笔即让开画布）
        if (!topPinned) fadeOut(topBar)
        if (!bottomPinned) fadeOut(toolBar)
        if (!palettePinned) fadeOut(paletteBar)
    }

    private fun onStrokeEnded() {
        if (topPinned) fadeIn(topBar)
        if (bottomPinned) fadeIn(toolBar)
        if (palettePinned && Prefs.getShowPalette(this)) fadeIn(paletteBar)
    }

    // ---------------- 气泡提示 ----------------

    private fun showHint(text: String) {
        hintView.text = text
        hintView.visibility = View.VISIBLE
        hintView.animate().alpha(1f).setDuration(90).start()
        mainHandler.removeCallbacks(hintHide)
        mainHandler.postDelayed(hintHide, 900L)
        // 定位：屏幕中央偏上
        hintView.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = Gravity.CENTER
            bottomMargin = (60f * d()).toInt()
        }
    }

    private val hintHide = Runnable {
        hintView.animate().alpha(0f).setDuration(160).withEndAction {
            hintView.visibility = View.GONE
        }.start()
    }

    // ---------------- 工具与颜色 ----------------

    private fun onToolSelected(t: Int) {
        if (controller.tool == t) {
            // 再次点当前工具：弹出/收起笔刷条
            brushBar.visibility = if (brushBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (brushBar.visibility == View.VISIBLE) {
                brushBar.setState(controller.brushDiameterDp, controller.eraserSquare,
                    controller.shapeFilled, controller.eraserModePixel)
            }
            layoutBars()
            return
        }
        controller.tool = t
        Prefs.lastTool = t
        brushBar.visibility = View.GONE
        toolBar.selected = t
        layoutBars()
        keepAlive()
    }

    private fun onColorPicked(c: Int) {
        controller.color = c
        Prefs.setColor(this, c)
        paletteBar.setSelected(c)
        keepAlive()
    }

    private fun onPickedColor(c: Int) {
        onColorPicked(c)
        controller.tool = Tool.PEN
        toolBar.selected = Tool.PEN
        showHint("已取色")
    }

    // ---------------- 顶部动作 ----------------

    private fun onTopAction(a: TopAction) {
        when (a) {
            TopAction.GALLERY -> backToGallery()
            TopAction.UNDO -> controller.undo()
            TopAction.REDO -> controller.redo()
            TopAction.CLEAR -> confirmClear()
            TopAction.LAYERS -> toggleLayerPanel()
            TopAction.EXPORT -> exportPng()
            TopAction.SHARE -> sharePng()
            TopAction.SETTINGS -> showSettings()
        }
        keepAlive()
    }

    // ---------------- 图层 ----------------

    private fun toggleLayerPanel() {
        val existing = root.findViewWithTag<View>("layerPanel")
        if (existing != null) { root.removeView(existing); return }
        val holder = FrameLayout(this).apply { tag = "layerPanel" }
        holder.setBackgroundColor(0xE6FFFFFF.toInt())
        holder.addView(LayerPanel(this, controller) { root.removeView(holder) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(holder, FrameLayout.LayoutParams(
            (300f * d()).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
    }

    // ---------------- 胶囊动作 ----------------

    private fun openNewProject() {
        performAutoSave()
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_PROJECT_ID, ProjectIO.newId()))
        finish()
    }

    private fun saveNow() {
        performAutoSave()
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
    }

    private fun backToGallery() {
        performAutoSave()
        startActivity(Intent(this, GalleryActivity::class.java))
        finish()
    }

    // ---------------- 状态同步与保存 ----------------

    private fun onDrawingChanged() {
        scheduleAutoSave()
        syncTopBarState()
        paintView.invalidate()
    }

    private fun syncTopBarState() {
        topBar.canUndo = (controller.drawing.activeLayer?.ops?.isNotEmpty() == true)
        topBar.canRedo = false
        topBar.invalidate()
    }

    private val autoSaveRun = Runnable { performAutoSave() }

    private fun scheduleAutoSave() {
        mainHandler.removeCallbacks(autoSaveRun)
        mainHandler.postDelayed(autoSaveRun, 1200L)
    }

    private fun performAutoSave() {
        try {
            ProjectIO.saveProject(this, controller.drawing, projectId, projectTitle)
            Thumbs.make(this, projectId, controller.drawing)
        } catch (_: Exception) {}
    }

    private fun exportPng() {
        val comp = controller.drawing.composite()
        val uri = ExportUtil.exportPng(this, comp, projectTitle)
        comp.recycle()
        Toast.makeText(this,
            if (uri != null) "已保存到相册「简画」" else "导出失败",
            Toast.LENGTH_SHORT).show()
    }

    private fun sharePng() {
        val comp = controller.drawing.composite()
        val intent = ExportUtil.sharePng(this, comp)
        comp.recycle()
        if (intent != null) startActivity(Intent.createChooser(intent, "分享画作"))
    }

    // ---------------- 对话框 ----------------

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空画布")
            .setMessage("将清空当前图层内容，确定？")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ ->
                controller.drawing.activeLayer?.let { l ->
                    l.ops.clear()
                    controller.drawing.repaintLayer(controller.drawing.activeIndex)
                    controller.clearRedo()
                    onDrawingChanged()
                }
            }.show()
    }

    /** 打开「颜色」对话框（标准 = 蜂窝色轮，自定义 = SV+色相条+RGB+Hex） */
    private fun showColorPicker() {
        ColorDialog.show(this, controller.color) { c -> onColorPicked(c) }
    }

    private fun showSettings() {
        val autoHide = Prefs.getAutoHideToolbar(this)
        val secs = Prefs.getAutoHideSeconds(this)
        val paletteOn = Prefs.getShowPalette(this)
        val palmOn = Prefs.getPalmRejection(this)
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { VERSION }
        MaterialAlertDialogBuilder(this)
            .setTitle("设置  ·  v$ver")
            .setItems(arrayOf(
                "防误触：${if (palmOn) "开" else "关"}",
                "调色板：${if (paletteOn) "显示" else "隐藏"}",
                "落笔自动隐藏未钉住的栏：${if (autoHide) "开" else "关"}")) { _, which ->
                when (which) {
                    0 -> { Prefs.setPalmRejection(this, !palmOn); controller.palmRejection = !palmOn; showSettings() }
                    1 -> { Prefs.setShowPalette(this, !paletteOn)
                           paletteBar.visibility = if (!paletteOn) View.VISIBLE else View.GONE
                           layoutBars(); showSettings() }
                    2 -> { Prefs.setAutoHideToolbar(this, !autoHide); showSettings() }
                }
            }
            .setNegativeButton("关闭", null)
            .setNeutralButton("关于") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("简画")
                    .setMessage("版本 v$ver\n开发者 ZhangChuang\n\n" +
                        "· 各面板均可用图钉切换常驻/自动隐藏\n" +
                        "· 点击工具/按钮会显示中文名称提示\n" +
                        "· 右下角胶囊可长按拖动")
                    .setPositiveButton("好", null).show()
            }
            .show()
    }

    // ---------------- 生命周期 ----------------

    override fun onPause() { super.onPause(); performAutoSave() }
    override fun onStop() { super.onStop(); performAutoSave() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转后：纸张不变，工具栏重新排布
        onOrientationChanged()
    }

    override fun onResume() {
        super.onResume()
        resizeToWindow()
        layoutBars()
        setBarsVisible(true)
        capsule.setPositionRatio(Prefs.getCapsuleX(this), Prefs.getCapsuleY(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}

private fun View.doOnLayoutOnce(block: () -> Unit) {
    if (width > 0 && height > 0) { block(); return }
    var done = false
    addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
        if (!done && r - l > 0 && b - t > 0) { done = true; block() }
    }
}