package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.scanner.ProxyResultState
import com.masterdnsvpn.android.scanner.ScanPreset
import com.masterdnsvpn.android.scanner.ScanRecordType
import com.masterdnsvpn.android.scanner.ScanStatus
import com.masterdnsvpn.android.scanner.ScannerConfig
import com.masterdnsvpn.android.scanner.ScannerEntry
import com.masterdnsvpn.android.scanner.ScannerSessionState
import com.masterdnsvpn.android.ui.theme.MetricBadge
import com.masterdnsvpn.android.ui.theme.SectionTitle
import com.masterdnsvpn.android.ui.theme.StatusPill
import com.masterdnsvpn.android.ui.theme.VpnAppBackground
import com.masterdnsvpn.android.ui.theme.VpnCard
import com.masterdnsvpn.android.ui.theme.VpnHeroCard

const val SCANNER_START_TAG = "scanner_start"
const val SCANNER_BEST_APPLY_TAG = "scanner_best_apply"

@Composable
fun ScannerScreen(
    scannerConfig: ScannerConfig,
    scannerSession: ScannerSessionState,
    onScannerPickCidr: () -> Unit,
    onScannerDomainChanged: (String) -> Unit,
    onScannerRecordTypeChanged: (ScanRecordType) -> Unit,
    onScannerRandomSubdomainChanged: (Boolean) -> Unit,
    onScannerPresetChanged: (ScanPreset) -> Unit,
    onScannerConcurrencyChanged: (String) -> Unit,
    onScannerProxyEnabledChanged: (Boolean) -> Unit,
    onScannerSlipstreamPathChanged: (String) -> Unit,
    onScannerRemoteServerChanged: (String) -> Unit,
    onScannerRemoteNameChanged: (String) -> Unit,
    onScannerFetchRemoteProfile: () -> Unit,
    onScannerStart: () -> Unit,
    onScannerPause: () -> Unit,
    onScannerResume: () -> Unit,
    onScannerShuffle: () -> Unit,
    onScannerStop: () -> Unit,
    onScannerSave: () -> Unit,
    onScannerApplyDns: (String) -> Unit,
) {
    val isRunning = scannerSession.status == ScanStatus.RUNNING
    val isPaused = scannerSession.status == ScanStatus.PAUSED
    val canStart = !isRunning && !isPaused
    val canPause = isRunning
    val canResume = isPaused
    val canShuffle = isRunning || isPaused
    val canStop = isRunning || isPaused
    val canSave = scannerSession.entries.isNotEmpty()

    VpnAppBackground {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                VpnHeroCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.scanner_hero_title),
                        subtitle = stringResource(R.string.scanner_hero_subtitle),
                    )
                    StatusPill(
                        label = stringResource(R.string.scanner_status_pill, scannerSession.status.name),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBadge(
                            label = stringResource(R.string.scanner_summary_found),
                            value = scannerSession.stats.foundDns.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricBadge(
                            label = stringResource(R.string.scanner_summary_speed),
                            value = String.format("%.1f IP/s", scannerSession.stats.speedIpsPerSec),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = scannerSession.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    scannerSession.error?.let { error ->
                        Text(
                            text = stringResource(R.string.scanner_error_value, error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                VpnCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.scanner_setup_title),
                        subtitle = stringResource(R.string.scanner_setup_subtitle),
                    )
                    Text(
                        text = stringResource(R.string.scanner_cidr_value, scannerConfig.cidrLabel),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onScannerPickCidr,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.scanner_select_cidr))
                    }
                    OutlinedTextField(
                        value = scannerConfig.domain,
                        onValueChange = onScannerDomainChanged,
                        label = { Text(text = stringResource(R.string.scanner_domain_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = scannerConfig.concurrency.toString(),
                        onValueChange = onScannerConcurrencyChanged,
                        label = { Text(text = stringResource(R.string.scanner_concurrency_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.scanner_remote_profile_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedTextField(
                        value = scannerConfig.remoteProfileServer,
                        onValueChange = onScannerRemoteServerChanged,
                        label = { Text(text = stringResource(R.string.scanner_remote_profile_server_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = scannerConfig.remoteProfileName,
                        onValueChange = onScannerRemoteNameChanged,
                        label = { Text(text = stringResource(R.string.scanner_remote_profile_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onScannerFetchRemoteProfile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.scanner_remote_profile_fetch))
                    }
                    Text(
                        text = stringResource(R.string.scanner_record_type_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScanRecordType.entries) { type ->
                            FilterChip(
                                selected = scannerConfig.recordType == type,
                                onClick = { onScannerRecordTypeChanged(type) },
                                label = { Text(text = type.name) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.scanner_preset_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScanPreset.entries) { preset ->
                            FilterChip(
                                selected = scannerConfig.preset == preset,
                                onClick = { onScannerPresetChanged(preset) },
                                label = { Text(text = presetLabelRes(preset)) },
                            )
                        }
                    }
                    ToggleRow(
                        label = stringResource(R.string.scanner_random_subdomain_label),
                        checked = scannerConfig.randomSubdomain,
                        onCheckedChange = onScannerRandomSubdomainChanged,
                    )
                    ToggleRow(
                        label = stringResource(R.string.scanner_proxy_test_label),
                        checked = scannerConfig.proxyTestEnabled,
                        onCheckedChange = onScannerProxyEnabledChanged,
                    )
                    if (scannerConfig.proxyTestEnabled) {
                        OutlinedTextField(
                            value = scannerConfig.slipstreamBinaryPath,
                            onValueChange = onScannerSlipstreamPathChanged,
                            label = { Text(text = stringResource(R.string.scanner_slipstream_path_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!scannerSession.proxyFeatureAvailable) {
                            Text(
                                text = stringResource(R.string.scanner_proxy_unavailable),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item {
                VpnCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.scanner_run_controls_title),
                        subtitle = stringResource(R.string.scanner_run_controls_subtitle),
                    )
                    ActionRow(
                        leftLabel = stringResource(R.string.scanner_start),
                        leftEnabled = canStart,
                        leftModifier = Modifier.testTag(SCANNER_START_TAG),
                        leftAction = onScannerStart,
                        middleLabel = stringResource(R.string.scanner_pause),
                        middleEnabled = canPause,
                        middleAction = onScannerPause,
                        rightLabel = stringResource(R.string.scanner_resume),
                        rightEnabled = canResume,
                        rightAction = onScannerResume,
                    )
                    ActionRow(
                        leftLabel = stringResource(R.string.scanner_shuffle),
                        leftEnabled = canShuffle,
                        leftAction = onScannerShuffle,
                        middleLabel = stringResource(R.string.scanner_stop),
                        middleEnabled = canStop,
                        middleAction = onScannerStop,
                        rightLabel = stringResource(R.string.scanner_save_csv),
                        rightEnabled = canSave,
                        rightAction = onScannerSave,
                    )
                }
            }

            item {
                VpnCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.scanner_stats_title),
                        subtitle = stringResource(R.string.scanner_stats_subtitle),
                    )
                    Text(
                        text = stringResource(
                            R.string.scanner_stats_value,
                            scannerSession.stats.scannedIps,
                            scannerSession.stats.totalIps,
                            scannerSession.stats.foundDns,
                            scannerSession.stats.proxyPassed,
                            scannerSession.stats.proxyFailed,
                            scannerSession.stats.speedIpsPerSec,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    scannerSession.csvPath?.let { csvPath ->
                        Text(
                            text = stringResource(R.string.scanner_last_csv, csvPath),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            scannerSession.entries.firstOrNull()?.let { top ->
                item {
                    VpnCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(
                            title = stringResource(R.string.scanner_best_result_title),
                            subtitle = stringResource(R.string.scanner_best_dns, top.dns, top.pingMs),
                        )
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SCANNER_BEST_APPLY_TAG),
                            onClick = { onScannerApplyDns(top.dns) },
                        ) {
                            Text(text = stringResource(R.string.scanner_apply_to_tunnel))
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.scanner_results_title, scannerSession.entries.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (scannerSession.entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.scanner_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(
                items = scannerSession.entries,
                key = { entry -> "${entry.dns}-${entry.foundAt}" },
            ) { entry ->
                ScannerResultCard(
                    entry = entry,
                    onApplyDns = onScannerApplyDns,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    leftLabel: String,
    leftEnabled: Boolean,
    leftAction: () -> Unit,
    middleLabel: String,
    middleEnabled: Boolean,
    middleAction: () -> Unit,
    rightLabel: String,
    rightEnabled: Boolean,
    rightAction: () -> Unit,
    leftModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            modifier = leftModifier.weight(1f),
            onClick = leftAction,
            enabled = leftEnabled,
        ) {
            Text(text = leftLabel)
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = middleAction,
            enabled = middleEnabled,
        ) {
            Text(text = middleLabel)
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = rightAction,
            enabled = rightEnabled,
        ) {
            Text(text = rightLabel)
        }
    }
}

@Composable
private fun ScannerResultCard(
    entry: ScannerEntry,
    onApplyDns: (String) -> Unit,
) {
    val proxyLabel = when (entry.proxyResult) {
        ProxyResultState.PENDING -> stringResource(R.string.proxy_state_pending)
        ProxyResultState.TESTING -> stringResource(R.string.proxy_state_testing)
        ProxyResultState.SUCCESS -> stringResource(R.string.proxy_state_success)
        ProxyResultState.FAILED -> stringResource(R.string.proxy_state_failed)
        ProxyResultState.UNAVAILABLE -> stringResource(R.string.proxy_state_unavailable)
        null -> stringResource(R.string.proxy_state_na)
    }

    val ipVersion = when (entry.protocolInfo.ipv6) {
        true -> stringResource(R.string.scanner_ip_v4_v6)
        false -> stringResource(R.string.scanner_ip_v4)
        null -> stringResource(R.string.scanner_pending)
    }

    val security = entry.securityInfo?.let {
        buildString {
            if (it.dnssec) append("DNSSEC ")
            if (it.openResolver) append("Open ")
            if (it.hijacked) append("Hijacked")
            if (isBlank()) append("Secure")
        }.trim()
    } ?: stringResource(R.string.scanner_pending)

    val edns0 = when (entry.protocolInfo.edns0) {
        true -> stringResource(R.string.scanner_yes)
        false -> stringResource(R.string.scanner_no)
        null -> stringResource(R.string.scanner_pending)
    }

    VpnCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.dns,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.scanner_result_line,
                entry.dns,
                entry.pingMs,
                proxyLabel,
                ipVersion,
                entry.tcpUdp ?: stringResource(R.string.scanner_pending),
                security,
                edns0,
                entry.resolvedIp ?: stringResource(R.string.scanner_pending),
                entry.isp?.org ?: stringResource(R.string.scanner_pending),
            ),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onApplyDns(entry.dns) }) {
            Text(text = stringResource(R.string.scanner_apply))
        }
    }
}

@Composable
private fun presetLabelRes(preset: ScanPreset): String {
    val labelRes = when (preset) {
        ScanPreset.FAST -> R.string.scanner_preset_fast
        ScanPreset.DEEP -> R.string.scanner_preset_deep
        ScanPreset.FULL -> R.string.scanner_preset_full
    }
    return stringResource(id = labelRes)
}
