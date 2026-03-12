package com.masterdnsvpn.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masterdnsvpn.android.LogLine
import com.masterdnsvpn.android.R

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

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.logs_controls_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onClearLogs) {
                            Text(text = stringResource(R.string.logs_clear))
                        }
                        Button(onClick = onExportLogs) {
                            Text(text = stringResource(R.string.logs_export))
                        }
                        Button(
                            modifier = Modifier.testTag(LOGS_MODE_TAG),
                            onClick = { showFullLogs = !showFullLogs },
                        ) {
                            Text(
                                text = stringResource(
                                    if (showFullLogs) R.string.logs_show_tail else R.string.logs_show_full,
                                ),
                            )
                        }
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
            val timestamp = line.timestamp.ifBlank { "-" }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.logs_line_format, timestamp, line.level, line.message),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
