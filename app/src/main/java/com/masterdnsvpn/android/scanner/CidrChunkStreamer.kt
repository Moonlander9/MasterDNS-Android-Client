package com.masterdnsvpn.android.scanner

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

private const val IPV4_MAX = 0xFFFFFFFFL

data class Ipv4Cidr(
    val networkAddress: Int,
    val prefix: Int,
) {
    val numberOfAddresses: Long
        get() = if (prefix == 32) 1 else (1L shl (32 - prefix))

    fun blockKey(): String = intToIpv4(networkAddress)

    fun splitBy24(): List<Ipv4Cidr> {
        if (prefix >= 24) {
            return listOf(this)
        }
        val count = 1 shl (24 - prefix)
        val out = ArrayList<Ipv4Cidr>(count)
        val base = networkAddress.toLong() and IPV4_MAX
        for (idx in 0 until count) {
            val next = (base + (idx.toLong() shl 8)) and IPV4_MAX
            out += Ipv4Cidr(next.toInt(), 24)
        }
        return out
    }

    fun randomizedHosts(random: SecureRandom): List<Int> {
        if (prefix == 32) {
            return listOf(networkAddress)
        }

        val base = networkAddress.toLong() and IPV4_MAX
        if (prefix == 31) {
            val hosts = mutableListOf(base.toInt(), ((base + 1) and IPV4_MAX).toInt())
            secureShuffle(hosts, random)
            return hosts
        }

        val hostCount = (numberOfAddresses - 2).toInt().coerceAtLeast(0)
        val hosts = ArrayList<Int>(hostCount)
        for (offset in 0 until hostCount) {
            val next = ((base + 1 + offset) and IPV4_MAX).toInt()
            hosts += next
        }
        secureShuffle(hosts, random)
        return hosts
    }

    companion object {
        fun parse(rawLine: String): Ipv4Cidr? {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) {
                return null
            }

            val slashIdx = line.indexOf('/')
            if (slashIdx <= 0 || slashIdx >= line.lastIndex) {
                return null
            }
            val ipPart = line.substring(0, slashIdx).trim()
            val prefixPart = line.substring(slashIdx + 1).trim()
            val prefix = prefixPart.toIntOrNull() ?: return null
            if (prefix !in 0..32) {
                return null
            }

            val ip = ipv4ToInt(ipPart) ?: return null
            val mask = if (prefix == 0) {
                0
            } else {
                ((IPV4_MAX shl (32 - prefix)) and IPV4_MAX).toInt()
            }
            val network = ip and mask
            return Ipv4Cidr(networkAddress = network, prefix = prefix)
        }
    }
}

class CidrChunkStreamer(
    private val secureRandom: SecureRandom = SecureRandom(),
) {

    fun countHosts(cidrs: Sequence<Ipv4Cidr>): Long {
        var count = 0L
        for (cidr in cidrs) {
            count += when {
                cidr.prefix >= 31 -> cidr.numberOfAddresses
                else -> (cidr.numberOfAddresses - 2).coerceAtLeast(0)
            }
        }
        return count
    }

    fun parseCidrs(lines: Sequence<String>): List<Ipv4Cidr> {
        val cidrs = mutableListOf<Ipv4Cidr>()
        for (line in lines) {
            Ipv4Cidr.parse(line)?.let { cidrs += it }
        }
        return cidrs
    }

    fun streamChunks(
        cidrs: List<Ipv4Cidr>,
        testedBlocks: MutableSet<String>,
        totalGeneratedIps: AtomicInteger,
        maxIps: Int,
        chunkSize: Int = 500,
    ): Sequence<List<String>> = sequence {
        val shuffledCidrs = cidrs.toMutableList()
        secureShuffle(shuffledCidrs, secureRandom)

        val chunk = ArrayList<String>(chunkSize)

        outer@ for (cidr in shuffledCidrs) {
            val blocks24 = cidr.splitBy24().toMutableList()
            secureShuffle(blocks24, secureRandom)

            for (block in blocks24) {
                val key = block.blockKey()
                if (testedBlocks.contains(key)) {
                    continue
                }

                val hosts = block.randomizedHosts(secureRandom)
                for (host in hosts) {
                    chunk += intToIpv4(host)
                    val generated = totalGeneratedIps.incrementAndGet()
                    if (chunk.size >= chunkSize) {
                        yield(chunk.toList())
                        chunk.clear()
                    }
                    if (maxIps > 0 && generated >= maxIps) {
                        testedBlocks += key
                        break@outer
                    }
                }

                testedBlocks += key
            }
        }

        if (chunk.isNotEmpty()) {
            yield(chunk.toList())
        }
    }
}

internal fun <T> secureShuffle(list: MutableList<T>, random: SecureRandom) {
    if (list.size < 2) {
        return
    }
    for (i in list.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
}

internal fun ipv4ToInt(input: String): Int? {
    val parts = input.split('.')
    if (parts.size != 4) {
        return null
    }

    var out = 0L
    for (part in parts) {
        val value = part.toIntOrNull() ?: return null
        if (value !in 0..255) {
            return null
        }
        out = (out shl 8) or value.toLong()
    }
    return out.toInt()
}

internal fun intToIpv4(value: Int): String {
    val raw = value.toLong() and IPV4_MAX
    return buildString(15) {
        append((raw ushr 24) and 0xFF)
        append('.')
        append((raw ushr 16) and 0xFF)
        append('.')
        append((raw ushr 8) and 0xFF)
        append('.')
        append(raw and 0xFF)
    }
}
