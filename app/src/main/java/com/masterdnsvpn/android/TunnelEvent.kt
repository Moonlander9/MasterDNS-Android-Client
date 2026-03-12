package com.masterdnsvpn.android

import java.time.Instant

enum class TunnelStatus {
    IDLE,
    STARTING,
    CONNECTED,
    RECONNECTING,
    STOPPING,
    ERROR,
}

data class TunnelEvent(
    val type: String,
    val status: TunnelStatus? = null,
    val level: String = "INFO",
    val message: String = "",
    val code: String? = null,
    val timestamp: String = Instant.now().toString(),
)
