package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.BuildConfig
import com.masterdnsvpn.android.MainUiState
import com.masterdnsvpn.android.ProfileQrCode
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.TunnelStatus
import com.masterdnsvpn.android.VpnMode

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
) {
    var qrProfilePayload by remember { mutableStateOf<String?>(null) }
    val primaryActionIsDisconnect = state.status in setOf(
        TunnelStatus.STARTING,
        TunnelStatus.CONNECTED,
        TunnelStatus.RECONNECTING,
        TunnelStatus.STOPPING,
    )

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_status_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_status_value,
                            stringResource(id = statusLabelRes(state.status)),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val routingLabel = if (state.config.vpnMode == VpnMode.SPLIT_ALLOWLIST) {
                        stringResource(R.string.home_routing_split)
                    } else {
                        stringResource(R.string.home_routing_full)
                    }
                    Text(
                        text = stringResource(R.string.home_routing_mode, routingLabel),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_build_marker,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.UI_BUILD_MARKER,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.config.vpnMode == VpnMode.SPLIT_ALLOWLIST) {
                        Text(
                            text = stringResource(
                                R.string.home_routing_apps,
                                state.config.splitAllowlistPackages.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.showTcpFirstWarning) {
                        Text(
                            text = stringResource(R.string.home_tcp_first_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.lastCode?.let { code ->
                        Text(
                            text = stringResource(R.string.home_status_code, code),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = if (primaryActionIsDisconnect) onDisconnect else onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HOME_PRIMARY_ACTION_TAG),
            ) {
                Text(
                    text = stringResource(
                        if (primaryActionIsDisconnect) R.string.action_disconnect else R.string.action_connect,
                    ),
                )
            }
        }

        if (state.validationErrors.isNotEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_validation_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.validationErrors.take(3).forEach { error ->
                            Text(
                                text = "- $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedButton(onClick = onOpenConfig) {
                            Text(text = stringResource(R.string.home_open_config))
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_quick_actions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedButton(onClick = onImportToml, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.action_import_toml))
                    }
                    OutlinedButton(onClick = onExportToml, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.action_export_toml))
                    }
                    OutlinedButton(onClick = onCopyProfile, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.action_copy_profile))
                    }
                    OutlinedButton(onClick = onImportProfile, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.action_import_profile))
                    }
                    OutlinedButton(
                        onClick = { qrProfilePayload = onExportProfileQr() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.action_show_profile_qr))
                    }
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
