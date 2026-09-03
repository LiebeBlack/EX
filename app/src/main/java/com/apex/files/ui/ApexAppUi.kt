package com.apex.files.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apex.files.Navigator
import com.apex.files.Screen
import com.apex.files.core.AppContainer
import com.apex.files.ui.components.OperationCenter
import com.apex.files.ui.components.OperationCenterViewModel
import com.apex.files.ui.screens.home.HomeScreen
import com.apex.files.ui.screens.permissions.PermissionScreen
import com.apex.files.ui.screens.permissions.Permissions
import com.apex.files.ui.theme.ApexBlack
import com.apex.files.ui.theme.ApexTheme

/** Root composable: theme, DI locals, permission gate, navigation, overlays. */
@Composable
fun ApexAppUi(container: AppContainer) {
    val settings = container.settings
    val accent by settings.accent.collectAsStateWithLifecycle()
    val operationCenter: OperationCenterViewModel = viewModel()

    ApexTheme(accent = accent) {
        CompositionLocalProvider(
            LocalContainer provides container,
            LocalOperationCenter provides operationCenter,
        ) {
            val navigator = remember { Navigator() }
            CompositionLocalProvider(LocalNavigator provides navigator) {
                var permissionsReady by remember { mutableStateOf(Permissions.isGranted(container.appContext)) }

                // Re-check permissions when returning from system settings.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionsReady = Permissions.isGranted(container.appContext)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Box(Modifier.fillMaxSize().background(ApexBlack)) {
                    if (permissionsReady) {
                        NavHost(navigator)
                    } else {
                        PermissionScreen(
                            onGranted = { permissionsReady = true },
                            onSafFallback = { uri ->
                                permissionsReady = true
                                navigator.replace(
                                    Screen.Explorer(
                                        com.apex.files.data.model.Location.Saf(uri, "SAF")
                                    )
                                )
                            },
                        )
                    }
                    OperationCenter(
                        operationCenter,
                        Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/** Renders the current screen of the back stack. */
@Composable
fun NavHost(navigator: Navigator) {
    val current = navigator.current
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }
    when (val s = current) {
        is Screen.Home -> HomeScreen()
        is Screen.Explorer -> com.apex.files.ui.screens.explorer.ExplorerScreen(s.location)
        is Screen.Search -> com.apex.files.ui.screens.search.SearchScreen()
        is Screen.Category -> com.apex.files.ui.screens.category.CategoryScreen(s.category)
        is Screen.Drives -> com.apex.files.ui.screens.drives.DrivesScreen()
        is Screen.Settings -> com.apex.files.ui.screens.settings.SettingsScreen()
        is Screen.Cleaner -> com.apex.files.ui.screens.tools.CleanerScreen()
        is Screen.Duplicates -> com.apex.files.ui.screens.tools.DuplicatesScreen()
        is Screen.Apk -> com.apex.files.ui.screens.tools.ApkScreen()
        is Screen.Stats -> com.apex.files.ui.screens.stats.StatsScreen()
        is Screen.SpaceAnalyzer -> com.apex.files.ui.screens.space.SpaceAnalyzerScreen(s.location)
        is Screen.Benchmark -> com.apex.files.ui.screens.benchmark.BenchmarkScreen()
        is Screen.ImageViewer -> com.apex.files.ui.screens.viewer.ImageViewerScreen(s.nodes, s.index)
        is Screen.TextViewer -> com.apex.files.ui.screens.viewer.TextViewerScreen(s.node)
        is Screen.PdfViewer -> com.apex.files.ui.screens.viewer.PdfViewerScreen(s.node)
        is Screen.ArchiveViewer -> com.apex.files.ui.screens.viewer.ArchiveViewerScreen(s.node)
    }
}