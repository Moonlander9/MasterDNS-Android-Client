package com.masterdnsvpn.android

internal object VpnDnsPolicy {
    private val fallbackResolvers = listOf("1.1.1.1", "8.8.8.8")
    private val ipv4Regex = Regex(
        pattern = "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$",
    )

    fun dnsServersForVpn(config: ClientConfig): List<String> {
        val configuredResolvers = config.resolverDnsServers
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && isLiteralIpAddress(it) }
            .distinct()
            .toList()

        return if (configuredResolvers.isNotEmpty()) {
            configuredResolvers
        } else {
            fallbackResolvers
        }
    }

    private fun isLiteralIpAddress(value: String): Boolean {
        return ipv4Regex.matches(value) || value.contains(':')
    }
}
