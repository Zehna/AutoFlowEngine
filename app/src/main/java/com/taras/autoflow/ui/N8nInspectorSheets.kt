package com.taras.autoflow.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.taras.autoflow.n8n.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun N8nWorkflowDetailSheet(
    detail: N8nWorkflowDetail,
    executions: List<N8nExecutionInfo>,
    onDismiss: () -> Unit,
    onRun: () -> Unit,
    onInspectExecution: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Node (${detail.nodes.size})", "Alur (${detail.edges.size})", "Riwayat", "JSON")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(detail.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "ID: ${detail.id} • ${if (detail.active) "AKTIF" else "NONAKTIF"} • Sumber: ${detail.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Button(onClick = onRun, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (detail.stickyNotes.isNotEmpty()) {
                            itemsIndexed(detail.stickyNotes) { _, note ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(note.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF795548))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(note.content, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4E342E))
                                    }
                                }
                            }
                        }
                        itemsIndexed(detail.nodes) { idx, node ->
                            var expanded by remember(node.id) { mutableStateOf(idx == 0) }
                            OutlinedCard(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${idx + 1}. ${node.name}",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (node.isTrigger) {
                                            Badge(containerColor = Color(0xFF1565C0)) { Text("TRIGGER", color = Color.White) }
                                        } else if (node.disabled) {
                                            Badge(containerColor = Color.Gray) { Text("DISABLED", color = Color.White) }
                                        }
                                    }
                                    Text(
                                        "${node.type} (v${node.typeVersion})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (node.credentialTypes.isNotEmpty()) {
                                        Text(
                                            "Kredensial: ${node.credentialTypes.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                    if (expanded) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        Text("Parameter:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            node.parametersJson,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (detail.edges.isEmpty()) {
                            item {
                                Text("Belum ada koneksi antar node.", color = Color.Gray, modifier = Modifier.padding(12.dp))
                            }
                        } else {
                            itemsIndexed(detail.edges) { idx, edge ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${idx + 1}. ${edge.from}  →  ${edge.to}",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text("${edge.type}[${edge.branch}]", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    val wfExecs = executions.filter { it.workflowId == detail.id }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (wfExecs.isEmpty()) {
                            item {
                                Text("Belum ada riwayat eksekusi untuk workflow ini.", color = Color.Gray, modifier = Modifier.padding(12.dp))
                            }
                        } else {
                            itemsIndexed(wfExecs) { _, ex ->
                                OutlinedCard(
                                    onClick = { onInspectExecution(ex.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Eksekusi #${ex.id} (${ex.mode})", fontWeight = FontWeight.Bold)
                                            Text(
                                                ex.status.uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = if (ex.status == "success") Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                        }
                                        Text("Mulai: ${ex.startedAt}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        if (!ex.errorMessage.isNullOrBlank()) {
                                            Text("Error: ${ex.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(detail.rawJson)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Raw JSON Workflow")
                        }
                        Surface(
                            color = Color(0xFF101317),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            Text(
                                detail.rawJson,
                                color = Color(0xFFE0E0E0),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun N8nExecutionDetailSheet(
    execution: N8nExecutionInfo,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Eksekusi #${execution.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${execution.workflowName} • mode: ${execution.mode}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Text(
                    execution.status.uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = if (execution.status == "success") Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }

            if (!execution.errorMessage.isNullOrBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                    Text(
                        "Error: ${execution.errorMessage}",
                        color = Color(0xFFB71C1C),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(execution.nodes) { _, node ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(node.nodeName, fontWeight = FontWeight.Bold)
                                Text(
                                    "${node.status} • ${node.executionTimeMs}ms • ${node.itemCount} item",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (node.error != null) Color(0xFFC62828) else Color(0xFF2E7D32)
                                )
                            }
                            if (!node.error.isNullOrBlank()) {
                                Text("Error: ${node.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (node.outputPreview.isNotBlank()) {
                                Text(
                                    node.outputPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun N8nConfigSheet(
    current: N8nConfig,
    onDismiss: () -> Unit,
    onSave: (N8nConfig) -> Unit,
    onLoginAndGenerateKey: (N8nConfig) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var baseUrl by remember(current) { mutableStateOf(current.baseUrl) }
    var bridgeUrl by remember(current) { mutableStateOf(current.bridgeUrl) }
    var bridgeToken by remember(current) { mutableStateOf(current.bridgeToken) }
    var apiKey by remember(current) { mutableStateOf(current.apiKey) }
    var email by remember(current) { mutableStateOf(current.email) }
    var password by remember(current) { mutableStateOf(current.password) }
    var mode by remember(current) { mutableStateOf(current.autoStartMode) }
    var shizukuCmd by remember(current) { mutableStateOf(current.shizukuStartCommand) }
    var termuxCmd by remember(current) { mutableStateOf(current.termuxCommand) }
    var timeoutSec by remember(current) { mutableStateOf(current.bootTimeoutSeconds.toString()) }

    fun buildConfig(): N8nConfig = N8nConfig(
        baseUrl = baseUrl,
        bridgeUrl = bridgeUrl,
        bridgeToken = bridgeToken,
        apiKey = apiKey,
        email = email,
        password = password,
        autoStartMode = mode,
        shizukuStartCommand = shizukuCmd,
        termuxCommand = termuxCmd,
        bootTimeoutSeconds = timeoutSec.toIntOrNull()?.coerceIn(20, 300) ?: 150
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Konfigurasi n8n Localhost & Auto-Start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("URL Server n8n") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bridgeUrl,
                onValueChange = { bridgeUrl = it },
                label = { Text("URL Local Bridge (Omalauncher/Linux)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bridgeToken,
                onValueChange = { bridgeToken = it },
                label = { Text("Bridge Token (opsional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Mode Auto-Start Server", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                N8nAutoStartMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { mode = item },
                        label = { Text(item.name) }
                    )
                }
            }
            Text(mode.label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)

            OutlinedTextField(
                value = timeoutSec,
                onValueChange = { timeoutSec = it.filter { c -> c.isDigit() } },
                label = { Text("Timeout Boot n8n (detik)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Autentikasi n8n (Opsional untuk API & Manual Run)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("n8n API Key (X-N8N-API-KEY)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Login n8n") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password Login n8n") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { onLoginAndGenerateKey(buildConfig()) },
                enabled = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Login & Buat API Key Otomatis")
            }

            if (mode == N8nAutoStartMode.AUTO || mode == N8nAutoStartMode.SHIZUKU) {
                OutlinedTextField(
                    value = shizukuCmd,
                    onValueChange = { shizukuCmd = it },
                    label = { Text("Perintah Shizuku Start (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (mode == N8nAutoStartMode.AUTO || mode == N8nAutoStartMode.TERMUX) {
                OutlinedTextField(
                    value = termuxCmd,
                    onValueChange = { termuxCmd = it },
                    label = { Text("Perintah Termux Start") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedButton(
                onClick = {
                    val cmd = "git clone https://github.com/Zehna/AutoFlowEngine.git && bash AutoFlowEngine/bridge/install.sh"
                    clipboard.setText(AnnotatedString(cmd))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salin Perintah Pasang Bridge di Linux/Omalauncher")
            }

            Button(
                onClick = { onSave(buildConfig()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan Konfigurasi")
            }
        }
    }
}
