package com.taras.autoflow.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.taras.autoflow.engine.AutoFlowRuntime

class AutoFlowNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (AutoFlowRuntime.isEngineActive.value) {
            // Callback trigger notifikasi aktif
        }
    }
}
