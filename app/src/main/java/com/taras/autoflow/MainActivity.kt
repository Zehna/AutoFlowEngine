package com.taras.autoflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.ui.*

private sealed interface AppScreen {
    data object Dashboard : AppScreen
    data object Vault : AppScreen
    data object WebLogin : AppScreen
    data object KvStore : AppScreen
    data class Editor(val workflow: WorkflowDefinition) : AppScreen
}

class MainActivity : ComponentActivity() {
    private val viewModel: AutoFlowViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val importBundleLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.importWorkflowFromUri(uri)
            }
        }

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
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Dashboard) }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                if (currentScreen !is AppScreen.Dashboard) {
                    BackHandler {
                        currentScreen = AppScreen.Dashboard
                    }
                }

                when (val screen = currentScreen) {
                    AppScreen.Dashboard -> AutoFlowDashboardScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onCreateWorkflow = {
                            currentScreen = AppScreen.Editor(viewModel.createNewWorkflowTemplate())
                        },
                        onEditWorkflow = { wf ->
                            currentScreen = AppScreen.Editor(wf)
                        },
                        onNavigateVault = { currentScreen = AppScreen.Vault },
                        onNavigateWebLogin = { currentScreen = AppScreen.WebLogin },
                        onNavigateKvStore = { currentScreen = AppScreen.KvStore },
                        onImportClick = {
                            importBundleLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                        }
                    )
                    AppScreen.Vault -> OutputVaultScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = AppScreen.Dashboard }
                    )
                    AppScreen.WebLogin -> WebViewLoginScreen(
                        onBack = { currentScreen = AppScreen.Dashboard },
                        onCookieCaptured = { url, cookies ->
                            viewModel.saveCookieForUrl(url, cookies)
                            currentScreen = AppScreen.Dashboard
                        }
                    )
                    AppScreen.KvStore -> KeyValueStoreScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = AppScreen.Dashboard }
                    )
                    is AppScreen.Editor -> WorkflowEditorScreen(
                        workflow = screen.workflow,
                        onBack = { currentScreen = AppScreen.Dashboard },
                        onSaveWorkflow = { updated ->
                            viewModel.saveWorkflow(updated)
                        },
                        onRunWorkflow = { updated ->
                            viewModel.startWorkflow(updated)
                            currentScreen = AppScreen.Dashboard
                        }
                    )
                }
            }
        }
    }
}
