package com.masterdnsvpn.android

import android.content.Context

class ConfigStoreV3(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ClientConfig {
        val toml = prefs.getString(KEY_CONFIG_TOML, null)
        if (toml.isNullOrBlank()) {
            return ClientConfig()
        }

        return runCatching { TomlCodec.decode(toml) }.getOrDefault(ClientConfig())
    }

    fun save(config: ClientConfig) {
        val toml = TomlCodec.encode(config)
        prefs.edit()
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .putString(KEY_CONFIG_TOML, toml)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "config_store_v3"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_CONFIG_TOML = "config_toml"
        private const val SCHEMA_VERSION = 3
    }
}
