package com.masterdnsvpn.android

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HevConfigBuilderTest {
    @Test
    fun build_includesSocksEndpointAndMtu() {
        val yaml = HevConfigBuilder.build(
            TunnelEngineConfig(
                tunFd = 123,
                socksHost = "127.0.0.1",
                socksPort = 1080,
                socksUser = "user",
                socksPass = "pass",
                mtu = 1500,
            ),
        )

        assertTrue(yaml.contains("mtu: 1500"))
        assertTrue(yaml.contains("ipv4: 10.42.0.2"))
        assertTrue(yaml.contains("ipv6: 'fd42:4242::2'"))
        assertTrue(yaml.contains("address: 127.0.0.1"))
        assertTrue(yaml.contains("port: 1080"))
        assertTrue(yaml.contains("udp: 'tcp'"))
        assertTrue(yaml.contains("username: 'user'"))
        assertTrue(yaml.contains("password: 'pass'"))
    }

    @Test
    fun build_rejectsInvalidPort() {
        assertThrows(IllegalArgumentException::class.java) {
            HevConfigBuilder.build(
                TunnelEngineConfig(
                    tunFd = 1,
                    socksHost = "127.0.0.1",
                    socksPort = 70000,
                    mtu = 1500,
                ),
            )
        }
    }
}
