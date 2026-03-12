package com.masterdnsvpn.android

class HevTunnelEngine : TunnelEngine {
    @Synchronized
    override fun start(config: TunnelEngineConfig, onLogLine: (String) -> Unit): TunnelEngineResult {
        if (isRunning()) {
            return TunnelEngineResult(
                ok = false,
                code = "ALREADY_RUNNING",
                message = "Hev tunnel engine is already running",
            )
        }

        val yaml = runCatching { HevConfigBuilder.build(config) }.getOrElse { error ->
            return TunnelEngineResult(
                ok = false,
                code = "HEV_CONFIG_INVALID",
                message = "Failed to build Hev config: ${error.message}",
            )
        }

        onLogLine("[hev] Starting Hev tunnel runtime")
        val startResult = runCatching { HevNativeBridge.nativeStart(yaml, config.tunFd) }
            .getOrElse { error ->
                return TunnelEngineResult(
                    ok = false,
                    code = "HEV_NATIVE_START_EXCEPTION",
                    message = "Hev native start threw exception: ${error.message}",
                )
            }

        if (startResult != 0) {
            return TunnelEngineResult(
                ok = false,
                code = "HEV_NATIVE_START_FAILED",
                message = "Hev native start failed with code=$startResult",
            )
        }

        Thread.sleep(350)
        if (!isRunning()) {
            val nativeResult = runCatching { HevNativeBridge.nativeLastResult() }.getOrDefault(-1)
            return TunnelEngineResult(
                ok = false,
                code = "HEV_EXITED_EARLY",
                message = "Hev exited early with native result=$nativeResult",
            )
        }

        onLogLine("[hev] Hev tunnel runtime started")
        return TunnelEngineResult(
            ok = true,
            code = "STARTED",
            message = "Hev tunnel engine started",
        )
    }

    @Synchronized
    override fun stop(timeoutMs: Long): TunnelEngineResult {
        val stopResult = runCatching { HevNativeBridge.nativeStop() }.getOrElse { error ->
            return TunnelEngineResult(
                ok = false,
                code = "HEV_NATIVE_STOP_EXCEPTION",
                message = "Hev native stop threw exception: ${error.message}",
            )
        }

        if (stopResult != 0) {
            return TunnelEngineResult(
                ok = false,
                code = "HEV_NATIVE_STOP_FAILED",
                message = "Hev native stop failed with code=$stopResult",
            )
        }

        return TunnelEngineResult(
            ok = true,
            code = "STOPPED",
            message = "Hev tunnel engine stopped",
        )
    }

    @Synchronized
    override fun isRunning(): Boolean {
        return runCatching { HevNativeBridge.nativeIsRunning() }.getOrDefault(false)
    }
}
