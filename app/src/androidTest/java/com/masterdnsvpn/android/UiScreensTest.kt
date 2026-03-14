package com.masterdnsvpn.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.masterdnsvpn.android.scanner.ScanStatus
import com.masterdnsvpn.android.scanner.ScannerEntry
import com.masterdnsvpn.android.scanner.ScannerSessionState
import com.masterdnsvpn.android.ui.screens.CONFIG_ADVANCED_TOGGLE_TAG
import com.masterdnsvpn.android.ui.screens.CONFIG_SEARCH_TAG
import com.masterdnsvpn.android.ui.screens.ConfigScreen
import com.masterdnsvpn.android.ui.screens.HOME_PRIMARY_ACTION_TAG
import com.masterdnsvpn.android.ui.screens.HomeScreen
import com.masterdnsvpn.android.ui.screens.LOGS_MODE_TAG
import com.masterdnsvpn.android.ui.screens.LogsScreen
import com.masterdnsvpn.android.ui.screens.ROUTING_APP_SEARCH_TAG
import com.masterdnsvpn.android.ui.screens.ROUTING_MODE_FULL_TAG
import com.masterdnsvpn.android.ui.screens.RoutingScreen
import com.masterdnsvpn.android.ui.screens.SCANNER_BEST_APPLY_TAG
import com.masterdnsvpn.android.ui.screens.SCANNER_START_TAG
import com.masterdnsvpn.android.ui.screens.ScannerScreen
import com.masterdnsvpn.android.ui.theme.MasterDnsVPNAndroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UiScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreenShowsConnectWhenIdle() {
        composeRule.setThemedContent {
            HomeScreen(
                state = MainUiState(status = TunnelStatus.IDLE, statusMessage = "Idle"),
                onConnect = {},
                onDisconnect = {},
                onImportToml = {},
                onExportToml = {},
                onCopyProfile = {},
                onImportProfile = {},
                onExportProfileQr = { "" },
                onOpenConfig = {},
                onOpenRouting = {},
                onOpenScanner = {},
                onOpenLogs = {},
            )
        }

        composeRule.onNodeWithTag(HOME_PRIMARY_ACTION_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun homeScreenShowsDisconnectWhenConnected() {
        composeRule.setThemedContent {
            HomeScreen(
                state = MainUiState(status = TunnelStatus.CONNECTED, statusMessage = "Connected"),
                onConnect = {},
                onDisconnect = {},
                onImportToml = {},
                onExportToml = {},
                onCopyProfile = {},
                onImportProfile = {},
                onExportProfileQr = { "" },
                onOpenConfig = {},
                onOpenRouting = {},
                onOpenScanner = {},
                onOpenLogs = {},
            )
        }

        composeRule.onNodeWithText("Disconnect").assertIsDisplayed()
    }

    @Test
    fun configScreenSupportsSearchAndAdvancedToggle() {
        composeRule.setThemedContent {
            ConfigScreen(
                state = MainUiState(),
                onEncryptionMethodChanged = {},
                onEncryptionKeyChanged = {},
                onDomainsChanged = {},
                onResolversChanged = {},
                onListenPortChanged = {},
                onSocksAuthChanged = {},
                onSocksUserChanged = {},
                onSocksPassChanged = {},
                onSocksHandshakeTimeoutChanged = {},
                onBaseEncodeDataChanged = {},
                onUploadCompressionTypeChanged = {},
                onDownloadCompressionTypeChanged = {},
                onLogLevelChanged = {},
                onPacketDuplicationCountChanged = {},
                onMaxPacketsPerBatchChanged = {},
                onResolverBalancingStrategyChanged = {},
                onMinUploadMtuChanged = {},
                onMinDownloadMtuChanged = {},
                onMaxUploadMtuChanged = {},
                onMaxDownloadMtuChanged = {},
                onMtuTestRetriesChanged = {},
                onMtuTestTimeoutChanged = {},
                onMaxConnectionAttemptsChanged = {},
                onArqWindowSizeChanged = {},
                onArqInitialRtoChanged = {},
                onArqMaxRtoChanged = {},
                onDnsQueryTimeoutChanged = {},
                onNumRxWorkersChanged = {},
                onNumDnsWorkersChanged = {},
                onSocketBufferSizeChanged = {},
            )
        }

        composeRule.onNodeWithTag(CONFIG_ADVANCED_TOGGLE_TAG).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Hide Advanced Settings").assertIsDisplayed()

        composeRule.onNodeWithTag(CONFIG_SEARCH_TAG).performTextInput("socket")
        composeRule.onNodeWithText("SOCKET_BUFFER_SIZE").assertIsDisplayed()
    }

    @Test
    fun scannerScreenUpdatesControlsByStatusAndShowsApply() {
        val scannerState = ScannerSessionState(
            status = ScanStatus.RUNNING,
            entries = listOf(ScannerEntry(dns = "1.1.1.1", pingMs = 19)),
        )

        composeRule.setThemedContent {
            ScannerScreen(
                scannerConfig = scannerState.config,
                scannerSession = scannerState,
                onScannerPickCidr = {},
                onScannerDomainChanged = {},
                onScannerRecordTypeChanged = {},
                onScannerRandomSubdomainChanged = {},
                onScannerPresetChanged = {},
                onScannerConcurrencyChanged = {},
                onScannerProxyEnabledChanged = {},
                onScannerSlipstreamPathChanged = {},
                onScannerRemoteServerChanged = {},
                onScannerRemoteNameChanged = {},
                onScannerFetchRemoteProfile = {},
                onScannerStart = {},
                onScannerPause = {},
                onScannerResume = {},
                onScannerShuffle = {},
                onScannerStop = {},
                onScannerSave = {},
                onScannerApplyDns = {},
            )
        }

        composeRule.onNodeWithTag(SCANNER_START_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("Pause").assertIsEnabled()
        composeRule.onNodeWithTag(SCANNER_BEST_APPLY_TAG).assertIsDisplayed()
    }

    @Test
    fun logsScreenSupportsTailAndFullModesAndClear() {
        var clearClicked = false
        val logs = (1..250).map { i ->
            LogLine(
                timestamp = "2026-03-09T00:00:00Z",
                level = "INFO",
                message = "message-${i.toString().padStart(3, '0')}",
                type = "log",
            )
        }

        composeRule.setThemedContent {
            LogsScreen(
                logs = logs,
                onClearLogs = { clearClicked = true },
                onExportLogs = {},
            )
        }

        composeRule.onNodeWithText("message-250", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("message-001", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag(LOGS_MODE_TAG).performClick()
        composeRule.onNodeWithText("message-001", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Clear").performClick()
        composeRule.waitForIdle()
        assertTrue(clearClicked)
    }

    @Test
    fun routingScreenSupportsModeSwitchAndAppSearch() {
        var selectedMode = VpnMode.SPLIT_ALLOWLIST
        val state = MainUiState(
            config = ClientConfig(
                vpnMode = VpnMode.SPLIT_ALLOWLIST,
                splitAllowlistPackages = listOf("com.whatsapp"),
            ),
            installedApps = listOf(
                InstalledAppInfo(packageName = "com.whatsapp", label = "WhatsApp"),
                InstalledAppInfo(packageName = "com.instagram.android", label = "Instagram"),
            ),
        )

        composeRule.setThemedContent {
            RoutingScreen(
                state = state,
                onVpnModeChanged = { selectedMode = it },
                onPackageToggled = { _, _ -> },
                onQuickAddPackage = {},
            )
        }

        composeRule.onNodeWithTag(ROUTING_APP_SEARCH_TAG).assertIsDisplayed().performTextInput("insta")
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithTag(ROUTING_MODE_FULL_TAG).performClick()
        composeRule.waitForIdle()
        assertTrue(selectedMode == VpnMode.FULL)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setThemedContent(
    content: @Composable () -> Unit,
) {
    setContent {
        MasterDnsVPNAndroidTheme {
            content()
        }
    }
}
