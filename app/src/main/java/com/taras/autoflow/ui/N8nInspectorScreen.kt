package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taras.autoflow.n8n.N8nWorkflowSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun N8nInspectorScreen(
    viewModel: AutoFlowViewModel,
    onBack: () -> Unit,
    onOpenWebUi: (url: String) -> Unit
) {
    val config by viewModel.n8nConfig.collectAsStateWithLifecycle()
    val status by viewModel.n8nStatus.collectAsStateWithLifecycle()
    val workflows by viewModel.n8nWorkflows.collectAsStateWithLifecycle()
    val executions by viewModel.n8nExecutions.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.n8nSelectedDetail.collectAsStateWithLifecycle()
    val selectedExec by viewModel.n8nSelectedExec.collectAsStateWithLifecycle()
    val busyMessage by viewModel.n8nBusyMessage.collectAsStateWithLifecycle()
    val lastOutcome by viewModel.n8nLastOutcome.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showConfigSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshN8nAll()
    }

    val filteredWorkflows = remember(workflows, searchQuery) {
        if (searchQuery.isBlank()) workflows
        else workflows.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true) ||
                it.pipelinePreview.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("n8n Localhost", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshN8nAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showConfigSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Konfigurasi")
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            item {
                N8nServerControlCard(
                    baseUrl = config.normalizedBaseUrl(),
                    statusMessage = status.message,
                    n8nRunning = status.n8nRunning,
                    n8nBooting = status.n8nBooting,
                    bridgeAvailable = status.bridgeAvailable,
                    workflowCount = maxOf(status.workflowCount, workflows.size),
                    busyMessage = busyMessage,
                    onStart = { viewModel.startN8nServerAndRefresh() },
                    onStop = { viewModel.stopN8nServer() },
                    onOpenWeb = {
                        val url = config.normalizedBaseUrl()
                        if (status.n8nRunning) {
                            onOpenWebUi(url)
                        } else {
                            viewModel.startN8nServerAndRefresh { onOpenWebUi(url) }
                        }
                    },
                    onOpenConfig = { showConfigSheet = true }
                )
            }

            if (lastOutcome != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                lastOutcome!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearN8nOutcome() }) {
                                Text("Tutup")
                            }
                        }
                    }
                }
            }

            item {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Workflows (${workflows.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Eksekusi (${executions.size})") }
                    )
                }
            }

            if (selectedTab == 0) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari workflow di n8n localhost...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filteredWorkflows.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Belum ada workflow terbaca dari n8n lokal.", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Pastikan Bridge (:15678) berjalan di Omalauncher/Linux atau isi API Key / Email+Password di Pengaturan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { showConfigSheet = true }) {
                                        Text("Pengaturan")
                                    }
                                    Button(onClick = { viewModel.startN8nServerAndRefresh() }) {
                                        Text("Auto-Start & Muat")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredWorkflows, key = { it.id }) { wf ->
                        N8nWorkflowItemCard(
                            workflow = wf,
                            isBusy = busyMessage != null,
                            onInspect = { viewModel.inspectN8nWorkflow(wf.id) },
                            onToggleActive = { active -> viewModel.toggleN8nWorkflowActive(wf.id, active) },
                            onPinToDashboard = { viewModel.pinN8nWorkflowToDashboard(wf) },
                            onExecute = {
                                val hook = wf.triggers.firstOrNull { it.webhookPath.isNotBlank() }?.webhookPath
                                viewModel.executeN8nWorkflow(wf.id, webhookPath = hook)
                            }
                        )
                    }
                }
            } else {
                if (executions.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Belum ada riwayat eksekusi di database n8n lokal.",
                                modifier = Modifier.padding(20.dp),
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    items(executions, key = { "${it.id}_${it.startedAt}" }) { ex ->
                        OutlinedCard(
                            onClick = { viewModel.inspectN8nExecution(ex.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "#${ex.id} • ${ex.workflowName.ifBlank { ex.workflowId }}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        ex.status.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (ex.status.lowercase()) {
                                            "success" -> Color(0xFF2E7D32)
                                            "running", "waiting" -> Color(0xFFEF6C00)
                                            else -> Color(0xFFC62828)
                                        }
                                    )
                                }
                                Text(
                                    "Mode: ${ex.mode} • ${ex.nodes.size} node dieksekusi • ${ex.startedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                if (!ex.errorMessage.isNullOrBlank()) {
                                    Text(
                                        ex.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDetail?.let { detail ->
        N8nWorkflowDetailSheet(
            detail = detail,
            executions = executions,
            onDismiss = { viewModel.clearSelectedN8nWorkflow() },
            onRun = {
                val hook = detail.triggers.firstOrNull { it.webhookPath.isNotBlank() }?.webhookPath
                viewModel.executeN8nWorkflow(detail.id, webhookPath = hook)
            },
            onInspectExecution = { execId -> viewModel.inspectN8nExecution(execId) }
        )
    }

    selectedExec?.let { exec ->
        N8nExecutionDetailSheet(
            execution = exec,
            onDismiss = { viewModel.clearSelectedN8nExecution() }
        )
    }

    if (showConfigSheet) {
        N8nConfigSheet(
            current = config,
            onDismiss = { showConfigSheet = false },
            onSave = { updated ->
                viewModel.saveN8nConfig(updated)
                showConfigSheet = false
            },
            onLoginAndGenerateKey = { updated ->
                viewModel.loginAndGenerateN8nApiKey(updated)
                showConfigSheet = false
            }
        )
    }
}

@Composable
private fun N8nServerControlCard(
    baseUrl: String,
    statusMessage: String,
    n8nRunning: Boolean,
    n8nBooting: Boolean,
    bridgeAvailable: Boolean,
    workflowCount: Int,
    busyMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenConfig: () -> Unit
) {
    val containerColor = when {
        n8nRunning -> Color(0xFFE8F5E9)
        n8nBooting || busyMessage != null -> Color(0xFFFFF8E1)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            n8nRunning -> "● n8n ONLINE"
                            n8nBooting -> "◐ n8n BOOTING..."
                            else -> "○ n8n STANDBY / OFFLINE"
                        },
                        fontWeight = FontWeight.Bold,
                        color = when {
                            n8nRunning -> Color(0xFF1B5E20)
                            n8nBooting -> Color(0xFFE65100)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        "$baseUrl • $workflowCount workflow • Bridge: ${if (bridgeAvailable) "Aktif" else "Offline"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray
                    )
                }
                IconButton(onClick = onOpenConfig) {
                    Icon(Icons.Default.Tune, contentDescription = "Atur Koneksi")
                }
            }

            Text(busyMessage ?: statusMessage, style = MaterialTheme.typography.bodySmall)

            if (busyMessage != null || n8nBooting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (n8nRunning) {
                    OutlinedButton(
                        onClick = onStop,
                        enabled = busyMessage == null,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop n8n")
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = busyMessage == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start n8n")
                    }
                }
                FilledTonalButton(
                    onClick = onOpenWeb,
                    enabled = busyMessage == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buka Web UI")
                }
            }
        }
    }
}

@Composable
private fun N8nWorkflowItemCard(
    workflow: N8nWorkflowSummary,
    isBusy: Boolean,
    onInspect: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onPinToDashboard: () -> Unit,
    onExecute: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(workflow.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "ID: ${workflow.id} • ${workflow.nodeCount} node • Sumber: ${workflow.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = workflow.active,
                    onCheckedChange = onToggleActive
                )
            }

            if (workflow.pipelinePreview.isNotBlank()) {
                Text(
                    workflow.pipelinePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (workflow.triggers.isNotEmpty()) {
                Text(
                    "Trigger: ${workflow.triggers.joinToString(", ") { it.name.ifBlank { it.type } }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1565C0)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onInspect,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Inspeksi")
                    }
                    IconButton(onClick = onPinToDashboard) {
                        Icon(Icons.Default.PushPin, contentDescription = "Sematkan ke Dashboard")
                    }
                }
                Button(
                    onClick = onExecute,
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }
            }
        }
    }
}
