package com.masterdnsvpn.android

import java.nio.charset.StandardCharsets
import java.util.Base64

object ProfileCodec {
    private const val PROFILE_PREFIX_V3 = "MDVPN3:"

    fun encode(config: ClientConfig): String {
        val toml = TomlCodec.encode(config)
        val payload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(toml.toByteArray(StandardCharsets.UTF_8))
        return PROFILE_PREFIX_V3 + payload
    }

    fun decode(profileText: String): ClientConfig {
        val normalized = profileText.trim()
            .removePrefix("mdvpn://")
            .removePrefix("MDVPN://")
            .trim()

        val base64 = when {
            normalized.startsWith(PROFILE_PREFIX_V3) -> normalized.removePrefix(PROFILE_PREFIX_V3)
            else -> throw IllegalArgumentException(
                "Unsupported profile format. Expected $PROFILE_PREFIX_V3",
            )
        }

        val tomlBytes = decodeBase64(base64)
        val toml = String(tomlBytes, StandardCharsets.UTF_8)
        return TomlCodec.decode(toml)
    }

    private fun decodeBase64(value: String): ByteArray {
        val clean = value.replace("\n", "").replace("\r", "").trim()

        return try {
            Base64.getUrlDecoder().decode(clean)
        } catch (_: IllegalArgumentException) {
            Base64.getDecoder().decode(clean)
        }
    }
}
