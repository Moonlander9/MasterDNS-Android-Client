package com.masterdnsvpn.android

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.masterdnsvpn.android.ui.MasterDnsVpnApp
import com.masterdnsvpn.android.ui.theme.MasterDnsVPNAndroidTheme

class MainActivity : ComponentActivity() {
    companion object {
        private val configImportMimeTypes = arrayOf(
            "application/toml",
            "text/plain",
            "text/x-toml",
            "text/*",
            "application/octet-stream",
            "*/*",
        )
    }

    private val viewModel by viewModels<MainViewModel>()
    private var pendingVpnConfigPath: String? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.onSuccess { content ->
            if (content != null) {
                viewModel.importToml(content)
                toast(getString(R.string.toast_config_imported))
            }
        }.onFailure {
            toast(getString(R.string.toast_config_import_failed, it.message ?: "unknown"))
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/toml")) { uri ->
        if (uri == null) return@registerForActivityResult
        val output = viewModel.exportToml()
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(output) }
        }.onSuccess {
            toast(getString(R.string.toast_config_exported))
        }.onFailure {
            toast(getString(R.string.toast_config_export_failed, it.message ?: "unknown"))
        }
    }

    private val diagnosticsExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            val output = viewModel.exportDiagnostics(this).getOrElse {
                toast(getString(R.string.toast_logs_export_failed, it.message ?: "unknown"))
                return@registerForActivityResult
            }

            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(output) }
            }.onSuccess {
                toast(getString(R.string.toast_logs_exported))
            }.onFailure {
                toast(getString(R.string.toast_logs_export_failed, it.message ?: "unknown"))
            }
        }

    private val scannerCidrLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val label = resolveDisplayName(uri) ?: uri.lastPathSegment ?: getString(R.string.scanner_cidr_default_label)
        viewModel.updateScannerCidr(uri.toString(), label)
        toast(getString(R.string.toast_scanner_cidr_selected, label))
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val configPath = pendingVpnConfigPath ?: return@registerForActivityResult
            pendingVpnConfigPath = null
            if (result.resultCode == RESULT_OK) {
                startServiceTunnelWithConfig(configPath)
            } else {
                viewModel.onTunnelEvent(
                    TunnelEvent(
                        type = "status",
                        status = TunnelStatus.ERROR,
                        level = "ERROR",
                        message = getString(R.string.error_vpn_permission_denied),
                        code = "VPN_PERMISSION_DENIED",
                    ),
                )
                toast(getString(R.string.error_vpn_permission_denied))
            }
        }

    private val tunnelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MasterDnsVpnService.BROADCAST_TUNNEL_EVENT) return
            val event = TunnelEvent(
                type = intent.getStringExtra(MasterDnsVpnService.EXTRA_EVENT_TYPE) ?: "log",
                status = intent.getStringExtra(MasterDnsVpnService.EXTRA_EVENT_STATUS)
                    ?.let { runCatching { TunnelStatus.valueOf(it) }.getOrNull() },
                level = intent.getStringExtra(MasterDnsVpnService.EXTRA_EVENT_LEVEL) ?: "INFO",
                message = intent.getStringExtra(MasterDnsVpnService.EXTRA_EVENT_MESSAGE) ?: "",
                code = intent.getStringExtra(MasterDnsVpnService.EXTRA_EVENT_CODE),
                timestamp = intent.getStringExtra(MasterDnsVpnService.EXTRA_EVENT_TIMESTAMP) ?: "",
            )
            viewModel.onTunnelEvent(event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            MasterDnsVPNAndroidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MasterDnsVpnApp(
                        state = uiState,
                        viewModel = viewModel,
                        onConnect = ::startServiceTunnel,
                        onDisconnect = ::stopServiceTunnel,
                        onImportToml = { importLauncher.launch(configImportMimeTypes) },
                        onExportToml = { exportLauncher.launch("client_config.toml") },
                        onCopyProfile = ::copyProfileToClipboard,
                        onImportProfile = ::importProfileFromClipboard,
                        onPickScannerCidr = { scannerCidrLauncher.launch(arrayOf("text/*")) },
                        onExportLogs = { diagnosticsExportLauncher.launch("masterdnsvpn-diagnostics.log") },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MasterDnsVpnService.BROADCAST_TUNNEL_EVENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tunnelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(tunnelReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(tunnelReceiver) }
        super.onStop()
    }

    private fun startServiceTunnel() {
        val configPath = viewModel.persistConfig(this).getOrElse {
            val errorMessage = it.message ?: getString(R.string.error_invalid_config)
            toast(errorMessage)
            viewModel.onTunnelEvent(
                TunnelEvent(
                    type = "status",
                    status = TunnelStatus.ERROR,
                    level = "ERROR",
                    message = errorMessage,
                    code = "CONFIG_VALIDATION",
                ),
            )
            return
        }

        val vpnPrepareIntent = VpnService.prepare(this)
        if (vpnPrepareIntent != null) {
            pendingVpnConfigPath = configPath
            vpnPermissionLauncher.launch(vpnPrepareIntent)
            return
        }

        viewModel.onTunnelEvent(
            TunnelEvent(
                type = "status",
                status = TunnelStatus.STARTING,
                level = "INFO",
                message = getString(R.string.status_vpn_permission_already_granted),
                code = "VPN_PERMISSION_ALREADY_GRANTED",
            ),
        )
        startServiceTunnelWithConfig(configPath)
    }

    private fun startServiceTunnelWithConfig(configPath: String) {
        val intent = Intent(this, MasterDnsVpnService::class.java).apply {
            action = MasterDnsVpnService.ACTION_START
            putExtra(MasterDnsVpnService.EXTRA_CONFIG_PATH, configPath)
            putExtra(MasterDnsVpnService.EXTRA_AUTO_RECONNECT, true)
        }
        runCatching {
            startForegroundService(intent)
        }.onFailure { error ->
            val message = "Failed to start VPN service: ${error.message ?: "unknown"}"
            viewModel.onTunnelEvent(
                TunnelEvent(
                    type = "status",
                    status = TunnelStatus.ERROR,
                    level = "ERROR",
                    message = message,
                    code = "FGS_START_EXCEPTION",
                ),
            )
            toast(message)
        }
    }

    private fun stopServiceTunnel() {
        val intent = Intent(this, MasterDnsVpnService::class.java).apply {
            action = MasterDnsVpnService.ACTION_STOP
        }
        runCatching {
            startService(intent)
        }.onFailure { error ->
            val message = "Failed to stop VPN service: ${error.message ?: "unknown"}"
            viewModel.onTunnelEvent(
                TunnelEvent(
                    type = "status",
                    status = TunnelStatus.ERROR,
                    level = "ERROR",
                    message = message,
                    code = "SERVICE_STOP_EXCEPTION",
                ),
            )
            toast(message)
        }
    }

    private fun copyProfileToClipboard() {
        val profile = viewModel.exportProfileString()
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MasterDnsVPN Profile", profile)
        manager.setPrimaryClip(clip)
        toast(getString(R.string.toast_profile_copied))
    }

    private fun importProfileFromClipboard() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = manager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            toast(getString(R.string.toast_clipboard_empty))
            return
        }

        viewModel.importProfileString(text).onSuccess {
            toast(getString(R.string.toast_profile_imported))
        }.onFailure {
            toast(getString(R.string.toast_profile_invalid))
            viewModel.onTunnelEvent(
                TunnelEvent(
                    type = "status",
                    status = TunnelStatus.ERROR,
                    level = "ERROR",
                    message = getString(R.string.error_profile_import_failed, it.message ?: "unknown"),
                    code = "PROFILE_IMPORT_ERROR",
                ),
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
