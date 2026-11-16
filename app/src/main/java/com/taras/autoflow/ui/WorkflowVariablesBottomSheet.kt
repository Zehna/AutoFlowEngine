package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.taras.autoflow.engine.VariableValueType
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.engine.asPlainString
import com.taras.autoflow.engine.detectType
import com.taras.autoflow.engine.stringToTypedJsonElement
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowVariablesBottomSheet(
    definition: WorkflowDefinition,
    onDismiss: () -> Unit,
    onSave: (Map<String, JsonElement>) -> Unit,
    onSaveAndRun: (Map<String, JsonElement>) -> Unit
) {
    val editableValues = remember(definition.id) {
        mutableStateMapOf<String, String>().apply {
            definition.variables.forEach { (k, v) -> put(k, v.asPlainString()) }
        }
    }
    val typesMap = remember(definition.id) {
        definition.variables.mapValues { (_, v) -> v.detectType() }
    }

    fun buildTypedVariables(): Map<String, JsonElement> {
        return editableValues.mapValues { (k, raw) ->
            val t = typesMap[k] ?: VariableValueType.STRING
            stringToTypedJsonElement(raw, t)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Konfigurasi Variabel: ${definition.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (editableValues.isEmpty()) {
                Text(
                    "Workflow ini belum memiliki variabel global ({{variables.*}}). Tambahkan melalui Workflow Editor.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    editableValues.keys.toList().forEach { key ->
                        val type = typesMap[key] ?: VariableValueType.STRING
                        val currentVal = editableValues[key] ?: ""

                        when (type) {
                            VariableValueType.BOOLEAN -> {
                                val checked = currentVal.equals("true", ignoreCase = true)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(key, fontWeight = FontWeight.SemiBold)
                                        Text("Tipe: Boolean", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Switch(
                                        checked = checked,
                                        onCheckedChange = { editableValues[key] = it.toString() }
                                    )
                                }
                            }
                            VariableValueType.NUMBER -> {
                                OutlinedTextField(
                                    value = currentVal,
                                    onValueChange = { editableValues[key] = it },
                                    label = { Text("$key (Angka)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            VariableValueType.JSON -> {
                                OutlinedTextField(
                                    value = currentVal,
                                    onValueChange = { editableValues[key] = it },
                                    label = { Text("$key (JSON)") },
                                    minLines = 2,
                                    maxLines = 6,
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            VariableValueType.STRING -> {
                                OutlinedTextField(
                                    value = currentVal,
                                    onValueChange = { editableValues[key] = it },
                                    label = { Text("$key (Teks)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSave(buildTypedVariables()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simpan")
                }
                Button(
                    onClick = { onSaveAndRun(buildTypedVariables()) },
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan & Jalankan")
                }
            }
        }
    }
}
