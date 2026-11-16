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
import com.taras.autoflow.engine.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

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
    var wfName by remember(workflow.id) { mutableStateOf(workflow.name) }
    var wfDescription by remember(workflow.id) { mutableStateOf(workflow.description) }
    var wfVariables by remember(workflow.id) { mutableStateOf(workflow.variables) }
    var wfExecutionMode by remember(workflow.id) { mutableStateOf(workflow.executionPolicy.mode) }
    var wfMaxIterations by remember(workflow.id) { mutableStateOf(workflow.executionPolicy.maxIterations.toString()) }
    var wfLoopDelayMs by remember(workflow.id) { mutableStateOf(workflow.executionPolicy.loopDelayMs.toString()) }
    var currentNodes by remember(workflow.id) { mutableStateOf(workflow.nodes) }

    var isRawJsonMode by remember { mutableStateOf(false) }
    var rawJsonText by remember(workflow.id) {
        mutableStateOf(editorJson.encodeToString(WorkflowDefinition.serializer(), workflow))
    }
    var jsonError by remember { mutableStateOf<String?>(null) }

    var expandedNodeIndex by remember { mutableStateOf<Int?>(0) }
    var showAddNodeSheet by remember { mutableStateOf(false) }

    var editingNodeIndex by remember { mutableStateOf<Int?>(null) }
    var editingParamKey by remember { mutableStateOf<String?>(null) }
    var editingGlobalVarKey by remember { mutableStateOf<String?>(null) }

    fun buildCurrentDefinition(): WorkflowDefinition = workflow.copy(
        name = wfName.ifBlank { "Untitled Workflow" },
        description = wfDescription,
        variables = wfVariables,
        executionPolicy = ExecutionPolicy(
            mode = wfExecutionMode,
            maxIterations = wfMaxIterations.toIntOrNull() ?: 1,
            loopDelayMs = wfLoopDelayMs.toLongOrNull()?.coerceAtLeast(100L) ?: 2000L
        ),
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
                wfExecutionMode = parsed.executionPolicy.mode
                wfMaxIterations = parsed.executionPolicy.maxIterations.toString()
                wfLoopDelayMs = parsed.executionPolicy.loopDelayMs.toString()
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
                        wfName.ifBlank { "Workflow Editor" },
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
                // Metadata, ExecutionPolicy & Global Variables
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kebijakan Eksekusi:", style = MaterialTheme.typography.labelLarge)
                                FilterChip(
                                    selected = wfExecutionMode == ExecutionMode.ONCE,
                                    onClick = { wfExecutionMode = ExecutionMode.ONCE },
                                    label = { Text("ONCE") }
                                )
                                FilterChip(
                                    selected = wfExecutionMode == ExecutionMode.LOOP,
                                    onClick = { wfExecutionMode = ExecutionMode.LOOP },
                                    label = { Text("LOOP") }
                                )
                            }

                            if (wfExecutionMode == ExecutionMode.LOOP) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = wfMaxIterations,
                                        onValueChange = { wfMaxIterations = it.filter { c -> c.isDigit() || c == '-' } },
                                        label = { Text("Max Loop (0 = ∞)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = wfLoopDelayMs,
                                        onValueChange = { wfLoopDelayMs = it.filter { c -> c.isDigit() } },
                                        label = { Text("Jeda Loop (ms)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Variabel Global ({{variables.*}})",
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
                                            Text(
                                                "variables.$k (${v.detectType().name})",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                v.asPlainString(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall
                                            )
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
                    "Template Cepat Node Modular",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 440.dp)
                ) {
                    itemsIndexed(WorkflowNodePresets.allPresets) { _, preset ->
                        Card(
                            onClick = {
                                val nextIndex = currentNodes.size + 1
                                val uniqueId = "${preset.worker.lowercase().substringBefore("_")}_$nextIndex"
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
                                    Badge { Text("${preset.worker}:${preset.action}") }
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

    val targetNodeIdx = editingNodeIndex
    val targetParamKey = editingParamKey
    if (targetNodeIdx != null && targetParamKey != null && targetNodeIdx in currentNodes.indices) {
        val targetNode = currentNodes[targetNodeIdx]
        val prevNodes = currentNodes.take(targetNodeIdx)
        val suggestions = buildList {
            prevNodes.forEach { prev ->
                when (prev.worker.uppercase()) {
                    "NETWORK_HTTP", "HTTP" -> add("{{${prev.id}.output.body}}")
                    "PYTHON_COMPUTE", "PYTHON" -> add("{{${prev.id}.output.status}}")
                    "WEBVIEW_HEADLESS", "OFFSCREEN_WEB" -> add("{{${prev.id}.output.content}}")
                    "SHIZUKU_SHELL", "SHELL" -> add("{{${prev.id}.output.stdout}}")
                    "GEMINI_AI" -> add("{{${prev.id}.output.reply}}")
                    "COMPUTE_KOTLIN", "COMPUTE" -> add("{{${prev.id}.output.value}}")
                    else -> add("{{${prev.id}.output.result}}")
                }
            }
            wfVariables.keys.forEach { add("{{variables.$it}}") }
            add("{{kv.gemini_api_key}}")
        }

        val currentElem = targetNode.params[targetParamKey]
        VariableEditorBottomSheet(
            initialKey = targetParamKey,
            initialValue = currentElem?.asPlainString() ?: "",
            availableSuggestions = suggestions,
            onDismiss = {
                editingNodeIndex = null
                editingParamKey = null
            },
            onDelete = if (targetParamKey.isNotBlank()) {
                {
                    val newParams = targetNode.params.toMutableMap().apply { remove(targetParamKey) }
                    currentNodes = currentNodes.toMutableList().apply { set(targetNodeIdx, targetNode.copy(params = newParams)) }
                    editingNodeIndex = null
                    editingParamKey = null
                }
            } else null,
            onSave = { newKey, newVal ->
                val newParams = targetNode.params.toMutableMap().apply {
                    if (targetParamKey.isNotBlank() && targetParamKey != newKey) remove(targetParamKey)
                    val targetType = currentElem?.detectType() ?: VariableValueType.STRING
                    put(newKey, stringToTypedJsonElement(newVal, targetType))
                }
                currentNodes = currentNodes.toMutableList().apply { set(targetNodeIdx, targetNode.copy(params = newParams)) }
                editingNodeIndex = null
                editingParamKey = null
            }
        )
    }

    val globalVarKey = editingGlobalVarKey
    if (globalVarKey != null) {
        val currentVarElem = wfVariables[globalVarKey]
        VariableEditorBottomSheet(
            initialKey = globalVarKey,
            initialValue = currentVarElem?.asPlainString() ?: "",
            availableSuggestions = listOf("true", "false", "10"),
            onDismiss = { editingGlobalVarKey = null },
            onDelete = if (globalVarKey.isNotBlank()) {
                {
                    wfVariables = wfVariables.toMutableMap().apply { remove(globalVarKey) }
                    editingGlobalVarKey = null
                }
            } else null,
            onSave = { newKey, newVal ->
                val inferredType = when {
                    currentVarElem != null -> currentVarElem.detectType()
                    newVal.equals("true", ignoreCase = true) || newVal.equals("false", ignoreCase = true) -> VariableValueType.BOOLEAN
                    newVal.toLongOrNull() != null || newVal.toDoubleOrNull() != null -> VariableValueType.NUMBER
                    newVal.trim().startsWith("{") || newVal.trim().startsWith("[") -> VariableValueType.JSON
                    else -> VariableValueType.STRING
                }
                wfVariables = wfVariables.toMutableMap().apply {
                    if (globalVarKey.isNotBlank() && globalVarKey != newKey) remove(globalVarKey)
                    put(newKey, stringToTypedJsonElement(newVal, inferredType))
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
                        "${index + 1}. ${node.name.ifBlank { node.id }}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ID: ${node.id} • ${node.worker}${if (node.action.isNotBlank()) ":${node.action}" else ""}" +
                            if (!node.next.isNullOrBlank()) " → ${node.next}" else "",
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
                        value = node.name,
                        onValueChange = { onUpdateNode(node.copy(name = it)) },
                        label = { Text("Nama Node") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = node.action,
                        onValueChange = { onUpdateNode(node.copy(action = it.uppercase())) },
                        label = { Text("Action") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = node.next ?: "",
                        onValueChange = { onUpdateNode(node.copy(next = it.ifBlank { null })) },
                        label = { Text("Next Node / END") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = node.condition ?: "",
                    onValueChange = { onUpdateNode(node.copy(condition = it.ifBlank { null })) },
                    label = { Text("Kondisi (mis. not_empty {{prev.output.body}})") },
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
                        value = node.retry.maxAttempts.toString(),
                        onValueChange = { v ->
                            val max = v.toIntOrNull()?.coerceIn(0, 10) ?: 0
                            onUpdateNode(node.copy(retry = node.retry.copy(maxAttempts = max)))
                        },
                        label = { Text("Retry") },
                        singleLine = true,
                        modifier = Modifier.width(92.dp)
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

                node.params.forEach { (k, v) ->
                    OutlinedCard(
                        onClick = { onEditParam(k) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(k, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(
                                v.asPlainString(),
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
