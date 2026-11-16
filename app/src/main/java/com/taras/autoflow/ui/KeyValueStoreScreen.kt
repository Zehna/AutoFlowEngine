package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyValueStoreScreen(
    viewModel: AutoFlowViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.kvStates.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf("") }
    var editingValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KV Store & Secrets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingKey = ""
                editingValue = ""
                showAddSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Key")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Text(
                        "Gunakan {{kv.nama_key}} di node manapun (contoh: simpan 'gemini_api_key' di sini agar tidak perlu diulang).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada data tersimpan di KV Vault.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.key }) { item ->
                        Card(
                            onClick = {
                                editingKey = item.key
                                editingValue = item.value
                                showAddSheet = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.key, fontWeight = FontWeight.Bold)
                                    Text(
                                        "ns: ${item.namespace} • ${item.value}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.Gray
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteKvEntry(item.key) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        VariableEditorBottomSheet(
            initialKey = editingKey,
            initialValue = editingValue,
            availableSuggestions = listOf("gemini_api_key"),
            onDismiss = { showAddSheet = false },
            onDelete = if (editingKey.isNotBlank()) {
                {
                    viewModel.deleteKvEntry(editingKey)
                    showAddSheet = false
                }
            } else null,
            onSave = { k, v ->
                viewModel.saveKvEntry(k, v)
                showAddSheet = false
            }
        )
    }
}
