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
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.masterdnsvpn.android.ui.MasterDnsVpnApp
import com.masterdnsvpn.android.ui.theme.MasterDnsVPNAndroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    private var pendingRestartConfigPath: String? = null

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

            if (event.type == "status" && event.status == TunnelStatus.IDLE) {
                val configPath = pendingRestartConfigPath ?: return
                pendingRestartConfigPath = null
                startServiceTunnelWithConfig(configPath)
            }
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
                        onFetchScannerRemoteProfile = ::fetchScannerRemoteProfile,
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
        requestServiceTunnelStop()
    }

    private fun requestServiceTunnelStop(): Boolean {
        val intent = Intent(this, MasterDnsVpnService::class.java).apply {
            action = MasterDnsVpnService.ACTION_STOP
        }
        return runCatching {
            startService(intent)
            true
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
        }.getOrDefault(false)
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

    private fun fetchScannerRemoteProfile() {
        val scannerConfig = viewModel.uiState.value.scannerConfig
        if (scannerConfig.remoteProfileServer.isBlank() || scannerConfig.remoteProfileName.isBlank()) {
            return
        }

        lifecycleScope.launch {
            val toml = withContext(Dispatchers.IO) {
                downloadRemoteProfile(
                    server = scannerConfig.remoteProfileServer,
                    profileName = scannerConfig.remoteProfileName,
                )
            } ?: return@launch

            viewModel.importTomlIfValid(toml).getOrNull() ?: return@launch
            val configPath = viewModel.persistConfig(this@MainActivity).getOrNull() ?: return@launch

            toast(getString(R.string.toast_remote_profile_applied))

            if (viewModel.uiState.value.status.requiresTunnelRestart()) {
                if (requestServiceTunnelStop()) {
                    pendingRestartConfigPath = configPath
                }
            }
        }
    }

    private fun downloadRemoteProfile(server: String, profileName: String): String? {
        val normalizedServer = server.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        if (normalizedServer.isBlank()) {
            return null
        }

        val encodedProfileName = Uri.encode(profileName.trim())
        val requestPath = "/$encodedProfileName"
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()
        val signature = buildRemoteProfileSignature(
            method = "GET",
            path = requestPath,
            timestamp = timestamp,
            nonce = nonce,
            secret = BuildConfig.REMOTE_PROFILE_SHARED_SECRET,
        ) ?: return null

        val url = URL("http://$normalizedServer$requestPath")
        val connection = (url.openConnection() as? HttpURLConnection) ?: return null
        return runCatching {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("X-Timestamp", timestamp)
            connection.setRequestProperty("X-Nonce", nonce)
            connection.setRequestProperty("X-Signature", signature)
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@runCatching null
            }

            connection.inputStream.bufferedReader().use { reader ->
                reader.readText().takeIf { it.isNotBlank() }
            }
        }.getOrNull().also {
            connection.disconnect()
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

private fun buildRemoteProfileSignature(
    method: String,
    path: String,
    timestamp: String,
    nonce: String,
    secret: String,
): String? {
    return runCatching {
        val payload = "$method|$path|$timestamp|$nonce"
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(key)
        mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHexString()
    }.getOrNull()
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun TunnelStatus.requiresTunnelRestart(): Boolean {
    return this == TunnelStatus.STARTING ||
        this == TunnelStatus.CONNECTED ||
        this == TunnelStatus.RECONNECTING ||
        this == TunnelStatus.STOPPING
}
