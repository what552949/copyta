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
    private lateinit var resetButton: View
    private lateinit var colorWhite: View
    private lateinit var colorBlack: View
    private lateinit var scaleSeekBar: SeekBar
    private lateinit var rulerView: RulerView
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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val b = tracingView.getBitmap()
        val wasLocked = isLocked

        setContentView(R.layout.activity_main)
        bindViews()
        setupToolbar()
        setupLockButtons()

        b?.let { tracingView.setBitmap(it) }
        tracingView.setScaleDirect(1f) // 旋转屏幕后，图片尺寸重置为初始状态
        scaleSeekBar.progress = 100

        if (wasLocked) applyLockedState() else applyUnlockedState()
    }

    private fun bindViews() {
        tracingView = findViewById(R.id.tracingView)
        toolbar = findViewById(R.id.toolbar)
        loadButton = findViewById(R.id.loadButton)
        resetButton = findViewById(R.id.resetButton)
        colorWhite = findViewById(R.id.colorWhite)
        colorBlack = findViewById(R.id.colorBlack)
        scaleSeekBar = findViewById(R.id.scaleSeekBar)
        rulerView = findViewById(R.id.rulerView)
        lockButton = findViewById(R.id.lockButton)
        floatingUnlockButton = findViewById(R.id.floatingUnlockButton)
    }

    private fun setupToolbar() {
        loadButton.setOnClickListener { pickImage.launch("image/*") }

        resetButton.setOnClickListener {
            tracingView.reset()
            scaleSeekBar.progress = 100
        }

        colorWhite.setOnClickListener { tracingView.setBackgroundColor(Color.WHITE) }
        colorBlack.setOnClickListener { tracingView.setBackgroundColor(Color.BLACK) }

        // 滑块重写：启用步进吸附，绝对接管缩放，取消联动
        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 实时同步给 TracingView（因为 p 是 0.1 倍数的整数，所以自带卡顿感）
                    tracingView.setScaleDirect(p / 100f)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupLockButtons() {
        // 工具栏里的锁定按钮：点击一次直接锁定
        lockButton.setOnClickListener { lock() }

        // 浮动解锁按钮：可拖动 + 双击解锁
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
        c.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                scaleSeekBar.progress = 100 // 载入后滑块重置回 1x
            }
        } catch (e: Exception) {
            Toast.makeText(this, "载入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
