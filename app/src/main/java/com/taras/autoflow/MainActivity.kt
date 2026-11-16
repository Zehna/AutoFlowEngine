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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taras.autoflow.engine.WorkflowDefinition
import com.taras.autoflow.ui.*
import kotlinx.coroutines.launch

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
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val workflows by viewModel.workflows.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                BackHandler(enabled = drawerState.isOpen || currentScreen !is AppScreen.Dashboard) {
                    if (drawerState.isOpen) {
                        scope.launch { drawerState.close() }
                    } else {
                        currentScreen = AppScreen.Dashboard
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen !is AppScreen.WebLogin,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "AutoFlow Engine v2",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                label = { Text("Dashboard") },
                                selected = currentScreen is AppScreen.Dashboard,
                                onClick = {
                                    currentScreen = AppScreen.Dashboard
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Language, contentDescription = null) },
                                label = { Text("Session WebView Login") },
                                selected = currentScreen is AppScreen.WebLogin,
                                onClick = {
                                    currentScreen = AppScreen.WebLogin
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                label = { Text("Workflow Editor") },
                                selected = currentScreen is AppScreen.Editor,
                                onClick = {
                                    val target = workflows.firstOrNull()?.let {
                                        runCatching { it.toDefinition() }.getOrNull()
                                    } ?: viewModel.createNewWorkflowTemplate()
                                    currentScreen = AppScreen.Editor(target)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                label = { Text("Output Vault") },
                                selected = currentScreen is AppScreen.Vault,
                                onClick = {
                                    currentScreen = AppScreen.Vault
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Key, contentDescription = null) },
                                label = { Text("KV Store & Secrets") },
                                selected = currentScreen is AppScreen.KvStore,
                                onClick = {
                                    currentScreen = AppScreen.KvStore
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                ) {
                    when (val screen = currentScreen) {
                        AppScreen.Dashboard -> AutoFlowDashboardScreen(
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            onOpenMenu = { scope.launch { drawerState.open() } },
                            onCreateWorkflow = {
                                currentScreen = AppScreen.Editor(viewModel.createNewWorkflowTemplate())
                            },
                            onEditWorkflow = { wf ->
                                currentScreen = AppScreen.Editor(wf)
                            },
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
}
