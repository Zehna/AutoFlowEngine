package com.taras.autoflow.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariableEditorBottomSheet(
    initialKey: String,
    initialValue: String,
    availableSuggestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (key: String, value: String) -> Unit
) {
    var key by remember { mutableStateOf(initialKey) }
    var value by remember { mutableStateOf(initialValue) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Parameter Node", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Nama Parameter (Key)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (availableSuggestions.isNotEmpty()) {
                Text("Sisipkan Referensi Cepat:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableSuggestions.forEach { token ->
                        SuggestionChip(
                            onClick = { value = if (value.isEmpty()) token else "$value$token" },
                            label = { Text(token, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Nilai (mendukung {{node.field}}, {{vars.k}}, {{kv.k}})") },
                minLines = 3,
                maxLines = 10,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onDelete != null && initialKey.isNotBlank()) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hapus")
                    }
                }
                Button(
                    onClick = {
                        if (key.isNotBlank()) {
                            onSave(key.trim(), value)
                        }
                    },
                    modifier = Modifier.weight(2f)
                ) {
                    Text("Simpan Parameter")
                }
            }
        }
    }
}
