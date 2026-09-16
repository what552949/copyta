package com.copyta.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class ScaleSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 核心参数
    private val minValue = 50   // 0.5x
    private val maxValue = 300  // 3.0x
    private val stepSize = 10   // 0.1x 步进

    private var isVertical = false

    var progress = 100
        set(value) {
            val clamped = value.coerceIn(minValue, maxValue)
            val snapped = snapToStep(clamped)
            if (field != snapped) {
                field = snapped
                invalidate()
                onValueChanged?.invoke(field)
            }
        }

    var onValueChanged: ((Int) -> Unit)? = null

    // 画笔
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD1D1D6.toInt()
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C1C1E.toInt()
        style = Paint.Style.FILL
    }

    private val tickMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C1C1E.toInt()
        strokeWidth = 4f
    }

    private val tickMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAEAEB2.toInt()
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C1C1E.toInt()
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var thumbRadius = 24f
    private var currentThumbX = 0f
    private var currentThumbY = 0f

    // 内边距（防止滑块画出边界）
    private val paddingLeftRight = 40f

    // 供外部调用切换横竖
    fun setOrientation(vertical: Boolean) {
        isVertical = vertical
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    private fun snapToStep(value: Int): Int {
        val steps = (value - minValue).toFloat() / stepSize
        return (minValue + steps.roundToInt() * stepSize).coerceIn(minValue, maxValue)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalSteps = (maxValue - minValue) / stepSize

        if (!isVertical) {
            // ================= 横向布局 =================
            // 把轨道放在偏下的位置，给文字留出上方空间
            val trackY = height * 0.7f
            val trackStart = paddingLeftRight
            val trackEnd = width - paddingLeftRight
            val trackLength = trackEnd - trackStart

            // 1. 轨道
            canvas.drawLine(trackStart, trackY, trackEnd, trackY, trackPaint)

            // 2. 刻度
            for (i in 0..totalSteps) {
                val value = minValue + i * stepSize
                val x = trackStart + trackLength * ((value - minValue).toFloat() / (maxValue - minValue))

                if (value % 50 == 0) {
                    // 大刻度
                    canvas.drawLine(x, trackY - 15f, x, trackY - 35f, tickMajorPaint)
                    val label = String.format("%.1fx", value / 100f)
                    canvas.drawText(label, x, trackY - 45f, textPaint)
                } else {
                    // 小刻度
                    canvas.drawLine(x, trackY - 15f, x, trackY - 25f, tickMinorPaint)
                }
            }

            // 3. 滑块（去掉了白色描边，只画黑色圆点）
            currentThumbX = trackStart + trackLength * ((progress - minValue).toFloat() / (maxValue - minValue))
            currentThumbY = trackY
            canvas.drawCircle(currentThumbX, currentThumbY, thumbRadius, thumbPaint)

        } else {
            // ================= 竖向布局 =================
            // 把轨道放在偏左的位置，给右侧文字留出空间
            val trackX = width * 0.4f
            val trackStart = paddingLeftRight
            val trackEnd = height - paddingLeftRight
            val trackLength = trackEnd - trackStart

            // 1. 轨道
            canvas.drawLine(trackX, trackStart, trackX, trackEnd, trackPaint)

            // 2. 刻度
            for (i in 0..totalSteps) {
                val value = minValue + i * stepSize
                val y = trackStart + trackLength * ((value - minValue).toFloat() / (maxValue - minValue))

                if (value % 50 == 0) {
                    // 大刻度
                    canvas.drawLine(trackX + 15f, y, trackX + 35f, y, tickMajorPaint)
                    val label = String.format("%.1fx", value / 100f)
                    canvas.save()
                    canvas.translate(trackX + 60f, y + 10f)
                    canvas.drawText(label, 0f, 0f, textPaint)
                    canvas.restore()
                } else {
                    // 小刻度
                    canvas.drawLine(trackX + 15f, y, trackX + 25f, y, tickMinorPaint)
                }
            }

            // 3. 滑块
            currentThumbX = trackX
            currentThumbY = trackStart + trackLength * ((progress - minValue).toFloat() / (maxValue - minValue))
            canvas.drawCircle(currentThumbX, currentThumbY, thumbRadius, thumbPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val trackStart = paddingLeftRight
                val trackEnd = if (!isVertical) width - paddingLeftRight else height - paddingLeftRight
                val trackLength = trackEnd - trackStart

                val touchPos = if (!isVertical) event.x else event.y
                val ratio = ((touchPos - trackStart) / trackLength).coerceIn(0f, 1f)
                val rawValue = (minValue + ratio * (maxValue - minValue)).roundToInt()
                progress = snapToStep(rawValue)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun resetToCenter() {
        progress = 100
    }
}
