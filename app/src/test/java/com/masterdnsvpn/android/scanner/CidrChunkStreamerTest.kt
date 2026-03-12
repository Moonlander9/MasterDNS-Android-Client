package com.masterdnsvpn.android.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

class CidrChunkStreamerTest {

    private val deterministicRandom = SecureRandom().apply {
        setSeed(1234L)
    }

    @Test
    fun countHosts_handles31And32Correctly() {
        val streamer = CidrChunkStreamer(deterministicRandom)
        val cidrs = streamer.parseCidrs(
            sequenceOf(
                "10.0.0.0/31",
                "10.0.0.5/32",
                "10.0.1.0/30",
            ),
        )

        val count = streamer.countHosts(cidrs.asSequence())

        assertEquals(5, count)
    }

    @Test
    fun streamChunks_includes31BothAddresses_andExcludesNetworkBroadcastBelow31() {
        val streamer = CidrChunkStreamer(deterministicRandom)
        val cidrs = streamer.parseCidrs(
            sequenceOf(
                "10.0.0.0/31",
                "10.0.0.5/32",
                "10.0.1.0/30",
            ),
        )

        val generated = AtomicInteger(0)
        val testedBlocks = mutableSetOf<String>()
        val ips = streamer.streamChunks(
            cidrs = cidrs,
            testedBlocks = testedBlocks,
            totalGeneratedIps = generated,
            maxIps = 0,
        ).flatten().toList()

        assertTrue(ips.contains("10.0.0.0"))
        assertTrue(ips.contains("10.0.0.1"))
        assertTrue(ips.contains("10.0.0.5"))
        assertTrue(ips.contains("10.0.1.1"))
        assertTrue(ips.contains("10.0.1.2"))
        assertEquals(5, ips.size)
    }
}
