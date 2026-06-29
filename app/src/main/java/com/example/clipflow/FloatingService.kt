package com.example.clipflow

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var removeView: View
    private lateinit var vibrator: Vibrator

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
// windowManager để hiển thị floatingView trên màn hình với độ ưu tiên cao
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)

        floatingView = inflater.inflate(R.layout.layout_floating_widget, null)
        removeView = inflater.inflate(R.layout.layout_remove_target, null)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 200

        val removeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(removeView, removeParams)
        windowManager.addView(floatingView, layoutParams)

        floatingView.alpha = 0.5f
        val btnBubble = floatingView.findViewById<ImageView>(R.id.btnBubble)
        val layoutMenu = floatingView.findViewById<LinearLayout>(R.id.layoutMenu)
        val removeImg = removeView.findViewById<ImageView>(R.id.remove_img)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val handler = Handler(Looper.getMainLooper())
        var longPressRunnable: Runnable? = null
// Xử lý sự kiện touch trên floatingView
        btnBubble.setOnTouchListener { view, event ->
            when (event.action) {
                // Xử lý sự kiện chạm vào
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false

                    floatingView.alpha = 1.0f
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()

                    longPressRunnable = Runnable {
                        isDragging = true
                        vibratePhone(50)
                        if (layoutMenu.visibility == View.GONE) {
                            layoutMenu.visibility = View.VISIBLE
                            removeImg.visibility = View.VISIBLE
                        } else {
                            layoutMenu.visibility = View.GONE
                            removeImg.visibility = View.GONE
                        }
                    }
                    // Đặt thời gian chờ trước khi mở menu phụ
                    handler.postDelayed(longPressRunnable!!, 400)
                    true
                }
                // Xử lý sự kiện kéo đi
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                // Nếu di chuển quá 10 pixel thì hủy đồng hồ 400ms
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable!!)
                        layoutMenu.visibility = View.GONE
                        removeImg.visibility = View.VISIBLE

                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, layoutParams)

                        val screenHeight = resources.displayMetrics.heightPixels
                        val screenWidth = resources.displayMetrics.widthPixels

                        if (event.rawY > screenHeight - 300 && event.rawX > screenWidth / 2 - 150 && event.rawX < screenWidth / 2 + 150) {
                            removeImg.animate().scaleX(1.5f).scaleY(1.5f).setDuration(100).start()
                            floatingView.alpha = 0.3f
                        } else {
                            removeImg.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                            floatingView.alpha = 1.0f
                        }
                    }
                    true
                }
                // Xử lý sự kiện lúc thả tay
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable!!)
                    removeImg.visibility = View.GONE
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()

                    val screenHeight = resources.displayMetrics.heightPixels
                    val screenWidth = resources.displayMetrics.widthPixels
                // Nếu tọa độ chỗ thùng rác thì stopSelf()
                    if (isDragging && event.rawY > screenHeight - 300 && event.rawX > screenWidth / 2 - 150 && event.rawX < screenWidth / 2 + 150) {
                        vibratePhone(100)
                        stopSelf()
                    } else {
                        if (layoutMenu.visibility == View.GONE) floatingView.alpha = 0.5f
                        if (!isDragging) {
                            // Nếu không kéo mà chỉ bấm nhẹ thì gọi InvisibleActivity lên
                            val intent = Intent(this, InvisibleActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                    }
                    true
                }

                else -> false
            }
        }

        floatingView.findViewById<View>(R.id.btnMenuFile)
            .setOnClickListener { openAppWithAction("FILE") }
        floatingView.findViewById<View>(R.id.btnMenuOCR)
            .setOnClickListener { openAppWithAction("OCR") }
        floatingView.findViewById<View>(R.id.btnMenuTranslate)
            .setOnClickListener { openAppWithAction("TRANSLATE") }
    }

    private fun vibratePhone(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(duration)
        }
    }

    private fun openAppWithAction(action: String) {
        floatingView.findViewById<View>(R.id.layoutMenu).visibility = View.GONE
        floatingView.alpha = 0.5f
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("QUICK_ACTION", action)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                windowManager.removeView(floatingView)
            }
            if (::removeView.isInitialized && removeView.isAttachedToWindow) {
                windowManager.removeView(removeView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}