package com.masterdnsvpn.android.scanner

import java.time.Instant

private const val DNS_TIMEOUT_MS = 2_000

fun classifyDnsValidity(
    elapsedMs: Int,
    responded: Boolean,
    rcode: Int?,
): DnsValidityClassification {
    if (!responded) {
        return DnsValidityClassification.ERROR
    }
    if (elapsedMs >= DNS_TIMEOUT_MS) {
        return DnsValidityClassification.REJECTED_LATENCY
    }

    return when (rcode) {
        null -> DnsValidityClassification.SUCCESS
        0 -> DnsValidityClassification.SUCCESS
        3 -> DnsValidityClassification.NXDOMAIN_OR_NODATA
        else -> DnsValidityClassification.ERROR
    }
}

fun isDnsWorking(classification: DnsValidityClassification): Boolean {
    return classification == DnsValidityClassification.SUCCESS ||
        classification == DnsValidityClassification.NXDOMAIN_OR_NODATA
}

fun reachedPresetLimit(preset: ScanPreset, scanned: Int): Boolean {
    if (preset.maxIps <= 0) {
        return false
    }
    return scanned >= preset.maxIps
}

class AutoShuffleController(
    private val preset: ScanPreset,
    private val proxyModeEnabled: Boolean,
) {
    var consecutiveMisses: Int = 0
        private set

    var autoShuffleCount: Int = 0
        private set

    private var proxyPassedAtLastDecision: Int = 0
    private var foundAtLastDecision: Int = 0

    fun onFound(currentFound: Int, currentProxyPassed: Int) {
        consecutiveMisses = 0
        // Keep baseline current so suppression logic remains tied to progress windows.
        foundAtLastDecision = maxOf(foundAtLastDecision, currentFound)
        proxyPassedAtLastDecision = maxOf(proxyPassedAtLastDecision, currentProxyPassed)
    }

    fun onMiss(
        paused: Boolean,
        currentFound: Int,
        currentProxyPassed: Int,
    ): Boolean {
        consecutiveMisses += 1
        if (paused) {
            return false
        }
        if (preset.autoShuffleThreshold <= 0 || consecutiveMisses < preset.autoShuffleThreshold) {
            return false
        }

        val suppressed = when {
            preset == ScanPreset.FAST && proxyModeEnabled -> {
                currentProxyPassed > proxyPassedAtLastDecision
            }
            preset == ScanPreset.FULL -> {
                currentFound > foundAtLastDecision
            }
            else -> false
        }

        if (suppressed) {
            consecutiveMisses = 0
            proxyPassedAtLastDecision = currentProxyPassed
            foundAtLastDecision = currentFound
            return false
        }

        autoShuffleCount += 1
        consecutiveMisses = 0
        proxyPassedAtLastDecision = currentProxyPassed
        foundAtLastDecision = currentFound
        return true
    }
}

fun isEntryFinalized(
    entry: ScannerEntry,
    proxyEnabled: Boolean,
    proxyFeatureAvailable: Boolean,
): Boolean {
    if (entry.tcpUdp == null) return false
    if (entry.securityInfo == null) return false
    if (entry.protocolInfo.ipv6 == null) return false
    if (entry.protocolInfo.edns0 == null) return false
    if (entry.resolvedIp == null) return false
    if (entry.isp == null) return false
    if (!proxyEnabled) return true

    if (!proxyFeatureAvailable) {
        return entry.proxyResult == ProxyResultState.UNAVAILABLE
    }

    return entry.proxyResult == ProxyResultState.SUCCESS || entry.proxyResult == ProxyResultState.FAILED
}

