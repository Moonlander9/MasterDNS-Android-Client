package com.masterdnsvpn.android.scanner

import android.content.Context
import org.json.JSONObject

class ScannerConfigStore(
    private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ScannerConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return ScannerConfig()
        return runCatching {
            val json = JSONObject(raw)
            ScannerConfig(
                cidrUri = json.optString("cidrUri", BUNDLED_CIDR_URI),
                cidrLabel = json.optString("cidrLabel", BUNDLED_CIDR_LABEL),
                domain = json.optString("domain", "google.com"),
                recordType = runCatching {
                    ScanRecordType.valueOf(json.optString("recordType", ScanRecordType.A.name))
                }.getOrDefault(ScanRecordType.A),
                randomSubdomain = json.optBoolean("randomSubdomain", false),
                preset = runCatching {
                    ScanPreset.valueOf(json.optString("preset", ScanPreset.FAST.name))
                }.getOrDefault(ScanPreset.FAST),
                concurrency = json.optInt("concurrency", 120).coerceAtLeast(1),
                proxyTestEnabled = json.optBoolean("proxyTestEnabled", false),
                slipstreamBinaryPath = json.optString("slipstreamBinaryPath", ""),
            )
        }.map { loaded ->
            loaded.copy(
                cidrUri = loaded.cidrUri.ifBlank { BUNDLED_CIDR_URI },
                cidrLabel = loaded.cidrLabel.ifBlank { BUNDLED_CIDR_LABEL },
            )
        }.getOrDefault(ScannerConfig())
    }

    fun save(config: ScannerConfig) {
        val json = JSONObject().apply {
            put("cidrUri", config.cidrUri)
            put("cidrLabel", config.cidrLabel)
            put("domain", config.domain)
            put("recordType", config.recordType.name)
            put("randomSubdomain", config.randomSubdomain)
            put("preset", config.preset.name)
            put("concurrency", config.concurrency)
            put("proxyTestEnabled", config.proxyTestEnabled)
            put("slipstreamBinaryPath", config.slipstreamBinaryPath)
        }
        prefs.edit().putString(KEY_CONFIG_JSON, json.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "scanner_config"
        private const val KEY_CONFIG_JSON = "scanner_config_json"
    }
}
