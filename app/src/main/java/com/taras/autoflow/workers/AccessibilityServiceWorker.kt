package com.taras.autoflow.workers

import com.taras.autoflow.engine.ExecutionContext
import com.taras.autoflow.engine.NodeExecutionResult
import com.taras.autoflow.service.NodeWorker
import com.taras.autoflow.service.PlatformAutomation

class AccessibilityServiceWorker : NodeWorker {
    override suspend fun execute(parameters: Map<String, String>, context: ExecutionContext): NodeExecutionResult {
        return PlatformAutomation.executeUiAction(parameters)
    }
}
