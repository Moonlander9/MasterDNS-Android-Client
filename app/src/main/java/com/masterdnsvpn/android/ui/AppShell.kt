package com.masterdnsvpn.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.masterdnsvpn.android.ui.screens.ProfileQrDialog
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
    onFetchRemoteProfile: () -> Unit,
    onExportLogs: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = AppDestination.fromRoute(navBackStackEntry?.destination?.route)
    val remoteProfileConfigured = state.scannerConfig.remoteProfileServer.isNotBlank() &&
        state.scannerConfig.remoteProfileName.isNotBlank()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var showRemoteProfileDialog by rememberSaveable { mutableStateOf(false) }
    var qrProfilePayload by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentDestination != AppDestination.Home) {
        navigateTopLevel(navController, AppDestination.Home)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactAppBar(
                title = stringResource(id = currentDestination.titleRes),
                menuExpanded = menuExpanded,
                onOpenMenu = { menuExpanded = true },
                onDismissMenu = { menuExpanded = false },
                onOpenRemoteProfileSetup = {
                    menuExpanded = false
                    showRemoteProfileDialog = true
                },
                onImportToml = {
                    menuExpanded = false
                    onImportToml()
                },
                onExportToml = {
                    menuExpanded = false
                    onExportToml()
                },
                onCopyProfile = {
                    menuExpanded = false
                    onCopyProfile()
                },
                onImportProfile = {
                    menuExpanded = false
                    onImportProfile()
                },
                onShowProfileQr = {
                    menuExpanded = false
                    qrProfilePayload = viewModel.exportProfileString()
                },
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
                    remoteProfileConfigured = remoteProfileConfigured,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onRemoteProfileAction = {
                        if (remoteProfileConfigured) {
                            onFetchRemoteProfile()
                        } else {
                            showRemoteProfileDialog = true
                        }
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

    if (showRemoteProfileDialog) {
        RemoteProfileSettingsDialog(
            server = state.scannerConfig.remoteProfileServer,
            profileName = state.scannerConfig.remoteProfileName,
            onDismiss = { showRemoteProfileDialog = false },
            onSave = { server, profileName ->
                viewModel.updateScannerRemoteProfileServer(server)
                viewModel.updateScannerRemoteProfileName(profileName)
                showRemoteProfileDialog = false
            },
        )
    }

    qrProfilePayload?.let { profile ->
        ProfileQrDialog(
            profile = profile,
            onDismiss = { qrProfilePayload = null },
        )
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

@Composable
private fun CompactAppBar(
    title: String,
    menuExpanded: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpenRemoteProfileSetup: () -> Unit,
    onImportToml: () -> Unit,
    onExportToml: () -> Unit,
    onCopyProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onShowProfileQr: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = onOpenMenu) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.menu_more_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onDismissMenu,
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.menu_remote_profile_setup)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = null,
                            )
                        },
                        onClick = onOpenRemoteProfileSetup,
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_import_toml)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = null,
                            )
                        },
                        onClick = onImportToml,
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_export_toml)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                            )
                        },
                        onClick = onExportToml,
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_copy_profile)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                            )
                        },
                        onClick = onCopyProfile,
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_import_profile)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = null,
                            )
                        },
                        onClick = onImportProfile,
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_show_profile_qr)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Dns,
                                contentDescription = null,
                            )
                        },
                        onClick = onShowProfileQr,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteProfileSettingsDialog(
    server: String,
    profileName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var draftServer by rememberSaveable(server) { mutableStateOf(server) }
    var draftProfileName by rememberSaveable(profileName) { mutableStateOf(profileName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.remote_profile_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.remote_profile_dialog_subtitle),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draftServer,
                    onValueChange = { draftServer = it },
                    label = { Text(text = stringResource(R.string.scanner_remote_profile_server_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draftProfileName,
                    onValueChange = { draftProfileName = it },
                    label = { Text(text = stringResource(R.string.scanner_remote_profile_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draftServer.trim(), draftProfileName.trim()) }) {
                Text(text = stringResource(R.string.action_save))
            }
        },
    )
}
