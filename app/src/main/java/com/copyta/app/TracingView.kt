package com.copyta.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

class TracingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val drawMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    var locked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var baseScale = 1f
    private var userScale = 1f
    private var transX = 0f
    private var transY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastDistance = 0f

    var onScaleChanged: ((Float) -> Unit)? = null

    private val minScale = 0.5f
    private val maxScale = 3f

    fun setBitmap(bmp: Bitmap) {
        bitmap = bmp
        userScale = 1f
        transX = 0f
        transY = 0f
        post {
            computeBase()
            updateMatrix()
            invalidate()
            onScaleChanged?.invoke(userScale)
        }
    }

    fun getBitmap(): Bitmap? = bitmap

    private fun computeBase() {
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        baseScale = if (bw <= vw && bh <= vh) 1f
                    else min(vw / bw, vh / bh)
    }

    private fun updateMatrix() {
        val bmp = bitmap ?: return
        drawMatrix.reset()
        val cx = width / 2f
        val cy = height / 2f
        drawMatrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        drawMatrix.postScale(baseScale * userScale, baseScale * userScale)
        drawMatrix.postTranslate(cx + transX, cy + transY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, drawMatrix, paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeBase()
        updateMatrix()
    }

    fun setUserScale(scale: Float) {
        userScale = scale.coerceIn(minScale, maxScale)
        updateMatrix()
        invalidate()
    }

    fun getUserScale(): Float = userScale

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked) return false
        if (bitmap == null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) lastDistance = distance(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    transX += dx
                    transY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    updateMatrix()
                    invalidate()
                } else if (event.pointerCount >= 2) {
                    val d = distance(event)
                    if (lastDistance > 0f) {
                        val ratio = d / lastDistance
                        userScale = (userScale * ratio).coerceIn(minScale, maxScale)
                        onScaleChanged?.invoke(userScale)
                        updateMatrix()
                        invalidate()
                    }
                    lastDistance = d
                    lastTouchX = (event.getX(0) + event.getX(1)) / 2f
                    lastTouchY = (event.getY(0) + event.getY(1)) / 2f
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val other = if (idx == 0) 1 else 0
                lastTouchX = event.getX(other)
                lastTouchY = event.getY(other)
                lastDistance = 0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastDistance = 0f
            }
        }
        return true
    }

    private fun distance(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }
}
