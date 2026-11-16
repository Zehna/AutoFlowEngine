package com.taras.autoflow.workers

import android.graphics.PointF
import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.AutoFlowAccessibilityService
import com.taras.autoflow.service.HumanizedMotionEngine
import com.taras.autoflow.service.NodeWorker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AccessibilityServiceWorker : NodeWorker {
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        val a11y = AutoFlowAccessibilityService.instance
            ?: return NodeExecutionResult.Failure("Accessibility Service belum diaktifkan oleh pengguna")

        val action = parameters["action"] ?: "CLICK"
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
                else -> cont.resume(NodeExecutionResult.Failure("Aksi gestur '$action' tidak dikenal"))
            }
        }
    }
}
