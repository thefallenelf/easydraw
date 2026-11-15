package com.zhangchuang.nianchu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 工程文件格式：文件夹里一份 meta.json + 每层一张 PNG */
object ProjectIO {

    private const val TAG = "ProjectIO"

    /** 工程目录：files/artworks/<工程id>/ */
    fun projectsDir(context: android.content.Context): File =
        File(context.filesDir, "artworks").apply { mkdirs() }

    fun newId(): String = System.currentTimeMillis().toString()

    fun projectDir(context: android.content.Context, id: String): File {
        val d = File(projectsDir(context), id)
        d.mkdirs()
        return d
    }

    data class LayerMeta(
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val path: String
    )

    data class ProjectMeta(
        val id: String,
        val title: String,
        val width: Int,
        val height: Int,
        val paperColor: Int,
        val createdAt: Long,
        val updatedAt: Long,
        val layers: List<LayerMeta>
    )

    /** 保存整个工程 */
    fun saveProject(context: android.content.Context, drawing: Drawing, id: String, title: String): String {
        val dir = projectDir(context, id)
        // 先清理旧层文件
        dir.listFiles()?.forEach {
            if (it.name != "_resized") it.delete()
        }
        // 每层写 PNG（含透明）
        val metas = ArrayList<LayerMeta>()
        for ((i, layer) in drawing.layers.withIndex()) {
            val fn = "layer_$i.png"
            val f = File(dir, fn)
            savePng(layer.resultBitmap(), f)
            metas.add(LayerMeta(layer.name, layer.visible, layer.opacity, fn))
        }
        val now = System.currentTimeMillis()
        val meta = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("width", drawing.width)
            put("height", drawing.height)
            put("paper", drawing.paperColor)
            put("created_at", now)
            put("updated_at", now)
            put("layers", JSONArray(metas.map { it.toJson() }))
        }
        File(dir, "meta.json").writeText(meta.toString())
        return id
    }

    private fun LayerMeta.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("visible", visible)
        put("opacity", opacity.toDouble())
        put("path", path)
    }

    private fun savePng(bmp: Bitmap, f: File) {
        FileOutputStream(f).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    }

    /** 读取工程；返回 null 表示损坏 */
    fun loadProject(context: android.content.Context, id: String): ProjectMeta? {
        val dir = projectDir(context, id)
        val metaFile = File(dir, "meta.json")
        if (!metaFile.exists()) return null
        return try {
            val meta = JSONObject(metaFile.readText())
            val layers = meta.getJSONArray("layers")
            val lm = ArrayList<LayerMeta>()
            for (i in 0 until layers.length()) {
                val j = layers.getJSONObject(i)
                lm.add(LayerMeta(
                    j.optString("name", "图层${i + 1}"),
                    j.optBoolean("visible", true),
                    j.optDouble("opacity", 1.0).toFloat(),
                    j.optString("path", "layer_$i.png")
                ))
            }
            ProjectMeta(
                meta.optString("id", id),
                meta.optString("title", "未命名"),
                meta.optInt("width", 1024),
                meta.optInt("height", 768),
                meta.optInt("paper", 0xFFFCFAF4.toInt()).apply {
                },
                meta.optLong("created_at", System.currentTimeMillis()),
                meta.optLong("updated_at", System.currentTimeMillis()),
                lm
            )
        } catch (e: Exception) {
            Log.e(TAG, "load meta failed", e)
            null
        }
    }

    /** 把一个已保存工程加载进 [Drawing] */
    fun restoreInto(context: android.content.Context, meta: ProjectMeta, drawing: Drawing) {
        drawing.layers.clear()
        drawing.width = meta.width
        drawing.height = meta.height
        drawing.paperColor = meta.paperColor
        val dir = projectDir(context, meta.id)
        for (lm in meta.layers) {
            val f = File(dir, lm.path)
            val bmp = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            val layer = Layer(lm.name, drawing.width, drawing.height)
            layer.seed = bmp
            layer.visible = lm.visible
            layer.opacity = lm.opacity
            drawing.layers.add(layer)
        }
        if (drawing.layers.isEmpty()) drawing.addLayer("画布")
        drawing.repaintAll()
        drawing.activeIndex = drawing.layers.size - 1
    }

    /** 列出所有可用的工程（缩略图存取除外） */
    fun listProjects(context: android.content.Context): List<ProjectMeta> {
        val dir = projectsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isDirectory && File(f, "meta.json").exists() }
            ?.map { loadProject(context, it.name) }
            ?.filterNotNull()
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    /** 生成扁平 PNG（所有可见层合成） */
    fun compositeToPng(context: android.content.Context, drawing: Drawing, outFile: File): File {
        val comp = drawing.composite()
        savePng(comp, outFile)
        comp.recycle()
        return outFile
    }

    fun deleteProject(context: android.content.Context, id: String) {
        projectDir(context, id).deleteRecursively()
    }

    fun updateTitle(context: android.content.Context, id: String, title: String) {
        val meta = loadProject(context, id) ?: return
        val existing = ProjectFile(context, id)
        val json = existing.readJson() ?: return
        json.put("title", title)
        json.put("updated_at", System.currentTimeMillis())
        existing.writeJson(json)
        Log.d(TAG, "title updated: $title")
    }

    class ProjectFile(context: android.content.Context, id: String) {
        private val file = File(projectDir(context, id), "meta.json")
        fun readJson(): JSONObject? = try { JSONObject(file.readText()) } catch (e: Exception) { null }
        fun writeJson(j: JSONObject) { file.writeText(j.toString()) }
    }

    fun timeLabel(ts: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
}