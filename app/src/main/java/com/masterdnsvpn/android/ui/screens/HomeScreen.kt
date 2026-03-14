package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.BuildConfig
import com.masterdnsvpn.android.MainUiState
import com.masterdnsvpn.android.ProfileQrCode
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.TunnelStatus
import com.masterdnsvpn.android.VpnMode
import com.masterdnsvpn.android.ui.theme.MetricBadge
import com.masterdnsvpn.android.ui.theme.SectionTitle
import com.masterdnsvpn.android.ui.theme.StatusPill
import com.masterdnsvpn.android.ui.theme.VpnAppBackground
import com.masterdnsvpn.android.ui.theme.VpnCard
import com.masterdnsvpn.android.ui.theme.VpnHeroCard

const val HOME_PRIMARY_ACTION_TAG = "home_primary_action"

@Composable
fun HomeScreen(
    state: MainUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImportToml: () -> Unit,
    onExportToml: () -> Unit,
    onCopyProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onExportProfileQr: () -> String,
    onOpenConfig: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    var qrProfilePayload by remember { mutableStateOf<String?>(null) }
    val primaryActionIsDisconnect = state.status in setOf(
        TunnelStatus.STARTING,
        TunnelStatus.CONNECTED,
        TunnelStatus.RECONNECTING,
        TunnelStatus.STOPPING,
    )
    val routingLabel = if (state.config.vpnMode == VpnMode.SPLIT_ALLOWLIST) {
        stringResource(R.string.home_routing_split)
    } else {
        stringResource(R.string.home_routing_full)
    }

    VpnAppBackground {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                VpnHeroCard(modifier = Modifier.fillMaxWidth()) {
                    StatusPill(
                        label = stringResource(
                            R.string.home_status_pill,
                            stringResource(id = statusLabelRes(state.status)),
                        ),
                        containerColor = statusContainerColor(state.status),
                        contentColor = statusContentColor(state.status),
                    )
                    Text(
                        text = stringResource(R.string.home_hero_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBadge(
                            label = stringResource(R.string.home_routing_metric),
                            value = routingLabel,
                            modifier = Modifier.weight(1f),
                        )
                        MetricBadge(
                            label = stringResource(R.string.home_dns_metric),
                            value = state.config.resolverDnsServers.firstOrNull() ?: "--",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBadge(
                            label = stringResource(R.string.home_apps_metric),
                            value = state.config.splitAllowlistPackages.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricBadge(
                            label = stringResource(R.string.home_build_metric),
                            value = BuildConfig.VERSION_NAME,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = if (primaryActionIsDisconnect) onDisconnect else onConnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag(HOME_PRIMARY_ACTION_TAG),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (primaryActionIsDisconnect) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (primaryActionIsDisconnect) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                if (primaryActionIsDisconnect) R.string.action_disconnect else R.string.action_connect,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    state.lastCode?.let { code ->
                        Text(
                            text = stringResource(R.string.home_status_code, code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.validationErrors.isNotEmpty()) {
                item {
                    VpnCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(
                            title = stringResource(R.string.home_validation_title),
                            subtitle = stringResource(R.string.home_validation_subtitle),
                        )
                        state.validationErrors.take(3).forEach { error ->
                            Text(
                                text = "• $error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedButton(onClick = onOpenConfig) {
                            Text(text = stringResource(R.string.home_open_config))
                        }
                    }
                }
            }

            if (state.showTcpFirstWarning) {
                item {
                    VpnCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(title = stringResource(R.string.home_transport_title))
                        Text(
                            text = stringResource(R.string.home_tcp_first_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                VpnCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.home_navigation_title),
                        subtitle = stringResource(R.string.home_navigation_subtitle),
                    )
                    NavigationShortcutRow(
                        leftTitle = stringResource(R.string.tab_config),
                        leftSubtitle = stringResource(R.string.home_navigation_config),
                        leftAction = onOpenConfig,
                        rightTitle = stringResource(R.string.tab_routing),
                        rightSubtitle = stringResource(R.string.home_navigation_routing),
                        rightAction = onOpenRouting,
                    )
                    NavigationShortcutRow(
                        leftTitle = stringResource(R.string.tab_scanner),
                        leftSubtitle = stringResource(R.string.home_navigation_scanner),
                        leftAction = onOpenScanner,
                        rightTitle = stringResource(R.string.tab_logs),
                        rightSubtitle = stringResource(R.string.home_navigation_logs, state.logs.size),
                        rightAction = onOpenLogs,
                    )
                }
            }

            item {
                VpnCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.home_quick_actions),
                        subtitle = stringResource(R.string.home_quick_actions_subtitle),
                    )
                    QuickActionRow(
                        leftLabel = stringResource(R.string.action_import_toml),
                        leftAction = onImportToml,
                        rightLabel = stringResource(R.string.action_export_toml),
                        rightAction = onExportToml,
                    )
                    QuickActionRow(
                        leftLabel = stringResource(R.string.action_copy_profile),
                        leftAction = onCopyProfile,
                        rightLabel = stringResource(R.string.action_import_profile),
                        rightAction = onImportProfile,
                    )
                    OutlinedButton(
                        onClick = { qrProfilePayload = onExportProfileQr() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.action_show_profile_qr))
                    }
                }
            }

            item {
                VpnCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.home_tunnel_details_title),
                        subtitle = stringResource(
                            R.string.home_build_marker,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.UI_BUILD_MARKER,
                        ),
                    )
                    DetailRow(
                        label = stringResource(R.string.home_routing_metric),
                        value = routingLabel,
                    )
                    DetailRow(
                        label = stringResource(R.string.home_domains_metric),
                        value = state.config.domains.joinToString(),
                    )
                    DetailRow(
                        label = stringResource(R.string.home_resolvers_metric),
                        value = state.config.resolverDnsServers.joinToString(),
                    )
                }
            }
        }
    }

    qrProfilePayload?.let { profile ->
        ProfileQrDialog(
            profile = profile,
            onDismiss = { qrProfilePayload = null },
        )
    }
}

