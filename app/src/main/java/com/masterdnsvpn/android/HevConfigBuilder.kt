package com.masterdnsvpn.android

object HevConfigBuilder {
    fun build(config: TunnelEngineConfig): String {
        require(config.tunFd >= 0) { "tunFd must be >= 0" }
        require(config.socksHost.isNotBlank()) { "socksHost must not be blank" }
        require(config.socksPort in 1..65535) { "socksPort must be in 1..65535" }
        require(config.mtu in 576..9500) { "mtu must be in 576..9500" }

        return buildString {
            appendLine("tunnel:")
            appendLine("  name: tun0")
            appendLine("  mtu: ${config.mtu}")
            appendLine("  ipv4: ${config.tunIpv4Address}")
            appendLine("  ipv6: '${config.tunIpv6Address}'")
            appendLine()
            appendLine("socks5:")
            appendLine("  address: ${config.socksHost}")
            appendLine("  port: ${config.socksPort}")
            appendLine("  udp: 'tcp'")
            if (!config.socksUser.isNullOrBlank()) {
                appendLine("  username: '${yamlSingleQuote(config.socksUser)}'")
            }
            if (!config.socksPass.isNullOrBlank()) {
                appendLine("  password: '${yamlSingleQuote(config.socksPass)}'")
            }
            appendLine()
            appendLine("misc:")
            appendLine("  log-file: stderr")
            appendLine("  log-level: warn")
        }
    }

    private fun yamlSingleQuote(value: String): String {
        return value.replace("'", "''")
    }
}
