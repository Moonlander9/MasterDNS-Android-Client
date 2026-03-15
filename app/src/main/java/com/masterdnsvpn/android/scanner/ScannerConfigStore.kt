package com.masterdnsvpn.android.scanner

import android.content.Context
import org.json.JSONObject

class ScannerConfigStore(
    private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(defaultBundledLabel: String = BUNDLED_CIDR_LABEL): ScannerConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return ScannerConfig(cidrLabel = defaultBundledLabel)
        return runCatching {
            val json = JSONObject(raw)
            ScannerConfig(
                cidrUri = json.optString("cidrUri", BUNDLED_CIDR_URI),
                cidrLabel = json.optString("cidrLabel", defaultBundledLabel),
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
                remoteProfileServer = json.optString("remoteProfileServer", ""),
                remoteProfileName = json.optString("remoteProfileName", ""),
            )
        }.map { loaded ->
            loaded.copy(
                cidrUri = loaded.cidrUri.ifBlank { BUNDLED_CIDR_URI },
                cidrLabel = when {
                    loaded.cidrUri == BUNDLED_CIDR_URI &&
                        (loaded.cidrLabel.isBlank() || loaded.cidrLabel == BUNDLED_CIDR_LABEL) -> defaultBundledLabel
                    else -> loaded.cidrLabel.ifBlank { defaultBundledLabel }
                },
            )
        }.getOrDefault(ScannerConfig(cidrLabel = defaultBundledLabel))
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
            put("remoteProfileServer", config.remoteProfileServer)
            put("remoteProfileName", config.remoteProfileName)
        }
        prefs.edit().putString(KEY_CONFIG_JSON, json.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "scanner_config"
        private const val KEY_CONFIG_JSON = "scanner_config_json"
    }
}
