package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputVaultScreen(
    viewModel: AutoFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Output Vault & Logs", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.nodeId, fontWeight = FontWeight.Bold)
                            Text(item.status, color = if (item.status == "SUCCESS") Color(0xFF2E7D32) else Color(0xFFC62828))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Output: ${item.outputPayload}", style = MaterialTheme.typography.bodySmall)
                        if (!item.errorTrace.isNullOrEmpty()) {
                            Text("Error: ${item.errorTrace}", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
