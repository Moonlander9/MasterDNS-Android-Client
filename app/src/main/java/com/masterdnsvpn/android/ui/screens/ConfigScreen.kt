package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.MainUiState
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.ui.theme.MetricBadge
import com.masterdnsvpn.android.ui.theme.SectionTitle
import com.masterdnsvpn.android.ui.theme.VpnAppBackground
import com.masterdnsvpn.android.ui.theme.VpnCard
import com.masterdnsvpn.android.ui.theme.VpnHeroCard

const val CONFIG_SEARCH_TAG = "config_search"
const val CONFIG_ADVANCED_TOGGLE_TAG = "config_advanced_toggle"

private enum class ConfigSection(val titleRes: Int) {
    ALL(R.string.config_section_all),
    POLICY(R.string.config_policy_title),
    ESSENTIALS(R.string.config_essentials_title),
    PROXY(R.string.config_proxy_title),
    TRANSPORT(R.string.config_transport_title),
    DIAGNOSTICS(R.string.config_diagnostics_title),
    ADVANCED(R.string.config_advanced_title),
    ERRORS(R.string.config_validation_errors_title),
}

@Composable
fun ConfigScreen(
    state: MainUiState,
    onEncryptionMethodChanged: (String) -> Unit,
    onEncryptionKeyChanged: (String) -> Unit,
    onDomainsChanged: (String) -> Unit,
    onResolversChanged: (String) -> Unit,
    onListenPortChanged: (String) -> Unit,
    onSocksAuthChanged: (Boolean) -> Unit,
    onSocksUserChanged: (String) -> Unit,
    onSocksPassChanged: (String) -> Unit,
    onSocksHandshakeTimeoutChanged: (String) -> Unit,
    onBaseEncodeDataChanged: (Boolean) -> Unit,
    onUploadCompressionTypeChanged: (String) -> Unit,
    onDownloadCompressionTypeChanged: (String) -> Unit,
    onLogLevelChanged: (String) -> Unit,
    onPacketDuplicationCountChanged: (String) -> Unit,
    onMaxPacketsPerBatchChanged: (String) -> Unit,
    onResolverBalancingStrategyChanged: (String) -> Unit,
    onMinUploadMtuChanged: (String) -> Unit,
    onMinDownloadMtuChanged: (String) -> Unit,
    onMaxUploadMtuChanged: (String) -> Unit,
    onMaxDownloadMtuChanged: (String) -> Unit,
    onMtuTestRetriesChanged: (String) -> Unit,
    onMtuTestTimeoutChanged: (String) -> Unit,
    onMaxConnectionAttemptsChanged: (String) -> Unit,
    onArqWindowSizeChanged: (String) -> Unit,
    onArqInitialRtoChanged: (String) -> Unit,
    onArqMaxRtoChanged: (String) -> Unit,
    onDnsQueryTimeoutChanged: (String) -> Unit,
    onNumRxWorkersChanged: (String) -> Unit,
    onNumDnsWorkersChanged: (String) -> Unit,
    onSocketBufferSizeChanged: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf(ConfigSection.ALL) }
    val normalizedQuery = query.trim().lowercase()

    fun matches(vararg terms: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        return terms.any { it.lowercase().contains(normalizedQuery) }
    }

    fun matchesSection(section: ConfigSection): Boolean {
        return selectedSection == ConfigSection.ALL || selectedSection == section
    }

    val showPolicySection = matchesSection(ConfigSection.POLICY) &&
        matches("policy", "protocol", "data encryption", "listen ip", "socks5")
    val showEncryptionMethod = matches("encryption method", "DATA_ENCRYPTION_METHOD")
    val showEncryptionKey = matches("encryption", "key", "ENCRYPTION_KEY")
    val showDomains = matches("domains", "DOMAINS")
    val showResolvers = matches("resolver", "dns", "RESOLVER_DNS_SERVERS")
    val showListenPort = matches("listen", "port", "LISTEN_PORT")
    val showSocksAuth = matches("socks", "auth", "SOCKS5_AUTH")
    val showSocksUser = matches("socks", "user", "SOCKS5_USER")
    val showSocksPass = matches("socks", "pass", "SOCKS5_PASS")
    val showSocksHandshakeTimeout = matches("socks", "handshake", "timeout", "SOCKS_HANDSHAKE_TIMEOUT")
    val showLogLevel = matches("log", "level", "LOG_LEVEL")
    val showBaseEncode = matches("base encode", "BASE_ENCODE_DATA")
    val showUploadCompression = matches("upload compression", "UPLOAD_COMPRESSION_TYPE")
    val showDownloadCompression = matches("download compression", "DOWNLOAD_COMPRESSION_TYPE")

    val showEssentialsSection = matchesSection(ConfigSection.ESSENTIALS) &&
        (showEncryptionKey || showDomains || showResolvers)
    val showProxySection = matchesSection(ConfigSection.PROXY) &&
        (showListenPort || showSocksAuth || showSocksUser || showSocksPass || showSocksHandshakeTimeout)
    val showCompatibilitySection = matchesSection(ConfigSection.TRANSPORT) &&
        (showBaseEncode || showUploadCompression || showDownloadCompression)
    val showDiagnosticsSection = matchesSection(ConfigSection.DIAGNOSTICS) && showLogLevel

    val showPacketDuplication = matches("packet", "duplication", "PACKET_DUPLICATION_COUNT")
    val showMaxBatch = matches("packets", "batch", "MAX_PACKETS_PER_BATCH")
    val showResolverBalancing = matches("resolver", "balancing", "RESOLVER_BALANCING_STRATEGY")
    val showMinUploadMtu = matches("min", "upload", "mtu", "MIN_UPLOAD_MTU")
    val showMinDownloadMtu = matches("min", "download", "mtu", "MIN_DOWNLOAD_MTU")
    val showMaxUploadMtu = matches("max", "upload", "mtu", "MAX_UPLOAD_MTU")
    val showMaxDownloadMtu = matches("max", "download", "mtu", "MAX_DOWNLOAD_MTU")
    val showMtuRetries = matches("mtu", "retries", "MTU_TEST_RETRIES")
    val showMtuTimeout = matches("mtu", "timeout", "MTU_TEST_TIMEOUT")
    val showConnectionAttempts = matches("connection", "attempt", "MAX_CONNECTION_ATTEMPTS")
    val showArqWindow = matches("arq", "window", "ARQ_WINDOW_SIZE")
    val showArqInitialRto = matches("arq", "initial", "rto", "ARQ_INITIAL_RTO")
    val showArqMaxRto = matches("arq", "max", "rto", "ARQ_MAX_RTO")
    val showDnsQueryTimeout = matches("dns", "query", "timeout", "DNS_QUERY_TIMEOUT")
    val showRxWorkers = matches("rx", "workers", "NUM_RX_WORKERS")
    val showDnsWorkers = matches("dns", "workers", "NUM_DNS_WORKERS")
    val showSocketBuffer = matches("socket", "buffer", "SOCKET_BUFFER_SIZE")

    val advancedOptionMatch = listOf(
        showPacketDuplication,
        showMaxBatch,
        showResolverBalancing,
        showMinUploadMtu,
        showMinDownloadMtu,
        showMaxUploadMtu,
        showMaxDownloadMtu,
        showMtuRetries,
        showMtuTimeout,
        showConnectionAttempts,
        showArqWindow,
        showArqInitialRto,
        showArqMaxRto,
        showDnsQueryTimeout,
        showRxWorkers,
        showDnsWorkers,
        showSocketBuffer,
    ).any { it }
    val hasAdvancedMatches = matchesSection(ConfigSection.ADVANCED) && advancedOptionMatch
    val showValidationSection = state.validationErrors.isNotEmpty() &&
        matchesSection(ConfigSection.ERRORS) &&
        matches("validation", "error")
    val advancedExpanded = showAdvanced || (selectedSection == ConfigSection.ADVANCED && advancedOptionMatch)

    val hasAnyMatch = showPolicySection ||
        showEssentialsSection ||
        showProxySection ||
        showCompatibilitySection ||
        showDiagnosticsSection ||
        hasAdvancedMatches ||
        showValidationSection

    VpnAppBackground {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                VpnHeroCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.config_hero_title),
                        subtitle = stringResource(R.string.config_hero_subtitle),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBadge(
                            label = stringResource(R.string.config_summary_domains),
                            value = state.config.domains.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricBadge(
                            label = stringResource(R.string.config_summary_resolvers),
                            value = state.config.resolverDnsServers.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricBadge(
                            label = stringResource(R.string.config_summary_errors),
                            value = state.validationErrors.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(text = stringResource(R.string.config_search_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CONFIG_SEARCH_TAG),
                        singleLine = true,
                    )
                    Text(
                        text = stringResource(R.string.config_sections_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ConfigSection.entries) { section ->
                            FilterChip(
                                selected = section == selectedSection,
                                onClick = { selectedSection = section },
                                label = { Text(text = stringResource(section.titleRes)) },
                            )
                        }
                    }
                }
            }

            if (showPolicySection) {
                item {
                    SectionCard(
                        title = stringResource(R.string.config_policy_title),
                        subtitle = stringResource(R.string.config_policy_subtitle),
                    ) {
                        ReadOnlySetting(
                            label = stringResource(R.string.config_protocol_type_label),
                            value = state.config.protocolType,
                        )
                        ReadOnlySetting(
                            label = stringResource(R.string.config_listen_ip_label),
                            value = state.config.listenIp,
                        )
                        if (showEncryptionMethod) {
                            SettingTextField(
                                label = stringResource(R.string.config_encryption_method_label),
                                value = state.config.dataEncryptionMethod.toString(),
                                onValueChange = onEncryptionMethodChanged,
                            )
                        }
                        Text(
                            text = stringResource(R.string.config_policy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.config_tcp_first_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.config_routing_tab_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (showEssentialsSection) {
                item {
                    SectionCard(
                        title = stringResource(R.string.config_essentials_title),
                        subtitle = stringResource(R.string.config_essentials_subtitle),
                    ) {
                        if (showEncryptionKey) {
                            SettingTextField(
                                label = stringResource(R.string.config_encryption_key_label),
                                value = state.config.encryptionKey,
                                onValueChange = onEncryptionKeyChanged,
                            )
                        }
                        if (showDomains) {
                            SettingTextField(
                                label = stringResource(R.string.config_domains_label),
                                value = state.config.domains.joinToString(","),
                                onValueChange = onDomainsChanged,
                            )
                        }
                        if (showResolvers) {
                            SettingTextField(
                                label = stringResource(R.string.config_resolvers_label),
                                value = state.config.resolverDnsServers.joinToString(","),
                                onValueChange = onResolversChanged,
                            )
                        }
                    }
                }
            }

            if (showProxySection) {
                item {
                    SectionCard(
                        title = stringResource(R.string.config_proxy_title),
                        subtitle = stringResource(R.string.config_proxy_subtitle),
                    ) {
                        if (showListenPort) {
                            SettingTextField(
                                label = stringResource(R.string.config_listen_port_label),
                                value = state.config.listenPort.toString(),
                                onValueChange = onListenPortChanged,
                            )
                        }
                        if (showSocksAuth) {
                            SettingSwitch(
                                label = stringResource(R.string.config_socks_auth_label),
                                checked = state.config.socks5Auth,
                                onCheckedChange = onSocksAuthChanged,
                            )
                        }
                        if (showSocksUser) {
                            SettingTextField(
                                label = stringResource(R.string.config_socks_user_label),
                                value = state.config.socks5User,
                                onValueChange = onSocksUserChanged,
                            )
                        }
                        if (showSocksPass) {
                            SettingTextField(
                                label = stringResource(R.string.config_socks_pass_label),
                                value = state.config.socks5Pass,
                                onValueChange = onSocksPassChanged,
                            )
                        }
                        if (showSocksHandshakeTimeout) {
                            SettingTextField(
                                label = stringResource(R.string.config_socks_handshake_timeout_label),
                                value = state.config.socksHandshakeTimeout.toString(),
                                onValueChange = onSocksHandshakeTimeoutChanged,
                            )
                        }
                    }
                }
            }

            if (showCompatibilitySection) {
                item {
                    SectionCard(
                        title = stringResource(R.string.config_transport_title),
                        subtitle = stringResource(R.string.config_transport_subtitle),
                    ) {
                        if (showBaseEncode) {
                            SettingSwitch(
                                label = stringResource(R.string.config_base_encode_label),
                                checked = state.config.baseEncodeData,
                                onCheckedChange = onBaseEncodeDataChanged,
                            )
                        }
                        if (showUploadCompression) {
                            SettingTextField(
                                label = stringResource(R.string.config_upload_compression_label),
                                value = state.config.uploadCompressionType.toString(),
                                onValueChange = onUploadCompressionTypeChanged,
                            )
                        }
                        if (showDownloadCompression) {
                            SettingTextField(
                                label = stringResource(R.string.config_download_compression_label),
                                value = state.config.downloadCompressionType.toString(),
                                onValueChange = onDownloadCompressionTypeChanged,
                            )
                        }
                        Text(
                            text = stringResource(R.string.config_transport_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (showDiagnosticsSection) {
                item {
                    SectionCard(
                        title = stringResource(R.string.config_diagnostics_title),
                        subtitle = stringResource(R.string.config_diagnostics_subtitle),
                    ) {
                        SettingTextField(
                            label = stringResource(R.string.config_log_level_label),
                            value = state.config.logLevel,
                            onValueChange = onLogLevelChanged,
                        )
                    }
                }
            }

            if (hasAdvancedMatches) {
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CONFIG_ADVANCED_TOGGLE_TAG),
                        onClick = { showAdvanced = !advancedExpanded },
                    ) {
                        Text(
                            text = stringResource(
                                if (advancedExpanded) {
                                    R.string.config_hide_advanced
                                } else {
                                    R.string.config_show_advanced
                                },
                            ),
                        )
                    }
                }
            }

            if (advancedExpanded && hasAdvancedMatches) {
                item {
                    SectionCard(
                        title = stringResource(R.string.config_advanced_title),
                        subtitle = stringResource(R.string.config_advanced_subtitle),
                    ) {
                        if (showPacketDuplication) {
                            SettingTextField(
                                label = stringResource(R.string.config_packet_duplication_label),
                                value = state.config.packetDuplicationCount.toString(),
                                onValueChange = onPacketDuplicationCountChanged,
                            )
                        }
                        if (showMaxBatch) {
                            SettingTextField(
                                label = stringResource(R.string.config_max_packets_batch_label),
                                value = state.config.maxPacketsPerBatch.toString(),
                                onValueChange = onMaxPacketsPerBatchChanged,
                            )
                        }
                        if (showResolverBalancing) {
                            SettingTextField(
                                label = stringResource(R.string.config_resolver_balancing_label),
                                value = state.config.resolverBalancingStrategy.toString(),
                                onValueChange = onResolverBalancingStrategyChanged,
                            )
                        }
                        if (showMinUploadMtu) {
                            SettingTextField(
                                label = stringResource(R.string.config_min_upload_mtu_label),
                                value = state.config.minUploadMtu.toString(),
                                onValueChange = onMinUploadMtuChanged,
                            )
                        }
                        if (showMinDownloadMtu) {
                            SettingTextField(
                                label = stringResource(R.string.config_min_download_mtu_label),
                                value = state.config.minDownloadMtu.toString(),
                                onValueChange = onMinDownloadMtuChanged,
                            )
                        }
                        if (showMaxUploadMtu) {
                            SettingTextField(
                                label = stringResource(R.string.config_max_upload_mtu_label),
                                value = state.config.maxUploadMtu.toString(),
                                onValueChange = onMaxUploadMtuChanged,
                            )
                        }
                        if (showMaxDownloadMtu) {
                            SettingTextField(
                                label = stringResource(R.string.config_max_download_mtu_label),
                                value = state.config.maxDownloadMtu.toString(),
                                onValueChange = onMaxDownloadMtuChanged,
                            )
                        }
                        if (showMtuRetries) {
                            SettingTextField(
                                label = stringResource(R.string.config_mtu_retries_label),
                                value = state.config.mtuTestRetries.toString(),
                                onValueChange = onMtuTestRetriesChanged,
                            )
                        }
                        if (showMtuTimeout) {
                            SettingTextField(
                                label = stringResource(R.string.config_mtu_timeout_label),
                                value = state.config.mtuTestTimeout.toString(),
                                onValueChange = onMtuTestTimeoutChanged,
                            )
                        }
                        if (showConnectionAttempts) {
                            SettingTextField(
                                label = stringResource(R.string.config_connection_attempts_label),
                                value = state.config.maxConnectionAttempts.toString(),
                                onValueChange = onMaxConnectionAttemptsChanged,
                            )
                        }
                        if (showArqWindow) {
                            SettingTextField(
                                label = stringResource(R.string.config_arq_window_label),
                                value = state.config.arqWindowSize.toString(),
                                onValueChange = onArqWindowSizeChanged,
                            )
                        }
                        if (showArqInitialRto) {
                            SettingTextField(
                                label = stringResource(R.string.config_arq_initial_rto_label),
                                value = state.config.arqInitialRto.toString(),
                                onValueChange = onArqInitialRtoChanged,
                            )
                        }
                        if (showArqMaxRto) {
                            SettingTextField(
                                label = stringResource(R.string.config_arq_max_rto_label),
                                value = state.config.arqMaxRto.toString(),
                                onValueChange = onArqMaxRtoChanged,
                            )
                        }
                        if (showDnsQueryTimeout) {
                            SettingTextField(
                                label = stringResource(R.string.config_dns_query_timeout_label),
                                value = state.config.dnsQueryTimeout.toString(),
                                onValueChange = onDnsQueryTimeoutChanged,
                            )
                        }
                        if (showRxWorkers) {
                            SettingTextField(
                                label = stringResource(R.string.config_rx_workers_label),
                                value = state.config.numRxWorkers.toString(),
                                onValueChange = onNumRxWorkersChanged,
                            )
                        }
                        if (showDnsWorkers) {
                            SettingTextField(
                                label = stringResource(R.string.config_dns_workers_label),
                                value = state.config.numDnsWorkers.toString(),
                                onValueChange = onNumDnsWorkersChanged,
                            )
                        }
                        if (showSocketBuffer) {
                            SettingTextField(
                                label = stringResource(R.string.config_socket_buffer_label),
                                value = state.config.socketBufferSize.toString(),
                                onValueChange = onSocketBufferSizeChanged,
                            )
                        }
                    }
                }
            }

            if (showValidationSection) {
                item {
                    SectionCard(
                        title = stringResource(R.string.config_validation_errors_title),
                        subtitle = stringResource(R.string.config_validation_errors_subtitle),
                    ) {
                        state.validationErrors.forEach { error ->
                            Text(
                                text = "• $error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (!hasAnyMatch) {
                item {
                    VpnCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.config_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    VpnCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title = title, subtitle = subtitle)
        content()
    }
}

@Composable
private fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReadOnlySetting(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
