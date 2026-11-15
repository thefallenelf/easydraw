package com.zhangchuang.nianchu

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 「颜色」对话框（仿 Windows 颜色对话框）。
 *
 * ┌ 颜色                                  ┐
 * │ [标准] [自定义]                        │
 * │  ┌──────────── 内容区 ────────────┐   │
 * │  │ 标准页：蜂窝色轮 + 灰阶条        │   │
 * │  │ 自定义页：SV 方块 + 色相条       │   │
 * │  │           RGB + Hex + 对比色块   │   │
 * │  └────────────────────────────────┘   │
 * │                   [确定] [取消]        │
 * └──────────────────────────────────────┘
 *
 * 两个 Tab 共享同一个「当前色」，确定时回传。
 */
object ColorDialog {

    /** 打开颜色对话框；返回前回调最终颜色 */
    fun show(
        activity: Activity,
        initial: Int,
        onPick: (Int) -> Unit
    ) {
        val d = activity.resources.displayMetrics.density
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16f * d).toInt(), (8f * d).toInt(), (16f * d).toInt(), (8f * d).toInt())
        }

        var current = initial

        // ---- Tab 行 ----
        val tabRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val tabStandard = tabButton(activity, "标准", true)
        val tabCustom = tabButton(activity, "自定义", false)
        tabRow.addView(tabStandard)
        tabRow.addView(tabCustom)
        root.addView(tabRow)

        // ---- 内容区 ----
        val content = FrameLayout(activity)
        // 对话框是 wrap_content，权重高度会塌成 0 —— 直接给定高度
        val contentH = (280f * d).toInt()
        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, contentH))

        val standardPage = HexWheelView(activity, initial)
        val customPage = buildCustomPage(activity, initial)

        content.addView(standardPage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(customPage.root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        customPage.root.visibility = View.GONE

        standardPage.onColor = { c ->
            current = c
            customPage.sync(c, fromPicker = false)
        }

        // ---- 底部按钮 ----
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (8f * d).toInt(), 0, 0)
        }
        val cancel = actionButton(activity, "取消", false)
        val ok = actionButton(activity, "确定", true)
        btnRow.addView(cancel)
        btnRow.addView(ok)
        root.addView(btnRow)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(root)
            .create()
        dialog.setCanceledOnTouchOutside(true)

        fun selectTab(standard: Boolean) {
            standardPage.visibility = if (standard) View.VISIBLE else View.GONE
            customPage.root.visibility = if (standard) View.GONE else View.VISIBLE
            tabStandard.isSelected = standard
            tabCustom.isSelected = !standard
            styleTab(activity, tabStandard, standard)
            styleTab(activity, tabCustom, !standard)
        }
        tabStandard.setOnClickListener { selectTab(true) }
        tabCustom.setOnClickListener { selectTab(false) }

        customPage.onChanged = { c ->
            current = c
            standardPage.setColorSilently(c)
        }

        ok.setOnClickListener {
            onPick(current)
            dialog.dismiss()
        }
        cancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
        // 让对话框足够大（平板/横屏）
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val width = (wm.defaultDisplay.width * 0.85f).toInt().coerceAtMost((760f * d).toInt())
            .coerceAtLeast((320f * d).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---------------- 自定义页 ----------------

    private class CustomPage(
        val root: LinearLayout,
        val picker: ColorPickerView,
        val rView: TextView,
        val gView: TextView,
        val bView: TextView,
        val hexView: TextView,
        val oldSwatch: View,
        val newSwatch: View
    ) {
        var onChanged: ((Int) -> Unit)? = null

        /** 外部改色 → 同步界面（不回环触发 onChanged） */
        fun sync(c: Int, fromPicker: Boolean) {
            val d = root.context.resources.displayMetrics.density
            newSwatch.setBackgroundColor(c)
            hexView.text = String.format("#%06X", 0xFFFFFF and c)
            rView.text = Color.red(c).toString()
            gView.text = Color.green(c).toString()
            bView.text = Color.blue(c).toString()
            if (!fromPicker) picker.setColorSilently(c)
            currentValue = c
        }

        var currentValue: Int = Color.BLACK
    }

    private fun buildCustomPage(activity: Activity, initial: Int): CustomPage {
        val d = activity.resources.displayMetrics.density
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6f * d).toInt(), 0, 0)
        }
        val picker = ColorPickerView(activity, initial)
        col.addView(picker, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (200f * d).toInt()))

        val label = TextView(activity).apply {
            text = "RGB 数值（点数字可输入）"
            textSize = 11f
            setTextColor(0xFF888888.toInt())
            setPadding(0, (6f * d).toInt(), 0, (2f * d).toInt())
        }
        col.addView(label)

        val rgbRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun chip(name: String, value: Int): Pair<View, TextView> {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = 8f * d
                    setColor(0xFFEFEFEF.toInt())
                }
                setPadding((10f * d).toInt(), (6f * d).toInt(),
                    (10f * d).toInt(), (6f * d).toInt())
                layoutParams = LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = (8f * d).toInt()
                }
            }
            box.addView(TextView(activity).apply {
                text = name; textSize = 12f; setTextColor(0xFF888888.toInt())
                setPadding(0, 0, (6f * d).toInt(), 0)
            })
            val v = TextView(activity).apply {
                text = value.toString(); textSize = 14f; setTextColor(0xFF333333.toInt())
            }
            box.addView(v)
            return box to v
        }
        val (rBox, rVal) = chip("R", Color.red(initial))
        val (gBox, gVal) = chip("G", Color.green(initial))
        val (bBox, bVal) = chip("B", Color.blue(initial))
        rgbRow.addView(rBox); rgbRow.addView(gBox); rgbRow.addView(bBox)
        col.addView(rgbRow)

        val hex = TextView(activity).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF444444.toInt())
            setPadding((10f * d).toInt(), (8f * d).toInt(), (10f * d).toInt(), (8f * d).toInt())
            text = String.format("#%06X", 0xFFFFFF and initial)
            background = GradientDrawable().apply {
                cornerRadius = 8f * d
                setColor(0xFFEFEFEF.toInt())
            }
        }
        col.addView(hex, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (36f * d).toInt()).apply {
            topMargin = (8f * d).toInt()
        })

        val oldSw = View(activity).apply { setBackgroundColor(initial) }
        val newSw = View(activity).apply { setBackgroundColor(initial) }
        val cmp = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                text = "当前"; textSize = 11f; setTextColor(0xFF777777.toInt())
                setPadding(0, 0, (8f * d).toInt(), 0)
            })
            addView(oldSw, LinearLayout.LayoutParams(0, (40f * d).toInt(), 1f))
            addView(TextView(activity).apply {
                text = "  新色"; textSize = 11f; setTextColor(0xFF777777.toInt())
                setPadding((12f * d).toInt(), 0, (8f * d).toInt(), 0)
            })
            addView(newSw, LinearLayout.LayoutParams(0, (40f * d).toInt(), 1f))
        }
        col.addView(cmp, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10f * d).toInt()
        })

        val page = CustomPage(col, picker, rVal, gVal, bVal, hex, oldSw, newSw)
        page.currentValue = initial

        fun setColor(c: Int, fromPicker: Boolean) {
            page.sync(c, fromPicker)
            page.onChanged?.invoke(c)
        }

        picker.onColorChanged = { c -> setColor(c, true) }

        fun editChannel(channel: Int) {
            val cur = page.currentValue
            val input = EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(
                    when (channel) {
                        0 -> Color.red(cur)
                        1 -> Color.green(cur)
                        else -> Color.blue(cur)
                    }.toString()
                )
                setSelection(text.length)
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle("${arrayOf("R", "G", "B")[channel]} 值（0-255）")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定") { _, _ ->
                    val n = input.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: return@setPositiveButton
                    val c = page.currentValue
                    val nc = when (channel) {
                        0 -> Color.rgb(n, Color.green(c), Color.blue(c))
                        1 -> Color.rgb(Color.red(c), n, Color.blue(c))
                        else -> Color.rgb(Color.red(c), Color.green(c), n)
                    }
                    setColor(nc, false)
                }.show()
        }
        rBox.setOnClickListener { editChannel(0) }
        gBox.setOnClickListener { editChannel(1) }
        bBox.setOnClickListener { editChannel(2) }

        hex.setOnClickListener {
            val input = EditText(activity).apply {
                setText(hex.text)
                setSelection(text.length)
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle("十六进制颜色")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定") { _, _ ->
                    val t = input.text.toString().trim().removePrefix("#")
                    val v = t.toLongOrNull(16) ?: return@setPositiveButton
                    setColor((0xFF000000L or v).toInt(), false)
                }.show()
        }

        return page
    }

    // ---------------- 小部件 ----------------

    private fun tabButton(activity: Activity, text: String, selected: Boolean): TextView {
        val d = activity.resources.displayMetrics.density
        return TextView(activity).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((18f * d).toInt(), (8f * d).toInt(), (18f * d).toInt(), (8f * d).toInt())
            isSelected = selected
            styleTab(activity, this, selected)
        }
    }

    private fun styleTab(activity: Activity, v: TextView, selected: Boolean) {
        val d = activity.resources.displayMetrics.density
        v.background = GradientDrawable().apply {
            cornerRadius = 4f * d
            setColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFF0F0F0.toInt())
            setStroke((1f * d).toInt(), if (selected) 0xFF1A73E8.toInt() else 0xFFDDDDDD.toInt())
        }
        v.setTextColor(if (selected) 0xFF1A73E8.toInt() else 0xFF444444.toInt())
    }

    private fun actionButton(activity: Activity, text: String, primary: Boolean): TextView {
        val d = activity.resources.displayMetrics.density
        return TextView(activity).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((20f * d).toInt(), (9f * d).toInt(), (20f * d).toInt(), (9f * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 4f * d
                setColor(if (primary) 0xFF1A73E8.toInt() else 0xFFFFFFFF.toInt())
                if (!primary) setStroke((1f * d).toInt(), 0xFFCCCCCC.toInt())
            }
            setTextColor(if (primary) Color.WHITE else 0xFF444444.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = (10f * d).toInt()
            }
        }
    }
}