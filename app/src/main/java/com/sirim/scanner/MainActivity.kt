package com.sirim.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.sirim.scanner.data.AppContainer
import com.sirim.scanner.ui.screens.export.ExportScreen
import com.sirim.scanner.ui.screens.export.ExportViewModel
import com.sirim.scanner.ui.common.AuthenticationDialog
import com.sirim.scanner.ui.model.ScanDraft
import com.sirim.scanner.ui.model.ScanIssueReport
import com.sirim.scanner.ui.screens.feedback.FeedbackScreen
import com.sirim.scanner.ui.screens.records.RecordFormScreen
import com.sirim.scanner.ui.screens.records.RecordListScreen
import com.sirim.scanner.ui.screens.records.RecordViewModel
import com.sirim.scanner.ui.screens.scanner.ScannerScreen
import com.sirim.scanner.ui.screens.scanner.ScannerViewModel
import com.sirim.scanner.ui.screens.settings.SettingsScreen
import com.sirim.scanner.ui.screens.sku.SkuScannerScreen
import com.sirim.scanner.ui.screens.startup.StartupScreen
import com.sirim.scanner.ui.theme.SirimScannerTheme
import com.sirim.scanner.ui.viewmodel.PreferencesViewModel
import com.sirim.scanner.data.preferences.StartupPage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy {
        (application as SirimScannerApplication).container
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SirimScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SirimApp(container = container)
                }
            }
        }
    }
}

sealed class Destinations(val route: String) {
    data object StartupResolver : Destinations("startup_resolver")
    data object Startup : Destinations("startup")
    data object SirimScanner : Destinations("sirim_scanner")
    data object SkuScanner : Destinations("sku_scanner")
    data object Storage : Destinations("storage")
    data object RecordForm : Destinations("record_form")
    data object Export : Destinations("export")
    data object Settings : Destinations("settings")
    data object Feedback : Destinations("feedback")
}

@Composable
fun SirimApp(container: AppContainer) {
    val navController = rememberNavController()
    NavGraph(container = container, navController = navController)
}

