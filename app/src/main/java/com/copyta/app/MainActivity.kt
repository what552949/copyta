package com.copyta.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tracingView: TracingView
    private lateinit var toolbar: View
    private lateinit var loadButton: View
    private lateinit var colorWhite: View
    private lateinit var colorBlack: View
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var lockButton: ImageButton

    private var isLocked = false
    private var lastTapTime = 0L

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { loadImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        bindViews()
        setupToolbar()
        setupLockButton()
        setupBackHandler()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val s = tracingView.getUserScale()
        val b = tracingView.getBitmap()
        val wasLocked = isLocked

        setContentView(R.layout.activity_main)
        bindViews()
        setupToolbar()
        setupLockButton()

        b?.let { tracingView.setBitmap(it) }
        tracingView.setUserScale(s)
        if (wasLocked) applyLockedState() else applyUnlockedState()
    }

    private fun bindViews() {
        tracingView = findViewById(R.id.tracingView)
        toolbar = findViewById(R.id.toolbar)
        loadButton = findViewById(R.id.loadButton)
        colorWhite = findViewById(R.id.colorWhite)
        colorBlack = findViewById(R.id.colorBlack)
        scaleSeekBar = findViewById(R.id.scaleSeekBar)
        lockButton = findViewById(R.id.lockButton)
    }

    private fun setupToolbar() {
        loadButton.setOnClickListener { pickImage.launch("image/*") }

        colorWhite.setOnClickListener { tracingView.setBackgroundColor(Color.WHITE) }
        colorBlack.setOnClickListener { tracingView.setBackgroundColor(Color.BLACK) }

        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) tracingView.setUserScale(p / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                val scale = sb.progress / 100f
                val snapped = snapToScale(scale)
                sb.progress = (snapped * 100).toInt()
                tracingView.setUserScale(snapped)
            }
        })

        tracingView.onScaleChanged = { s ->
            val p = (s * 100).toInt().coerceIn(10, 300)
            if (scaleSeekBar.progress != p) scaleSeekBar.progress = p
        }
    }

    private fun snapToScale(s: Float): Float {
        val points = floatArrayOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f)
        var best = points[0]
        var bestD = Float.MAX_VALUE
        for (p in points) {
            val d = abs(p - s)
            if (d < bestD) { bestD = d; best = p }
        }
        return best
    }

    private fun setupLockButton() {
        lockButton.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var startTouchX = 0f
            private var startTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = v.x
                        startY = v.y
                        startTouchX = event.rawX
                        startTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startTouchX
                        val dy = event.rawY - startTouchY
                        if (!isDragging && (abs(dx) > 12f || abs(dy) > 12f)) isDragging = true
                        if (isDragging) {
                            v.x = startX + dx
                            v.y = startY + dy
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!isDragging) handleLockButtonTap()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun handleLockButtonTap() {
        val now = System.currentTimeMillis()
        if (isLocked) {
            if (now - lastTapTime < 1000) {
                lastTapTime = 0L
                unlock()
            } else {
                lastTapTime = now
            }
        } else {
            lock()
        }
    }

    private fun lock() {
        if (isLocked) return
        isLocked = true
        applyLockedState()
        enterImmersive()
    }

    private fun unlock() {
        if (!isLocked) return
        isLocked = false
        applyUnlockedState()
        exitImmersive()
    }

    private fun applyLockedState() {
        isLocked = true
        tracingView.locked = true
        toolbar.visibility = View.GONE
        lockButton.setImageResource(R.drawable.ic_lock_x)
    }

    private fun applyUnlockedState() {
        isLocked = false
        tracingView.locked = false
        toolbar.visibility = View.VISIBLE
        lockButton.setImageResource(android.R.color.transparent)
    }

    private fun enterImmersive() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun exitImmersive() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isLocked) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show()
                    return
                }
                val bmp = BitmapFactory.decodeStream(input)
                if (bmp == null) {
                    Toast.makeText(this, "图片解码失败", Toast.LENGTH_SHORT).show()
                    return
                }
                tracingView.setBitmap(bmp)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "载入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
