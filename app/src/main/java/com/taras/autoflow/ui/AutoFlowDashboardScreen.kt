package com.taras.autoflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.taras.autoflow.engine.WorkflowDefinition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoFlowDashboardScreen(
    viewModel: AutoFlowViewModel,
    snackbarHostState: SnackbarHostState,
    onCreateWorkflow: () -> Unit,
    onEditWorkflow: (WorkflowDefinition) -> Unit,
    onNavigateVault: () -> Unit,
    onNavigateWebLogin: () -> Unit,
    onNavigateKvStore: () -> Unit,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    val isRunning by AutoFlowRuntime.isEngineActive.collectAsStateWithLifecycle()
    val activeWorkflowName by AutoFlowRuntime.activeWorkflowName.collectAsStateWithLifecycle()
    val activeNode by AutoFlowRuntime.runningNodeId.collectAsStateWithLifecycle()
    val progressText by AutoFlowRuntime.progressText.collectAsStateWithLifecycle()
    val consoleLogs by AutoFlowRuntime.consoleLogs.collectAsStateWithLifecycle()
    val workflows by viewModel.workflows.collectAsStateWithLifecycle()

    var showConsole by remember { mutableStateOf(false) }
    var scheduleTarget by remember { mutableStateOf<WorkflowEntity?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AutoFlow Engine", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateWebLogin) {
                        Icon(Icons.Default.Language, contentDescription = "Browser Login Cookie")
                    }
                    IconButton(onClick = onNavigateKvStore) {
                        Icon(Icons.Default.Key, contentDescription = "KV Store & Secrets")
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Impor JSON/ZIP")
                    }
                    IconButton(onClick = onNavigateVault) {
                        Icon(Icons.Default.Storage, contentDescription = "Output Vault")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateWorkflow,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Workflow Baru") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 92.dp)
        ) {
            item {
                SystemHealthCard()
            }

            // Kartu Status Engine & Live Console
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRunning) "ENGINE RUNNING" else "ENGINE SIAGA",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRunning) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                                if (isRunning) {
                                    Text(
                                        "${activeWorkflowName ?: "Workflow"} • ${progressText ?: ""} (${activeNode ?: "..."})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Text(
                                        "${workflows.size} workflow siap dijalankan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { showConsole = !showConsole }) {
                                    Text(if (showConsole) "Tutup Log" else "Console")
                                }
                                if (isRunning) {
                                    Button(
                                        onClick = { viewModel.stopExecution() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("STOP")
                                    }
                                }
                            }
                        }

                        if (showConsole || isRunning) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("LIVE CONSOLE", color = Color(0xFF81C784), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    TextButton(
                                        onClick = { AutoFlowRuntime.clearConsole() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Clear", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                val tail = consoleLogs.takeLast(8)
                                if (tail.isEmpty()) {
                                    Text("Belum ada log aktivitas.", color = Color.Gray, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    tail.forEach { line ->
                                        Text(line, color = Color(0xFFE0E0E0), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Daftar Workflow Modular",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.restoreStarterWorkflows() }) {
                        Text("+ Preset Starter")
                    }
                }
            }

            if (workflows.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Belum ada workflow tersimpan.")
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
                        WorkflowItemCard(
                            entity = entity,
                            definition = definition,
                            isEngineRunning = isRunning,
                            onRun = { viewModel.startWorkflow(definition) },
                            onEdit = { onEditWorkflow(definition) },
                            onSchedule = { scheduleTarget = entity },
                            onExport = {
                                viewModel.exportWorkflowBundle(definition) { zipFile ->
                                    BundleShareHelper.shareFile(context, zipFile, "application/zip")
                                }
                            },
                            onDuplicate = { viewModel.duplicateWorkflow(entity) },
                            onDelete = { viewModel.deleteWorkflow(entity.id) }
                        )
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
}

@Composable
private fun WorkflowItemCard(
    entity: WorkflowEntity,
    definition: WorkflowDefinition,
    isEngineRunning: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onSchedule: () -> Unit,
    onExport: () -> Unit,
    onDuplicate: () -> Unit,
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            val pipeline = definition.nodes.joinToString(" → ") { it.type }
            Text(
                "${definition.nodes.size} Node: $pipeline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (entity.isScheduled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "⏰ Terjadwal otomatis tiap ${entity.scheduleIntervalMinutes} menit",
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
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onSchedule) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Jadwal",
                            tint = if (entity.isScheduled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = "Ekspor ZIP")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplikat")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                    }
                }

                Button(onClick = onRun, enabled = !isEngineRunning) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Jalankan")
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
