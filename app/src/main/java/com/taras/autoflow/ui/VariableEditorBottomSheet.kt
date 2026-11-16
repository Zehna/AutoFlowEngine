package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariableEditorBottomSheet(
    initialKey: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (key: String, value: String) -> Unit
) {
    var key by remember { mutableStateOf(initialKey) }
    var value by remember { mutableStateOf(initialValue) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Edit Variabel Parameter", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Nama Kunci (Key)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Nilai (Dukung {{node.output}})") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSave(key, value) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan Variabel")
            }
        }
    }
}