@Composable
private fun NavGraph(container: AppContainer, navController: NavHostController) {
    val preferencesViewModel: PreferencesViewModel = viewModel(
        factory = PreferencesViewModel.Factory(container.preferencesManager)
    )
    val preferences by preferencesViewModel.preferences.collectAsState()
    val isSessionValid by preferencesViewModel.isSessionValid.collectAsState()
    val authError by preferencesViewModel.authError.collectAsState()
    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestAuthentication(afterAuth: () -> Unit) {
        if (isSessionValid) {
            afterAuth()
        } else {
            pendingAction = afterAuth
            showAuthDialog = true
        }
    }

    LaunchedEffect(isSessionValid) {
        if (isSessionValid && pendingAction != null) {
            pendingAction?.invoke()
            pendingAction = null
            showAuthDialog = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            preferencesViewModel.checkSessionExpiry()
        }
    }
    NavHost(navController = navController, startDestination = Destinations.StartupResolver.route) {
        composable(Destinations.StartupResolver.route) {
            LaunchedEffect(preferences.startupPage) {
                val target = when (preferences.startupPage) {
                    StartupPage.AskEveryTime -> Destinations.Startup.route
                    StartupPage.SirimScanner -> Destinations.SirimScanner.route
                    StartupPage.SkuScanner -> Destinations.SkuScanner.route
                    StartupPage.Storage -> Destinations.Storage.route
                }
                navController.navigate(target) {
                    popUpTo(Destinations.StartupResolver.route) { inclusive = true }
                }
            }
        }
        composable(Destinations.Startup.route) {
            StartupScreen(
                onOpenSirimScanner = { navController.navigate(Destinations.SirimScanner.route) },
                onOpenSkuScanner = { navController.navigate(Destinations.SkuScanner.route) },
                onOpenStorage = { navController.navigate(Destinations.Storage.route) },
                onOpenSettings = { navController.navigate(Destinations.Settings.route) }
            )
        }
        composable(Destinations.SirimScanner.route) {
            val viewModel: ScannerViewModel = viewModel(
                factory = ScannerViewModel.Factory(
                    repository = container.repository,
                    analyzer = container.labelAnalyzer,
                    appScope = container.applicationScope
                )
            )
            ScannerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRecordSaved = { draft ->
                    navController.navigate("${Destinations.RecordForm.route}?recordId=${draft.recordId}") {
                        popUpTo(Destinations.SirimScanner.route) { inclusive = true }
                    }
                    navController.getBackStackEntry(Destinations.RecordForm.route).savedStateHandle["scan_draft"] = draft
                }
            )
        }
        composable(Destinations.SkuScanner.route) {
            SkuScannerScreen(
                onBack = { navController.popBackStack() },
                onRecordSaved = {},
                repository = container.repository,
                analyzer = container.barcodeAnalyzer,
                appScope = container.applicationScope
            )
        }
        composable(Destinations.Storage.route) {
            val viewModel: RecordViewModel = viewModel(
                factory = RecordViewModel.Factory(container.repository)
            )
            RecordListScreen(
                viewModel = viewModel,
                onAdd = { navController.navigate(Destinations.RecordForm.route) },
                onEdit = { record ->
                    navController.navigate("${Destinations.RecordForm.route}?recordId=${record.id}")
                },
                onBack = { navController.popBackStack() },
                onNavigateToExport = { navController.navigate(Destinations.Export.route) },
                isAuthenticated = isSessionValid,
                onRequireAuthentication = { action -> requestAuthentication(action) }
            )
        }
        composable(
            route = Destinations.RecordForm.route + "?recordId={recordId}",
            arguments = listOf(
                navArgument("recordId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val viewModel: RecordViewModel = viewModel(
                factory = RecordViewModel.Factory(container.repository)
            )
            val scanDraft = navController.currentBackStackEntry?.savedStateHandle?.remove<ScanDraft>("scan_draft")
            RecordFormScreen(
                viewModel = viewModel,
                onSaved = { _: Long -> navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onRetake = if (scanDraft != null) {
                    {
                        navController.navigate(Destinations.SirimScanner.route) {
                            popUpTo(Destinations.RecordForm.route) { inclusive = true }
                        }
                    }
                } else null,
                onReportIssue = { report ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("feedback_prefill", report)
                    navController.navigate(Destinations.Feedback.route)
                },
                recordId = backStackEntry.arguments?.getLong("recordId")?.takeIf { it > 0 },
                scanDraft = scanDraft
            )
        }
        composable(Destinations.Export.route) {
            val viewModel: ExportViewModel = viewModel(
                factory = ExportViewModel.Factory(container.repository, container.exportManager)
            )
            ExportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.Settings.route) {
            SettingsScreen(
                preferences = preferences,
                authError = authError,
                onStartupSelected = preferencesViewModel::setStartupPage,
                onAuthenticate = { username, password ->
                    preferencesViewModel.authenticate(username, password)
                },
                onLogout = preferencesViewModel::logout,
                onDismissAuthError = preferencesViewModel::clearAuthError,
                onBack = { navController.popBackStack() },
                onOpenFeedback = { navController.navigate(Destinations.Feedback.route) }
            )
        }
        composable(Destinations.Feedback.route) {
            val prefill = navController.previousBackStackEntry?.savedStateHandle?.remove<ScanIssueReport>("feedback_prefill")
            FeedbackScreen(
                onBack = { navController.popBackStack() },
                prefill = prefill
            )
        }
    }

    AuthenticationDialog(
        visible = showAuthDialog,
        error = authError,
        onDismiss = {
            showAuthDialog = false
            pendingAction = null
            preferencesViewModel.clearAuthError()
        },
        onAuthenticate = { username, password ->
            preferencesViewModel.authenticate(username, password)
        }
    )
}
