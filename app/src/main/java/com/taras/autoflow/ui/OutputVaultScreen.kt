package com.taras.autoflow.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputVaultScreen(
    viewModel: AutoFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == "ALL") logs else logs.filter { it.status == selectedFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Output Vault (${filteredLogs.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.exportVaultCsv { file ->
                            BundleShareHelper.shareFile(context, file)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Ekspor CSV")
                    }
                    IconButton(onClick = { viewModel.clearVaultLogs() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Bersihkan")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "SUCCESS", "FAILED", "SKIPPED", "ABORTED").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada log untuk filter '$selectedFilter'.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { item ->
                        val isExpanded = expandedLogId == item.id
                        val timeText = remember(item.executedAt) {
                            SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(item.executedAt))
                        }

                        Card(
                            onClick = { expandedLogId = if (isExpanded) null else item.id },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${item.workflowName} → ${item.nodeId}", fontWeight = FontWeight.Bold)
                                        Text(
                                            "${item.nodeType} • ${item.executionDurationMs}ms • $timeText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        item.status,
                                        fontWeight = FontWeight.Bold,
                                        color = when (item.status) {
                                            "SUCCESS" -> Color(0xFF2E7D32)
                                            "FAILED", "ABORTED" -> Color(0xFFC62828)
                                            else -> Color(0xFFEF6C00)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (isExpanded) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    Text("Input:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(item.inputPayload, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Output:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(item.outputPayload, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                } else {
                                    Text(
                                        "Output: ${item.outputPayload}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!item.errorTrace.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Error: ${item.errorTrace}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = if (isExpanded) 20 else 2,
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
}
