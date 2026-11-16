package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.WorkflowNode
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    workflow: WorkflowDefinition,
    onBack: () -> Unit,
    onSaveWorkflow: (WorkflowDefinition) -> Unit
) {
    var isRawJsonMode by remember { mutableStateOf(false) }
    var rawJsonText by remember {
        mutableStateOf(Json { prettyPrint = true }.encodeToString(WorkflowDefinition.serializer(), workflow))
    }
    var currentNodes by remember { mutableStateOf(workflow.nodes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor Alur: ${workflow.name}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = { isRawJsonMode = !isRawJsonMode }) {
                        Text(if (isRawJsonMode) "Mode Visual" else "Mode JSON")
                    }
                    IconButton(onClick = {
                        if (isRawJsonMode) {
                            try {
                                val parsed = Json.decodeFromString<WorkflowDefinition>(rawJsonText)
                                onSaveWorkflow(parsed)
                            } catch (_: Exception) {}
                        } else {
                            onSaveWorkflow(workflow.copy(nodes = currentNodes))
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Simpan")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isRawJsonMode) {
                FloatingActionButton(onClick = {
                    currentNodes = currentNodes + WorkflowNodePresets.createPythonScraperPreset()
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Node")
                }
            }
        }
    ) { padding ->
        if (isRawJsonMode) {
            OutlinedTextField(
                value = rawJsonText,
                onValueChange = { rawJsonText = it },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentNodes) { node ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("ID: ${node.id}", fontWeight = FontWeight.Bold)
                            Text("Tipe: ${node.type}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Parameter: ${node.parameters}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
