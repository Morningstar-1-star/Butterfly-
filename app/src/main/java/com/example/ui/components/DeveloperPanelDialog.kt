package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.intelligence.SourceIntelligenceEngine
import com.example.plugin.manager.ProviderHealthMonitor
import com.example.plugin.manager.ProviderHealthRecord
import com.example.plugin.manager.ProviderHealthStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperPanelDialog(
    healthMonitor: ProviderHealthMonitor,
    rawJsonResponse: String = "",
    currentMagnetOrUrl: String = "",
    exoPlayerLogs: List<String> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val healthState by healthMonitor.healthState.collectAsState()

    val intelligenceEngine = remember { SourceIntelligenceEngine.getInstance(context) }
    val reportsState by intelligenceEngine.reportsState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Providers & Health", "ExoPlayer Logs", "JSON & Magnet", "Source Intelligence")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = "Developer Panel",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Developer Diagnostic Panel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Real-time Telemetry & Plugin Debugger",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Content View
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> ProviderHealthTab(healthRecords = healthState.values.toList(), intelReports = reportsState)
                        1 -> ExoPlayerLogsTab(logs = exoPlayerLogs)
                        2 -> JsonAndMagnetTab(rawJson = rawJsonResponse, magnetUrl = currentMagnetOrUrl)
                        3 -> SourceIntelligenceTab(reportsState = reportsState)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderHealthTab(
    healthRecords: List<ProviderHealthRecord>,
    intelReports: Map<String, com.example.intelligence.ProviderIntelligenceReport>
) {
    if (healthRecords.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No provider activity registered yet.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(healthRecords) { record ->
            val report = intelReports[record.providerId]
            val healthPct = if (record.successCount + record.failureCount > 0) {
                ((record.successCount.toFloat() / (record.successCount + record.failureCount)) * 100).toInt()
            } else 100

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (record.status) {
                                            ProviderHealthStatus.ALIVE -> Color(0xFF4CAF50)
                                            ProviderHealthStatus.SLOW -> Color(0xFFFF9800)
                                            ProviderHealthStatus.OFFLINE -> Color(0xFFF44336)
                                            ProviderHealthStatus.BLOCKED -> Color(0xFF9C27B0)
                                            ProviderHealthStatus.MAINTENANCE -> Color.Gray
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = record.providerName.ifBlank { record.providerId },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Score: ${String.format("%.1f", report?.intelligenceScore ?: 75.0)}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Latency: ${record.avgLatencyMs}ms",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Health: $healthPct%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (healthPct >= 80) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Text(
                            text = "OK: ${record.successCount} / FAIL: ${record.failureCount}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    if (record.lastFailureReason != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last Failure: ${record.lastFailureReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExoPlayerLogsTab(logs: List<String>) {
    val clipboardManager = LocalClipboardManager.current
    val combinedLogs = remember(logs) {
        if (logs.isEmpty()) "No active ExoPlayer logs captured yet." else logs.joinToString("\n")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Live Playback & Codec Events", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { clipboardManager.setText(AnnotatedString(combinedLogs)) }) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Logs")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .padding(12.dp)
        ) {
            LazyColumn {
                item {
                    Text(
                        text = combinedLogs,
                        color = Color(0xFF00FF66),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonAndMagnetTab(rawJson: String, magnetUrl: String) {
    val clipboardManager = LocalClipboardManager.current

    val infoHash = remember(magnetUrl) {
        if (magnetUrl.contains("btih:")) {
            magnetUrl.substringAfter("btih:").substringBefore("&")
        } else "N/A"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Torrent & Magnet Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("InfoHash: $infoHash", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (magnetUrl.isNotBlank()) magnetUrl else "No active magnet link.",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (magnetUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(magnetUrl)) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Copy Magnet URL")
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text("Raw JSON Response Payload", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 350.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121212))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (rawJson.isNotBlank()) rawJson else "{\n  \"status\": \"200 OK\",\n  \"message\": \"No recent raw response captured\"\n}",
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceIntelligenceTab(
    reportsState: Map<String, com.example.intelligence.ProviderIntelligenceReport>
) {
    if (reportsState.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Source Intelligence Engine collecting data...", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(reportsState.values.toList().sortedByDescending { it.intelligenceScore }) { report ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.providerId.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format("%.1f", report.intelligenceScore)} / 100",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (report.intelligenceScore >= 70) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (report.intelligenceScore / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (report.intelligenceScore >= 70) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Success Rate: ${String.format("%.0f", report.successRatePct)}%", style = MaterialTheme.typography.labelSmall)
                        Text("Avg Startup: ${report.avgStartupTimeMs}ms", style = MaterialTheme.typography.labelSmall)
                        Text("Buffering: ${report.bufferingEventsCount}", style = MaterialTheme.typography.labelSmall)
                        Text("Crashes: ${report.crashCount}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
