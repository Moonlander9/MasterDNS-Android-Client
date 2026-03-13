package com.masterdnsvpn.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class MasterDnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.masterdnsvpn.android.action.START"
        const val ACTION_STOP = "com.masterdnsvpn.android.action.STOP"
        const val EXTRA_CONFIG_PATH = "extra_config_path"
        const val EXTRA_AUTO_RECONNECT = "extra_auto_reconnect"

        const val BROADCAST_TUNNEL_EVENT = "com.masterdnsvpn.android.broadcast.TUNNEL_EVENT"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_EVENT_STATUS = "event_status"
        const val EXTRA_EVENT_LEVEL = "event_level"
        const val EXTRA_EVENT_MESSAGE = "event_message"
        const val EXTRA_EVENT_CODE = "event_code"
        const val EXTRA_EVENT_TIMESTAMP = "event_timestamp"

        private const val CHANNEL_ID = "masterdnsvpn_tunnel"
        private const val NOTIFICATION_ID = 7021

        private const val VPN_IPV4_ADDRESS = "10.42.0.2"
        private const val VPN_IPV4_PREFIX = 24
        private const val VPN_IPV6_ADDRESS = "fd42:4242::2"
        private const val VPN_IPV6_PREFIX = 128
        private const val VPN_MTU = 1500
        private const val SOCKS_STARTUP_TIMEOUT_MS = 45_000L
        private const val PY_CLIENT_INIT_IDLE_CODE = "CLIENT_INIT_IDLE"
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleServiceException("SERVICE_COROUTINE_EXCEPTION", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private lateinit var bridge: PythonBridge
    private lateinit var tunnelEngine: TunnelEngine
    private lateinit var diagnosticsStore: TunnelDiagnosticsStore

    private var pollJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    private var desiredRunning = false
    private var autoReconnect = true
    private var lastConfigPath: String? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunChildFd: Int? = null

    private data class SocksReadinessResult(
        val ready: Boolean,
        val message: String? = null,
        val code: String? = null,
    )

    private data class VpnEstablishResult(
        val interfaceFd: ParcelFileDescriptor? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
    )

    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (desiredRunning && (!bridge.isRunning() || !tunnelEngine.isRunning())) {
                emitStatus(
                    status = TunnelStatus.RECONNECTING,
                    message = "Network available. Reconnect scheduled.",
                    code = "NETWORK_AVAILABLE",
                )
                scheduleReconnect(initialDelayMillis = 300)
            }
        }

        override fun onLost(network: Network) {
            if (desiredRunning) {
                emitStatus(
                    status = TunnelStatus.RECONNECTING,
                    message = "Network lost. Waiting to reconnect.",
                    code = "NETWORK_LOST",
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        VpnSocketProtector.attach(this)
        bridge = PythonBridge(applicationContext)
        tunnelEngine = HevTunnelEngine()
        diagnosticsStore = TunnelDiagnosticsStore(applicationContext)
        createNotificationChannel()

        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }.onFailure { error ->
            diagnosticsStore.appendFailure(
                stage = "service-create",
                code = "NETWORK_CALLBACK_REGISTER_FAILED",
                message = "Failed to register network callback.",
                throwable = error,
            )
        }
    }

    override fun onDestroy() {
        desiredRunning = false
        reconnectJob?.cancel()
        pollJob?.cancel()
        runCatching { tunnelEngine.stop() }
        closeChildTunFd()
        closeVpnInterface()
        runCatching { bridge.stopClient(2.0) }
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        VpnSocketProtector.detach(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        emitStatus(TunnelStatus.ERROR, "VPN permission was revoked.", "VPN_REVOKED")
        stopTunnel(manual = true)
        super.onRevoke()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                val configPath = intent.getStringExtra(EXTRA_CONFIG_PATH)
                autoReconnect = intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, true)

                if (configPath.isNullOrBlank()) {
                    emitStatus(
                        status = TunnelStatus.ERROR,
                        message = "Missing config path for start action.",
                        code = "MISSING_CONFIG_PATH",
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                desiredRunning = true
                lastConfigPath = configPath

                val foregroundStarted = runCatching {
                    startForeground(NOTIFICATION_ID, buildNotification("Starting VPN tunnel..."))
                }.onFailure { error ->
                    diagnosticsStore.appendFailure(
                        stage = "service-start",
                        code = "FGS_START_EXCEPTION",
                        message = "Failed to start foreground service.",
                        throwable = error,
                    )
                }.isSuccess

                if (!foregroundStarted) {
                    emitStatus(
                        TunnelStatus.ERROR,
                        "Failed to start foreground service.",
                        "FGS_START_EXCEPTION",
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                startTunnel(configPath)
            }

            ACTION_STOP -> {
                stopTunnel(manual = true)
            }

            else -> {
                if (desiredRunning && !lastConfigPath.isNullOrBlank()) {
                    runCatching {
                        startForeground(NOTIFICATION_ID, buildNotification("Restoring VPN tunnel..."))
                    }.onFailure { error ->
                        diagnosticsStore.appendFailure(
                            stage = "service-restore",
                            code = "FGS_START_EXCEPTION",
                            message = "Failed to restore foreground service.",
                            throwable = error,
                        )
                    }
                    startTunnel(lastConfigPath!!)
                }
            }
        }

        return START_STICKY
    }

    private fun startTunnel(configPath: String) {
        reconnectJob?.cancel()
        reconnectJob = null

        serviceScope.launch {
            val started = runCatching {
                startTunnelBlocking(configPath)
            }.getOrElse { error ->
                handleServiceException("START_TUNNEL_EXCEPTION", error)
                false
            }

            if (!started && desiredRunning && autoReconnect) {
                scheduleReconnect()
            }
        }
    }

    private suspend fun startTunnelBlocking(configPath: String): Boolean {
        emitStatus(TunnelStatus.STARTING, "Requesting Python tunnel start.", "START_REQUESTED")

        val configResult = loadConfig(configPath)
        if (configResult == null) {
            emitStatus(TunnelStatus.ERROR, "Unable to parse config file.", "CONFIG_PARSE_FAILED")
            return false
        }

        val validationErrors = configResult.validateForAndroidV1()
        if (validationErrors.isNotEmpty()) {
            emitStatus(
                TunnelStatus.ERROR,
                validationErrors.joinToString(" | "),
                "CONFIG_VALIDATION",
            )
            return false
        }

        if (configResult.vpnMode == VpnMode.SPLIT_ALLOWLIST && configResult.splitAllowlistPackages.isEmpty()) {
            emitStatus(
                TunnelStatus.ERROR,
                "Split allowlist mode requires at least one package.",
                "SPLIT_ALLOWLIST_EMPTY",
            )
            return false
        }

        runCatching { tunnelEngine.stop() }
        closeChildTunFd()
        closeVpnInterface()

        val bridgeResult = bridge.startClient(configPath)
        if (!bridgeResult.ok && bridgeResult.code != "ALREADY_RUNNING") {
            emitStatus(TunnelStatus.ERROR, bridgeResult.message, bridgeResult.code)
            diagnosticsStore.appendFailure(
                stage = "python-start",
                code = bridgeResult.code,
                message = bridgeResult.message,
            )
            return false
        }

        val socksReadiness = awaitSocksReady(configResult.listenIp, configResult.listenPort)
        if (!socksReadiness.ready) {
            val failureMessage = socksReadiness.message ?: "SOCKS endpoint did not become ready in time."
            val failureCode = socksReadiness.code ?: "SOCKS_NOT_READY"
            emitStatus(
                TunnelStatus.ERROR,
                failureMessage,
                failureCode,
            )
            runCatching { bridge.stopClient(2.0) }
            diagnosticsStore.appendFailure(
                stage = "python-socks-ready",
                code = failureCode,
                message = failureMessage,
            )
            return false
        }

        val vpnResult = establishVpn(configResult)
        val establishedInterface = vpnResult.interfaceFd
        if (establishedInterface == null) {
            val failureCode = vpnResult.errorCode ?: "VPN_ESTABLISH_FAILED"
            val failureMessage = vpnResult.errorMessage ?: "Failed to establish VPN interface."
            emitStatus(TunnelStatus.ERROR, failureMessage, failureCode)
            runCatching { bridge.stopClient(2.0) }
            diagnosticsStore.appendFailure(
                stage = "vpn-establish",
                code = failureCode,
                message = failureMessage,
            )
            return false
        }
        vpnInterface = establishedInterface

        val tunFdForChild = prepareTunFdForChild(establishedInterface)
        if (tunFdForChild == null) {
            emitStatus(
                TunnelStatus.ERROR,
                "Unable to prepare TUN file descriptor for tunnel engine.",
                "VPN_TUN_FD_PREPARE_FAILED",
            )
            closeChildTunFd()
            closeVpnInterface()
            runCatching { bridge.stopClient(2.0) }
            diagnosticsStore.appendFailure(
                stage = "tun-fd-prepare",
                code = "VPN_TUN_FD_PREPARE_FAILED",
                message = "Unable to prepare TUN file descriptor.",
            )
            return false
        }
        tunChildFd = tunFdForChild

        val tunnelResult = runCatching {
            tunnelEngine.start(
                config = TunnelEngineConfig(
                    tunFd = tunFdForChild,
                    socksHost = configResult.listenIp,
                    socksPort = configResult.listenPort,
                    socksUser = configResult.socks5User.takeIf { configResult.socks5Auth },
                    socksPass = configResult.socks5Pass.takeIf { configResult.socks5Auth },
                    mtu = VPN_MTU,
                    tunIpv4Address = VPN_IPV4_ADDRESS,
                    tunIpv6Address = VPN_IPV6_ADDRESS,
                ),
            ) { line ->
                emitEvent(
                    TunnelEvent(
                        type = "log",
                        level = "INFO",
                        message = "[hev] $line",
                    ),
                )
            }
        }.getOrElse { error ->
            diagnosticsStore.appendFailure(
                stage = "hev-start",
                code = "HEV_START_EXCEPTION",
                message = "Hev tunnel start threw exception.",
                throwable = error,
            )
            TunnelEngineResult(
                ok = false,
                code = "HEV_START_EXCEPTION",
                message = "Hev tunnel start threw exception: ${error.message}",
            )
        }

        if (!tunnelResult.ok) {
            emitStatus(TunnelStatus.ERROR, tunnelResult.message, tunnelResult.code)
            diagnosticsStore.appendFailure(
                stage = "hev-start",
                code = tunnelResult.code,
                message = tunnelResult.message,
            )
            closeChildTunFd()
            closeVpnInterface()
            runCatching { bridge.stopClient(2.0) }
            return false
        }

        reconnectAttempt = 0
        val modeLabel = if (configResult.vpnMode == VpnMode.SPLIT_ALLOWLIST) "split" else "full"
        emitStatus(TunnelStatus.CONNECTED, "VPN tunnel connected ($modeLabel mode).", "VPN_CONNECTED")
        updateNotification("VPN tunnel running")
        startPolling()
        return true
    }

    private fun stopTunnel(manual: Boolean) {
        desiredRunning = !manual
        reconnectJob?.cancel()
        reconnectJob = null
        pollJob?.cancel()
        pollJob = null

        serviceScope.launch {
            runCatching { tunnelEngine.stop() }
            closeChildTunFd()
            closeVpnInterface()

            val result = bridge.stopClient(10.0)
            if (!result.ok) {
                emitStatus(TunnelStatus.ERROR, result.message, result.code)
                diagnosticsStore.appendFailure(
                    stage = "python-stop",
                    code = result.code,
                    message = result.message,
                )
            } else {
                emitStatus(TunnelStatus.IDLE, "Tunnel stopped.", "STOPPED")
            }
            updateNotification("Tunnel stopped")
            if (manual) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun scheduleReconnect(initialDelayMillis: Long = 1500) {
        val configPath = lastConfigPath ?: return
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(initialDelayMillis)

            while (isActive && desiredRunning && autoReconnect) {
                reconnectAttempt += 1
                val backoffMillis = (1500L * reconnectAttempt).coerceAtMost(15000L)
                emitStatus(
                    TunnelStatus.RECONNECTING,
                    "Reconnect attempt #$reconnectAttempt",
                    "RECONNECT_ATTEMPT",
                )

                val started = runCatching { startTunnelBlocking(configPath) }.getOrElse { error ->
                    handleServiceException("RECONNECT_EXCEPTION", error)
                    false
                }
                if (started) {
                    reconnectAttempt = 0
                    updateNotification("VPN tunnel running")
                    break
                }

                delay(backoffMillis)
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (isActive && desiredRunning) {
                if (!tunnelEngine.isRunning()) {
                    emitStatus(
                        TunnelStatus.ERROR,
                        "Tunnel engine exited.",
                        "HEV_ENGINE_EXITED",
                    )
                    diagnosticsStore.appendFailure(
                        stage = "tunnel-poll",
                        code = "HEV_ENGINE_EXITED",
                        message = "Tunnel engine exited unexpectedly.",
                    )
                    if (autoReconnect && desiredRunning) {
                        scheduleReconnect()
                    }
                    break
                }

                val events = bridge.pollEvents(200)
                if (events.isNotEmpty()) {
                    for (event in events) {
                        emitEvent(event)
                        if (event.type == "status") {
                            when (event.status) {
                                TunnelStatus.CONNECTED -> updateNotification("VPN tunnel running")
                                TunnelStatus.RECONNECTING -> updateNotification("Reconnecting...")
                                TunnelStatus.ERROR -> {
                                    updateNotification("Tunnel error")
                                    if (autoReconnect && desiredRunning && !bridge.isRunning()) {
                                        scheduleReconnect()
                                    }
                                }

                                TunnelStatus.STOPPING -> updateNotification("Stopping tunnel...")
                                TunnelStatus.IDLE -> updateNotification("Tunnel stopped")
                                TunnelStatus.STARTING -> updateNotification("Starting tunnel...")
                                null -> Unit
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun loadConfig(configPath: String): ClientConfig? {
        return runCatching {
            TomlCodec.decode(File(configPath).readText())
        }.onFailure { error ->
            diagnosticsStore.appendFailure(
                stage = "config-load",
                code = "CONFIG_LOAD_EXCEPTION",
                message = "Failed to load config file.",
                throwable = error,
            )
        }.getOrNull()
    }

    private suspend fun awaitSocksReady(host: String, port: Int): SocksReadinessResult {
        val deadline = System.currentTimeMillis() + SOCKS_STARTUP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && desiredRunning) {
            val startupFailure = consumeBridgeEventsDuringStartup()
            if (startupFailure != null) {
                return startupFailure
            }

            val reachable = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 350)
                }
                true
            }.getOrDefault(false)

            if (reachable) {
                return SocksReadinessResult(ready = true)
            }
            delay(200)
        }

        val startupFailure = consumeBridgeEventsDuringStartup()
        if (startupFailure != null) {
            return startupFailure
        }

        return SocksReadinessResult(
            ready = false,
            message = "SOCKS endpoint did not become ready in time.",
            code = "SOCKS_NOT_READY",
        )
    }

    private fun consumeBridgeEventsDuringStartup(): SocksReadinessResult? {
        val events = bridge.pollEvents(200)
        if (events.isEmpty()) {
            return null
        }

        var terminalStatusEvent: TunnelEvent? = null

        for (event in events) {
            emitEvent(event)
            val terminalIdle = event.status == TunnelStatus.IDLE && event.code != PY_CLIENT_INIT_IDLE_CODE
            if (terminalStatusEvent == null &&
                event.type == "status" &&
                (event.status == TunnelStatus.ERROR || terminalIdle)
            ) {
                terminalStatusEvent = event
            }
        }

        val failureEvent = terminalStatusEvent ?: return null
        val defaultCode = when (failureEvent.status) {
            TunnelStatus.IDLE -> "PYTHON_STARTUP_IDLE"
            TunnelStatus.ERROR -> "PYTHON_STARTUP_ERROR"
            else -> "SOCKS_NOT_READY"
        }
        val defaultMessage = when (failureEvent.status) {
            TunnelStatus.IDLE -> "Python runtime stopped before SOCKS endpoint became ready."
            TunnelStatus.ERROR -> "Python runtime failed before SOCKS endpoint became ready."
            else -> "SOCKS endpoint did not become ready in time."
        }

        return SocksReadinessResult(
            ready = false,
            message = failureEvent.message.ifBlank { defaultMessage },
            code = failureEvent.code ?: defaultCode,
        )
    }

    private fun establishVpn(config: ClientConfig): VpnEstablishResult {
        val builder = Builder()
            .setSession("MasterDnsVPN")
            .setMtu(VPN_MTU)
            .addAddress(VPN_IPV4_ADDRESS, VPN_IPV4_PREFIX)
            .addAddress(VPN_IPV6_ADDRESS, VPN_IPV6_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        val dnsSelection = VpnDnsPolicy.selectDnsServers(config)
        if (dnsSelection.servers.isEmpty()) {
            val invalidResolvers = dnsSelection.invalidConfiguredResolvers.joinToString()
            return VpnEstablishResult(
                errorCode = "VPN_DNS_CONFIG_INVALID",
                errorMessage = if (invalidResolvers.isBlank()) {
                    "RESOLVER_DNS_SERVERS did not contain any VPN-compatible DNS IPs."
                } else {
                    "Configured RESOLVER_DNS_SERVERS are not VPN-compatible IPs: $invalidResolvers"
                },
            )
        }

        if (dnsSelection.invalidConfiguredResolvers.isNotEmpty()) {
            emitEvent(
                TunnelEvent(
                    type = "log",
                    level = "WARN",
                    message = "Ignoring non-IP VPN DNS resolvers: ${dnsSelection.invalidConfiguredResolvers.joinToString()}",
                ),
            )
        }

        if (dnsSelection.usedFallback) {
            emitEvent(
                TunnelEvent(
                    type = "log",
                    level = "WARN",
                    message = "No RESOLVER_DNS_SERVERS configured. Falling back to ${dnsSelection.servers.joinToString()}",
                ),
            )
        } else {
            emitEvent(
                TunnelEvent(
                    type = "log",
                    level = "INFO",
                    message = "Using VPN DNS resolvers: ${dnsSelection.servers.joinToString()}",
                ),
            )
        }

        dnsSelection.servers.forEach { dns ->
            val error = runCatching { builder.addDnsServer(dns) }.exceptionOrNull()
            if (error != null) {
                return VpnEstablishResult(
                    errorCode = "VPN_DNS_CONFIG_FAILED",
                    errorMessage = "Failed to configure VPN DNS server $dns: ${error.message ?: "unknown"}",
                )
            }
        }

        when (config.vpnMode) {
            VpnMode.FULL -> {
                runCatching {
                    builder.addDisallowedApplication(packageName)
                }.onFailure { err ->
                    emitEvent(
                        TunnelEvent(
                            type = "log",
                            level = "WARN",
                            message = "Could not exclude app package from full tunnel: ${err.message}",
                        ),
                    )
                }
            }

            VpnMode.SPLIT_ALLOWLIST -> {
                var applied = 0
                config.splitAllowlistPackages.distinct().forEach { pkg ->
                    runCatching {
                        builder.addAllowedApplication(pkg)
                        applied += 1
                    }.onFailure {
                        val message = if (it is PackageManager.NameNotFoundException) {
                            "Split package not installed, skipping: $pkg"
                        } else {
                            "Failed to apply split package $pkg: ${it.message}"
                        }
                        emitEvent(
                            TunnelEvent(
                                type = "log",
                                level = "WARN",
                                message = message,
                            ),
                        )
                    }
                }

                if (applied == 0) {
                    return VpnEstablishResult(
                        errorCode = "SPLIT_ALLOWLIST_INVALID",
                        errorMessage = "No valid split allowlist packages were found on this device.",
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val established = runCatching {
            builder.establish()
        }.getOrElse { error ->
            return VpnEstablishResult(
                errorCode = "VPN_ESTABLISH_EXCEPTION",
                errorMessage = "Failed to establish VPN interface: ${error.message ?: "unknown"}",
            )
        }

        if (established == null) {
            return VpnEstablishResult(
                errorCode = "VPN_ESTABLISH_RETURNED_NULL",
                errorMessage = "VpnService.Builder.establish() returned null.",
            )
        }

        return VpnEstablishResult(interfaceFd = established)
    }

    private fun closeVpnInterface() {
        runCatching {
            vpnInterface?.close()
        }
        vpnInterface = null
    }

    private fun closeChildTunFd() {
        val fd = tunChildFd ?: return
        tunChildFd = null
        runCatching {
            ParcelFileDescriptor.adoptFd(fd).close()
        }
    }

    private fun prepareTunFdForChild(pfd: ParcelFileDescriptor): Int? {
        return runCatching {
            val duplicate = ParcelFileDescriptor.dup(pfd.fileDescriptor)
            val flags = Os.fcntlInt(duplicate.fileDescriptor, OsConstants.F_GETFD, 0)
            Os.fcntlInt(duplicate.fileDescriptor, OsConstants.F_SETFD, flags and OsConstants.FD_CLOEXEC.inv())
            duplicate.detachFd()
        }.onFailure { err ->
            emitEvent(
                TunnelEvent(
                    type = "log",
                    level = "ERROR",
                    message = "Failed to prepare tun fd for tunnel engine: ${err.message}",
                ),
            )
            diagnosticsStore.appendFailure(
                stage = "tun-fd-prepare",
                code = "VPN_TUN_FD_PREPARE_EXCEPTION",
                message = "Failed to prepare tun fd for tunnel engine.",
                throwable = err,
            )
        }.getOrNull()
    }

    private fun emitStatus(status: TunnelStatus, message: String, code: String?) {
        emitEvent(
            TunnelEvent(
                type = "status",
                status = status,
                level = if (status == TunnelStatus.ERROR) "ERROR" else "INFO",
                message = message,
                code = code,
            ),
        )
    }

    private fun emitEvent(event: TunnelEvent) {
        if (::diagnosticsStore.isInitialized) {
            diagnosticsStore.appendEvent(event, stage = event.code)
        }

        val intent = Intent(BROADCAST_TUNNEL_EVENT).apply {
            `package` = packageName
            putExtra(EXTRA_EVENT_TYPE, event.type)
            putExtra(EXTRA_EVENT_STATUS, event.status?.name)
            putExtra(EXTRA_EVENT_LEVEL, event.level)
            putExtra(EXTRA_EVENT_MESSAGE, event.message)
            putExtra(EXTRA_EVENT_CODE, event.code)
            putExtra(EXTRA_EVENT_TIMESTAMP, event.timestamp)
        }
        sendBroadcast(intent)
    }

    private fun handleServiceException(code: String, throwable: Throwable) {
        val snippet = throwable.stackTrace
            .take(4)
            .joinToString(" | ") { "${it.className}:${it.lineNumber}" }
        val message = "${throwable.message ?: "Service failure"} [$snippet]"

        if (::diagnosticsStore.isInitialized) {
            diagnosticsStore.appendFailure(
                stage = "service-exception",
                code = code,
                message = throwable.message ?: "Service coroutine exception",
                throwable = throwable,
            )
        }

        emitStatus(TunnelStatus.ERROR, message, code)
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, MasterDnsVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            200,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(launchPendingIntent)
            .addAction(0, getString(R.string.action_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
