package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.LogLine
import com.masterdnsvpn.android.R
import com.masterdnsvpn.android.ui.theme.MetricBadge
import com.masterdnsvpn.android.ui.theme.SectionTitle
import com.masterdnsvpn.android.ui.theme.VpnAppBackground
import com.masterdnsvpn.android.ui.theme.VpnCard
import com.masterdnsvpn.android.ui.theme.VpnHeroCard

const val LOGS_MODE_TAG = "logs_mode"

private enum class LogLevelFilter {
    ALL,
    INFO,
    WARN,
    ERROR,
}

@Composable
fun LogsScreen(
    logs: List<LogLine>,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit,
) {
    var showFullLogs by rememberSaveable { mutableStateOf(false) }
    var levelFilter by rememberSaveable { mutableStateOf(LogLevelFilter.ALL) }

    val baseLogs = if (showFullLogs) logs else logs.takeLast(200)
    val filteredLogs = baseLogs.filter { line ->
        when (levelFilter) {
            LogLevelFilter.ALL -> true
            LogLevelFilter.INFO -> line.level.uppercase().contains("INFO")
            LogLevelFilter.WARN -> line.level.uppercase().contains("WARN")
            LogLevelFilter.ERROR -> line.level.uppercase().contains("ERROR")
        }
    }

    VpnAppBackground {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                VpnHeroCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.logs_hero_title),
                        subtitle = stringResource(R.string.logs_hero_subtitle),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricBadge(
                            label = stringResource(R.string.logs_summary_visible),
                            value = filteredLogs.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricBadge(
                            label = stringResource(R.string.logs_summary_total),
                            value = logs.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                VpnCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = stringResource(R.string.logs_controls_title),
                        subtitle = stringResource(R.string.logs_controls_subtitle),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onClearLogs, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.logs_clear))
                        }
                        Button(onClick = onExportLogs, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.logs_export))
                        }
                    }
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(LOGS_MODE_TAG),
                        onClick = { showFullLogs = !showFullLogs },
                    ) {
                        Text(
                            text = stringResource(
                                if (showFullLogs) R.string.logs_show_tail else R.string.logs_show_full,
                            ),
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LogLevelFilter.entries) { filter ->
                            FilterChip(
                                selected = filter == levelFilter,
                                onClick = { levelFilter = filter },
                                label = {
                                    Text(
                                        text = when (filter) {
                                            LogLevelFilter.ALL -> stringResource(R.string.logs_filter_all)
                                            LogLevelFilter.INFO -> stringResource(R.string.logs_filter_info)
                                            LogLevelFilter.WARN -> stringResource(R.string.logs_filter_warn)
                                            LogLevelFilter.ERROR -> stringResource(R.string.logs_filter_error)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.logs_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(
                items = filteredLogs,
                key = { line -> "${line.timestamp}-${line.level}-${line.message}" },
            ) { line ->
                LogCard(line = line)
            }
        }
    }
}

@Composable
private fun LogCard(line: LogLine) {
    val accent = when {
        line.level.contains("ERROR", ignoreCase = true) -> Color(0xFFFF8E8E)
        line.level.contains("WARN", ignoreCase = true) -> Color(0xFFFFC980)
        else -> Color(0xFF4AE3C6)
    }
    val timestamp = line.timestamp.ifBlank { "-" }

    VpnCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.08f))
                    .padding(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.logs_line_format, timestamp, line.level, line.message),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
