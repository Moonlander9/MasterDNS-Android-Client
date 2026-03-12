package com.masterdnsvpn.android

import android.content.Context
import java.io.File

class TunnelDiagnosticsStore(context: Context) {
    private val diagnosticsFile = File(context.filesDir, DIAGNOSTICS_FILE_NAME)

    @Synchronized
    fun appendEvent(event: TunnelEvent, stage: String? = null, details: String? = null) {
        append(
            timestamp = event.timestamp,
            level = event.level,
            type = event.type,
            status = event.status?.name,
            code = event.code,
            stage = stage,
            message = event.message,
            details = details,
        )
    }

    @Synchronized
    fun appendFailure(
        stage: String,
        code: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        append(
            timestamp = java.time.Instant.now().toString(),
            level = "ERROR",
            type = "failure",
            status = TunnelStatus.ERROR.name,
            code = code,
            stage = stage,
            message = message,
            details = throwable?.let { shortStackTrace(it) },
        )
    }

    @Synchronized
    fun exportText(): String {
        if (!diagnosticsFile.exists()) {
            return ""
        }
        return runCatching { diagnosticsFile.readText() }.getOrDefault("")
    }

    @Synchronized
    fun clear() {
        runCatching { diagnosticsFile.delete() }
    }

    private fun append(
        timestamp: String,
        level: String,
        type: String,
        status: String?,
        code: String?,
        stage: String?,
        message: String,
        details: String?,
    ) {
        rotateIfNeeded()

        val payload = buildString {
            append('{')
            append("\"timestamp\":\"").append(jsonEscape(timestamp)).append("\",")
            append("\"level\":\"").append(jsonEscape(level)).append("\",")
            append("\"type\":\"").append(jsonEscape(type)).append("\",")
            append("\"status\":\"").append(jsonEscape(status ?: "")).append("\",")
            append("\"code\":\"").append(jsonEscape(code ?: "")).append("\",")
            append("\"stage\":\"").append(jsonEscape(stage ?: "")).append("\",")
            append("\"message\":\"").append(jsonEscape(message)).append("\",")
            append("\"details\":\"").append(jsonEscape(details ?: "")).append("\"")
            append('}')
            appendLine()
        }

        runCatching {
            diagnosticsFile.parentFile?.mkdirs()
            diagnosticsFile.appendText(payload)
        }
    }

    private fun rotateIfNeeded() {
        if (!diagnosticsFile.exists()) {
            return
        }

        val fileSize = diagnosticsFile.length()
        if (fileSize < MAX_FILE_BYTES) {
            return
        }

        runCatching {
            val tailBytes = diagnosticsFile.readBytes().takeLast(ROTATE_KEEP_BYTES)
            diagnosticsFile.writeBytes(tailBytes.toByteArray())
        }
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun shortStackTrace(error: Throwable): String {
        val lines = error.stackTrace
            .take(4)
            .joinToString(" | ") { "${it.className}:${it.lineNumber}" }
        return "${error::class.java.simpleName}: ${error.message ?: ""} [$lines]"
    }

    companion object {
        private const val DIAGNOSTICS_FILE_NAME = "tunnel_diagnostics_v3.log"
        private const val MAX_FILE_BYTES = 384 * 1024L
        private const val ROTATE_KEEP_BYTES = 192 * 1024
    }
}
