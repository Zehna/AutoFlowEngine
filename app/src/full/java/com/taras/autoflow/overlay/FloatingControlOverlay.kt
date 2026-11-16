package com.taras.autoflow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.taras.autoflow.engine.AutoFlowRuntime

class FloatingControlOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    @SuppressLint("InflateParams")
    fun show(nodeName: String) {
        if (overlayView != null) return

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

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xCC000000.toInt())
        }

        val textView = TextView(context).apply {
            setText("AutoFlow: $nodeName")
            setTextColor(0xFFFFFFFF.toInt())
        }

        val stopBtn = Button(context).apply {
            setText("STOP")
            setBackgroundColor(0xFFD32F2F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                AutoFlowRuntime.triggerEmergencyStop()
                hide()
            }
        }

        layout.addView(textView)
        layout.addView(stopBtn)

        overlayView = layout
        windowManager.addView(overlayView, params)
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
