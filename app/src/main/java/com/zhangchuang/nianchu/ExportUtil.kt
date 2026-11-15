package com.zhangchuang.nianchu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 导出 PNG 到相册 / 分享 / 打开系统相册 */
object ExportUtil {

    fun authority(context: Context): String = context.packageName + ".fileprovider"

    /** 导出为 PNG 到公共相册 Pictures/念初的画画板 */
    fun exportPng(context: Context, bitmap: android.graphics.Bitmap, title: String): Uri? {
        val name = sanitize(title)
        val fn = "念初_${name}_${System.currentTimeMillis()}.png"
        return if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fn)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/念初的画画板")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "念初的画画板")
            if (!dir.exists() && !dir.mkdirs()) return null
            val f = File(dir, fn)
            FileOutputStream(f).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(f)
        }
    }

    /** 分享当前画作 PNG（临时文件） */
    fun sharePng(context: Context, bitmap: android.graphics.Bitmap): Intent? {
        val dir = File(context.cacheDir, "share")
        dir.mkdirs()
        val f = File(dir, "share_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, authority(context), f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent
    }

    private fun sanitize(s: String): String {
        val v = s.trim().ifEmpty { "未命名" }
        return v.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}