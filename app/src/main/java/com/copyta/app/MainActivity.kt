package com.copyta.app

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
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
    private lateinit var resetButton: View
    private lateinit var colorWhite: View
    private lateinit var colorBlack: View
    private lateinit var scaleSlider: ScaleSliderView
    private lateinit var lockButton: ImageButton
    private lateinit var floatingUnlockButton: ImageButton

    private var isLocked = false

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
        setupLockButtons()
        setupBackHandler()
        updateOrientation() // 初始化方向
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val b = tracingView.getBitmap()
        val wasLocked = isLocked

        setContentView(R.layout.activity_main)
        bindViews()
        setupToolbar()
        setupLockButtons()
        updateOrientation() // 横竖屏切换时重新设置方向

        b?.let { tracingView.setBitmap(it) }
        tracingView.setScaleDirect(1f)
        scaleSlider.progress = 100

        if (wasLocked) applyLockedState() else applyUnlockedState()
    }

    private fun updateOrientation() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        scaleSlider.setOrientation(isLandscape)
    }

    private fun bindViews() {
        tracingView = findViewById(R.id.tracingView)
        toolbar = findViewById(R.id.toolbar)
        loadButton = findViewById(R.id.loadButton)
        resetButton = findViewById(R.id.resetButton)
        colorWhite = findViewById(R.id.colorWhite)
        colorBlack = findViewById(R.id.colorBlack)
        scaleSlider = findViewById(R.id.scaleSlider)
        lockButton = findViewById(R.id.lockButton)
        floatingUnlockButton = findViewById(R.id.floatingUnlockButton)
    }

    private fun setupToolbar() {
        loadButton.setOnClickListener { pickImage.launch("image/*") }

        resetButton.setOnClickListener {
            tracingView.reset()
            scaleSlider.progress = 100
        }

        colorWhite.setOnClickListener { tracingView.setBackgroundColor(Color.WHITE) }
        colorBlack.setOnClickListener { tracingView.setBackgroundColor(Color.BLACK) }

        scaleSlider.onValueChanged = { value ->
            tracingView.setScaleAnimated(value / 100f)
        }
    }

    private fun setupLockButtons() {
        lockButton.setOnClickListener { lock() }

        floatingUnlockButton.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var startTouchX = 0f
            private var startTouchY = 0f
            private var isDragging = false
            private var lastTapTime = 0L

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
                        if (!isDragging) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 1000) {
                                lastTapTime = 0L
                                unlock()
                            } else {
                                lastTapTime = now
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
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
        floatingUnlockButton.visibility = View.VISIBLE
    }

    private fun applyUnlockedState() {
        isLocked = false
        tracingView.locked = false
        toolbar.visibility = View.VISIBLE
        floatingUnlockButton.visibility = View.GONE
    }

    private fun enterImmersive() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun exitImmersive() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
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
                scaleSlider.progress = 100
            }
        } catch (e: Exception) {
            Toast.makeText(this, "载入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
