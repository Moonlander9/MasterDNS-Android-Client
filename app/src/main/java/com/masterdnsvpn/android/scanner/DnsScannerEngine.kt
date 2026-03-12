package com.masterdnsvpn.android.scanner

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

private const val CHUNK_SIZE = 500
private const val EXTRA_TEST_CONCURRENCY = 5
private const val PROXY_TEST_CONCURRENCY = 5
private const val PROXY_PORT_START = 10800

class DnsScannerEngine(
    private val scope: CoroutineScope,
    private val networkClient: ScannerNetworkClient = DnsJavaScannerNetworkClient(),
    private val proxyTester: SlipstreamProxyTester = ProcessSlipstreamProxyTester(),
    private val cidrChunkStreamer: CidrChunkStreamer = CidrChunkStreamer(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val _state = MutableStateFlow(ScannerSessionState())
    val state: StateFlow<ScannerSessionState> = _state.asStateFlow()

    private var scanJob: Job? = null

    private val paused = MutableStateFlow(false)
    private val stopRequested = AtomicBoolean(false)
    private val cancelledByUser = AtomicBoolean(false)
    private val shuffleRequested = AtomicBoolean(false)

    private val entries = ConcurrentHashMap<String, ScannerEntry>()

    private val scannedIps = AtomicInteger(0)
    private val proxyPassed = AtomicInteger(0)
    private val proxyFailed = AtomicInteger(0)

    @Volatile
    private var currentConfig: ScannerConfig = ScannerConfig()

    @Volatile
    private var currentExportDir: File = File(".")

    @Volatile
    private var proxyFeatureAvailable: Boolean = true

    @Volatile
    private var scanStartedAtMs: Long = 0L

    @Volatile
    private var totalIpsForProgress: Int = 0

    @Volatile
    private var testedBlocksCount: Int = 0

    @Volatile
    private var autoShuffleCount: Int = 0

    @Volatile
    private var latestCsvPath: String? = null

    fun startScan(
        config: ScannerConfig,
        cidrReaderProvider: suspend () -> BufferedReader,
        exportDir: File,
    ) {
        stopRequested.set(false)
        cancelledByUser.set(false)
        paused.value = false
        shuffleRequested.set(false)
        currentConfig = config
        currentExportDir = exportDir
        latestCsvPath = null

        entries.clear()
        scannedIps.set(0)
        proxyPassed.set(0)
        proxyFailed.set(0)
        totalIpsForProgress = 0
        testedBlocksCount = 0
        autoShuffleCount = 0

        scanJob?.cancel()
        scanJob = scope.launch(ioDispatcher) {
            runScan(config, cidrReaderProvider)
        }
    }

    fun pauseScan() {
        if (_state.value.status != ScanStatus.RUNNING) {
            return
        }
        paused.value = true
        emitSnapshot(status = ScanStatus.PAUSED, message = "Scan paused")
    }

    fun resumeScan() {
        if (_state.value.status != ScanStatus.PAUSED) {
            return
        }
        paused.value = false
        emitSnapshot(status = ScanStatus.RUNNING, message = "Scan resumed")
    }

    fun requestShuffle() {
        if (_state.value.status != ScanStatus.RUNNING && _state.value.status != ScanStatus.PAUSED) {
            return
        }
        shuffleRequested.set(true)
        emitSnapshot(message = "Manual shuffle requested")
    }

    fun stopScan() {
        stopRequested.set(true)
        cancelledByUser.set(true)
        paused.value = false
        val jobToStop = scanJob
        jobToStop?.cancel()
        scope.launch {
            jobToStop?.runCatching { cancelAndJoin() }
            emitSnapshot(status = ScanStatus.CANCELLED, message = "Scan cancelled")
        }
    }

    suspend fun saveResultsNow(): Result<String> {
        val snapshot = _state.value
        if (snapshot.entries.isEmpty()) {
            return Result.failure(IllegalStateException("No results available"))
        }
        val result = writeCsvFile(autoSave = false)
        result.onSuccess { path ->
            latestCsvPath = path
            emitSnapshot(csvPath = path, message = "Results saved")
        }
        return result
    }

    private suspend fun runScan(
        config: ScannerConfig,
        cidrReaderProvider: suspend () -> BufferedReader,
    ) {
        scanStartedAtMs = System.currentTimeMillis()

        val cidrs = runCatching {
            withContext(ioDispatcher) {
                cidrReaderProvider().use { reader ->
                    cidrChunkStreamer.parseCidrs(reader.lineSequence())
                }
            }
        }.getOrElse { error ->
            emitSnapshot(
                status = ScanStatus.ERROR,
                message = "Failed to read CIDR file",
                error = error.message ?: "Unable to read CIDR file",
            )
            return
        }

        if (cidrs.isEmpty()) {
            emitSnapshot(
                status = ScanStatus.ERROR,
                message = "No valid CIDR ranges found",
                error = "CIDR file is empty or invalid",
            )
            return
        }

        val totalHosts = cidrChunkStreamer.countHosts(cidrs.asSequence())
        totalIpsForProgress = if (config.preset.maxIps > 0) {
            min(totalHosts, config.preset.maxIps.toLong()).toInt()
        } else {
            totalHosts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        proxyFeatureAvailable = true
        if (config.proxyTestEnabled) {
            val capability = proxyTester.checkCapability(config.slipstreamBinaryPath)
            proxyFeatureAvailable = capability.available
            if (!capability.available) {
                emitSnapshot(
                    status = ScanStatus.RUNNING,
                    message = "Proxy test unavailable: ${capability.reason}",
                )
            }
        }

        emitSnapshot(
            status = ScanStatus.RUNNING,
            message = "Scanning started",
        )

        val concurrency = config.concurrency.coerceAtLeast(1)
        val dnsSemaphore = Semaphore(concurrency)
        val extraTestSemaphore = Semaphore(EXTRA_TEST_CONCURRENCY)
        val proxyTestSemaphore = Semaphore(PROXY_TEST_CONCURRENCY)

        val proxyPorts = Channel<Int>(capacity = PROXY_TEST_CONCURRENCY)
        repeat(PROXY_TEST_CONCURRENCY) { offset ->
            proxyPorts.trySend(PROXY_PORT_START + offset)
        }

        val testedBlocks = mutableSetOf<String>()
        val totalGeneratedIps = AtomicInteger(0)
        val activeScanTasks = mutableSetOf<Deferred<DnsProbeResult>>()
        val extraTestJobs = Collections.synchronizedSet(mutableSetOf<Job>())
        val proxyTestJobs = Collections.synchronizedSet(mutableSetOf<Job>())
        val runScope = CoroutineScope(currentCoroutineContext())
        val autoShuffleController = AutoShuffleController(
            preset = config.preset,
            proxyModeEnabled = config.proxyTestEnabled,
        )

        var lastStatsUpdate = System.currentTimeMillis()

        suspend fun queueExtraTests(ip: String) {
            val job = runScope.launch(ioDispatcher) {
                extraTestSemaphore.withPermit {
                    coroutineScope {
                        val tcpUdp = async { networkClient.testTcpUdpSupport(ip) }
                        val security = async { networkClient.testSecurity(ip) }
                        val ipv6 = async { networkClient.testIpv6Support(ip) }
                        val resolvedIp = async {
                            networkClient.resolveARecord(
                                dnsIp = ip,
                                scanType = config.recordType,
                                scanDomain = config.domain,
                            )
                        }
                        val edns0 = async { networkClient.testEdns0Support(ip) }
                        val isp = async { networkClient.lookupIsp(ip) }

                        val tcpUdpResult = tcpUdp.await()
                        val securityResult = security.await()
                        val ipv6Result = ipv6.await()
                        val resolvedIpResult = resolvedIp.await()
                        val edns0Result = edns0.await()
                        val ispResult = isp.await()

                        entries.computeIfPresent(ip) { _, latest ->
                            latest.withUpdate(
                                tcpUdp = tcpUdpResult,
                                securityInfo = securityResult,
                                protocolInfo = latest.protocolInfo.copy(
                                    ipv6 = ipv6Result,
                                    edns0 = edns0Result,
                                ),
                                resolvedIp = resolvedIpResult,
                                isp = ispResult,
                            )
                        }
                    }
                }

                emitSnapshot(message = "Extra tests updated for $ip")
            }

            extraTestJobs += job
            job.invokeOnCompletion { extraTestJobs -= job }
        }

        suspend fun queueProxyTest(ip: String) {
            if (!config.proxyTestEnabled) {
                return
            }

            if (!proxyFeatureAvailable) {
                val current = entries[ip] ?: return
                entries[ip] = current.withUpdate(proxyResult = ProxyResultState.UNAVAILABLE)
                emitSnapshot(message = "Proxy test unavailable")
                return
            }

            val job = runScope.launch(ioDispatcher) {
                proxyTestSemaphore.withPermit {
                    val current = entries[ip] ?: return@withPermit
                    entries[ip] = current.withUpdate(proxyResult = ProxyResultState.TESTING)
                    emitSnapshot(message = "Proxy testing $ip")

                    val port = proxyPorts.receive()
                    val result = try {
                        proxyTester.runProxyTest(
                            dnsIp = ip,
                            domain = config.domain,
                            binaryPath = config.slipstreamBinaryPath,
                            port = port,
                        )
                    } finally {
                        proxyPorts.trySend(port)
                    }

                    val latest = entries[ip] ?: return@withPermit
                    entries[ip] = latest.withUpdate(proxyResult = result)
                    if (result == ProxyResultState.SUCCESS) {
                        proxyPassed.incrementAndGet()
                    } else if (result == ProxyResultState.FAILED) {
                        proxyFailed.incrementAndGet()
                    }

                    emitSnapshot(message = "Proxy test ${result.name.lowercase()} for $ip")
                }
            }

            proxyTestJobs += job
            job.invokeOnCompletion { proxyTestJobs -= job }
        }

        suspend fun processProbeResult(result: DnsProbeResult) {
            val scanned = scannedIps.incrementAndGet()

            if (result.valid) {
                val defaultProxy = if (config.proxyTestEnabled) {
                    if (proxyFeatureAvailable) ProxyResultState.PENDING else ProxyResultState.UNAVAILABLE
                } else {
                    null
                }

                val inserted = entries.putIfAbsent(
                    result.dns,
                    ScannerEntry(
                        dns = result.dns,
                        pingMs = result.elapsedMs,
                        proxyResult = defaultProxy,
                        foundAt = Instant.now(),
                    ),
                ) == null

                autoShuffleController.onFound(entries.size, proxyPassed.get())

                if (inserted) {
                    queueExtraTests(result.dns)
                    if (config.proxyTestEnabled) {
                        queueProxyTest(result.dns)
                    }
                }
            } else {
                val shouldShuffle = autoShuffleController.onMiss(
                    paused = paused.value,
                    currentFound = entries.size,
                    currentProxyPassed = proxyPassed.get(),
                )
                if (shouldShuffle) {
                    shuffleRequested.set(true)
                }
            }

            if (reachedPresetLimit(config.preset, scanned)) {
                stopRequested.set(true)
            }

            val now = System.currentTimeMillis()
            if (scanned % 10 == 0 || now - lastStatsUpdate >= 100) {
                lastStatsUpdate = now
                autoShuffleCount = autoShuffleController.autoShuffleCount
                testedBlocksCount = testedBlocks.size
                emitSnapshot(message = "Scanning in progress")
            }
        }

        suspend fun processDoneTasks(doneTasks: Collection<Deferred<DnsProbeResult>>) {
            for (task in doneTasks) {
                activeScanTasks -= task
                val result = runCatching { task.await() }.getOrNull() ?: continue
                processProbeResult(result)
            }
        }

        suspend fun awaitResumedOrStopped() {
            while (paused.value && !stopRequested.get() && currentCoroutineContext().isActive) {
                delay(50)
            }
        }

        suspend fun drainActiveTasks(timeoutMs: Long) {
            if (activeScanTasks.isEmpty()) {
                return
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            while (activeScanTasks.isNotEmpty() && System.currentTimeMillis() < deadline) {
                val done = activeScanTasks.filter { it.isCompleted }
                if (done.isEmpty()) {
                    delay(20)
                    continue
                }
                processDoneTasks(done)
            }

            activeScanTasks.forEach { it.cancel() }
            activeScanTasks.clear()
        }

        try {
            var scanComplete = false

            while (!scanComplete && !stopRequested.get() && currentCoroutineContext().isActive) {
                shuffleRequested.set(false)
                var reshuffled = false

                for (chunk in cidrChunkStreamer.streamChunks(
                    cidrs = cidrs,
                    testedBlocks = testedBlocks,
                    totalGeneratedIps = totalGeneratedIps,
                    maxIps = config.preset.maxIps,
                    chunkSize = CHUNK_SIZE,
                )) {
                    awaitResumedOrStopped()
                    if (stopRequested.get() || !currentCoroutineContext().isActive) {
                        break
                    }

                    if (shuffleRequested.getAndSet(false)) {
                        reshuffled = true
                        break
                    }

                    for (ip in chunk) {
                        if (stopRequested.get() || !currentCoroutineContext().isActive) {
                            break
                        }
                        val task = runScope.async(ioDispatcher) {
                            dnsSemaphore.withPermit {
                                networkClient.probeDns(
                                    dnsIp = ip,
                                    domain = config.domain,
                                    recordType = config.recordType,
                                    randomSubdomain = config.randomSubdomain,
                                )
                            }
                        }
                        activeScanTasks += task
                        while (activeScanTasks.size >= concurrency * 2) {
                            awaitResumedOrStopped()
                            if (stopRequested.get() || !currentCoroutineContext().isActive) {
                                break
                            }
                            if (shuffleRequested.getAndSet(false)) {
                                reshuffled = true
                                break
                            }

                            val done = activeScanTasks.filter { it.isCompleted }
                            if (done.isEmpty()) {
                                delay(20)
                                continue
                            }
                            processDoneTasks(done)
                        }

                        if (reshuffled || stopRequested.get() || !currentCoroutineContext().isActive) {
                            break
                        }
                    }

                    if (reshuffled || stopRequested.get() || !currentCoroutineContext().isActive) {
                        break
                    }
                }

                autoShuffleCount = autoShuffleController.autoShuffleCount
                testedBlocksCount = testedBlocks.size

                if (stopRequested.get() || !currentCoroutineContext().isActive) {
                    break
                }

                if (!reshuffled) {
                    scanComplete = true
                } else {
                    emitSnapshot(message = "Reshuffling stream order")
                    drainActiveTasks(timeoutMs = 5_000)
                }
            }

            processDoneTasks(activeScanTasks.filter { it.isCompleted })
            withTimeoutOrNull(15_000) {
                while (activeScanTasks.isNotEmpty()) {
                    val done = activeScanTasks.filter { it.isCompleted }
                    if (done.isEmpty()) {
                        delay(25)
                    } else {
                        processDoneTasks(done)
                    }
                }
            }
            activeScanTasks.forEach { it.cancel() }
            activeScanTasks.clear()

            val extrasCompleted = withTimeoutOrNull(30_000) {
                extraTestJobs.toList().joinAll()
                true
            }
            if (extrasCompleted == null) {
                extraTestJobs.forEach { it.cancel() }
            }

            val proxyCompleted = withTimeoutOrNull(60_000) {
                proxyTestJobs.toList().joinAll()
                true
            }
            if (proxyCompleted == null) {
                proxyTestJobs.forEach { it.cancel() }
            }

            if (!cancelledByUser.get() && currentCoroutineContext().isActive) {
                writeCsvFile(autoSave = true).onSuccess { path ->
                    latestCsvPath = path
                    emitSnapshot(csvPath = path, message = "Results auto-saved")
                }
                emitSnapshot(status = ScanStatus.COMPLETED, message = "Scan completed")
            }
        } catch (cancelled: CancellationException) {
            emitSnapshot(status = ScanStatus.CANCELLED, message = "Scan cancelled")
            throw cancelled
        } catch (error: Exception) {
            emitSnapshot(
                status = ScanStatus.ERROR,
                message = "Scanner failed",
                error = error.message ?: "Unknown error",
            )
        } finally {
            proxyPorts.close()
        }
    }

    private fun emitSnapshot(
        status: ScanStatus? = null,
        message: String? = null,
        error: String? = null,
        csvPath: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val elapsed = (now - scanStartedAtMs).coerceAtLeast(0)
        val scanned = scannedIps.get()
        val speed = if (elapsed > 0) scanned / (elapsed / 1_000.0) else 0.0

        _state.update { current ->
            val nextStatus = status ?: when {
                paused.value && current.status == ScanStatus.RUNNING -> ScanStatus.PAUSED
                !paused.value && current.status == ScanStatus.PAUSED -> ScanStatus.RUNNING
                else -> current.status
            }

            current.copy(
                status = nextStatus,
                config = currentConfig,
                stats = ScannerStats(
                    totalIps = totalIpsForProgress,
                    scannedIps = scanned,
                    foundDns = entries.size,
                    proxyPassed = proxyPassed.get(),
                    proxyFailed = proxyFailed.get(),
                    speedIpsPerSec = speed,
                    elapsedMs = elapsed,
                ),
                entries = sortedEntries(
                    entries = entries.values,
                    proxyEnabled = currentConfig.proxyTestEnabled,
                    proxyFeatureAvailable = proxyFeatureAvailable,
                ),
                testedBlocks = testedBlocksCount,
                autoShuffleCount = autoShuffleCount,
                proxyFeatureAvailable = proxyFeatureAvailable,
                lastMessage = message ?: current.lastMessage,
                csvPath = csvPath ?: latestCsvPath,
                error = error,
            )
        }
    }

    private suspend fun writeCsvFile(autoSave: Boolean): Result<String> {
        return withContext(ioDispatcher) {
            val export = buildCsvExport(
                entries = entries.values,
                proxyEnabled = currentConfig.proxyTestEnabled,
                proxyFeatureAvailable = proxyFeatureAvailable,
            )
            if (export.rows.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No rows matched export filter"))
            }

            val resultsDir = File(currentExportDir, "scanner_results")
            if (!resultsDir.exists() && !resultsDir.mkdirs()) {
                return@withContext Result.failure(IllegalStateException("Failed to create scanner_results directory"))
            }

            val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now())
            val prefix = if (autoSave) "auto" else "manual"
            val file = File(resultsDir, "${prefix}_scan_$timestamp.csv")

            runCatching {
                file.bufferedWriter().use { writer ->
                    writer.appendLine(export.headers.joinToString(",") { csvEscape(it) })
                    export.rows.forEach { row ->
                        writer.appendLine(row.joinToString(",") { csvEscape(it) })
                    }
                }
                file.absolutePath
            }
        }
    }

    private fun csvEscape(value: String): String {
        if (!value.contains(',') && !value.contains('"') && !value.contains('\n')) {
            return value
        }
        return buildString {
            append('"')
            value.forEach { ch ->
                if (ch == '"') append("\"\"") else append(ch)
            }
            append('"')
        }
    }
}
