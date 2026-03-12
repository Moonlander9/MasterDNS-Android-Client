package com.masterdnsvpn.android.scanner

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

@OptIn(ExperimentalCoroutinesApi::class)
class DnsScannerEngineTest {

    @Test
    fun scannerEngine_runsPipeline_withFakes_andAutoSavesCsv() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tempDir = createTempDir(prefix = "scanner-test-")

        val engine = DnsScannerEngine(
            scope = this,
            networkClient = FakeNetworkClient(),
            proxyTester = FakeProxyTester(),
            ioDispatcher = dispatcher,
        )

        val config = ScannerConfig(
            cidrUri = "in-memory",
            cidrLabel = "test",
            domain = "example.com",
            recordType = ScanRecordType.A,
            randomSubdomain = false,
            preset = ScanPreset.FAST,
            concurrency = 4,
            proxyTestEnabled = false,
        )

        engine.startScan(
            config = config,
            cidrReaderProvider = {
                BufferedReader(StringReader("10.0.0.0/30\n10.0.1.0/30\n"))
            },
            exportDir = tempDir,
        )

        advanceUntilIdle()

        val state = engine.state.value
        assertEquals(ScanStatus.COMPLETED, state.status)
        assertTrue(state.stats.foundDns >= 2)
        assertTrue(state.entries.isNotEmpty())
        assertTrue(state.csvPath != null)
        assertTrue(File(state.csvPath!!).exists())

        tempDir.deleteRecursively()
    }

    private class FakeNetworkClient : ScannerNetworkClient {
        override suspend fun probeDns(
            dnsIp: String,
            domain: String,
            recordType: ScanRecordType,
            randomSubdomain: Boolean,
        ): DnsProbeResult {
            val valid = dnsIp.endsWith(".1") || dnsIp.endsWith(".2")
            delay(1)
            return DnsProbeResult(
                dns = dnsIp,
                valid = valid,
                elapsedMs = if (valid) 20 else 0,
                classification = if (valid) {
                    DnsValidityClassification.SUCCESS
                } else {
                    DnsValidityClassification.ERROR
                },
            )
        }

        override suspend fun testTcpUdpSupport(dnsIp: String): String = "TCP/UDP"

        override suspend fun testSecurity(dnsIp: String): SecurityInfo {
            return SecurityInfo(dnssec = dnsIp.endsWith(".1"), hijacked = false, openResolver = true)
        }

        override suspend fun testIpv6Support(dnsIp: String): Boolean = true

        override suspend fun resolveARecord(dnsIp: String, scanType: ScanRecordType, scanDomain: String): String {
            return "8.8.8.8"
        }

        override suspend fun testEdns0Support(dnsIp: String): Boolean = true

        override suspend fun lookupIsp(dnsIp: String): IspInfo {
            return IspInfo(org = "TestOrg", isp = "TestISP", asn = "AS123", countryCode = "SE")
        }
    }

    private class FakeProxyTester : SlipstreamProxyTester {
        override suspend fun checkCapability(binaryPath: String): ProxyCapability {
            return ProxyCapability(available = false, reason = "Not needed")
        }

        override suspend fun runProxyTest(
            dnsIp: String,
            domain: String,
            binaryPath: String,
            port: Int,
        ): ProxyResultState {
            return ProxyResultState.FAILED
        }
    }
}
