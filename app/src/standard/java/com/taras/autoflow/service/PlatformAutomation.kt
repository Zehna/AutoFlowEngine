package com.taras.autoflow.service

import android.content.Context
import com.taras.autoflow.engine.HealthCheckItem
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.util.ShizukuHelper
import com.taras.autoflow.util.SystemPermissionHelper
import com.taras.autoflow.workers.ShizukuShellWorker

object PlatformAutomation {
    suspend fun executeUiAction(parameters: Map<String, String>): NodeExecutionResult {
        return ShizukuShellWorker.executeUiViaShizuku(parameters)
    }

    fun getHealthItems(context: Context): List<HealthCheckItem> = listOf(
        HealthCheckItem(
            title = "Shizuku System Shell (UI & ADB)",
            isGranted = ShizukuHelper.hasPermission(),
            onAction = { ShizukuHelper.requestPermission(101) }
        ),
        HealthCheckItem(
            title = "Notifikasi Kontrol Engine",
            isGranted = SystemPermissionHelper.areNotificationsEnabled(context),
            onAction = { SystemPermissionHelper.openAppNotificationSettings(context) }
        ),
        HealthCheckItem(
            title = "Abaikan Optimasi Baterai",
            isGranted = SystemPermissionHelper.isBatteryOptimizationIgnored(context),
            actionLabel = "Atur",
            onAction = { SystemPermissionHelper.openBatteryOptimizationSettings(context) }
        )
    )

    fun onNodeRunning(context: Context, nodeName: String) {
        // Mode standar menggunakan notifikasi Foreground Service dengan tombol STOP
    }

    fun onWorkflowFinished() {
        // Tidak ada overlay mengambang yang perlu ditutup
    }
}
