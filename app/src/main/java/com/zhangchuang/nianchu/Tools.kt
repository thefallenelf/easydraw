package com.zhangchuang.nianchu

/** 工具类型常量 */
object Tool {
    const val PEN = 0
    const val MARKER = 1
    const val ERASER = 2
    const val FILL = 3
    const val LINE = 4
    const val RECT = 5
    const val OVAL = 6
    const val ARROW = 7
    const val EYEDROPPER = 8
}

/** 常用预设 24 色（含黑白灰 + 红橙黄绿青蓝紫粉棕等） */
val PALETTE_24 = intArrayOf(
    0xFF000000.toInt(), 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt(), 0xFF795548.toInt(),
    0xFFF44336.toInt(), 0xFFFF5722.toInt(), 0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(),
    0xFFFFC107.toInt(), 0xFF8BC34A.toInt(), 0xFF4CAF50.toInt(), 0xFF009688.toInt(),
    0xFF00BCD4.toInt(), 0xFF03A9F4.toInt(), 0xFF2196F3.toInt(), 0xFF3F51B5.toInt(),
    0xFF673AB7.toInt(), 0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFFFF80AB.toInt(),
    0xFF80D8FF.toInt(), 0xFFFCE4EC.toInt(), 0xFFE1BEE7.toInt(), 0xFF212121.toInt()
)

/** 值 -> UI 显示文本 */
fun toolName(tool: Int): String = when (tool) {
    Tool.PEN -> "铅笔"
    Tool.MARKER -> "马克笔"
    Tool.ERASER -> "橡皮"
    Tool.FILL -> "填充"
    Tool.LINE -> "直线"
    Tool.RECT -> "矩形"
    Tool.OVAL -> "椭圆"
    Tool.ARROW -> "箭头"
    Tool.EYEDROPPER -> "取色"
    else -> "铅笔"
}