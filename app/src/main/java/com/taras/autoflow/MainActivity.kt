package com.taras.autoflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.taras.autoflow.ui.AutoFlowDashboardScreen
import com.taras.autoflow.ui.AutoFlowViewModel
import com.taras.autoflow.ui.OutputVaultScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AutoFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
