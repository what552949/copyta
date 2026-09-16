package com.copyta.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class RulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val minValue = 50
    private val maxValue = 300
    private val stepSize = 10

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.color = 0xFF8E8E93.toInt()
        paint.strokeWidth = 2f
        textPaint.color = 0xFF8E8E93.toInt()
        textPaint.textSize = 24f
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val totalSteps = (maxValue - minValue) / stepSize // 25 格
        val stepWidth = width.toFloat() / totalSteps

        for (i in 0..totalSteps) {
            val value = minValue + i * stepSize
            val x = i * stepWidth
            val isMajor = (value - minValue) % 50 == 0

            if (isMajor) {
                // 大刻度
                canvas.drawLine(x, 0f, x, height * 0.6f, paint)
                // 文字
                val scaleText = String.format("%.1fx", value / 100f)
                canvas.drawText(scaleText, x, height * 0.9f, textPaint)
            } else {
                // 小刻度
                canvas.drawLine(x, 0f, x, height * 0.3f, paint)
            }
        }
    }
}
