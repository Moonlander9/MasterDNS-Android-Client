package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.MainUiState
import com.masterdnsvpn.android.QuickAddPackages
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.VpnMode
import com.masterdnsvpn.android.ui.theme.MetricBadge
import com.masterdnsvpn.android.ui.theme.SectionTitle
import com.masterdnsvpn.android.ui.theme.StatusPill
import com.masterdnsvpn.android.ui.theme.VpnAppBackground
import com.masterdnsvpn.android.ui.theme.VpnCard
import com.masterdnsvpn.android.ui.theme.VpnHeroCard

const val ROUTING_MODE_FULL_TAG = "routing_mode_full"
const val ROUTING_MODE_SPLIT_TAG = "routing_mode_split"
const val ROUTING_APP_SEARCH_TAG = "routing_app_search"

@Composable
fun RoutingScreen(
    state: MainUiState,
    onVpnModeChanged: (VpnMode) -> Unit,
    onPackageToggled: (String, Boolean) -> Unit,
    onQuickAddPackage: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val selectedPackages = state.config.splitAllowlistPackages.toSet()
    val normalizedQuery = query.trim().lowercase()

    val quickAddApps = remember(state.installedApps) {
        val installedByPackage = state.installedApps.associateBy { it.packageName }
        QuickAddPackages.mapNotNull { installedByPackage[it] }
    }

    val filteredApps = remember(state.installedApps, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            state.installedApps
        } else {
            state.installedApps.filter {
                it.label.lowercase().contains(normalizedQuery) || it.packageName.lowercase().contains(normalizedQuery)
            }
        }
    }

    val splitErrors = state.validationErrors.filter { it.contains("SPLIT_ALLOWLIST") }

    VpnAppBackground {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                VpnHeroCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.routing_hero_title),
                        subtitle = stringResource(R.string.routing_hero_subtitle),
                    )
                    StatusPill(
                        label = if (state.config.vpnMode == VpnMode.FULL) {
                            stringResource(R.string.routing_mode_full)
                        } else {
                            stringResource(R.string.routing_mode_split)
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBadge(
                            label = stringResource(R.string.routing_metric_selected),
                            value = selectedPackages.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricBadge(
                            label = stringResource(R.string.routing_metric_installed),
                            value = state.installedApps.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.showTcpFirstWarning) {
                        Text(
                            text = stringResource(R.string.routing_warning_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                RoutingSectionCard(
                    title = stringResource(R.string.routing_mode_title),
                    subtitle = stringResource(R.string.routing_mode_subtitle),
                ) {
                    RoutingModeRow(
                        modifier = Modifier.testTag(ROUTING_MODE_FULL_TAG),
                        selected = state.config.vpnMode == VpnMode.FULL,
                        title = stringResource(R.string.routing_mode_full),
                        description = stringResource(R.string.routing_mode_full_desc),
                        onClick = { onVpnModeChanged(VpnMode.FULL) },
                    )
                    RoutingModeRow(
                        modifier = Modifier.testTag(ROUTING_MODE_SPLIT_TAG),
                        selected = state.config.vpnMode == VpnMode.SPLIT_ALLOWLIST,
                        title = stringResource(R.string.routing_mode_split),
                        description = stringResource(R.string.routing_mode_split_desc),
                        onClick = { onVpnModeChanged(VpnMode.SPLIT_ALLOWLIST) },
                    )
                }
            }

            if (state.config.vpnMode == VpnMode.SPLIT_ALLOWLIST) {
                item {
                    RoutingSectionCard(
                        title = stringResource(R.string.routing_quick_add_title),
                        subtitle = stringResource(R.string.routing_quick_add_subtitle),
                    ) {
                        if (quickAddApps.isEmpty()) {
                            Text(
                                text = stringResource(R.string.routing_no_quick_apps),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(quickAddApps, key = { it.packageName }) { app ->
                                    FilterChip(
                                        selected = app.packageName in selectedPackages,
                                        onClick = {
                                            if (app.packageName in selectedPackages) {
                                                onPackageToggled(app.packageName, false)
                                            } else {
                                                onQuickAddPackage(app.packageName)
                                            }
                                        },
                                        label = { Text(text = app.label) },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    RoutingSectionCard(
                        title = stringResource(R.string.routing_app_picker_title),
                        subtitle = stringResource(R.string.routing_app_picker_subtitle),
                    ) {
                        Text(
                            text = stringResource(R.string.routing_app_count, selectedPackages.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(text = stringResource(R.string.routing_app_search_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ROUTING_APP_SEARCH_TAG),
                            singleLine = true,
                        )
                        splitErrors.forEach { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.routing_no_apps),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        VpnCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPackageToggled(app.packageName, app.packageName !in selectedPackages)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = app.packageName in selectedPackages,
                                    onCheckedChange = null,
                                )
                                Column(
                                    modifier = Modifier.padding(start = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutingModeRow(
    modifier: Modifier = Modifier,
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    VpnCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoutingSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    VpnCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title = title, subtitle = subtitle)
        content()
    }
}
