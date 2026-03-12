package com.masterdnsvpn.android

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class PythonBridge(private val context: Context) {

    data class BridgeResult(
        val ok: Boolean,
        val code: String,
        val message: String,
    )

    private fun ensurePython(): Python {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        return Python.getInstance()
    }

    private fun module(): PyObject {
        return ensurePython().getModule("android_client_bridge")
    }

    private fun pyDictToStringMap(value: PyObject): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((k, v) in value.asMap()) {
            result[k.toString()] = v.toString()
        }
        return result
    }

    fun startClient(configPath: String): BridgeResult {
        return runCatching {
            val raw = module().callAttr("start_client", configPath)
            val map = pyDictToStringMap(raw)
            BridgeResult(
                ok = map["ok"].toBoolean(),
                code = map["code"] ?: "UNKNOWN",
                message = map["message"] ?: "",
            )
        }.getOrElse { error ->
            BridgeResult(
                ok = false,
                code = "PYTHON_START_EXCEPTION",
                message = error.message ?: "Python start failed unexpectedly",
            )
        }
    }

    fun stopClient(timeoutSeconds: Double = 10.0): BridgeResult {
        return runCatching {
            val raw = module().callAttr("stop_client", timeoutSeconds)
            val map = pyDictToStringMap(raw)
            BridgeResult(
                ok = map["ok"].toBoolean(),
                code = map["code"] ?: "UNKNOWN",
                message = map["message"] ?: "",
            )
        }.getOrElse { error ->
            BridgeResult(
                ok = false,
                code = "PYTHON_STOP_EXCEPTION",
                message = error.message ?: "Python stop failed unexpectedly",
            )
        }
    }

    fun isRunning(): Boolean {
        return runCatching {
            module().callAttr("is_client_running").toBoolean()
        }.getOrDefault(false)
    }

    fun pollEvents(maxItems: Int = 200): List<TunnelEvent> {
        return runCatching {
            val output = mutableListOf<TunnelEvent>()
            val rawEvents = module().callAttr("poll_events", maxItems)
            for (eventObj in rawEvents.asList()) {
                val map = pyDictToStringMap(eventObj)
                val type = map["type"] ?: "log"
                val status = map["status"]?.let {
                    runCatching { TunnelStatus.valueOf(it.uppercase()) }.getOrNull()
                }
                output += TunnelEvent(
                    type = type,
                    status = status,
                    level = map["level"] ?: "INFO",
                    message = map["message"] ?: "",
                    code = map["code"],
                    timestamp = map["timestamp"] ?: "",
                )
            }
            output
        }.getOrElse { error ->
            listOf(
                TunnelEvent(
                    type = "status",
                    status = TunnelStatus.ERROR,
                    level = "ERROR",
                    message = "Python poll failed: ${error.message ?: "unknown"}",
                    code = "PYTHON_POLL_EXCEPTION",
                ),
            )
        }
    }
}
