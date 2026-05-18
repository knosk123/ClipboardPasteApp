package com.paste.clipboard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingWindowService : Service() {

    companion object {
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var tvButton: TextView
    private var layoutParams: WindowManager.LayoutParams

    init {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_overlay_required, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null, false)
        tvButton = floatingView.findViewById(R.id.tvFloatingBtn)

        setupDragListener()
        setupClickListener()

        try {
            windowManager.addView(floatingView, layoutParams)
            isRunning = true
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_floating_window_failed, e.message ?: getString(R.string.unknown_error)),
                Toast.LENGTH_SHORT
            ).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::windowManager.isInitialized && ::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (_: Exception) {}
        }
    }

    private fun startForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, PasteApp.CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pendingIntent)
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        tvButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }
                    // Move in the opposite direction since gravity is END
                    layoutParams.x = initialX - dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListener() {
        tvButton.setOnClickListener {
            if (PasteAccessibilityService.instance == null) {
                Toast.makeText(this, R.string.toast_accessibility_not_running, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val service = PasteAccessibilityService.instance!!
            if (service.typingHelper.isActive()) {
                Toast.makeText(this, R.string.toast_typing_active, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val text = PasteTextStore(this).get()

            if (text.isBlank()) {
                Toast.makeText(this, R.string.toast_no_text_to_paste, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Start typing
            tvButton.setText(R.string.floating_working)
            tvButton.textSize = 12f
            tvButton.setBackgroundResource(android.R.drawable.btn_default)

            service.typingHelper.onProgress = { current, total ->
                tvButton.post {
                    val progress = if (total == 0) 0 else current * 100 / total
                    tvButton.text = getString(R.string.progress_percent, progress)
                }
            }

            val started = service.typeText(text) { success ->
                tvButton.post {
                    tvButton.setText(R.string.floating_button_text)
                    tvButton.textSize = 18f
                    tvButton.setBackgroundResource(R.drawable.floating_button_bg)
                    if (success) {
                        Toast.makeText(this, R.string.toast_input_completed, Toast.LENGTH_SHORT).show()
                        HistoryManager(this).addEntry(text)
                    } else {
                        Toast.makeText(this, R.string.toast_input_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (!started) {
                tvButton.setText(R.string.floating_button_text)
                tvButton.textSize = 18f
                tvButton.setBackgroundResource(R.drawable.floating_button_bg)
                Toast.makeText(this, R.string.toast_no_input_field, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
