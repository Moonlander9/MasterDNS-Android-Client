package com.masterdnsvpn.android

internal object VpnDnsPolicy {
    data class Selection(
        val servers: List<String>,
        val invalidConfiguredResolvers: List<String>,
        val usedFallback: Boolean,
    )

    private val fallbackResolvers = listOf("1.1.1.1", "8.8.8.8")
    private val ipv4Regex = Regex(
        pattern = "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$",
    )

    fun selectDnsServers(config: ClientConfig): Selection {
        val normalizedResolvers = config.resolverDnsServers
            .asSequence()
            .map { it.trim() }
            .toList()

        val configuredResolvers = normalizedResolvers
            .filter { it.isNotEmpty() && isLiteralIpAddress(it) }
            .distinct()
        val invalidResolvers = normalizedResolvers
            .filter { it.isNotEmpty() && !isLiteralIpAddress(it) }
            .distinct()

        return when {
            configuredResolvers.isNotEmpty() -> Selection(
                servers = configuredResolvers,
                invalidConfiguredResolvers = invalidResolvers,
                usedFallback = false,
            )

            normalizedResolvers.any { it.isNotEmpty() } -> Selection(
                servers = emptyList(),
                invalidConfiguredResolvers = invalidResolvers,
                usedFallback = false,
            )

            else -> Selection(
                servers = fallbackResolvers,
                invalidConfiguredResolvers = emptyList(),
                usedFallback = true,
            )
        }
    }

    fun dnsServersForVpn(config: ClientConfig): List<String> = selectDnsServers(config).servers

    private fun isLiteralIpAddress(value: String): Boolean {
        return ipv4Regex.matches(value) || value.contains(':')
    }
}
