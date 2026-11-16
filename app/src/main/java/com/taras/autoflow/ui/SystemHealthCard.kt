package com.taras.autoflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taras.autoflow.util.ShizukuHelper
import com.taras.autoflow.util.SystemPermissionHelper

@Composable
fun SystemHealthCard() {
    val context = LocalContext.current
    val isA11y = SystemPermissionHelper.isAccessibilityEnabled(context)
    val isNotif = SystemPermissionHelper.isNotificationListenerEnabled(context)
    val isBattery = SystemPermissionHelper.isBatteryOptimizationIgnored(context)
    val isShizuku = ShizukuHelper.hasPermission()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status Kesehatan Sistem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            PermissionStatusRow("Accessibility Service", isA11y) {
                SystemPermissionHelper.openAccessibilitySettings(context)
            }
            PermissionStatusRow("Notification Listener", isNotif) {
                SystemPermissionHelper.openNotificationListenerSettings(context)
            }
            PermissionStatusRow("Abaikan Optimasi Baterai", isBattery) {
                SystemPermissionHelper.requestIgnoreBatteryOptimizations(context)
            }
            PermissionStatusRow("Shizuku System Shell", isShizuku) {
                ShizukuHelper.requestPermission(101)
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(title: String, isGranted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF2E7D32) else Color(0xFFE65100),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        if (!isGranted) {
            TextButton(onClick = onClick) {
                Text("Aktifkan")
            }
        }
    }
}