@Composable
private fun QuickActionRow(
    leftLabel: String,
    leftAction: () -> Unit,
    rightLabel: String,
    rightAction: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = leftAction,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = leftLabel, textAlign = TextAlign.Center)
        }
        OutlinedButton(
            onClick = rightAction,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = rightLabel, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NavigationShortcutRow(
    leftTitle: String,
    leftSubtitle: String,
    leftAction: () -> Unit,
    rightTitle: String,
    rightSubtitle: String,
    rightAction: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NavigationShortcutTile(
            title = leftTitle,
            subtitle = leftSubtitle,
            onClick = leftAction,
            modifier = Modifier.weight(1f),
        )
        NavigationShortcutTile(
            title = rightTitle,
            subtitle = rightSubtitle,
            onClick = rightAction,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavigationShortcutTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VpnCard(
        modifier = modifier.clickable(onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProfileQrDialog(
    profile: String,
    onDismiss: () -> Unit,
) {
    val qrBitmap = remember(profile) {
        runCatching { ProfileQrCode.encode(profile = profile) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close))
            }
        },
        title = { Text(text = stringResource(R.string.profile_qr_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (qrBitmap == null) {
                    Text(
                        text = stringResource(R.string.profile_qr_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.profile_qr_content_description),
                            modifier = Modifier.size(280.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

@Composable
private fun statusContainerColor(status: TunnelStatus): Color {
    return when (status) {
        TunnelStatus.CONNECTED -> Color(0x3329D6AE)
        TunnelStatus.STARTING, TunnelStatus.RECONNECTING -> Color(0x335FA8FF)
        TunnelStatus.STOPPING -> Color(0x33FFC980)
        TunnelStatus.ERROR -> Color(0x33FF8E8E)
        TunnelStatus.IDLE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    }
}

@Composable
private fun statusContentColor(status: TunnelStatus): Color {
    return when (status) {
        TunnelStatus.CONNECTED -> Color(0xFF8FF4DD)
        TunnelStatus.STARTING, TunnelStatus.RECONNECTING -> Color(0xFFBBD1FF)
        TunnelStatus.STOPPING -> Color(0xFFFFD8A3)
        TunnelStatus.ERROR -> Color(0xFFFFB4B4)
        TunnelStatus.IDLE -> MaterialTheme.colorScheme.onSecondaryContainer
    }
}

private fun statusLabelRes(status: TunnelStatus): Int {
    return when (status) {
        TunnelStatus.IDLE -> R.string.status_idle
        TunnelStatus.STARTING -> R.string.status_starting
        TunnelStatus.CONNECTED -> R.string.status_connected
        TunnelStatus.RECONNECTING -> R.string.status_reconnecting
        TunnelStatus.STOPPING -> R.string.status_stopping
        TunnelStatus.ERROR -> R.string.status_error
    }
}
