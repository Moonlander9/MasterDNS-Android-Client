package com.masterdnsvpn.android

import android.content.Context
import androidx.annotation.StringRes
import com.masterdnsvpn.android.scanner.ProxyResultState
import com.masterdnsvpn.android.scanner.ScanPreset
import com.masterdnsvpn.android.scanner.ScanStatus
import com.masterdnsvpn.android.scanner.SecurityInfo

@StringRes
fun tunnelStatusLabelRes(status: TunnelStatus): Int {
    return when (status) {
        TunnelStatus.IDLE -> R.string.status_idle
        TunnelStatus.STARTING -> R.string.status_starting
        TunnelStatus.CONNECTED -> R.string.status_connected
        TunnelStatus.RECONNECTING -> R.string.status_reconnecting
        TunnelStatus.STOPPING -> R.string.status_stopping
        TunnelStatus.ERROR -> R.string.status_error
    }
}

@StringRes
fun scanStatusLabelRes(status: ScanStatus): Int {
    return when (status) {
        ScanStatus.IDLE -> R.string.scan_status_idle
        ScanStatus.RUNNING -> R.string.scan_status_running
        ScanStatus.PAUSED -> R.string.scan_status_paused
        ScanStatus.COMPLETED -> R.string.scan_status_completed
        ScanStatus.CANCELLED -> R.string.scan_status_cancelled
        ScanStatus.ERROR -> R.string.scan_status_error
    }
}

@StringRes
fun scanPresetLabelRes(preset: ScanPreset): Int {
    return when (preset) {
        ScanPreset.FAST -> R.string.scanner_preset_fast
        ScanPreset.DEEP -> R.string.scanner_preset_deep
        ScanPreset.FULL -> R.string.scanner_preset_full
    }
}

@StringRes
fun proxyResultLabelRes(result: ProxyResultState?): Int {
    return when (result) {
        ProxyResultState.PENDING -> R.string.proxy_state_pending
        ProxyResultState.TESTING -> R.string.proxy_state_testing
        ProxyResultState.SUCCESS -> R.string.proxy_state_success
        ProxyResultState.FAILED -> R.string.proxy_state_failed
        ProxyResultState.UNAVAILABLE -> R.string.proxy_state_unavailable
        null -> R.string.proxy_state_na
    }
}

fun Context.localizedScanStatus(status: ScanStatus): String = getString(scanStatusLabelRes(status))

fun Context.localizedProxyResult(result: ProxyResultState?): String = getString(proxyResultLabelRes(result))

fun Context.localizedIpVersion(ipv6: Boolean?): String {
    return when (ipv6) {
        true -> getString(R.string.scanner_ip_v4_v6)
        false -> getString(R.string.scanner_ip_v4)
        null -> getString(R.string.scanner_pending)
    }
}

fun Context.localizedEdns0(enabled: Boolean?): String {
    return when (enabled) {
        true -> getString(R.string.scanner_yes)
        false -> getString(R.string.scanner_no)
        null -> getString(R.string.scanner_pending)
    }
}

fun Context.localizedTransportLabel(value: String?): String {
    return when (value) {
        "TCP/UDP" -> getString(R.string.scanner_transport_tcp_udp)
        "TCP only" -> getString(R.string.scanner_transport_tcp_only)
        "UDP only" -> getString(R.string.scanner_transport_udp_only)
        "None" -> getString(R.string.scanner_transport_none)
        null, "" -> getString(R.string.scanner_pending)
        else -> value
    }
}

fun Context.localizedSecuritySummary(info: SecurityInfo?): String {
    if (info == null) {
        return getString(R.string.scanner_pending)
    }

    val labels = buildList {
        if (info.dnssec) add(getString(R.string.scanner_security_dnssec))
        if (info.openResolver) add(getString(R.string.scanner_security_open_resolver))
        if (info.hijacked) add(getString(R.string.scanner_security_hijacked))
    }

    return if (labels.isEmpty()) {
        getString(R.string.scanner_security_secure)
    } else {
        labels.joinToString(separator = "، ")
    }
}

data class RemoteProfileRequestMessages(
    val serverAddressRequired: String,
    val serverMustUseHttpOrHttps: String,
    val invalidServerAddress: String,
    val serverAddressMustNotIncludeQueryOrFragment: String,
    val profileNameRequired: String,
)

fun Context.remoteProfileRequestMessages(): RemoteProfileRequestMessages {
    return RemoteProfileRequestMessages(
        serverAddressRequired = getString(R.string.error_server_address_required),
        serverMustUseHttpOrHttps = getString(R.string.error_server_must_use_http_or_https),
        invalidServerAddress = getString(R.string.error_invalid_server_address),
        serverAddressMustNotIncludeQueryOrFragment =
            getString(R.string.error_server_address_query_fragment_not_allowed),
        profileNameRequired = getString(R.string.error_profile_name_required),
    )
}
