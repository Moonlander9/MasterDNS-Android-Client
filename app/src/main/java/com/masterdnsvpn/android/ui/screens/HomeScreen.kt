package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.MainUiState
import com.masterdnsvpn.android.ProfileQrCode
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.TunnelStatus
import com.masterdnsvpn.android.ui.theme.StatusPill
import com.masterdnsvpn.android.ui.theme.VpnAppBackground

const val HOME_PRIMARY_ACTION_TAG = "home_primary_action"
const val HOME_REMOTE_PROFILE_ACTION_TAG = "home_remote_profile_action"

@Composable
fun HomeScreen(
    state: MainUiState,
    remoteProfileConfigured: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemoteProfileAction: () -> Unit,
) {
    val primaryActionIsDisconnect = state.status in setOf(
        TunnelStatus.STARTING,
        TunnelStatus.CONNECTED,
        TunnelStatus.RECONNECTING,
        TunnelStatus.STOPPING,
    )

    VpnAppBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusPill(
                label = stringResource(
                    R.string.home_status_pill,
                    stringResource(id = statusLabelRes(state.status)),
                ),
                containerColor = statusContainerColor(state.status),
                contentColor = statusContentColor(state.status),
            )
            Text(
                text = stringResource(R.string.home_simple_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            ConnectionActionButton(
                status = state.status,
                onClick = if (primaryActionIsDisconnect) onDisconnect else onConnect,
                modifier = Modifier.testTag(HOME_PRIMARY_ACTION_TAG),
            )
            OutlinedButton(
                onClick = onRemoteProfileAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HOME_REMOTE_PROFILE_ACTION_TAG),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_fetch_profile),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(
                    if (remoteProfileConfigured) {
                        R.string.home_remote_profile_ready
                    } else {
                        R.string.home_remote_profile_setup_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            state.lastCode?.let { code ->
                Text(
                    text = stringResource(R.string.home_status_code, code),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.validationErrors.isNotEmpty()) {
                Text(
                    text = state.validationErrors.first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ConnectionActionButton(
    status: TunnelStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionColors = connectionActionColors(status)
    Surface(
        modifier = modifier
            .size(240.dp)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = actionColors.outer,
        border = androidx.compose.foundation.BorderStroke(1.dp, actionColors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(actionColors.inner, actionColors.outer),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = actionEmojiRes(status)),
                    style = MaterialTheme.typography.displaySmall,
                    color = actionColors.content,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(id = actionTitleRes(status)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = actionColors.content,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(id = actionSubtitleRes(status)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = actionColors.content.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ProfileQrDialog(
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

private data class ConnectionActionColors(
    val outer: Color,
    val inner: Color,
    val border: Color,
    val content: Color,
)

@Composable
private fun connectionActionColors(status: TunnelStatus): ConnectionActionColors {
    return when (status) {
        TunnelStatus.CONNECTED -> ConnectionActionColors(
            outer = Color(0xFF0E3D36),
            inner = Color(0xFF31D4B6),
            border = Color(0x804AE3C6),
            content = Color(0xFFF3FFFC),
        )
        TunnelStatus.STARTING, TunnelStatus.RECONNECTING -> ConnectionActionColors(
            outer = Color(0xFF183454),
            inner = Color(0xFF5FA8FF),
            border = Color(0x80A8CCFF),
            content = Color(0xFFF2F7FF),
        )
        TunnelStatus.STOPPING -> ConnectionActionColors(
            outer = Color(0xFF43311A),
            inner = Color(0xFFFFC980),
            border = Color(0x80FFD6A0),
            content = Color(0xFFFFFAF2),
        )
        TunnelStatus.ERROR -> ConnectionActionColors(
            outer = Color(0xFF4D1B22),
            inner = Color(0xFFFF8E8E),
            border = Color(0x80FFB6B6),
            content = Color(0xFFFFF5F5),
        )
        TunnelStatus.IDLE -> ConnectionActionColors(
            outer = Color(0xFF0F2135),
            inner = Color(0xFF163451),
            border = Color(0x804AE3C6),
            content = Color(0xFFF2F7FF),
        )
    }
}

private fun actionTitleRes(status: TunnelStatus): Int {
    return when (status) {
        TunnelStatus.CONNECTED -> R.string.home_circle_connected
        TunnelStatus.STARTING, TunnelStatus.RECONNECTING -> R.string.home_circle_connecting
        TunnelStatus.STOPPING -> R.string.home_circle_stopping
        TunnelStatus.ERROR, TunnelStatus.IDLE -> R.string.action_connect
    }
}

private fun actionEmojiRes(status: TunnelStatus): Int {
    return when (status) {
        TunnelStatus.CONNECTED -> R.string.home_circle_emoji_connected
        TunnelStatus.STARTING, TunnelStatus.RECONNECTING, TunnelStatus.STOPPING -> R.string.home_circle_emoji_connecting
        TunnelStatus.ERROR -> R.string.home_circle_emoji_error
        TunnelStatus.IDLE -> R.string.home_circle_emoji_idle
    }
}

private fun actionSubtitleRes(status: TunnelStatus): Int {
    return when (status) {
        TunnelStatus.CONNECTED -> R.string.home_circle_disconnect_hint
        TunnelStatus.STARTING, TunnelStatus.RECONNECTING -> R.string.home_circle_wait_hint
        TunnelStatus.STOPPING -> R.string.home_circle_wait_hint
        TunnelStatus.ERROR -> R.string.home_circle_retry_hint
        TunnelStatus.IDLE -> R.string.home_circle_connect_hint
    }
}
