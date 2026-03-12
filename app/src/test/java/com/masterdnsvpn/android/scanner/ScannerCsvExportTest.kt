package com.masterdnsvpn.android.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerCsvExportTest {

    @Test
    fun export_filtersToProxySuccess_whenProxyEnabled_andSortsByProxyDnssecPing() {
        val entries = listOf(
            ScannerEntry(
                dns = "1.1.1.1",
                pingMs = 40,
                proxyResult = ProxyResultState.SUCCESS,
                protocolInfo = ProtocolInfo(ipv6 = false, edns0 = true),
                securityInfo = SecurityInfo(dnssec = false, openResolver = true, hijacked = false),
                tcpUdp = "TCP/UDP",
                resolvedIp = "8.8.8.8",
                isp = IspInfo(org = "OrgA"),
            ),
            ScannerEntry(
                dns = "2.2.2.2",
                pingMs = 10,
                proxyResult = ProxyResultState.FAILED,
                protocolInfo = ProtocolInfo(ipv6 = true, edns0 = true),
                securityInfo = SecurityInfo(dnssec = true, openResolver = true, hijacked = false),
                tcpUdp = "TCP/UDP",
                resolvedIp = "8.8.4.4",
                isp = IspInfo(org = "OrgB"),
            ),
            ScannerEntry(
                dns = "3.3.3.3",
                pingMs = 15,
                proxyResult = ProxyResultState.SUCCESS,
                protocolInfo = ProtocolInfo(ipv6 = true, edns0 = true),
                securityInfo = SecurityInfo(dnssec = true, openResolver = true, hijacked = false),
                tcpUdp = "TCP/UDP",
                resolvedIp = "1.0.0.1",
                isp = IspInfo(org = "OrgC"),
            ),
        )

        val export = buildCsvExport(
            entries = entries,
            proxyEnabled = true,
            proxyFeatureAvailable = true,
        )

        assertTrue(export.headers.contains("Proxy Test"))
        assertEquals(2, export.rows.size)
        assertEquals("3.3.3.3", export.rows[0][0])
        assertEquals("1.1.1.1", export.rows[1][0])
    }

    @Test
    fun export_includesAllRows_whenProxyDisabled_andSortsByDnssecThenPing() {
        val entries = listOf(
            ScannerEntry(
                dns = "1.1.1.1",
                pingMs = 50,
                proxyResult = null,
                protocolInfo = ProtocolInfo(ipv6 = false, edns0 = false),
                securityInfo = SecurityInfo(dnssec = false),
                tcpUdp = "UDP only",
                resolvedIp = "-",
                isp = IspInfo(org = "OrgA"),
            ),
            ScannerEntry(
                dns = "4.4.4.4",
                pingMs = 25,
                proxyResult = null,
                protocolInfo = ProtocolInfo(ipv6 = false, edns0 = true),
                securityInfo = SecurityInfo(dnssec = true),
                tcpUdp = "TCP/UDP",
                resolvedIp = "4.4.4.4",
                isp = IspInfo(org = "OrgD"),
            ),
            ScannerEntry(
                dns = "9.9.9.9",
                pingMs = 10,
                proxyResult = null,
                protocolInfo = ProtocolInfo(ipv6 = true, edns0 = true),
                securityInfo = SecurityInfo(dnssec = true),
                tcpUdp = "TCP/UDP",
                resolvedIp = "9.9.9.9",
                isp = IspInfo(org = "OrgN"),
            ),
        )

        val export = buildCsvExport(
            entries = entries,
            proxyEnabled = false,
            proxyFeatureAvailable = true,
        )

        assertEquals(3, export.rows.size)
        assertEquals("9.9.9.9", export.rows[0][0])
        assertEquals("4.4.4.4", export.rows[1][0])
        assertEquals("1.1.1.1", export.rows[2][0])
    }

    @Test
    fun successfulResolvers_filtersByProxySuccess_whenProxyModeIsActive() {
        val entries = listOf(
            ScannerEntry(
                dns = "9.9.9.9",
                pingMs = 25,
                proxyResult = ProxyResultState.SUCCESS,
                securityInfo = SecurityInfo(dnssec = true),
                protocolInfo = ProtocolInfo(ipv6 = true, edns0 = true),
                tcpUdp = "TCP/UDP",
                resolvedIp = "9.9.9.9",
                isp = IspInfo(org = "Org9"),
            ),
            ScannerEntry(
                dns = "1.1.1.1",
                pingMs = 10,
                proxyResult = ProxyResultState.FAILED,
                securityInfo = SecurityInfo(dnssec = true),
                protocolInfo = ProtocolInfo(ipv6 = true, edns0 = true),
                tcpUdp = "TCP/UDP",
                resolvedIp = "1.1.1.1",
                isp = IspInfo(org = "Org1"),
            ),
        )

        val resolvers = successfulResolversFromEntries(
            entries = entries,
            proxyEnabled = true,
            proxyFeatureAvailable = true,
        )

        assertEquals(listOf("9.9.9.9"), resolvers)
    }

    @Test
    fun successfulResolvers_returnsAllFound_whenProxyDisabled() {
        val entries = listOf(
            ScannerEntry(
                dns = "8.8.8.8",
                pingMs = 30,
                proxyResult = null,
                securityInfo = SecurityInfo(dnssec = false),
                protocolInfo = ProtocolInfo(ipv6 = false, edns0 = true),
                tcpUdp = "UDP only",
                resolvedIp = "8.8.8.8",
                isp = IspInfo(org = "Org8"),
            ),
            ScannerEntry(
                dns = "4.4.4.4",
                pingMs = 20,
                proxyResult = null,
                securityInfo = SecurityInfo(dnssec = true),
                protocolInfo = ProtocolInfo(ipv6 = true, edns0 = true),
                tcpUdp = "TCP/UDP",
                resolvedIp = "4.4.4.4",
                isp = IspInfo(org = "Org4"),
            ),
        )

        val resolvers = successfulResolversFromEntries(
            entries = entries,
            proxyEnabled = false,
            proxyFeatureAvailable = true,
        )

        assertEquals(listOf("4.4.4.4", "8.8.8.8"), resolvers)
    }
}
