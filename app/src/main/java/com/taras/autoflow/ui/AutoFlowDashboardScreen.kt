package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taras.autoflow.engine.AutoFlowRuntime
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.WorkflowNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoFlowDashboardScreen(
    viewModel: AutoFlowViewModel,
    onNavigateVault: () -> Unit
) {
    val isRunning by AutoFlowRuntime.isEngineActive.collectAsStateWithLifecycle()
    val activeNode by AutoFlowRuntime.runningNodeId.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoFlow Engine v2", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateVault) {
                        Icon(Icons.Default.Storage, contentDescription = "Output Vault")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SystemHealthCard()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isRunning) "ENGINE RUNNING" else "ENGINE IDLE",
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        if (isRunning && activeNode != null) {
                            Text("Node aktif: $activeNode", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(
                        onClick = {
                            if (isRunning) viewModel.stopExecution()
                            else {
                                val demoWorkflow = WorkflowDefinition(
                                    id = "wf_demo_01",
                                    name = "Demo Python Scraping",
                                    nodes = listOf(
                                        WorkflowNode(
                                            id = "node_python",
                                            type = "PYTHON",
                                            parameters = mapOf(
                                                "script" to "import requests\noutputs['status'] = 'OK'\noutputs['len'] = 10",
                                                "input_json" to "{}"
                                            )
                                        )
                                    )
                                )
                                viewModel.startWorkflow(demoWorkflow)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "STOP" else "RUN DEMO")
                    }
                }
            }

            Text("Riwayat Eksekusi Singkat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val logs by viewModel.logs.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs.take(20), key = { it.id }) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${log.workflowName} → ${log.nodeId}", fontWeight = FontWeight.SemiBold)
                                Text("Tipe: ${log.nodeType} | ${log.executionDurationMs}ms", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Text(
                                log.status,
                                fontWeight = FontWeight.Bold,
                                color = when (log.status) {
                                    "SUCCESS" -> Color(0xFF2E7D32)
                                    "FAILED" -> Color(0xFFC62828)
                                    else -> Color(0xFFEF6C00)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
