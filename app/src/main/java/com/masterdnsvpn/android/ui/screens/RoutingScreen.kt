package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
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

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.showTcpFirstWarning) {
            item {
                RoutingSectionCard(title = stringResource(R.string.routing_warning_title)) {
                    Text(
                        text = stringResource(R.string.routing_warning_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            RoutingSectionCard(title = stringResource(R.string.routing_mode_title)) {
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
                RoutingSectionCard(title = stringResource(R.string.routing_quick_add_title)) {
                    if (quickAddApps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.routing_no_quick_apps),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        quickAddApps.forEach { app ->
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

            item {
                RoutingSectionCard(title = stringResource(R.string.routing_app_picker_title)) {
                    Text(
                        text = stringResource(R.string.routing_app_count, selectedPackages.size),
                        style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPackageToggled(app.packageName, app.packageName !in selectedPackages)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.packageName in selectedPackages,
                                onCheckedChange = null,
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                )
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RoutingSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}
