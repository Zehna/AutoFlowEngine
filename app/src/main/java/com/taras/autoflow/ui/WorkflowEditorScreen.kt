package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.WorkflowNode
import kotlinx.serialization.json.Json

private val editorJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    workflow: WorkflowDefinition,
    onBack: () -> Unit,
    onSaveWorkflow: (WorkflowDefinition) -> Unit,
    onRunWorkflow: (WorkflowDefinition) -> Unit
) {
    var wfName by remember { mutableStateOf(workflow.name) }
    var wfDescription by remember { mutableStateOf(workflow.description) }
    var wfVariables by remember { mutableStateOf(workflow.variables) }
    var currentNodes by remember { mutableStateOf(workflow.nodes) }

    var isRawJsonMode by remember { mutableStateOf(false) }
    var rawJsonText by remember {
        mutableStateOf(editorJson.encodeToString(WorkflowDefinition.serializer(), workflow))
    }
    var jsonError by remember { mutableStateOf<String?>(null) }

    var expandedNodeIndex by remember { mutableStateOf<Int?>(0) }
    var showAddNodeSheet by remember { mutableStateOf(false) }

    // State untuk VariableEditorBottomSheet (bisa mengedit parameter node atau variabel workflow)
    var editingNodeIndex by remember { mutableStateOf<Int?>(null) }
    var editingParamKey by remember { mutableStateOf<String?>(null) }
    var editingGlobalVarKey by remember { mutableStateOf<String?>(null) }

    fun buildCurrentDefinition(): WorkflowDefinition = workflow.copy(
        name = wfName.ifBlank { "Untitled Workflow" },
        description = wfDescription,
        variables = wfVariables,
        nodes = currentNodes
    )

    fun switchMode(toRaw: Boolean) {
        if (toRaw) {
            rawJsonText = editorJson.encodeToString(WorkflowDefinition.serializer(), buildCurrentDefinition())
            jsonError = null
            isRawJsonMode = true
        } else {
            runCatching {
                val parsed = editorJson.decodeFromString(WorkflowDefinition.serializer(), rawJsonText)
                wfName = parsed.name
                wfDescription = parsed.description
                wfVariables = parsed.variables
                currentNodes = parsed.nodes
                jsonError = null
                isRawJsonMode = false
            }.onFailure { err ->
                jsonError = "JSON tidak valid: ${err.message}"
            }
        }
    }

    fun resolveFinalDefinitionOrNull(): WorkflowDefinition? {
        return if (isRawJsonMode) {
            runCatching {
                editorJson.decodeFromString(WorkflowDefinition.serializer(), rawJsonText)
            }.onFailure { err ->
                jsonError = "JSON tidak valid: ${err.message}"
            }.getOrNull()
        } else {
            buildCurrentDefinition()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        wfName.ifBlank { "Editor Workflow" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = { switchMode(!isRawJsonMode) }) {
                        Text(if (isRawJsonMode) "Visual" else "JSON")
                    }
                    IconButton(onClick = {
                        resolveFinalDefinitionOrNull()?.let { def ->
                            onSaveWorkflow(def)
                            onRunWorkflow(def)
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Simpan & Jalankan")
                    }
                    IconButton(onClick = {
                        resolveFinalDefinitionOrNull()?.let { def ->
                            onSaveWorkflow(def)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Simpan")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isRawJsonMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAddNodeSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Tambah Node") }
                )
            }
        }
    ) { padding ->
        if (isRawJsonMode) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (jsonError != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            jsonError!!,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                OutlinedTextField(
                    value = rawJsonText,
                    onValueChange = {
                        rawJsonText = it
                        jsonError = null
                    },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
            ) {
                // Metadata & Global Variables Card
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = wfName,
                                onValueChange = { wfName = it },
                                label = { Text("Nama Workflow") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = wfDescription,
                                onValueChange = { wfDescription = it },
                                label = { Text("Deskripsi") },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Variabel Global ({{vars.*}})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = { editingGlobalVarKey = "" }) {
                                    Text("+ Variabel")
                                }
                            }

                            if (wfVariables.isNotEmpty()) {
                                wfVariables.forEach { (k, v) ->
                                    OutlinedCard(
                                        onClick = { editingGlobalVarKey = k },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("vars.$k", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            Text(v, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Pipeline Nodes (${currentNodes.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                itemsIndexed(currentNodes) { index, node ->
                    val isExpanded = expandedNodeIndex == index
                    NodeEditorCard(
                        index = index,
                        total = currentNodes.size,
                        node = node,
                        isExpanded = isExpanded,
                        onToggleExpand = { expandedNodeIndex = if (isExpanded) null else index },
                        onUpdateNode = { updated ->
                            currentNodes = currentNodes.toMutableList().apply { set(index, updated) }
                        },
                        onMoveUp = {
                            if (index > 0) {
                                currentNodes = currentNodes.toMutableList().apply {
                                    val moved = removeAt(index)
                                    add(index - 1, moved)
                                }
                                expandedNodeIndex = index - 1
                            }
                        },
                        onMoveDown = {
                            if (index < currentNodes.lastIndex) {
                                currentNodes = currentNodes.toMutableList().apply {
                                    val moved = removeAt(index)
                                    add(index + 1, moved)
                                }
                                expandedNodeIndex = index + 1
                            }
                        },
                        onDeleteNode = {
                            currentNodes = currentNodes.toMutableList().apply { removeAt(index) }
                            expandedNodeIndex = null
                        },
                        onEditParam = { paramKey ->
                            editingNodeIndex = index
                            editingParamKey = paramKey
                        }
                    )
                }
            }
        }
    }

    if (showAddNodeSheet) {
        ModalBottomSheet(onDismissRequest = { showAddNodeSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    "Pilih Tipe Node Modular",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    itemsIndexed(WorkflowNodePresets.allPresets) { _, preset ->
                        Card(
                            onClick = {
                                val nextIndex = currentNodes.size + 1
                                val uniqueId = "${preset.type.lowercase()}_$nextIndex"
                                currentNodes = currentNodes + preset.createDefault(uniqueId)
                                expandedNodeIndex = currentNodes.lastIndex
                                showAddNodeSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(preset.title, fontWeight = FontWeight.Bold)
                                    Badge { Text(preset.type) }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(preset.summary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet editor parameter node
    val targetNodeIdx = editingNodeIndex
    val targetParamKey = editingParamKey
    if (targetNodeIdx != null && targetParamKey != null && targetNodeIdx in currentNodes.indices) {
        val targetNode = currentNodes[targetNodeIdx]
        val prevNodes = currentNodes.take(targetNodeIdx)
        val suggestions = buildList {
            prevNodes.forEach { prev ->
                when (prev.type) {
                    "HTTP" -> add("{{${prev.id}.body}}")
                    "PYTHON" -> add("{{${prev.id}.status}}")
                    "OFFSCREEN_WEB" -> add("{{${prev.id}.content}}")
                    "SHELL" -> add("{{${prev.id}.stdout}}")
                    "GEMINI_AI" -> add("{{${prev.id}.reply}}")
                    "COMPUTE" -> add("{{${prev.id}.value}}")
                    else -> add("{{${prev.id}.result}}")
                }
            }
            wfVariables.keys.forEach { add("{{vars.$it}}") }
            add("{{kv.gemini_api_key}}")
        }

        VariableEditorBottomSheet(
            initialKey = targetParamKey,
            initialValue = targetNode.parameters[targetParamKey] ?: "",
            availableSuggestions = suggestions,
            onDismiss = {
                editingNodeIndex = null
                editingParamKey = null
            },
            onDelete = if (targetParamKey.isNotBlank()) {
                {
                    val newParams = targetNode.parameters.toMutableMap().apply { remove(targetParamKey) }
                    currentNodes = currentNodes.toMutableList().apply { set(targetNodeIdx, targetNode.copy(parameters = newParams)) }
                    editingNodeIndex = null
                    editingParamKey = null
                }
            } else null,
            onSave = { newKey, newVal ->
                val newParams = targetNode.parameters.toMutableMap().apply {
                    if (targetParamKey.isNotBlank() && targetParamKey != newKey) remove(targetParamKey)
                    put(newKey, newVal)
                }
                currentNodes = currentNodes.toMutableList().apply { set(targetNodeIdx, targetNode.copy(parameters = newParams)) }
                editingNodeIndex = null
                editingParamKey = null
            }
        )
    }

    // Bottom sheet editor global variables
    val globalVarKey = editingGlobalVarKey
    if (globalVarKey != null) {
        VariableEditorBottomSheet(
            initialKey = globalVarKey,
            initialValue = wfVariables[globalVarKey] ?: "",
            onDismiss = { editingGlobalVarKey = null },
            onDelete = if (globalVarKey.isNotBlank()) {
                {
                    wfVariables = wfVariables.toMutableMap().apply { remove(globalVarKey) }
                    editingGlobalVarKey = null
                }
            } else null,
            onSave = { newKey, newVal ->
                wfVariables = wfVariables.toMutableMap().apply {
                    if (globalVarKey.isNotBlank() && globalVarKey != newKey) remove(globalVarKey)
                    put(newKey, newVal)
                }
                editingGlobalVarKey = null
            }
        )
    }
}

@Composable
private fun NodeEditorCard(
    index: Int,
    total: Int,
    node: WorkflowNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdateNode: (WorkflowNode) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDeleteNode: () -> Unit,
    onEditParam: (key: String) -> Unit
) {
    Card(
        onClick = onToggleExpand,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${index + 1}. ${node.label.ifBlank { node.id }}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ID: ${node.id} • Tipe: ${node.type}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Naik", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Turun", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteNode, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.id,
                        onValueChange = { onUpdateNode(node.copy(id = it.replace(" ", "_"))) },
                        label = { Text("Node ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = node.label,
                        onValueChange = { onUpdateNode(node.copy(label = it)) },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = node.condition ?: "",
                    onValueChange = { onUpdateNode(node.copy(condition = it.ifBlank { null })) },
                    label = { Text("Kondisi Opsional (mis. not_empty {{prev.body}})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = node.continueOnError,
                            onCheckedChange = { onUpdateNode(node.copy(continueOnError = it)) }
                        )
                        Text("Lanjut jika error", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = node.maxRetries.toString(),
                        onValueChange = { v -> onUpdateNode(node.copy(maxRetries = v.toIntOrNull()?.coerceIn(0, 10) ?: 0)) },
                        label = { Text("Max Retry") },
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Parameter:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { onEditParam("") }) {
                        Text("+ Parameter")
                    }
                }

                node.parameters.forEach { (k, v) ->
                    OutlinedCard(
                        onClick = { onEditParam(k) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(k, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(
                                v,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
