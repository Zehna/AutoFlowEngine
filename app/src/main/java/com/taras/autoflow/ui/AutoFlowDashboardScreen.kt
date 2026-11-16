package com.taras.autoflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taras.autoflow.data.WorkflowEntity
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.ExecutionMode
import com.taras.autoflow.engine.OrchestratorState
import com.taras.autoflow.engine.WorkflowDefinition
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoFlowDashboardScreen(
    viewModel: AutoFlowViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenMenu: () -> Unit,
    onCreateWorkflow: () -> Unit,
    onEditWorkflow: (WorkflowDefinition) -> Unit,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    val orchState by viewModel.orchestratorState.collectAsStateWithLifecycle()
    val consoleLogs by viewModel.consoleLogs.collectAsStateWithLifecycle()
    val workflows by viewModel.workflows.collectAsStateWithLifecycle()

    val consoleListState = rememberLazyListState()
    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) {
            consoleListState.animateScrollToItem(consoleLogs.lastIndex)
        }
    }

    var scheduleTarget by remember { mutableStateOf<WorkflowEntity?>(null) }
    var variableTarget by remember { mutableStateOf<WorkflowDefinition?>(null) }

    val activeWorkflowId = when (val st = orchState) {
        is OrchestratorState.Running -> st.workflowId
        is OrchestratorState.Paused -> st.workflowId
        else -> null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AutoFlow Engine v2", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu Navigasi")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                SystemHealthCard()
            }

            // a) Active Execution Telemetry Card (muncul saat Running / Paused)
            when (val st = orchState) {
                is OrchestratorState.Running -> {
                    item {
                        ActiveTelemetryCard(
                            workflowName = st.workflowName,
                            isPaused = false,
                            cycle = st.cycle,
                            stepIndex = st.stepIndex,
                            totalSteps = st.totalSteps,
                            nodeTitle = st.currentNodeName,
                            onPauseOrResume = { viewModel.pauseExecution() },
                            onStop = { viewModel.stopExecution() }
                        )
                    }
                }
                is OrchestratorState.Paused -> {
                    item {
                        ActiveTelemetryCard(
                            workflowName = st.workflowName,
                            isPaused = true,
                            cycle = st.cycle,
                            stepIndex = st.stepIndex,
                            totalSteps = st.totalSteps,
                            nodeTitle = st.currentNodeName,
                            onPauseOrResume = { viewModel.resumeExecution() },
                            onStop = { viewModel.stopExecution() }
                        )
                    }
                }
                is OrchestratorState.Completed -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✓ Selesai: ${st.workflowName} (${st.totalCycles} siklus)",
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { AutoFlowRuntime.getOrchestrator(context).resetToIdle() }) {
                                    Text("Tutup")
                                }
                            }
                        }
                    }
                }
                is OrchestratorState.Failed -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✕ ${st.workflowName}: ${st.reason}",
                                    color = Color(0xFFB71C1C),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { AutoFlowRuntime.getOrchestrator(context).resetToIdle() }) {
                                    Text("Tutup")
                                }
                            }
                        }
                    }
                }
                OrchestratorState.Idle -> Unit
            }

            // b) Installed Workflows List ("Workflow Terpasang")
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Workflow Terpasang (${workflows.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Import (.json/.zip)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = onCreateWorkflow,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Buat Workflow", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (workflows.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Belum ada workflow di filesDir/workflows/.")
                            Button(onClick = { viewModel.restoreStarterWorkflows() }) {
                                Text("Muat Preset Starter")
                            }
                        }
                    }
                }
            } else {
                items(workflows, key = { it.id }) { entity ->
                    val definition = remember(entity.definitionJson) {
                        runCatching { entity.toDefinition() }.getOrNull()
                    }
                    if (definition != null) {
                        val isThisActive = activeWorkflowId == definition.id
                        WorkflowItemCard(
                            entity = entity,
                            definition = definition,
                            isThisActive = isThisActive,
                            isAnyRunning = activeWorkflowId != null,
                            onTogglePlayStop = {
                                if (isThisActive) viewModel.stopExecution()
                                else viewModel.startWorkflow(definition)
                            },
                            onConfigureVariables = { variableTarget = definition },
                            onEdit = { onEditWorkflow(definition) },
                            onSchedule = { scheduleTarget = entity },
                            onExport = {
                                viewModel.exportWorkflowBundle(definition) { zipFile ->
                                    BundleShareHelper.shareFile(context, zipFile, "application/zip")
                                }
                            },
                            onDelete = { viewModel.deleteWorkflow(entity.id) }
                        )
                    }
                }
            }

            // Real-time auto-scrolling Live Execution Console
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Live Execution Console",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.restoreStarterWorkflows() }) {
                                Text("+ Preset")
                            }
                            TextButton(onClick = { AutoFlowRuntime.clearConsole() }) {
                                Text("Clear")
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(Color(0xFF101317), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        if (consoleLogs.isEmpty()) {
                            Text(
                                "Siaga. Tekan tombol Play pada salah satu workflow untuk melihat telemetry real-time...",
                                color = Color(0xFF78909C),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LazyColumn(
                                state = consoleListState,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(consoleLogs) { line ->
                                    Text(
                                        line,
                                        color = when {
                                            line.contains("✕") || line.contains("■") -> Color(0xFFEF9A9A)
                                            line.contains("✓") || line.contains("★") -> Color(0xFFA5D6A7)
                                            else -> Color(0xFFE0E0E0)
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    scheduleTarget?.let { target ->
        ScheduleDialog(
            entity = target,
            onDismiss = { scheduleTarget = null },
            onSave = { enabled, intervalMin ->
                viewModel.setWorkflowSchedule(target, enabled, intervalMin)
                scheduleTarget = null
            }
        )
    }

    variableTarget?.let { def ->
        WorkflowVariablesBottomSheet(
            definition = def,
            onDismiss = { variableTarget = null },
            onSave = { updatedVars: Map<String, JsonElement> ->
                viewModel.saveWorkflow(def.copy(variables = updatedVars))
                variableTarget = null
            },
            onSaveAndRun = { updatedVars: Map<String, JsonElement> ->
                val updated = def.copy(variables = updatedVars)
                viewModel.saveWorkflow(updated)
                variableTarget = null
                viewModel.startWorkflow(updated)
            }
        )
    }
}

@Composable
private fun ActiveTelemetryCard(
    workflowName: String,
    isPaused: Boolean,
    cycle: Int,
    stepIndex: Int,
    totalSteps: Int,
    nodeTitle: String,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit
) {
    val progress = (stepIndex.toFloat() / totalSteps.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPaused) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isPaused) "DIJEDA: $workflowName" else "BERJALAN: $workflowName",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Siklus #$cycle • Langkah $stepIndex/$totalSteps: $nodeTitle",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPauseOrResume,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPaused) "Resume" else "Pause")
                }
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun WorkflowItemCard(
    entity: WorkflowEntity,
    definition: WorkflowDefinition,
    isThisActive: Boolean,
    isAnyRunning: Boolean,
    onTogglePlayStop: () -> Unit,
    onConfigureVariables: () -> Unit,
    onEdit: () -> Unit,
    onSchedule: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(definition.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (definition.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            definition.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (entity.lastRunStatus != null) {
                        Text(
                            entity.lastRunStatus,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (entity.lastRunStatus) {
                                "SUCCESS" -> Color(0xFF2E7D32)
                                "FAILED", "ABORTED" -> Color(0xFFC62828)
                                else -> Color(0xFFEF6C00)
                            }
                        )
                    }
                    if (definition.executionPolicy.mode == ExecutionMode.LOOP) {
                        Text("LOOP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val pipeline = definition.nodes.joinToString(" → ") { it.worker }
            Text(
                "${definition.nodes.size} Node: $pipeline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (entity.isScheduled) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "⏰ Jadwal aktif tiap ${entity.scheduleIntervalMinutes} menit",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1565C0)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = onConfigureVariables, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Konfigurasi Variabel", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSchedule, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Jadwal",
                            tint = if (entity.isScheduled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Ekspor ZIP", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }

                Button(
                    onClick = onTogglePlayStop,
                    enabled = isThisActive || !isAnyRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isThisActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        if (isThisActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isThisActive) "Stop" else "Play")
                }
            }
        }
    }
}

@Composable
private fun ScheduleDialog(
    entity: WorkflowEntity,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, intervalMinutes: Long) -> Unit
) {
    var enabled by remember { mutableStateOf(entity.isScheduled) }
    var intervalInput by remember { mutableStateOf(entity.scheduleIntervalMinutes.coerceAtLeast(15L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jadwal Otomatis (WorkManager)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Workflow: ${entity.name}", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aktifkan Jadwal Berkala")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = { intervalInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Interval (Menit, min. 15)") },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val mins = intervalInput.toLongOrNull()?.coerceAtLeast(15L) ?: 15L
                onSave(enabled, mins)
            }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
