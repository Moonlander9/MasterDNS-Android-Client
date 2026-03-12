package com.masterdnsvpn.android.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerLogicTest {

    @Test
    fun dnsValidityClassification_matchesParityRules() {
        assertEquals(
            DnsValidityClassification.SUCCESS,
            classifyDnsValidity(elapsedMs = 150, responded = true, rcode = 0),
        )
        assertEquals(
            DnsValidityClassification.NXDOMAIN_OR_NODATA,
            classifyDnsValidity(elapsedMs = 1999, responded = true, rcode = 3),
        )
        assertEquals(
            DnsValidityClassification.REJECTED_LATENCY,
            classifyDnsValidity(elapsedMs = 2000, responded = true, rcode = 0),
        )
        assertEquals(
            DnsValidityClassification.ERROR,
            classifyDnsValidity(elapsedMs = 5, responded = false, rcode = null),
        )
        assertTrue(isDnsWorking(DnsValidityClassification.SUCCESS))
        assertTrue(isDnsWorking(DnsValidityClassification.NXDOMAIN_OR_NODATA))
        assertFalse(isDnsWorking(DnsValidityClassification.REJECTED_LATENCY))
    }

    @Test
    fun presetStopConditions_matchFastDeepFull() {
        assertFalse(reachedPresetLimit(ScanPreset.FAST, 24_999))
        assertTrue(reachedPresetLimit(ScanPreset.FAST, 25_000))
        assertFalse(reachedPresetLimit(ScanPreset.DEEP, 49_999))
        assertTrue(reachedPresetLimit(ScanPreset.DEEP, 50_000))
        assertFalse(reachedPresetLimit(ScanPreset.FULL, 999_999))
    }

    @Test
    fun autoShuffle_triggersAndSuppressesByPresetParityRules() {
        val fastWithProxy = AutoShuffleController(
            preset = ScanPreset.FAST,
            proxyModeEnabled = true,
        )

        repeat(499) {
            assertFalse(fastWithProxy.onMiss(paused = false, currentFound = 0, currentProxyPassed = 0))
        }
        assertTrue(fastWithProxy.onMiss(paused = false, currentFound = 0, currentProxyPassed = 0))
        assertEquals(1, fastWithProxy.autoShuffleCount)

        // Proxy passed improved since last auto-shuffle -> suppression for this threshold window.
        repeat(500) {
            assertFalse(fastWithProxy.onMiss(paused = false, currentFound = 0, currentProxyPassed = 1))
        }
        assertEquals(1, fastWithProxy.autoShuffleCount)

        // No further improvement -> threshold triggers shuffle again.
        repeat(499) {
            assertFalse(fastWithProxy.onMiss(paused = false, currentFound = 0, currentProxyPassed = 1))
        }
        assertTrue(fastWithProxy.onMiss(paused = false, currentFound = 0, currentProxyPassed = 1))
        assertEquals(2, fastWithProxy.autoShuffleCount)

        val fullPreset = AutoShuffleController(
            preset = ScanPreset.FULL,
            proxyModeEnabled = false,
        )

        repeat(2_999) {
            assertFalse(fullPreset.onMiss(paused = false, currentFound = 0, currentProxyPassed = 0))
        }
        assertTrue(fullPreset.onMiss(paused = false, currentFound = 0, currentProxyPassed = 0))
        assertEquals(1, fullPreset.autoShuffleCount)

        // Found DNS improved since last auto-shuffle -> suppression.
        repeat(3_000) {
            assertFalse(fullPreset.onMiss(paused = false, currentFound = 3, currentProxyPassed = 0))
        }
        assertEquals(1, fullPreset.autoShuffleCount)
    }
}
