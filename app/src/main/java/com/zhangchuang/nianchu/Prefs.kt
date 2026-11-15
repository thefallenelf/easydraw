package com.zhangchuang.nianchu

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlin.math.roundToInt

/**
 * 应用设置持久化（极简偏好）。
 */
object Prefs {

    private const val NAME = "nianchu_prefs"

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var lastTool: Int
        get() = prefs(App.appContext).getInt("last_tool", Tool.PEN)
        set(v) { prefs(App.appContext).edit().putInt("last_tool", v).apply() }

    fun getColor(c: Context): Int = prefs(c).getInt("last_color", 0xFF212121.toInt())
    fun setColor(c: Context, v: Int) { prefs(c).edit().putInt("last_color", v).apply() }

    fun getCustomColor(c: Context): Int = prefs(c).getInt("custom_color", -1)
    fun setCustomColor(c: Context, v: Int) { prefs(c).edit().putInt("custom_color", v).apply() }

    fun getBrushScale(c: Context): Float = prefs(c).getFloat("brush_scale", 0.25f)
    fun setBrushScale(c: Context, v: Float) =
        prefs(c).edit().putFloat("brush_scale", v.coerceIn(0f, 1f)).apply()

    /** 工具栏自动隐藏：**默认关闭**（开启后才 2 秒淡出、落笔隐藏）。
     *  用 v2 键名，保证旧版本存过的 true 不会继续影响新版本。 */
    fun getAutoHideToolbar(c: Context): Boolean = prefs(c).getBoolean("auto_hide_toolbar_v2", false)
    fun setAutoHideToolbar(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("auto_hide_toolbar_v2", v).apply()
    }

    fun getAutoHideSeconds(c: Context): Int = prefs(c).getInt("auto_hide_seconds", 2)
    fun setAutoHideSeconds(c: Context, v: Int) {
        prefs(c).edit().putInt("auto_hide_seconds", v.coerceIn(1, 30)).apply()
    }

    /** 调色板（默认显示） */
    fun getShowPalette(c: Context): Boolean = prefs(c).getBoolean("show_palette_v2", true)
    fun setShowPalette(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("show_palette_v2", v).apply()
    }

    /** 调色板是否钉住常驻（区别：show_palette 是"是否启用"，pinned 是"是否常驻不淡出"） */
    fun getPalettePinned(c: Context): Boolean = prefs(c).getBoolean("palette_pinned", true)
    fun setPalettePinned(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("palette_pinned", v).apply()
    }

    fun getPalmRejection(c: Context): Boolean = prefs(c).getBoolean("palm_rejection", true)
    fun setPalmRejection(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("palm_rejection", v).apply()
    }

    fun getShapeFilled(c: Context): Boolean = prefs(c).getBoolean("shape_filled", false)
    fun setShapeFilled(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("shape_filled", v).apply()
    }

    fun getEraserPixel(c: Context): Boolean = prefs(c).getBoolean("eraser_pixel", true)
    fun setEraserPixel(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("eraser_pixel", v).apply()
    }

    /** 笔头直径（dp） */
    fun getBrushDiameterDp(c: Context): Float = prefs(c).getFloat("brush_dp", 6f).coerceIn(1f, 60f)
    fun setBrushDiameterDp(c: Context, v: Float) {
        prefs(c).edit().putFloat("brush_dp", v.coerceIn(1f, 60f)).apply()
    }

    /** 橡皮是否方形笔头 */
    fun getEraserSquare(c: Context): Boolean = prefs(c).getBoolean("eraser_square", false)
    fun setEraserSquare(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("eraser_square", v).apply()
    }

    /** 左上工具条是否钉住常驻（默认钉住 = 常显示） */
    fun getTopPinned(c: Context): Boolean = prefs(c).getBoolean("top_pinned", true)
    fun setTopPinned(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("top_pinned", v).apply()
    }

    /** 底部工具条是否钉住常驻（默认钉住） */
    fun getBottomPinned(c: Context): Boolean = prefs(c).getBoolean("bottom_pinned", true)
    fun setBottomPinned(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("bottom_pinned", v).apply()
    }

    /** 浮动胶囊位置（相对屏幕宽高比例 0~1，默认右下） */
    fun getCapsuleX(c: Context): Float = prefs(c).getFloat("capsule_x", 0.88f)
    fun getCapsuleY(c: Context): Float = prefs(c).getFloat("capsule_y", 0.72f)
    fun setCapsulePos(c: Context, x: Float, y: Float) {
        prefs(c).edit().putFloat("capsule_x", x).putFloat("capsule_y", y).apply()
    }

    /** 旋转屏幕时是否允许纸张在屏幕上整体转 90° 以铺满（默认 false = 纸张永不转） */
    fun getRotateToFill(c: Context): Boolean = prefs(c).getBoolean("rotate_fill", false)
    fun setRotateToFill(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("rotate_fill", v).apply()
    }

    /** 数据目录里开关是否只针对“画布”相关，与画廊无关 */
    fun px(c: Context, dp: Float): Float = dp * c.resources.displayMetrics.density
    fun dp(c: Context, px: Float): Float = px / c.resources.displayMetrics.density
}