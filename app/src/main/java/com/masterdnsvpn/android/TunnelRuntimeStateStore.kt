package com.masterdnsvpn.android

import android.content.Context
import java.time.Instant

data class TunnelRuntimeSnapshot(
    val desiredRunning: Boolean = false,
    val autoReconnect: Boolean = true,
    val configPath: String? = null,
    val status: TunnelStatus = TunnelStatus.IDLE,
    val statusMessage: String = "",
    val lastCode: String? = null,
    val lastTimestamp: String = Instant.EPOCH.toString(),
) {
    fun toStatusEvent(context: Context): TunnelEvent {
        val effectiveStatus = if (!desiredRunning && status != TunnelStatus.ERROR) {
            TunnelStatus.IDLE
        } else {
            status
        }
        val message = statusMessage.takeIf { it.isNotBlank() && effectiveStatus == status } ?: context.getString(
            tunnelStatusLabelRes(effectiveStatus),
        )
        return TunnelEvent(
            type = "status",
            status = effectiveStatus,
            level = if (effectiveStatus == TunnelStatus.ERROR) "ERROR" else "INFO",
            message = message,
            code = lastCode,
            timestamp = lastTimestamp,
        )
    }
}

class TunnelRuntimeStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): TunnelRuntimeSnapshot {
        val rawStatus = prefs.getString(KEY_LAST_STATUS, null)
        val status = rawStatus
            ?.let { runCatching { TunnelStatus.valueOf(it) }.getOrNull() }
            ?: TunnelStatus.IDLE

        return TunnelRuntimeSnapshot(
            desiredRunning = prefs.getBoolean(KEY_DESIRED_RUNNING, false),
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            configPath = prefs.getString(KEY_CONFIG_PATH, null),
            status = status,
            statusMessage = prefs.getString(KEY_LAST_STATUS_MESSAGE, "") ?: "",
            lastCode = prefs.getString(KEY_LAST_STATUS_CODE, null),
            lastTimestamp = prefs.getString(KEY_LAST_STATUS_TIMESTAMP, Instant.EPOCH.toString())
                ?: Instant.EPOCH.toString(),
        )
    }

    fun saveCommandState(
        desiredRunning: Boolean,
        autoReconnect: Boolean,
        configPath: String?,
    ) {
        prefs.edit()
            .putBoolean(KEY_DESIRED_RUNNING, desiredRunning)
            .putBoolean(KEY_AUTO_RECONNECT, autoReconnect)
            .putString(KEY_CONFIG_PATH, configPath)
            .commit()
    }

    fun saveStatus(event: TunnelEvent) {
        if (event.type != "status") {
            return
        }

        prefs.edit()
            .putString(KEY_LAST_STATUS, event.status?.name ?: TunnelStatus.ERROR.name)
            .putString(KEY_LAST_STATUS_MESSAGE, event.message)
            .putString(KEY_LAST_STATUS_CODE, event.code)
            .putString(
                KEY_LAST_STATUS_TIMESTAMP,
                event.timestamp.ifBlank { Instant.now().toString() },
            )
            .commit()
    }

    companion object {
        private const val PREFS_NAME = "tunnel_runtime_state_v1"
        private const val KEY_DESIRED_RUNNING = "desired_running"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_CONFIG_PATH = "config_path"
        private const val KEY_LAST_STATUS = "last_status"
        private const val KEY_LAST_STATUS_MESSAGE = "last_status_message"
        private const val KEY_LAST_STATUS_CODE = "last_status_code"
        private const val KEY_LAST_STATUS_TIMESTAMP = "last_status_timestamp"
    }
}
