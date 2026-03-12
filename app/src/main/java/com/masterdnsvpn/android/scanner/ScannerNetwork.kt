package com.masterdnsvpn.android.scanner

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.ExtendedFlags
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.OPTRecord
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.time.Duration
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

interface ScannerNetworkClient {
    suspend fun probeDns(
        dnsIp: String,
        domain: String,
        recordType: ScanRecordType,
        randomSubdomain: Boolean,
    ): DnsProbeResult

    suspend fun testTcpUdpSupport(dnsIp: String): String

    suspend fun testSecurity(dnsIp: String): SecurityInfo

    suspend fun testIpv6Support(dnsIp: String): Boolean

    suspend fun resolveARecord(dnsIp: String, scanType: ScanRecordType, scanDomain: String): String

    suspend fun testEdns0Support(dnsIp: String): Boolean

    suspend fun lookupIsp(dnsIp: String): IspInfo
}

class DnsJavaScannerNetworkClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: SecureRandom = SecureRandom(),
) : ScannerNetworkClient {

    private val ispRateLock = Mutex()
    private var ispNextAllowedAt = 0L

    override suspend fun probeDns(
        dnsIp: String,
        domain: String,
        recordType: ScanRecordType,
        randomSubdomain: Boolean,
    ): DnsProbeResult = withContext(ioDispatcher) {
        val queryDomain = if (randomSubdomain) {
            "${randomHexBytes(4)}.${domain.trim()}"
        } else {
            domain.trim()
        }

        val start = System.nanoTime()
        runCatching {
            val resolver = createResolver(dnsIp = dnsIp, timeoutMs = 2_000, tcp = false)
            val query = buildQuery(
                domain = queryDomain,
                type = recordType.toDnsType(),
                addOpt = false,
                doFlag = false,
            )
            resolver.send(query)
        }.fold(
            onSuccess = { response ->
                val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
                val classification = classifyDnsValidity(
                    elapsedMs = elapsedMs,
                    responded = true,
                    rcode = response.header.rcode,
                )
                DnsProbeResult(
                    dns = dnsIp,
                    valid = isDnsWorking(classification),
                    elapsedMs = if (isDnsWorking(classification)) elapsedMs else 0,
                    classification = classification,
                )
            },
            onFailure = {
                DnsProbeResult(
                    dns = dnsIp,
                    valid = false,
                    elapsedMs = 0,
                    classification = DnsValidityClassification.ERROR,
                )
            },
        )
    }

    override suspend fun testTcpUdpSupport(dnsIp: String): String = withContext(ioDispatcher) {
        val udpWorks = true
        var tcpWorks = false

        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(dnsIp, 53), 2_000)
                socket.soTimeout = 2_000
                val payload = buildQuery(
                    domain = "google.com",
                    type = Type.A,
                    addOpt = false,
                    doFlag = false,
                ).toWire()

                val out = DataOutputStream(socket.getOutputStream())
                out.writeShort(payload.size)
                out.write(payload)
                out.flush()

                val input = DataInputStream(socket.getInputStream())
                val responseLength = input.readUnsignedShort()
                if (responseLength > 0) {
                    val data = ByteArray(responseLength)
                    input.readFully(data)
                    if (data.size >= 12 && data[0] == payload[0] && data[1] == payload[1]) {
                        tcpWorks = true
                    }
                }
            }
        }

        when {
            tcpWorks && udpWorks -> "TCP/UDP"
            tcpWorks -> "TCP only"
            udpWorks -> "UDP only"
            else -> "None"
        }
    }

    override suspend fun testSecurity(dnsIp: String): SecurityInfo = withContext(ioDispatcher) {
        var dnssec = false
        var hijacked = false
        var openResolver = false

        runCatching {
            val resolver = createResolver(dnsIp = dnsIp, timeoutMs = 3_000, tcp = false)

            runCatching {
                val query = buildQuery(
                    domain = "example.com",
                    type = Type.A,
                    addOpt = true,
                    doFlag = true,
                )
                val response = resolver.send(query)
                openResolver = true
                val adFlag = response.header.getFlag(Flags.AD.toInt())
                val hasOpt = response.getSectionArray(Section.ADDITIONAL).any { it.type == Type.OPT }
                dnssec = adFlag || hasOpt
            }

            runCatching {
                val hijackDomain = "${randomHexBytes(6)}.example.invalid"
                val query = buildQuery(
                    domain = hijackDomain,
                    type = Type.A,
                    addOpt = false,
                    doFlag = false,
                )
                val response = resolver.send(query)
                openResolver = true
                val answers = response.getSectionArray(Section.ANSWER)
                hijacked = answers.isNotEmpty()
            }
        }

        SecurityInfo(
            dnssec = dnssec,
            hijacked = hijacked,
            openResolver = openResolver,
        )
    }

    override suspend fun testIpv6Support(dnsIp: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            val resolver = createResolver(dnsIp = dnsIp, timeoutMs = 3_000, tcp = false)
            val query = buildQuery(
                domain = "google.com",
                type = Type.AAAA,
                addOpt = false,
                doFlag = false,
            )

            val start = System.nanoTime()
            val response = resolver.send(query)
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
            elapsedMs < 3_000 && (response.header.rcode == 0 || response.header.rcode == 3)
        }.getOrDefault(false)
    }

    override suspend fun resolveARecord(
        dnsIp: String,
        scanType: ScanRecordType,
        scanDomain: String,
    ): String = withContext(ioDispatcher) {
        val target = if (scanType == ScanRecordType.A) {
            scanDomain.trim().ifBlank { "google.com" }
        } else {
            "google.com"
        }

        runCatching {
            val resolver = createResolver(dnsIp = dnsIp, timeoutMs = 3_000, tcp = false)
            val query = buildQuery(
                domain = target,
                type = Type.A,
                addOpt = false,
                doFlag = false,
            )
            val response = resolver.send(query)
            val ips = response.getSectionArray(Section.ANSWER)
                .mapNotNull { (it as? ARecord)?.address?.hostAddress }
            when {
                ips.isEmpty() -> "-"
                ips.size == 1 -> ips.first()
                else -> ips.take(2).joinToString(", ")
            }
        }.getOrDefault("-")
    }

    override suspend fun testEdns0Support(dnsIp: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            val resolver = createResolver(dnsIp = dnsIp, timeoutMs = 3_000, tcp = false)
            val query = buildQuery(
                domain = "example.com",
                type = Type.A,
                addOpt = true,
                doFlag = false,
            )
            val response = resolver.send(query)
            response.getSectionArray(Section.ADDITIONAL).any { it.type == Type.OPT }
        }.getOrDefault(false)
    }

    override suspend fun lookupIsp(dnsIp: String): IspInfo {
        val waitMs = ispRateLock.withLock {
            val now = System.currentTimeMillis()
            val wait = (ispNextAllowedAt - now).coerceAtLeast(0L)
            ispNextAllowedAt = maxOf(now, ispNextAllowedAt) + 1_400
            wait
        }

        if (waitMs > 0) {
            delay(waitMs)
        }

        return withContext(ioDispatcher) {
            val endpoint = "http://ip-api.com/json/$dnsIp?fields=as,org,isp,countryCode"
            var lastInfo = IspInfo()

            repeat(2) { attempt ->
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                }

                try {
                    val code = conn.responseCode
                    if (code == 429 && attempt == 0) {
                        delay(60_000)
                        return@repeat
                    }
                    if (code == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        lastInfo = IspInfo(
                            org = json.optString("org", "-").ifBlank { "-" },
                            isp = json.optString("isp", "-").ifBlank { "-" },
                            asn = json.optString("as", ""),
                            countryCode = json.optString("countryCode", ""),
                        )
                    }
                    return@withContext lastInfo
                } catch (_: Exception) {
                    // Return default info on failure.
                } finally {
                    conn.disconnect()
                }
            }

            lastInfo
        }
    }

    private fun createResolver(dnsIp: String, timeoutMs: Int, tcp: Boolean): SimpleResolver {
        return SimpleResolver(dnsIp).apply {
            setTimeout(Duration.ofMillis(timeoutMs.toLong()))
            setTCP(tcp)
            setIgnoreTruncation(true)
        }
    }

    private fun buildQuery(
        domain: String,
        type: Int,
        addOpt: Boolean,
        doFlag: Boolean,
    ): Message {
        val normalized = domain.trim().trimEnd('.')
        val name = Name.fromString("$normalized.")
        val query = Message.newQuery(Record.newRecord(name, type, DClass.IN))
        if (addOpt) {
            val flags = if (doFlag) ExtendedFlags.DO else 0
            val optRecord = OPTRecord(4_096, 0, flags)
            query.addRecord(optRecord, Section.ADDITIONAL)
        }
        return query
    }

    private fun ScanRecordType.toDnsType(): Int {
        return when (this) {
            ScanRecordType.A -> Type.A
            ScanRecordType.AAAA -> Type.AAAA
            ScanRecordType.MX -> Type.MX
            ScanRecordType.TXT -> Type.TXT
            ScanRecordType.NS -> Type.NS
        }
    }

    private fun randomHexBytes(size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { value ->
            String.format(Locale.US, "%02x", value.toInt() and 0xFF)
        }
    }
}

