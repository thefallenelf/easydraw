package com.zhangchuang.nianchu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 浮动胶囊控制区。
 *
 * 收起态：只是一个 46dp 的圆点「⋯」（整个 View 就是圆点大小）。
 * 展开态：左侧滑出「新建 / 保存 / 返回」三枚小胶囊，圆点变成「×」。
 *
 * 长按拖动可移动；位置以屏幕比例回调 [onMoved]。
 */
class FloatingCapsule(
    context: Context,
    private val onNew: () -> Unit,
    private val onSave: () -> Unit,
    private val onBack: () -> Unit
) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val knobSize = (46f * d).toInt()

    private val knob: TextView
    private val panel: LinearLayout
    private var expanded = false

    private var anchorX = 0f
    private var anchorY = 0f

    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    var onMoved: ((Float, Float) -> Unit)? = null

    init {
        setWillNotDraw(false)

        panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = GONE
        }
        panel.addView(makePill("新建", Ico.NEWDOC) { collapse(); onNew() })
        panel.addView(makePill("保存", Ico.SAVE) { collapse(); onSave() })
        panel.addView(makePill("返回", Ico.BACK) { collapse(); onBack() })
        addView(panel, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, knobSize,
            Gravity.TOP or Gravity.END).apply {
            rightMargin = knobSize + (6f * d).toInt()
        })

        knob = TextView(context).apply {
            text = "⋯"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xD92B2E33.toInt())
                setStroke((1.2f * d).toInt(), 0x40FFFFFF)
            }
        }
        addView(knob, LayoutParams(knobSize, knobSize, Gravity.TOP or Gravity.END))
    }

    private fun makePill(text: String, icon: Int, onClick: () -> Unit): View {
        val w = (62f * d).toInt()
        val h = knobSize
        val v = object : View(context) {
            private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 10.5f * d
            }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(w, h)
            }
            override fun onDraw(canvas: Canvas) {
                bg.color = 0xD92B2E33.toInt()
                canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
                    16f * d, 16f * d, bg)
                Ico.draw(canvas, icon, Color.WHITE, 20f * d, width / 2f, height * 0.34f)
                tp.color = Color.WHITE
                canvas.drawText(text, width / 2f, height - 4f * d, tp)
            }
        }
        v.layoutParams = LinearLayout.LayoutParams(w, h).apply {
            leftMargin = (4f * d).toInt()
        }
        v.setOnClickListener { onClick() }
        return v
    }

    /** 按屏幕比例设置位置（圆点中心），会先收起 */
    fun setPositionRatio(rx: Float, ry: Float) {
        post {
            if (expanded) collapse()
            val pv = parent as? View ?: return@post
            val maxX = (pv.width - width).toFloat().coerceAtLeast(0f)
            val maxY = (pv.height - height).toFloat().coerceAtLeast(0f)
            anchorX = (rx * pv.width - width / 2f).coerceIn(0f, maxX)
            anchorY = (ry * pv.height - height / 2f).coerceIn(0f, maxY)
            translationX = anchorX
            translationY = anchorY
        }
    }

    private fun toggle() {
        expanded = !expanded
        panel.visibility = if (expanded) VISIBLE else GONE
        knob.text = if (expanded) "×" else "⋯"
        requestLayout()
        post { applyAnchor() }
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        panel.visibility = GONE
        knob.text = "⋯"
        requestLayout()
        post { applyAnchor() }
    }

    /** 保持「圆点」的屏幕位置不变：展开时整体左移，宽度增量部分由左侧面板吸收 */
    private fun applyAnchor() {
        val pv = parent as? View ?: return
        val shift = if (expanded) (width - knobSize).coerceAtLeast(0).toFloat() else 0f
        val maxX = (pv.width - width).toFloat().coerceAtLeast(0f)
        translationX = (anchorX - shift).coerceIn(0f, maxX)
        translationY = anchorY.coerceIn(0f, (pv.height - height).toFloat().coerceAtLeast(0f))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                    dragging = true
                    collapse()
                }
                if (dragging) {
                    val pv = parent as? View ?: return true
                    val maxX = (pv.width - width).toFloat().coerceAtLeast(0f)
                    val maxY = (pv.height - height).toFloat().coerceAtLeast(0f)
                    anchorX = (anchorX + dx).coerceIn(0f, maxX)
                    anchorY = (anchorY + dy).coerceIn(0f, maxY)
                    translationX = anchorX
                    translationY = anchorY
                    downRawX = event.rawX
                    downRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val pv = parent as? View ?: return true
                    val pw = pv.width.coerceAtLeast(1)
                    val ph = pv.height.coerceAtLeast(1)
                    onMoved?.invoke(
                        ((anchorX + width / 2f) / pw).coerceIn(0.02f, 0.98f),
                        ((anchorY + height / 2f) / ph).coerceIn(0.02f, 0.98f))
                } else {
                    toggle()
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
        }
        return super.onTouchEvent(event)
    }
}