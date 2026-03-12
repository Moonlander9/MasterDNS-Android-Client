package com.masterdnsvpn.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masterdnsvpn.android.scanner.BUNDLED_CIDR_ASSET_PATH
import com.masterdnsvpn.android.scanner.DnsScannerEngine
import com.masterdnsvpn.android.scanner.ScanPreset
import com.masterdnsvpn.android.scanner.ScanRecordType
import com.masterdnsvpn.android.scanner.ScanStatus
import com.masterdnsvpn.android.scanner.ScannerConfig
import com.masterdnsvpn.android.scanner.ScannerConfigStore
import com.masterdnsvpn.android.scanner.ScannerSessionState
import com.masterdnsvpn.android.scanner.successfulResolversFromEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LogLine(
    val timestamp: String,
    val level: String,
    val message: String,
    val type: String,
)

data class MainUiState(
    val config: ClientConfig = ClientConfig(),
    val status: TunnelStatus = TunnelStatus.IDLE,
    val statusMessage: String = "Idle",
    val lastCode: String? = null,
    val logs: List<LogLine> = emptyList(),
    val validationErrors: List<String> = emptyList(),
    val scannerConfig: ScannerConfig = ScannerConfig(),
    val scannerSession: ScannerSessionState = ScannerSessionState(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val showTcpFirstWarning: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = ConfigStoreV3(application.applicationContext)
    private val scannerConfigStore = ScannerConfigStore(application.applicationContext)
    private val scannerEngine = DnsScannerEngine(scope = viewModelScope)
    private val maxResolverCount = 10
    private val initialConfig = normalizeConfig(configStore.load())

    private val _uiState = MutableStateFlow(
        MainUiState(
            config = initialConfig,
            validationErrors = initialConfig.validateForAndroidV1(),
            scannerConfig = scannerConfigStore.load(),
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()

        viewModelScope.launch {
            scannerEngine.state.collectLatest { state ->
                _uiState.update { current ->
                    val shouldAutoApplyResolvers = current.scannerSession.status != ScanStatus.COMPLETED &&
                        state.status == ScanStatus.COMPLETED

                    if (!shouldAutoApplyResolvers) {
                        return@update current.copy(scannerSession = state)
                    }

                    val successfulResolvers = successfulResolversFromEntries(
                        entries = state.entries,
                        proxyEnabled = state.config.proxyTestEnabled,
                        proxyFeatureAvailable = state.proxyFeatureAvailable,
                    )

                    if (successfulResolvers.isEmpty()) {
                        return@update current.copy(scannerSession = state)
                    }

                    current.copy(
                        scannerSession = state,
                        config = current.config.copy(
                            resolverDnsServers = successfulResolvers.take(maxResolverCount),
                        ),
                    )
                }
            }
        }
    }

    fun updateProtocolType(value: String) = mutateConfig { copy(protocolType = value.trim()) }
    fun updateEncryptionMethod(value: String) = mutateConfig {
        copy(dataEncryptionMethod = value.toIntOrNull() ?: dataEncryptionMethod)
    }
    fun updateEncryptionKey(value: String) = mutateConfig { copy(encryptionKey = value.trim()) }
    fun updateDomainsCsv(value: String) = mutateConfig { copy(domains = csvToList(value)) }
    fun updateResolversCsv(value: String) = mutateConfig { copy(resolverDnsServers = csvToList(value)) }
    fun updateListenIp(value: String) = mutateConfig { copy(listenIp = value.trim()) }
    fun updateListenPort(value: String) = mutateConfig {
        copy(listenPort = value.toIntOrNull() ?: listenPort)
    }
    fun updateSocksAuth(value: Boolean) = mutateConfig { copy(socks5Auth = value) }
    fun updateSocksUser(value: String) = mutateConfig { copy(socks5User = value) }
    fun updateSocksPass(value: String) = mutateConfig { copy(socks5Pass = value) }
    fun updateSocksHandshakeTimeout(value: String) = mutateConfig {
        copy(socksHandshakeTimeout = value.toDoubleOrNull() ?: socksHandshakeTimeout)
    }
    fun updateVpnMode(value: VpnMode) = mutateConfig { copy(vpnMode = value) }
    fun setSplitAllowlistPackage(packageName: String, enabled: Boolean) = mutateConfig {
        val next = splitAllowlistPackages.toMutableList()
        if (enabled && packageName !in next) {
            next += packageName
        } else if (!enabled) {
            next.remove(packageName)
        }
        copy(splitAllowlistPackages = next)
    }

    fun addSplitAllowlistPackage(packageName: String) = setSplitAllowlistPackage(packageName, true)

    fun updatePacketDuplicationCount(value: String) = mutateConfig {
        copy(packetDuplicationCount = value.toIntOrNull() ?: packetDuplicationCount)
    }
    fun updateMaxPacketsPerBatch(value: String) = mutateConfig {
        copy(maxPacketsPerBatch = value.toIntOrNull() ?: maxPacketsPerBatch)
    }
    fun updateResolverBalancingStrategy(value: String) = mutateConfig {
        copy(resolverBalancingStrategy = value.toIntOrNull() ?: resolverBalancingStrategy)
    }
    fun updateBaseEncodeData(value: Boolean) = mutateConfig { copy(baseEncodeData = value) }
    fun updateUploadCompressionType(value: String) = mutateConfig {
        copy(uploadCompressionType = value.toIntOrNull() ?: uploadCompressionType)
    }
    fun updateDownloadCompressionType(value: String) = mutateConfig {
        copy(downloadCompressionType = value.toIntOrNull() ?: downloadCompressionType)
    }
    fun updateMinUploadMtu(value: String) = mutateConfig {
        copy(minUploadMtu = value.toIntOrNull() ?: minUploadMtu)
    }
    fun updateMinDownloadMtu(value: String) = mutateConfig {
        copy(minDownloadMtu = value.toIntOrNull() ?: minDownloadMtu)
    }
    fun updateMaxUploadMtu(value: String) = mutateConfig {
        copy(maxUploadMtu = value.toIntOrNull() ?: maxUploadMtu)
    }
    fun updateMaxDownloadMtu(value: String) = mutateConfig {
        copy(maxDownloadMtu = value.toIntOrNull() ?: maxDownloadMtu)
    }
    fun updateMtuTestRetries(value: String) = mutateConfig {
        copy(mtuTestRetries = value.toIntOrNull() ?: mtuTestRetries)
    }
    fun updateMtuTestTimeout(value: String) = mutateConfig {
        copy(mtuTestTimeout = value.toDoubleOrNull() ?: mtuTestTimeout)
    }
    fun updateMaxConnectionAttempts(value: String) = mutateConfig {
        copy(maxConnectionAttempts = value.toIntOrNull() ?: maxConnectionAttempts)
    }
    fun updateArqWindowSize(value: String) = mutateConfig {
        copy(arqWindowSize = value.toIntOrNull() ?: arqWindowSize)
    }
    fun updateArqInitialRto(value: String) = mutateConfig {
        copy(arqInitialRto = value.toDoubleOrNull() ?: arqInitialRto)
    }
    fun updateArqMaxRto(value: String) = mutateConfig {
        copy(arqMaxRto = value.toDoubleOrNull() ?: arqMaxRto)
    }
    fun updateArqControlInitialRto(value: String) = mutateConfig {
        copy(arqControlInitialRto = value.toDoubleOrNull() ?: arqControlInitialRto)
    }
    fun updateArqControlMaxRto(value: String) = mutateConfig {
        copy(arqControlMaxRto = value.toDoubleOrNull() ?: arqControlMaxRto)
    }
    fun updateArqControlMaxRetries(value: String) = mutateConfig {
        copy(arqControlMaxRetries = value.toIntOrNull() ?: arqControlMaxRetries)
    }
    fun updateDnsQueryTimeout(value: String) = mutateConfig {
        copy(dnsQueryTimeout = value.toDoubleOrNull() ?: dnsQueryTimeout)
    }
    fun updateNumRxWorkers(value: String) = mutateConfig {
        copy(numRxWorkers = value.toIntOrNull() ?: numRxWorkers)
    }
    fun updateNumDnsWorkers(value: String) = mutateConfig {
        copy(numDnsWorkers = value.toIntOrNull() ?: numDnsWorkers)
    }
    fun updateRxSemaphoreLimit(value: String) = mutateConfig {
        copy(rxSemaphoreLimit = value.toIntOrNull() ?: rxSemaphoreLimit)
    }
    fun updateMaxClosedStreamRecords(value: String) = mutateConfig {
        copy(maxClosedStreamRecords = value.toIntOrNull() ?: maxClosedStreamRecords)
    }
    fun updateSocketBufferSize(value: String) = mutateConfig {
        copy(socketBufferSize = value.toIntOrNull() ?: socketBufferSize)
    }
    fun updateLogLevel(value: String) = mutateConfig { copy(logLevel = value.uppercase()) }

    fun importToml(content: String) {
        val decoded = normalizeConfig(TomlCodec.decode(content))
        _uiState.value = _uiState.value.copy(
            config = decoded,
            validationErrors = decoded.validateForAndroidV1(),
        )
        configStore.save(decoded)
    }

    fun exportToml(): String {
        return TomlCodec.encode(_uiState.value.config)
    }

    fun exportProfileString(): String {
        return ProfileCodec.encode(_uiState.value.config)
    }

    fun importProfileString(profile: String): Result<Unit> {
        return runCatching {
            val decoded = normalizeConfig(ProfileCodec.decode(profile))
            _uiState.value = _uiState.value.copy(
                config = decoded,
                validationErrors = decoded.validateForAndroidV1(),
            )
            configStore.save(decoded)
        }
    }

    fun persistConfig(context: Context): Result<String> {
        val config = normalizeConfig(_uiState.value.config)
        _uiState.update { current -> current.copy(config = config) }
        val errors = config.validateForAndroidV1()
        _uiState.value = _uiState.value.copy(validationErrors = errors)
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(errors.joinToString("\n")))
        }

        return runCatching {
            val toml = TomlCodec.encode(config)
            val file = File(context.filesDir, "client_config.toml")
            file.writeText(toml)
            configStore.save(config)
            file.absolutePath
        }
    }

    fun onTunnelEvent(event: TunnelEvent) {
        if (event.type == "status") {
            val nextStatus = event.status ?: TunnelStatus.ERROR
            _uiState.value = _uiState.value.copy(
                status = nextStatus,
                statusMessage = event.message.ifBlank { nextStatus.name },
                lastCode = event.code,
            )
        }

        val line = LogLine(
            timestamp = event.timestamp.ifBlank { "" },
            level = event.level,
            message = event.message,
            type = event.type,
        )
        val nextLogs = (_uiState.value.logs + line).takeLast(5000)
        _uiState.value = _uiState.value.copy(logs = nextLogs)
    }

    fun exportDiagnostics(context: Context): Result<String> {
        return runCatching {
            TunnelDiagnosticsStore(context.applicationContext).exportText()
        }
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    fun updateScannerDomain(value: String) = mutateScannerConfig { copy(domain = value.trim()) }

    fun updateScannerRecordType(value: ScanRecordType) = mutateScannerConfig { copy(recordType = value) }

    fun updateScannerRandomSubdomain(value: Boolean) = mutateScannerConfig { copy(randomSubdomain = value) }

    fun updateScannerPreset(value: ScanPreset) = mutateScannerConfig { copy(preset = value) }

    fun updateScannerConcurrency(value: String) = mutateScannerConfig {
        copy(concurrency = value.toIntOrNull()?.coerceAtLeast(1) ?: concurrency)
    }

    fun updateScannerProxyEnabled(value: Boolean) = mutateScannerConfig { copy(proxyTestEnabled = value) }

    fun updateScannerSlipstreamPath(value: String) = mutateScannerConfig { copy(slipstreamBinaryPath = value.trim()) }

    fun updateScannerCidr(uri: String, label: String) = mutateScannerConfig {
        copy(cidrUri = uri, cidrLabel = label)
    }

    fun startScannerScan() {
        val config = _uiState.value.scannerConfig
        if (config.cidrUri.isBlank()) {
            _uiState.update { current ->
                current.copy(
                    scannerSession = current.scannerSession.copy(
                        status = ScanStatus.ERROR,
                        lastMessage = "Select a CIDR file before scanning",
                        error = "CIDR file not selected",
                    ),
                )
            }
            return
        }

        val app = getApplication<Application>()
        val exportDir = app.getExternalFilesDir(null) ?: app.filesDir

        val readerProvider: suspend () -> java.io.BufferedReader = if (config.cidrUri.startsWith("asset://")) {
            val assetPath = config.cidrUri.removePrefix("asset://").ifBlank { BUNDLED_CIDR_ASSET_PATH }
            suspend {
                app.assets.open(assetPath).bufferedReader()
            }
        } else {
            val uri = runCatching { Uri.parse(config.cidrUri) }.getOrNull()
            if (uri == null) {
                _uiState.update { current ->
                    current.copy(
                        scannerSession = current.scannerSession.copy(
                            status = ScanStatus.ERROR,
                            lastMessage = "Invalid CIDR URI",
                            error = "Unable to parse selected CIDR file",
                        ),
                    )
                }
                return
            }
            suspend {
                app.contentResolver.openInputStream(uri)?.bufferedReader()
                    ?: throw IllegalStateException("Unable to open CIDR file")
            }
        }

        scannerEngine.startScan(
            config = config,
            cidrReaderProvider = readerProvider,
            exportDir = exportDir,
        )
    }

    fun pauseScannerScan() {
        scannerEngine.pauseScan()
    }

    fun resumeScannerScan() {
        scannerEngine.resumeScan()
    }

    fun shuffleScannerScan() {
        scannerEngine.requestShuffle()
    }

    fun stopScannerScan() {
        scannerEngine.stopScan()
    }

    fun saveScannerResults() {
        viewModelScope.launch {
            scannerEngine.saveResultsNow().onFailure {
                _uiState.update { current ->
                    current.copy(
                        scannerSession = current.scannerSession.copy(
                            lastMessage = "Manual save failed",
                            error = it.message,
                        ),
                    )
                }
            }
        }
    }

    fun applyScannerDnsToTunnel(dnsIp: String) {
        mutateConfig {
            copy(resolverDnsServers = listOf(dnsIp))
        }
    }

    override fun onCleared() {
        scannerEngine.stopScan()
        super.onCleared()
    }

    private fun mutateConfig(transform: ClientConfig.() -> ClientConfig) {
        val next = normalizeConfig(_uiState.value.config.transform())
        _uiState.value = _uiState.value.copy(
            config = next,
            validationErrors = emptyList(),
        )
        configStore.save(next)
    }

    private fun mutateScannerConfig(transform: ScannerConfig.() -> ScannerConfig) {
        val next = _uiState.value.scannerConfig.transform()
        scannerConfigStore.save(next)
        _uiState.update { current ->
            current.copy(scannerConfig = next)
        }
    }

    private fun csvToList(value: String): List<String> {
        return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun normalizeConfig(config: ClientConfig): ClientConfig {
        val ownPackage = getApplication<Application>().packageName
        return config.copy(
            protocolType = "SOCKS5",
            dataEncryptionMethod = config.dataEncryptionMethod.takeIf { it in 1..5 } ?: 1,
            resolverDnsServers = config.resolverDnsServers
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(maxResolverCount),
            listenIp = "127.0.0.1",
            splitAllowlistPackages = config.splitAllowlistPackages
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != ownPackage }
                .distinct()
                .take(128),
            uploadCompressionType = config.uploadCompressionType.takeIf { it in 0..3 } ?: 3,
            downloadCompressionType = config.downloadCompressionType.takeIf { it in 0..3 } ?: 3,
            socksHandshakeTimeout = config.socksHandshakeTimeout.takeIf { it > 0.0 } ?: 240.0,
            arqControlInitialRto = config.arqControlInitialRto.takeIf { it > 0.0 } ?: 0.4,
            arqControlMaxRto = config.arqControlMaxRto.takeIf { it > 0.0 } ?: 1.0,
            arqControlMaxRetries = config.arqControlMaxRetries.coerceAtLeast(1),
            rxSemaphoreLimit = config.rxSemaphoreLimit.coerceAtLeast(1),
            maxClosedStreamRecords = config.maxClosedStreamRecords.coerceAtLeast(1),
            logLevel = config.logLevel.uppercase(),
            configVersion = 2.0,
        )
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val pm = app.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val apps = runCatching {
                val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, 0)
                }

                activities
                    .mapNotNull { resolveInfo ->
                        val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                        val packageName = activityInfo.packageName ?: return@mapNotNull null
                        if (packageName == app.packageName) {
                            return@mapNotNull null
                        }
                        val label = runCatching { resolveInfo.loadLabel(pm).toString() }
                            .getOrDefault(packageName)
                        InstalledAppInfo(packageName = packageName, label = label)
                    }
                    .distinctBy { it.packageName }
                    .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
            }.getOrDefault(emptyList())

            _uiState.update { current ->
                current.copy(installedApps = apps)
            }
        }
    }
}
