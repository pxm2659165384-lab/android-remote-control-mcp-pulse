package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class PulseKeepAliveOverlay(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    fun showIfPermitted() {
        if (!Settings.canDrawOverlays(context)) {
            PulseLogger.w("Pulse keep-alive overlay skipped: SYSTEM_ALERT_WINDOW not granted")
            return
        }
        mainHandler.post {
            if (overlayView != null) return@post
            val view =
                View(context).apply {
                    setBackgroundColor(Color.argb(1, 255, 255, 255))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            val params =
                WindowManager.LayoutParams(
                    1,
                    1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                    alpha = 0.01f
                    title = "PulseLinkKeepAlive"
                }
            runCatching {
                windowManager.addView(view, params)
                overlayView = view
                PulseLogger.i("Pulse keep-alive overlay attached")
            }.onFailure {
                PulseLogger.w("Pulse keep-alive overlay attach failed: ${it.message}")
            }
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: return@post
            overlayView = null
            runCatching {
                windowManager.removeViewImmediate(view)
                PulseLogger.i("Pulse keep-alive overlay removed")
            }.onFailure {
                PulseLogger.w("Pulse keep-alive overlay remove failed: ${it.message}")
            }
        }
    }
}
