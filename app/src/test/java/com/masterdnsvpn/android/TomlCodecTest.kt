package com.masterdnsvpn.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TomlCodecTest {

    @Test
    fun encodeDecode_roundTrip_preservesCoreFields() {
        val config = ClientConfig(
            dataEncryptionMethod = 5,
            encryptionKey = "secret",
            domains = listOf("v.example.com"),
            resolverDnsServers = listOf("8.8.8.8", "1.1.1.1"),
            listenPort = 1081,
            socksHandshakeTimeout = 90.5,
            baseEncodeData = true,
            uploadCompressionType = 1,
            downloadCompressionType = 2,
            vpnMode = VpnMode.SPLIT_ALLOWLIST,
            splitAllowlistPackages = listOf("com.whatsapp", "org.telegram.messenger"),
        )

        val encoded = TomlCodec.encode(config)
        val decoded = TomlCodec.decode(encoded)

        assertEquals("SOCKS5", decoded.protocolType)
        assertEquals(5, decoded.dataEncryptionMethod)
        assertEquals("secret", decoded.encryptionKey)
        assertEquals(listOf("v.example.com"), decoded.domains)
        assertEquals(1081, decoded.listenPort)
        assertEquals(90.5, decoded.socksHandshakeTimeout, 0.0)
        assertTrue(decoded.baseEncodeData)
        assertEquals(1, decoded.uploadCompressionType)
        assertEquals(2, decoded.downloadCompressionType)
        assertEquals(VpnMode.SPLIT_ALLOWLIST, decoded.vpnMode)
        assertEquals(listOf("com.whatsapp", "org.telegram.messenger"), decoded.splitAllowlistPackages)
    }

    @Test
    fun validateForAndroidV1_rejectsUnsupportedMode() {
        val config = ClientConfig(
            protocolType = "TCP",
            dataEncryptionMethod = 0,
            listenIp = "0.0.0.0",
        )

        val errors = config.validateForAndroidV1()
        assertTrue(errors.any { it.contains("SOCKS5") })
        assertTrue(errors.any { it.contains("1 through 5") })
        assertTrue(errors.any { it.contains("LISTEN_IP") })
    }

    @Test
    fun decode_missingNewFields_usesCurrentDefaults() {
        val legacyToml = """
            PROTOCOL_TYPE = "SOCKS5"
            DOMAINS = ["v.example.com"]
            DATA_ENCRYPTION_METHOD = 1
            ENCRYPTION_KEY = "secret"
            LISTEN_IP = "127.0.0.1"
            LISTEN_PORT = 1080
            RESOLVER_DNS_SERVERS = ["8.8.8.8"]
        """.trimIndent()

        val decoded = TomlCodec.decode(legacyToml)

        assertEquals(ClientConfig().socksHandshakeTimeout, decoded.socksHandshakeTimeout, 0.0)
        assertEquals(ClientConfig().baseEncodeData, decoded.baseEncodeData)
        assertEquals(ClientConfig().uploadCompressionType, decoded.uploadCompressionType)
        assertEquals(ClientConfig().downloadCompressionType, decoded.downloadCompressionType)
        assertEquals(ClientConfig().configVersion, decoded.configVersion, 0.0)
    }

    @Test
    fun profileCodec_roundTrip_worksWithNewId() {
        val config = ClientConfig(
            encryptionKey = "secret",
            domains = listOf("v.example.com"),
            resolverDnsServers = listOf("8.8.8.8"),
            vpnMode = VpnMode.SPLIT_ALLOWLIST,
            splitAllowlistPackages = listOf("com.instagram.android"),
        )

        val profile = ProfileCodec.encode(config)
        assertTrue(profile.startsWith("MDVPN3:"))

        val decoded = ProfileCodec.decode(profile)
        assertEquals(config.encryptionKey, decoded.encryptionKey)
        assertEquals(config.domains, decoded.domains)
        assertEquals(config.resolverDnsServers, decoded.resolverDnsServers)
        assertEquals(config.vpnMode, decoded.vpnMode)
        assertEquals(config.splitAllowlistPackages, decoded.splitAllowlistPackages)
    }

    @Test
    fun profileCodec_rejectsLegacyId() {
        val config = ClientConfig(
            encryptionKey = "legacy-key",
            domains = listOf("legacy.example.com"),
            resolverDnsServers = listOf("1.1.1.1"),
        )
        val payload = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(TomlCodec.encode(config).toByteArray(Charsets.UTF_8))
        val legacy = "MDVPN1:$payload"
        assertThrows(IllegalArgumentException::class.java) {
            ProfileCodec.decode(legacy)
        }
    }

    @Test
    fun validateForAndroidV1_splitModeRequiresPackages() {
        val config = ClientConfig(
            encryptionKey = "secret",
            domains = listOf("v.example.com"),
            resolverDnsServers = listOf("1.1.1.1"),
            vpnMode = VpnMode.SPLIT_ALLOWLIST,
            splitAllowlistPackages = emptyList(),
        )

        val errors = config.validateForAndroidV1()
        assertTrue(errors.any { it.contains("SPLIT_ALLOWLIST") })
    }
}
