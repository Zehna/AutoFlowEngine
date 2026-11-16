package com.taras.autoflow.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.taras.autoflow.data.AutoFlowDatabase
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.WorkflowOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AutoFlowNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg == packageName) return
        if (AutoFlowRuntime.isEngineActive.value) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        scope.launch {
            val db = AutoFlowDatabase.getInstance(applicationContext)
            val all = db.workflowDao().observeAllWorkflows().first()
            for (entity in all) {
                val def = runCatching { entity.toDefinition() }.getOrNull() ?: continue
                val notifTrigger = def.triggers.firstOrNull { it.type.equals("NOTIFICATION", ignoreCase = true) }
                    ?: continue

                val targetPkg = notifTrigger.options["package"]
                val textFilter = notifTrigger.options["contains"]
                val pkgMatches = targetPkg.isNullOrBlank() || targetPkg == pkg
                val textMatches = textFilter.isNullOrBlank() ||
                    title.contains(textFilter, ignoreCase = true) ||
                    text.contains(textFilter, ignoreCase = true)

                if (pkgMatches && textMatches) {
                    AutoFlowRuntime.appendLog("🔔 Trigger notifikasi dari $pkg memicu '${def.name}'")
                    val enrichedDef = def.copy(
                        variables = def.variables + mapOf(
                            "notif_package" to pkg,
                            "notif_title" to title,
                            "notif_text" to text
                        )
                    )
                    val orchestrator = WorkflowOrchestrator(
                        applicationContext,
                        WorkerRegistryFactory.create(applicationContext)
                    )
                    orchestrator.run(enrichedDef)
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
