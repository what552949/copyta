package com.copyta.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
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

    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
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

    fun setOrientation(vertical: Boolean) {
        isVertical = vertical
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateThumbPosition()
    }

    private fun snapToStep(value: Int): Int {
        val steps = (value - minValue).toFloat() / stepSize
        return (minValue + steps.roundToInt() * stepSize).coerceIn(minValue, maxValue)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 计算滑块可用的起始和结束长度
        val trackStart: Float
        val trackEnd: Float
        val trackLength: Float

        if (!isVertical) {
            trackStart = paddingLeftRight
            trackEnd = width - paddingLeftRight
            trackLength = trackEnd - trackStart
        } else {
            trackStart = paddingLeftRight
            trackEnd = height - paddingLeftRight
            trackLength = trackEnd - trackStart
        }

        val totalSteps = (maxValue - minValue) / stepSize

        // 1. 画轨道
        if (!isVertical) {
            canvas.drawLine(trackStart, height / 2f, trackEnd, height / 2f, trackPaint)
        } else {
            canvas.drawLine(width / 2f, trackStart, width / 2f, trackEnd, trackPaint)
        }

        // 2. 画刻度和数字
        for (i in 0..totalSteps) {
            val value = minValue + i * stepSize
            val ratio = (value - minValue).toFloat() / (maxValue - minValue)
            
            if (!isVertical) {
                val x = trackStart + trackLength * ratio
                if (value % 50 == 0) {
                    // 大刻度
                    canvas.drawLine(x, height / 2f - 20f, x, height / 2f - 40f, tickMajorPaint)
                    // 文字
                    val label = String.format("%.1fx", value / 100f)
                    canvas.drawText(label, x, height / 2f - 50f, textPaint)
                } else {
                    // 小刻度
                    canvas.drawLine(x, height / 2f - 20f, x, height / 2f - 30f, tickMinorPaint)
                }
            } else {
                val y = trackStart + trackLength * ratio
                if (value % 50 == 0) {
                    // 大刻度
                    canvas.drawLine(width / 2f + 20f, y, width / 2f + 40f, y, tickMajorPaint)
                    // 文字（竖向文字绘制比较麻烦，这里采用横向绘制并平移位置）
                    val label = String.format("%.1fx", value / 100f)
                    canvas.save()
                    canvas.translate(width / 2f + 65f, y + 10f)
                    canvas.drawText(label, 0f, 0f, textPaint)
                    canvas.restore()
                } else {
                    // 小刻度
                    canvas.drawLine(width / 2f + 20f, y, width / 2f + 30f, y, tickMinorPaint)
                }
            }
        }

        // 3. 画滑块（Thumb）
        if (!isVertical) {
            currentThumbX = trackStart + trackLength * ((progress - minValue).toFloat() / (maxValue - minValue))
            currentThumbY = height / 2f
            canvas.drawCircle(currentThumbX, currentThumbY, thumbRadius, thumbPaint)
            canvas.drawCircle(currentThumbX, currentThumbY, thumbRadius, thumbStrokePaint)
        } else {
            currentThumbX = width / 2f
            currentThumbY = trackStart + trackLength * ((progress - minValue).toFloat() / (maxValue - minValue))
            canvas.drawCircle(currentThumbX, currentThumbY, thumbRadius, thumbPaint)
            canvas.drawCircle(currentThumbX, currentThumbY, thumbRadius, thumbStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true) // 防止被外层工具栏拦截
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
    
    // 用于外部重置
    fun resetToCenter() {
        progress = 100
    }
}
