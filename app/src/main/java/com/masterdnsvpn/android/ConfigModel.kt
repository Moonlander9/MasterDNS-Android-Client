package com.masterdnsvpn.android

enum class VpnMode {
    FULL,
    SPLIT_ALLOWLIST,
}

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)

val QuickAddPackages: List<String> = listOf(
    "org.telegram.messenger",
    "com.whatsapp",
    "com.whatsapp.w4b",
    "com.instagram.android",
)

data class ClientConfig(
    val protocolType: String = "SOCKS5",
    val domains: List<String> = listOf("v.domain.com"),
    val dataEncryptionMethod: Int = 1,
    val encryptionKey: String = "",
    val listenIp: String = "127.0.0.1",
    val listenPort: Int = 1080,
    val socks5Auth: Boolean = true,
    val socks5User: String = "master_dns_vpn",
    val socks5Pass: String = "master_dns_vpn",
    val socksHandshakeTimeout: Double = 240.0,
    val vpnMode: VpnMode = VpnMode.FULL,
    val splitAllowlistPackages: List<String> = emptyList(),
    val resolverDnsServers: List<String> = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1"),
    val packetDuplicationCount: Int = 2,
    val maxPacketsPerBatch: Int = 100,
    val resolverBalancingStrategy: Int = 2,
    val baseEncodeData: Boolean = false,
    val uploadCompressionType: Int = 3,
    val downloadCompressionType: Int = 3,
    val minUploadMtu: Int = 100,
    val minDownloadMtu: Int = 100,
    val maxUploadMtu: Int = 220,
    val maxDownloadMtu: Int = 200,
    val mtuTestRetries: Int = 2,
    val mtuTestTimeout: Double = 1.0,
    val maxConnectionAttempts: Int = 10,
    val arqWindowSize: Int = 1000,
    val arqInitialRto: Double = 0.2,
    val arqMaxRto: Double = 1.0,
    val arqControlInitialRto: Double = 0.4,
    val arqControlMaxRto: Double = 1.0,
    val arqControlMaxRetries: Int = 180,
    val dnsQueryTimeout: Double = 5.0,
    val numRxWorkers: Int = 2,
    val numDnsWorkers: Int = 3,
    val rxSemaphoreLimit: Int = 1000,
    val maxClosedStreamRecords: Int = 2000,
    val socketBufferSize: Int = 8388608,
    val logLevel: String = "INFO",
    val configVersion: Double = 2.0,
)

fun ClientConfig.validateForAndroidV1(): List<String> {
    val errors = mutableListOf<String>()

    if (protocolType.uppercase() != "SOCKS5") {
        errors += "Android v1 supports PROTOCOL_TYPE=SOCKS5 only."
    }

    if (dataEncryptionMethod !in 1..5) {
        errors += "Android supports DATA_ENCRYPTION_METHOD values 1 through 5 only."
    }

    if (encryptionKey.isBlank()) {
        errors += "ENCRYPTION_KEY is required."
    }

    if (domains.isEmpty() || domains.any { it.isBlank() }) {
        errors += "DOMAINS must include at least one non-empty domain."
    }

    if (resolverDnsServers.isEmpty() || resolverDnsServers.any { it.isBlank() }) {
        errors += "RESOLVER_DNS_SERVERS must include at least one DNS resolver."
    }

    if (listenIp != "127.0.0.1") {
        errors += "LISTEN_IP must be 127.0.0.1 on Android for local-only binding."
    }

    if (listenPort !in 1..65535) {
        errors += "LISTEN_PORT must be between 1 and 65535."
    }

    if (socksHandshakeTimeout <= 0.0) {
        errors += "SOCKS_HANDSHAKE_TIMEOUT must be greater than 0."
    }

    if (uploadCompressionType !in 0..3) {
        errors += "UPLOAD_COMPRESSION_TYPE must be between 0 and 3."
    }

    if (downloadCompressionType !in 0..3) {
        errors += "DOWNLOAD_COMPRESSION_TYPE must be between 0 and 3."
    }

    if (vpnMode == VpnMode.SPLIT_ALLOWLIST) {
        if (splitAllowlistPackages.isEmpty()) {
            errors += "SPLIT_ALLOWLIST mode requires at least one package in SPLIT_ALLOWLIST_PACKAGES."
        }
        if (splitAllowlistPackages.any { it.isBlank() }) {
            errors += "SPLIT_ALLOWLIST_PACKAGES must not contain blank package names."
        }
    }

    return errors
}
