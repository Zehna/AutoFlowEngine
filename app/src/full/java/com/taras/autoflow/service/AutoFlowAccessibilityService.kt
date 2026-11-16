package com.taras.autoflow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.taras.autoflow.engine.AutoFlowRuntime

class AutoFlowAccessibilityService : AccessibilityService() {

    private var lastVolumeDownTime = 0L

    companion object {
        var instance: AutoFlowAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime < 450) {
                AutoFlowRuntime.triggerEmergencyStop()
                return true
            }
            lastVolumeDownTime = now
        }
        return super.onKeyEvent(event)
    }

    fun dispatchNaturalGesture(path: Path, durationMs: Long, onDone: (() -> Unit)? = null) {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
        }, null)
    }
}
