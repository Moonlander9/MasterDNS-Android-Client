package com.masterdnsvpn.android

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnDnsPolicyTest {
    @Test
    fun dnsServersForVpn_prefersConfiguredResolvers() {
        val config = ClientConfig(
            resolverDnsServers = listOf("8.8.8.8", "1.1.1.1"),
        )
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), VpnDnsPolicy.dnsServersForVpn(config))
    }

    @Test
    fun dnsServersForVpn_fallsBackToPublicResolversWhenConfigIsEmpty() {
        val config = ClientConfig(resolverDnsServers = emptyList())
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), VpnDnsPolicy.dnsServersForVpn(config))
    }

    @Test
    fun dnsServersForVpn_ignoresNonIpResolvers() {
        val config = ClientConfig(resolverDnsServers = listOf("dns.google", "8.8.8.8", ""))
        assertEquals(listOf("8.8.8.8"), VpnDnsPolicy.dnsServersForVpn(config))
    }

    @Test
    fun dnsServersForVpn_doesNotSilentlyFallbackWhenOnlyInvalidResolversAreConfigured() {
        val config = ClientConfig(resolverDnsServers = listOf("dns.google", "resolver.example"))
        assertEquals(emptyList<String>(), VpnDnsPolicy.dnsServersForVpn(config))
    }
}
