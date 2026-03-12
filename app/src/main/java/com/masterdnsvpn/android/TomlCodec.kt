package com.masterdnsvpn.android

object TomlCodec {
    private fun parseString(content: String, key: String): String? {
        val regex = Regex("""(?m)^\s*$key\s*=\s*\"([^\"]*)\"\s*$""")
        return regex.find(content)?.groupValues?.getOrNull(1)
    }

    private fun parseInt(content: String, key: String): Int? {
        val regex = Regex("""(?m)^\s*$key\s*=\s*(-?\d+)\s*$""")
        return regex.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseDouble(content: String, key: String): Double? {
        val regex = Regex("""(?m)^\s*$key\s*=\s*(-?\d+(?:\.\d+)?)\s*$""")
        return regex.find(content)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun parseBool(content: String, key: String): Boolean? {
        val regex = Regex("""(?m)^\s*$key\s*=\s*(true|false)\s*$""", RegexOption.IGNORE_CASE)
        return regex.find(content)?.groupValues?.getOrNull(1)?.lowercase()?.toBooleanStrictOrNull()
    }

    private fun parseStringArray(content: String, key: String): List<String>? {
        val regex = Regex("""(?s)$key\s*=\s*\[(.*?)]""")
        val raw = regex.find(content)?.groupValues?.getOrNull(1) ?: return null
        val itemRegex = Regex("""\"([^\"]*)\"""")
        val items = itemRegex.findAll(raw).map { it.groupValues[1] }.toList()
        return items
    }

    fun decode(content: String): ClientConfig {
        val defaults = ClientConfig()

        return ClientConfig(
            protocolType = parseString(content, "PROTOCOL_TYPE") ?: defaults.protocolType,
            domains = parseStringArray(content, "DOMAINS") ?: defaults.domains,
            dataEncryptionMethod = parseInt(content, "DATA_ENCRYPTION_METHOD") ?: defaults.dataEncryptionMethod,
            encryptionKey = parseString(content, "ENCRYPTION_KEY") ?: defaults.encryptionKey,
            listenIp = parseString(content, "LISTEN_IP") ?: defaults.listenIp,
            listenPort = parseInt(content, "LISTEN_PORT") ?: defaults.listenPort,
            socks5Auth = parseBool(content, "SOCKS5_AUTH") ?: defaults.socks5Auth,
            socks5User = parseString(content, "SOCKS5_USER") ?: defaults.socks5User,
            socks5Pass = parseString(content, "SOCKS5_PASS") ?: defaults.socks5Pass,
            socksHandshakeTimeout = parseDouble(content, "SOCKS_HANDSHAKE_TIMEOUT") ?: defaults.socksHandshakeTimeout,
            vpnMode = parseString(content, "VPN_MODE")
                ?.let { runCatching { VpnMode.valueOf(it.uppercase()) }.getOrNull() }
                ?: defaults.vpnMode,
            splitAllowlistPackages = parseStringArray(content, "SPLIT_ALLOWLIST_PACKAGES") ?: defaults.splitAllowlistPackages,
            resolverDnsServers = parseStringArray(content, "RESOLVER_DNS_SERVERS") ?: defaults.resolverDnsServers,
            packetDuplicationCount = parseInt(content, "PACKET_DUPLICATION_COUNT") ?: defaults.packetDuplicationCount,
            maxPacketsPerBatch = parseInt(content, "MAX_PACKETS_PER_BATCH") ?: defaults.maxPacketsPerBatch,
            resolverBalancingStrategy = parseInt(content, "RESOLVER_BALANCING_STRATEGY") ?: defaults.resolverBalancingStrategy,
            baseEncodeData = parseBool(content, "BASE_ENCODE_DATA") ?: defaults.baseEncodeData,
            uploadCompressionType = parseInt(content, "UPLOAD_COMPRESSION_TYPE") ?: defaults.uploadCompressionType,
            downloadCompressionType = parseInt(content, "DOWNLOAD_COMPRESSION_TYPE") ?: defaults.downloadCompressionType,
            minUploadMtu = parseInt(content, "MIN_UPLOAD_MTU") ?: defaults.minUploadMtu,
            minDownloadMtu = parseInt(content, "MIN_DOWNLOAD_MTU") ?: defaults.minDownloadMtu,
            maxUploadMtu = parseInt(content, "MAX_UPLOAD_MTU") ?: defaults.maxUploadMtu,
            maxDownloadMtu = parseInt(content, "MAX_DOWNLOAD_MTU") ?: defaults.maxDownloadMtu,
            mtuTestRetries = parseInt(content, "MTU_TEST_RETRIES") ?: defaults.mtuTestRetries,
            mtuTestTimeout = parseDouble(content, "MTU_TEST_TIMEOUT") ?: defaults.mtuTestTimeout,
            maxConnectionAttempts = parseInt(content, "MAX_CONNECTION_ATTEMPTS") ?: defaults.maxConnectionAttempts,
            arqWindowSize = parseInt(content, "ARQ_WINDOW_SIZE") ?: defaults.arqWindowSize,
            arqInitialRto = parseDouble(content, "ARQ_INITIAL_RTO") ?: defaults.arqInitialRto,
            arqMaxRto = parseDouble(content, "ARQ_MAX_RTO") ?: defaults.arqMaxRto,
            arqControlInitialRto = parseDouble(content, "ARQ_CONTROL_INITIAL_RTO") ?: defaults.arqControlInitialRto,
            arqControlMaxRto = parseDouble(content, "ARQ_CONTROL_MAX_RTO") ?: defaults.arqControlMaxRto,
            arqControlMaxRetries = parseInt(content, "ARQ_CONTROL_MAX_RETRIES") ?: defaults.arqControlMaxRetries,
            dnsQueryTimeout = parseDouble(content, "DNS_QUERY_TIMEOUT") ?: defaults.dnsQueryTimeout,
            numRxWorkers = parseInt(content, "NUM_RX_WORKERS") ?: defaults.numRxWorkers,
            numDnsWorkers = parseInt(content, "NUM_DNS_WORKERS") ?: defaults.numDnsWorkers,
            rxSemaphoreLimit = parseInt(content, "RX_SEMAPHORE_LIMIT") ?: defaults.rxSemaphoreLimit,
            maxClosedStreamRecords = parseInt(content, "MAX_CLOSED_STREAM_RECORDS") ?: defaults.maxClosedStreamRecords,
            socketBufferSize = parseInt(content, "SOCKET_BUFFER_SIZE") ?: defaults.socketBufferSize,
            logLevel = parseString(content, "LOG_LEVEL") ?: defaults.logLevel,
            configVersion = parseDouble(content, "CONFIG_VERSION") ?: defaults.configVersion,
        )
    }

    private fun quoteList(items: List<String>): String {
        return items.filter { it.isNotBlank() }
            .joinToString(prefix = "[", postfix = "]") { "\"${it.trim()}\"" }
    }

    fun encode(config: ClientConfig): String {
        return buildString {
            appendLine("PROTOCOL_TYPE = \"${config.protocolType.uppercase()}\"")
            appendLine("DOMAINS = ${quoteList(config.domains)}")
            appendLine("DATA_ENCRYPTION_METHOD = ${config.dataEncryptionMethod}")
            appendLine("ENCRYPTION_KEY = \"${config.encryptionKey}\"")
            appendLine()
            appendLine("LISTEN_IP = \"${config.listenIp}\"")
            appendLine("LISTEN_PORT = ${config.listenPort}")
            appendLine("SOCKS5_AUTH = ${config.socks5Auth}")
            appendLine("SOCKS5_USER = \"${config.socks5User}\"")
            appendLine("SOCKS5_PASS = \"${config.socks5Pass}\"")
            appendLine("SOCKS_HANDSHAKE_TIMEOUT = ${config.socksHandshakeTimeout}")
            appendLine("VPN_MODE = \"${config.vpnMode.name}\"")
            appendLine("SPLIT_ALLOWLIST_PACKAGES = ${quoteList(config.splitAllowlistPackages)}")
            appendLine()
            appendLine("RESOLVER_DNS_SERVERS = ${quoteList(config.resolverDnsServers)}")
            appendLine("PACKET_DUPLICATION_COUNT = ${config.packetDuplicationCount}")
            appendLine("MAX_PACKETS_PER_BATCH = ${config.maxPacketsPerBatch}")
            appendLine("RESOLVER_BALANCING_STRATEGY = ${config.resolverBalancingStrategy}")
            appendLine("BASE_ENCODE_DATA = ${config.baseEncodeData}")
            appendLine("UPLOAD_COMPRESSION_TYPE = ${config.uploadCompressionType}")
            appendLine("DOWNLOAD_COMPRESSION_TYPE = ${config.downloadCompressionType}")
            appendLine()
            appendLine("MIN_UPLOAD_MTU = ${config.minUploadMtu}")
            appendLine("MIN_DOWNLOAD_MTU = ${config.minDownloadMtu}")
            appendLine("MAX_UPLOAD_MTU = ${config.maxUploadMtu}")
            appendLine("MAX_DOWNLOAD_MTU = ${config.maxDownloadMtu}")
            appendLine("MTU_TEST_RETRIES = ${config.mtuTestRetries}")
            appendLine("MTU_TEST_TIMEOUT = ${config.mtuTestTimeout}")
            appendLine()
            appendLine("MAX_CONNECTION_ATTEMPTS = ${config.maxConnectionAttempts}")
            appendLine("ARQ_WINDOW_SIZE = ${config.arqWindowSize}")
            appendLine("ARQ_INITIAL_RTO = ${config.arqInitialRto}")
            appendLine("ARQ_MAX_RTO = ${config.arqMaxRto}")
            appendLine("ARQ_CONTROL_INITIAL_RTO = ${config.arqControlInitialRto}")
            appendLine("ARQ_CONTROL_MAX_RTO = ${config.arqControlMaxRto}")
            appendLine("ARQ_CONTROL_MAX_RETRIES = ${config.arqControlMaxRetries}")
            appendLine("DNS_QUERY_TIMEOUT = ${config.dnsQueryTimeout}")
            appendLine()
            appendLine("NUM_RX_WORKERS = ${config.numRxWorkers}")
            appendLine("NUM_DNS_WORKERS = ${config.numDnsWorkers}")
            appendLine("RX_SEMAPHORE_LIMIT = ${config.rxSemaphoreLimit}")
            appendLine("MAX_CLOSED_STREAM_RECORDS = ${config.maxClosedStreamRecords}")
            appendLine("SOCKET_BUFFER_SIZE = ${config.socketBufferSize}")
            appendLine("LOG_LEVEL = \"${config.logLevel.uppercase()}\"")
            appendLine("CONFIG_VERSION = ${config.configVersion}")
        }
    }
}