fun sortedEntries(
    entries: Collection<ScannerEntry>,
    proxyEnabled: Boolean,
    proxyFeatureAvailable: Boolean,
): List<ScannerEntry> {
    val comparator = compareBy<ScannerEntry>(
        {
            if (!proxyEnabled || !proxyFeatureAvailable) {
                0
            } else if (it.proxyResult == ProxyResultState.SUCCESS) {
                0
            } else {
                1
            }
        },
        {
            if (it.securityInfo?.dnssec == true) 0 else 1
        },
        { it.pingMs },
        { it.dns },
    )

    val finalized = entries.filter { isEntryFinalized(it, proxyEnabled, proxyFeatureAvailable) }.sortedWith(comparator)
    val testing = entries.filterNot { isEntryFinalized(it, proxyEnabled, proxyFeatureAvailable) }.sortedWith(comparator)
    return finalized + testing
}

data class CsvExport(
    val headers: List<String>,
    val rows: List<List<String>>,
)

fun buildCsvExport(
    entries: Collection<ScannerEntry>,
    proxyEnabled: Boolean,
    proxyFeatureAvailable: Boolean,
): CsvExport {
    val filtered = if (proxyEnabled && proxyFeatureAvailable) {
        entries.filter { it.proxyResult == ProxyResultState.SUCCESS }
    } else {
        entries
    }

    val sorted = sortedEntries(filtered, proxyEnabled = proxyEnabled, proxyFeatureAvailable = proxyFeatureAvailable)
    val headers = buildList {
        add("DNS")
        add("Ping (ms)")
        if (proxyEnabled) add("Proxy Test")
        add("IPv4/IPv6")
        add("TCP/UDP")
        add("Security")
        add("EDNS0")
        add("Resolved IP")
        add("ISP")
    }

    val rows = sorted.map { entry ->
        buildList {
            add(entry.dns)
            add(entry.pingMs.toString())
            if (proxyEnabled) {
                add(
                    when (entry.proxyResult) {
                        ProxyResultState.PENDING -> "Pending"
                        ProxyResultState.TESTING -> "Testing"
                        ProxyResultState.SUCCESS -> "Success"
                        ProxyResultState.FAILED -> "Failed"
                        ProxyResultState.UNAVAILABLE -> "Unavailable"
                        null -> "N/A"
                    },
                )
            }
            add(if (entry.protocolInfo.ipv6 == true) "IPv4/IPv6" else "IPv4")
            add(entry.tcpUdp ?: "")
            add(
                entry.securityInfo?.let {
                    val flags = mutableListOf<String>()
                    if (it.dnssec) flags += "DNSSEC"
                    if (it.openResolver) flags += "Open Resolver"
                    if (it.hijacked) flags += "Hijacked"
                    if (flags.isEmpty()) "Secure" else flags.joinToString(", ")
                } ?: "",
            )
            add(
                when (entry.protocolInfo.edns0) {
                    true -> "Yes"
                    false -> "No"
                    null -> ""
                },
            )
            add(entry.resolvedIp ?: "")
            add(entry.isp?.org?.takeUnless { it == "-" } ?: "")
        }
    }

    return CsvExport(headers = headers, rows = rows)
}

fun successfulResolversFromEntries(
    entries: Collection<ScannerEntry>,
    proxyEnabled: Boolean,
    proxyFeatureAvailable: Boolean,
): List<String> {
    val filtered = if (proxyEnabled && proxyFeatureAvailable) {
        entries.filter { it.proxyResult == ProxyResultState.SUCCESS }
    } else {
        entries
    }

    return sortedEntries(
        entries = filtered,
        proxyEnabled = proxyEnabled,
        proxyFeatureAvailable = proxyFeatureAvailable,
    ).map { it.dns }
        .distinct()
}

fun ScannerEntry.withUpdate(
    proxyResult: ProxyResultState? = this.proxyResult,
    protocolInfo: ProtocolInfo = this.protocolInfo,
    securityInfo: SecurityInfo? = this.securityInfo,
    tcpUdp: String? = this.tcpUdp,
    resolvedIp: String? = this.resolvedIp,
    isp: IspInfo? = this.isp,
): ScannerEntry {
    return copy(
        proxyResult = proxyResult,
        protocolInfo = protocolInfo,
        securityInfo = securityInfo,
        tcpUdp = tcpUdp,
        resolvedIp = resolvedIp,
        isp = isp,
        foundAt = Instant.now(),
    )
}
