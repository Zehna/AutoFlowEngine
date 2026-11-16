package com.taras.autoflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.taras.autoflow.ui.AutoFlowDashboardScreen
import com.taras.autoflow.ui.AutoFlowViewModel
import com.taras.autoflow.ui.OutputVaultScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AutoFlowViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf("dashboard") }

                when (currentScreen) {
                    "dashboard" -> AutoFlowDashboardScreen(
                        viewModel = viewModel,
                        onNavigateVault = { currentScreen = "vault" }
                    )
                    "vault" -> OutputVaultScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = "dashboard" }
                    )
                }
            }
        }
    }
}
