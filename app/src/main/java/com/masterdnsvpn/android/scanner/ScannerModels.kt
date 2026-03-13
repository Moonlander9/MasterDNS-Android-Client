package com.masterdnsvpn.android.scanner

import java.time.Instant

const val BUNDLED_CIDR_ASSET_PATH = "scanner/iran-ipv4.cidrs"
const val BUNDLED_CIDR_URI = "asset://$BUNDLED_CIDR_ASSET_PATH"
const val BUNDLED_CIDR_LABEL = "Bundled Iran CIDRs (PYDNS)"

enum class ScanPreset(val maxIps: Int, val autoShuffleThreshold: Int) {
    FAST(maxIps = 25_000, autoShuffleThreshold = 500),
    DEEP(maxIps = 50_000, autoShuffleThreshold = 1_000),
    FULL(maxIps = 0, autoShuffleThreshold = 3_000),
}

enum class ScanRecordType {
    A,
    AAAA,
    MX,
    TXT,
    NS,
}

enum class ScanStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR,
}

enum class ProxyResultState {
    PENDING,
    TESTING,
    SUCCESS,
    FAILED,
    UNAVAILABLE,
}

data class ScannerConfig(
    val cidrUri: String = BUNDLED_CIDR_URI,
    val cidrLabel: String = BUNDLED_CIDR_LABEL,
    val domain: String = "google.com",
    val recordType: ScanRecordType = ScanRecordType.A,
    val randomSubdomain: Boolean = false,
    val preset: ScanPreset = ScanPreset.FAST,
    val concurrency: Int = 120,
    val proxyTestEnabled: Boolean = false,
    val slipstreamBinaryPath: String = "",
    val remoteProfileServer: String = "",
    val remoteProfileName: String = "",
)

data class SecurityInfo(
    val dnssec: Boolean = false,
    val hijacked: Boolean = false,
    val openResolver: Boolean = false,
)

data class ProtocolInfo(
    val ipv6: Boolean? = null,
    val edns0: Boolean? = null,
)

data class IspInfo(
    val org: String = "-",
    val isp: String = "-",
    val asn: String = "",
    val countryCode: String = "",
)

data class ScannerEntry(
    val dns: String,
    val pingMs: Int,
    val proxyResult: ProxyResultState? = null,
    val protocolInfo: ProtocolInfo = ProtocolInfo(),
    val securityInfo: SecurityInfo? = null,
    val tcpUdp: String? = null,
    val resolvedIp: String? = null,
    val isp: IspInfo? = null,
    val foundAt: Instant = Instant.now(),
)

data class ScannerStats(
    val totalIps: Int = 0,
    val scannedIps: Int = 0,
    val foundDns: Int = 0,
    val proxyPassed: Int = 0,
    val proxyFailed: Int = 0,
    val speedIpsPerSec: Double = 0.0,
    val elapsedMs: Long = 0,
)

data class ScannerSessionState(
    val status: ScanStatus = ScanStatus.IDLE,
    val config: ScannerConfig = ScannerConfig(),
    val stats: ScannerStats = ScannerStats(),
    val entries: List<ScannerEntry> = emptyList(),
    val testedBlocks: Int = 0,
    val autoShuffleCount: Int = 0,
    val proxyFeatureAvailable: Boolean = true,
    val lastMessage: String = "Idle",
    val csvPath: String? = null,
    val error: String? = null,
)

data class DnsProbeResult(
    val dns: String,
    val valid: Boolean,
    val elapsedMs: Int,
    val classification: DnsValidityClassification,
)

enum class DnsValidityClassification {
    SUCCESS,
    NXDOMAIN_OR_NODATA,
    REJECTED_LATENCY,
    ERROR,
}
