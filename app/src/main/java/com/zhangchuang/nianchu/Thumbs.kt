package com.zhangchuang.nianchu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * 缩略图工具：把工程合成图缩成小图供画廊显示。
 * 缩略图缓存目录 = 工程目录/_thumb.png。
 */
object Thumbs {

    const val THUMB_NAME = "_thumb.png"

    fun thumbFile(context: android.content.Context, id: String): File =
        File(ProjectIO.projectDir(context, id), THUMB_NAME)

    fun has(context: android.content.Context, id: String): Boolean =
        thumbFile(context, id).exists()

    /** 生成工程缩略图（256x256 内） */
    fun make(context: android.content.Context, id: String, drawing: Drawing): Bitmap? {
        val comp = drawing.composite()
        val t = scaleFit(comp, 320, 320)
        comp.recycle()
        if (t != null) {
            thumbFile(context, id).let { f ->
                FileOutputStream(f).use { out ->
                    t.compress(Bitmap.CompressFormat.PNG, 85, out)
                }
            }
        }
        return t
    }

    fun load(context: android.content.Context, id: String): Bitmap? {
        val f = thumbFile(context, id)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }

    fun scaleFit(src: Bitmap, mw: Int, mh: Int): Bitmap? {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return null
        val scale = minOf(mw.toFloat() / w, mh.toFloat() / h, 1f)
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(src, (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1), true)
    }
}