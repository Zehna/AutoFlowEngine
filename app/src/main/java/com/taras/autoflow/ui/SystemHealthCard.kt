package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.taras.autoflow.engine.HealthCheckItem
import com.taras.autoflow.service.PlatformAutomation

@Composable
fun SystemHealthCard() {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshTick++
    }

    val items = remember(refreshTick) {
        PlatformAutomation.getHealthItems(context)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Status Kesehatan Sistem",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { refreshTick++ }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Periksa Ulang", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { item ->
                PermissionStatusRow(item)
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(item: HealthCheckItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (item.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (item.isGranted) Color(0xFF2E7D32) else Color(0xFFE65100),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
        }
        if (!item.isGranted) {
            TextButton(onClick = item.onAction) {
                Text(item.actionLabel)
            }
        }
    }
}
