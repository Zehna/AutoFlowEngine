package com.taras.autoflow.service

import android.content.Context
import android.graphics.PointF
import com.taras.autoflow.engine.HealthCheckItem
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.overlay.FloatingControlOverlay
import com.taras.autoflow.util.ShizukuHelper
import com.taras.autoflow.util.SystemPermissionHelper
import com.taras.autoflow.workers.ShizukuShellWorker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object PlatformAutomation {
    private var activeOverlay: FloatingControlOverlay? = null

    suspend fun executeUiAction(parameters: Map<String, String>): NodeExecutionResult {
        val a11y = AutoFlowAccessibilityService.instance
            ?: return ShizukuShellWorker.executeUiViaShizuku(parameters)

        val action = parameters["action"]?.uppercase() ?: "CLICK"
        val startX = parameters["x"]?.toFloatOrNull() ?: 500f
        val startY = parameters["y"]?.toFloatOrNull() ?: 1000f

        return suspendCancellableCoroutine { cont ->
            when (action) {
                "CLICK" -> {
                    val path = HumanizedMotionEngine.generateCurvedPath(
                        PointF(startX, startY),
                        PointF(startX + 2f, startY + 2f)
                    )
                    a11y.dispatchNaturalGesture(path, 120L) {
                        cont.resume(NodeExecutionResult.Success(mapOf("action" to "CLICKED", "x" to startX, "y" to startY)))
                    }
                }
                "SWIPE" -> {
                    val endX = parameters["end_x"]?.toFloatOrNull() ?: startX
                    val endY = parameters["end_y"]?.toFloatOrNull() ?: (startY - 600f)
                    val duration = parameters["duration_ms"]?.toLongOrNull() ?: 350L

                    val path = HumanizedMotionEngine.generateCurvedPath(
                        PointF(startX, startY),
                        PointF(endX, endY)
                    )
                    a11y.dispatchNaturalGesture(path, duration) {
                        cont.resume(NodeExecutionResult.Success(mapOf("action" to "SWIPED", "from" to "$startX,$startY", "to" to "$endX,$endY")))
                    }
                }
                else -> cont.resume(NodeExecutionResult.Failure("Aksi gestur '$action' tidak dikenal untuk Accessibility"))
            }
        }
    }

    fun getHealthItems(context: Context): List<HealthCheckItem> = listOf(
        HealthCheckItem(
            title = "Accessibility Service",
            isGranted = SystemPermissionHelper.isAccessibilityEnabled(context),
            onAction = { SystemPermissionHelper.openAccessibilitySettings(context) }
        ),
        HealthCheckItem(
            title = "Notification Listener",
            isGranted = SystemPermissionHelper.isNotificationListenerEnabled(context),
            onAction = { SystemPermissionHelper.openNotificationListenerSettings(context) }
        ),
        HealthCheckItem(
            title = "Floating Overlay",
            isGranted = SystemPermissionHelper.canDrawOverlays(context),
            onAction = { SystemPermissionHelper.openOverlaySettings(context) }
        ),
        HealthCheckItem(
            title = "Abaikan Optimasi Baterai",
            isGranted = SystemPermissionHelper.isBatteryOptimizationIgnored(context),
            actionLabel = "Atur",
            onAction = { SystemPermissionHelper.openBatteryOptimizationSettings(context) }
        ),
        HealthCheckItem(
            title = "Shizuku System Shell",
            isGranted = ShizukuHelper.hasPermission(),
            onAction = { ShizukuHelper.requestPermission(101) }
        )
    )

    fun onNodeRunning(context: Context, nodeName: String) {
        if (SystemPermissionHelper.canDrawOverlays(context)) {
            if (activeOverlay == null) {
                activeOverlay = FloatingControlOverlay(context)
            }
            activeOverlay?.show(nodeName)
        }
    }

    fun onWorkflowFinished() {
        activeOverlay?.hide()
        activeOverlay = null
    }
}