data class ProxyCapability(
    val available: Boolean,
    val reason: String = "",
)

interface SlipstreamProxyTester {
    suspend fun checkCapability(binaryPath: String): ProxyCapability

    suspend fun runProxyTest(
        dnsIp: String,
        domain: String,
        binaryPath: String,
        port: Int,
    ): ProxyResultState
}

class ProcessSlipstreamProxyTester(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SlipstreamProxyTester {

    override suspend fun checkCapability(binaryPath: String): ProxyCapability = withContext(ioDispatcher) {
        if (binaryPath.isBlank()) {
            return@withContext ProxyCapability(false, "Slipstream binary path is empty")
        }
        val file = File(binaryPath)
        if (!file.exists()) {
            return@withContext ProxyCapability(false, "Slipstream binary not found")
        }

        if (!file.canExecute()) {
            file.setExecutable(true)
        }

        runCatching {
            val process = ProcessBuilder(binaryPath, "--help")
                .redirectErrorStream(true)
                .start()
            val done = process.waitFor(2, TimeUnit.SECONDS)
            if (!done) {
                process.destroyForcibly()
            }
        }.fold(
            onSuccess = { ProxyCapability(true) },
            onFailure = { ProxyCapability(false, it.message ?: "Execution failed") },
        )
    }

    override suspend fun runProxyTest(
        dnsIp: String,
        domain: String,
        binaryPath: String,
        port: Int,
    ): ProxyResultState = withContext(ioDispatcher) {
        var process: Process? = null

        try {
            val command = listOf(
                binaryPath,
                "--resolver", "$dnsIp:53",
                "--resolver", "8.8.4.4:53",
                "--tcp-listen-port", port.toString(),
                "--domain", domain,
            )

            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val ready = waitForConnectionReady(process, timeoutMs = 15_000)
            if (!ready) {
                return@withContext ProxyResultState.FAILED
            }

            delay(2_500)

            if (testHttpsGetViaProxy(port, Proxy.Type.HTTP)) {
                return@withContext ProxyResultState.SUCCESS
            }
            if (testHttpsGetViaProxy(port, Proxy.Type.SOCKS)) {
                return@withContext ProxyResultState.SUCCESS
            }

            ProxyResultState.FAILED
        } catch (_: Exception) {
            ProxyResultState.FAILED
        } finally {
            process?.runCatching {
                destroyForcibly()
                waitFor(2, TimeUnit.SECONDS)
            }
        }
    }

    private fun waitForConnectionReady(process: Process, timeoutMs: Long): Boolean {
        val queue = LinkedBlockingQueue<String>()
        val readerThread = Thread {
            runCatching {
                InputStreamReader(process.inputStream).use { input ->
                    BufferedReader(input).forEachLine { line ->
                        queue.offer(line)
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) {
                return false
            }
            val line = queue.poll(250, TimeUnit.MILLISECONDS)
            if (line != null && line.contains("Connection ready", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun testHttpsGetViaProxy(port: Int, type: Proxy.Type): Boolean {
        return runCatching {
            val proxy = Proxy(type, InetSocketAddress("127.0.0.1", port))
            val connection = (URL("https://www.google.com").openConnection(proxy) as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = false
            }
            connection.use {
                val code = it.responseCode
                code == 200 || code == 301 || code == 302 || code == 307 || code == 308
            }
        }.getOrDefault(false)
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }
}
