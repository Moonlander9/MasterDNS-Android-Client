package com.masterdnsvpn.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.masterdnsvpn.android.MainUiState
import com.masterdnsvpn.android.MainViewModel
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.ui.screens.ConfigScreen
import com.masterdnsvpn.android.ui.screens.HomeScreen
import com.masterdnsvpn.android.ui.screens.LogsScreen
import com.masterdnsvpn.android.ui.screens.RoutingScreen
import com.masterdnsvpn.android.ui.screens.ScannerScreen

private sealed class AppDestination(
    val route: String,
    val titleRes: Int,
    val icon: @Composable () -> Unit,
) {
    data object Home : AppDestination(
        route = "home",
        titleRes = R.string.tab_home,
        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
    )

    data object Config : AppDestination(
        route = "config",
        titleRes = R.string.tab_config,
        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
    )

    data object Routing : AppDestination(
        route = "routing",
        titleRes = R.string.tab_routing,
        icon = { Icon(Icons.Outlined.Route, contentDescription = null) },
    )

    data object Scanner : AppDestination(
        route = "scanner",
        titleRes = R.string.tab_scanner,
        icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
    )

    data object Logs : AppDestination(
        route = "logs",
        titleRes = R.string.tab_logs,
        icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null) },
    )

    companion object {
        val topLevel = listOf(Home, Routing, Config, Scanner, Logs)

        fun fromRoute(route: String?): AppDestination {
            return topLevel.firstOrNull { it.route == route } ?: Home
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDnsVpnApp(
    state: MainUiState,
    viewModel: MainViewModel,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImportToml: () -> Unit,
    onExportToml: () -> Unit,
    onCopyProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onPickScannerCidr: () -> Unit,
    onExportLogs: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = AppDestination.fromRoute(navBackStackEntry?.destination?.route)

    BackHandler(enabled = currentDestination != AppDestination.Home) {
        navigateTopLevel(navController, AppDestination.Home)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = currentDestination.titleRes)) },
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            ) {
                AppDestination.topLevel.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_${destination.route}"),
                        selected = currentDestination.route == destination.route,
                        onClick = { navigateTopLevel(navController, destination) },
                        icon = destination.icon,
                        label = { Text(text = stringResource(id = destination.titleRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    state = state,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onImportToml = onImportToml,
                    onExportToml = onExportToml,
                    onCopyProfile = onCopyProfile,
                    onImportProfile = onImportProfile,
                    onExportProfileQr = viewModel::exportProfileString,
                    onOpenConfig = {
                        val target = if (state.validationErrors.any { it.contains("SPLIT_ALLOWLIST") }) {
                            AppDestination.Routing
                        } else {
                            AppDestination.Config
                        }
                        navigateTopLevel(navController, target)
                    },
                )
            }
            composable(AppDestination.Routing.route) {
                RoutingScreen(
                    state = state,
                    onVpnModeChanged = viewModel::updateVpnMode,
                    onPackageToggled = viewModel::setSplitAllowlistPackage,
                    onQuickAddPackage = viewModel::addSplitAllowlistPackage,
                )
            }
            composable(AppDestination.Config.route) {
                ConfigScreen(
                    state = state,
                    onEncryptionMethodChanged = viewModel::updateEncryptionMethod,
                    onEncryptionKeyChanged = viewModel::updateEncryptionKey,
                    onDomainsChanged = viewModel::updateDomainsCsv,
                    onResolversChanged = viewModel::updateResolversCsv,
                    onListenPortChanged = viewModel::updateListenPort,
                    onSocksAuthChanged = viewModel::updateSocksAuth,
                    onSocksUserChanged = viewModel::updateSocksUser,
                    onSocksPassChanged = viewModel::updateSocksPass,
                    onSocksHandshakeTimeoutChanged = viewModel::updateSocksHandshakeTimeout,
                    onBaseEncodeDataChanged = viewModel::updateBaseEncodeData,
                    onUploadCompressionTypeChanged = viewModel::updateUploadCompressionType,
                    onDownloadCompressionTypeChanged = viewModel::updateDownloadCompressionType,
                    onLogLevelChanged = viewModel::updateLogLevel,
                    onPacketDuplicationCountChanged = viewModel::updatePacketDuplicationCount,
                    onMaxPacketsPerBatchChanged = viewModel::updateMaxPacketsPerBatch,
                    onResolverBalancingStrategyChanged = viewModel::updateResolverBalancingStrategy,
                    onMinUploadMtuChanged = viewModel::updateMinUploadMtu,
                    onMinDownloadMtuChanged = viewModel::updateMinDownloadMtu,
                    onMaxUploadMtuChanged = viewModel::updateMaxUploadMtu,
                    onMaxDownloadMtuChanged = viewModel::updateMaxDownloadMtu,
                    onMtuTestRetriesChanged = viewModel::updateMtuTestRetries,
                    onMtuTestTimeoutChanged = viewModel::updateMtuTestTimeout,
                    onMaxConnectionAttemptsChanged = viewModel::updateMaxConnectionAttempts,
                    onArqWindowSizeChanged = viewModel::updateArqWindowSize,
                    onArqInitialRtoChanged = viewModel::updateArqInitialRto,
                    onArqMaxRtoChanged = viewModel::updateArqMaxRto,
                    onDnsQueryTimeoutChanged = viewModel::updateDnsQueryTimeout,
                    onNumRxWorkersChanged = viewModel::updateNumRxWorkers,
                    onNumDnsWorkersChanged = viewModel::updateNumDnsWorkers,
                    onSocketBufferSizeChanged = viewModel::updateSocketBufferSize,
                )
            }
            composable(AppDestination.Scanner.route) {
                ScannerScreen(
                    scannerConfig = state.scannerConfig,
                    scannerSession = state.scannerSession,
                    onScannerPickCidr = onPickScannerCidr,
                    onScannerDomainChanged = viewModel::updateScannerDomain,
                    onScannerRecordTypeChanged = viewModel::updateScannerRecordType,
                    onScannerRandomSubdomainChanged = viewModel::updateScannerRandomSubdomain,
                    onScannerPresetChanged = viewModel::updateScannerPreset,
                    onScannerConcurrencyChanged = viewModel::updateScannerConcurrency,
                    onScannerProxyEnabledChanged = viewModel::updateScannerProxyEnabled,
                    onScannerSlipstreamPathChanged = viewModel::updateScannerSlipstreamPath,
                    onScannerStart = viewModel::startScannerScan,
                    onScannerPause = viewModel::pauseScannerScan,
                    onScannerResume = viewModel::resumeScannerScan,
                    onScannerShuffle = viewModel::shuffleScannerScan,
                    onScannerStop = viewModel::stopScannerScan,
                    onScannerSave = viewModel::saveScannerResults,
                    onScannerApplyDns = viewModel::applyScannerDnsToTunnel,
                )
            }
            composable(AppDestination.Logs.route) {
                LogsScreen(
                    logs = state.logs,
                    onClearLogs = viewModel::clearLogs,
                    onExportLogs = onExportLogs,
                )
            }
        }
    }
}

private fun navigateTopLevel(
    navController: androidx.navigation.NavHostController,
    destination: AppDestination,
) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
