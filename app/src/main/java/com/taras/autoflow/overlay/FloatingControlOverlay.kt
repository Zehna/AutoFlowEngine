package com.taras.autoflow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.service.WorkflowForegroundService

class FloatingControlOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var statusTextView: TextView? = null

    @SuppressLint("InflateParams")
    fun show(statusText: String) {
        if (!Settings.canDrawOverlays(context)) return

        if (overlayView != null) {
            statusTextView?.setText("AutoFlow: $statusText")
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        val capsuleBg = GradientDrawable().apply {
            setColor(0xDD1A1C1E.toInt())
            cornerRadius = 48f
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 12, 16, 12)
            background = capsuleBg
        }

        val label = TextView(context).apply {
            setText("AutoFlow: $statusText")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setPadding(0, 0, 16, 0)
        }
        statusTextView = label

        val stopBg = GradientDrawable().apply {
            setColor(0xFFD32F2F.toInt())
            cornerRadius = 32f
        }

        val stopBtn = Button(context).apply {
            setText("STOP")
            background = stopBg
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setPadding(24, 10, 24, 10)
            setOnClickListener {
                AutoFlowRuntime.triggerEmergencyStop()
                runCatching {
                    val stopIntent = Intent(context, WorkflowForegroundService::class.java).apply {
                        action = WorkflowForegroundService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                }
                hide()
            }
        }

        layout.addView(label)
        layout.addView(stopBtn)

        overlayView = layout
        runCatching {
            windowManager.addView(overlayView, params)
        }.onFailure {
            overlayView = null
            statusTextView = null
        }
    }

    fun hide() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
            overlayView = null
            statusTextView = null
        }
    }
}
