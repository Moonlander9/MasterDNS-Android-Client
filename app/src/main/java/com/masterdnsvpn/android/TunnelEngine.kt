package com.masterdnsvpn.android

data class TunnelEngineConfig(
    val tunFd: Int,
    val socksHost: String,
    val socksPort: Int,
    val socksUser: String? = null,
    val socksPass: String? = null,
    val mtu: Int,
    val tunIpv4Address: String = "10.42.0.2",
    val tunIpv6Address: String = "fd42:4242::2",
)

data class TunnelEngineResult(
    val ok: Boolean,
    val code: String,
    val message: String,
)

interface TunnelEngine {
    fun start(config: TunnelEngineConfig, onLogLine: (String) -> Unit): TunnelEngineResult

    fun stop(timeoutMs: Long = 3_000L): TunnelEngineResult

    fun isRunning(): Boolean
}
