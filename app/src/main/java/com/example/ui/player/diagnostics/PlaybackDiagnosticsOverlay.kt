package com.example.ui.player.diagnostics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.player.cache.MediaCacheManager
import com.example.ui.player.metrics.PlaybackMetricsTracker

/**
 * Playback Diagnostics ("Stats for Nerds") HUD Overlay.
 * Provides transparent, real-time insight into video resolution, bitrate, download throughput,
 * buffer health, cache hit ratio, and rebuffer metrics.
 */
@Composable
fun PlaybackDiagnosticsOverlay(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { PlaybackMetricsTracker.setDiagnosticsOverlayVisible(false) }
) {
    val context = LocalContext.current
    val startupMs by PlaybackMetricsTracker.startupLatencyMs.collectAsState()
    val rebufferCount by PlaybackMetricsTracker.rebufferCount.collectAsState()
    val bufferSec by PlaybackMetricsTracker.bufferDurationSec.collectAsState()
    val bitrateKbps by PlaybackMetricsTracker.currentBitrateKbps.collectAsState()
    val resolution by PlaybackMetricsTracker.currentResolution.collectAsState()
    val throughputMbps by PlaybackMetricsTracker.measuredThroughputMbps.collectAsState()
    val netBytes by PlaybackMetricsTracker.networkBytesDownloaded.collectAsState()
    val cachedBytes by PlaybackMetricsTracker.cachedBytesRead.collectAsState()
    val cacheHitRatio by PlaybackMetricsTracker.cacheHitPercentage.collectAsState()
    val lastSeekLatency by PlaybackMetricsTracker.lastSeekLatencyMs.collectAsState()
    val cdnHost by PlaybackMetricsTracker.currentCdnHost.collectAsState()
    val connectionType by PlaybackMetricsTracker.connectionType.collectAsState()

    val netMb = remember(netBytes) { String.format("%.1f", netBytes / (1024f * 1024f)) }
    val cacheMb = remember(cachedBytes) { String.format("%.1f", cachedBytes / (1024f * 1024f)) }

    Box(
        modifier = modifier
            .padding(12.dp)
            .widthIn(max = 330.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE610141D))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Playback Stats",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Playback Diagnostics",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.8.dp)

            // Quality & Bitrate
            StatRow(
                icon = Icons.Default.HighQuality,
                label = "Resolution / Bitrate",
                value = if (bitrateKbps > 0) "$resolution @ ${(bitrateKbps / 1000f).let { String.format("%.1f", it) }} Mbps" else resolution,
                valueColor = Color(0xFF67E8F9)
            )

            // Speed & Throughput
            StatRow(
                icon = Icons.Default.Wifi,
                label = "Download Speed ($connectionType)",
                value = if (throughputMbps > 0f) "${String.format("%.1f", throughputMbps)} Mbps" else "Measuring...",
                valueColor = if (throughputMbps >= 10f) Color(0xFF4ADE80) else Color(0xFFFDE047)
            )

            // Buffer Health
            StatRow(
                icon = Icons.Default.HourglassBottom,
                label = "Buffer Depth",
                value = "${String.format("%.1f", bufferSec)}s ${if (bufferSec >= 10f) "(Safe)" else "(Buffering)"}",
                valueColor = if (bufferSec >= 8f) Color(0xFF4ADE80) else Color(0xFFF87171)
            )

            // Media Cache Savings
            StatRow(
                icon = Icons.Default.Storage,
                label = "Cache Efficiency",
                value = "${String.format("%.0f", cacheHitRatio)}% hit (${cacheMb} MB saved, ${netMb} MB net)",
                valueColor = Color(0xFFA78BFA)
            )

            // Latencies
            StatRow(
                icon = Icons.Default.Timer,
                label = "Startup / Seek Latency",
                value = "${if (startupMs > 0) "${startupMs}ms" else "--"} · Seek: ${if (lastSeekLatency > 0) "${lastSeekLatency}ms" else "--"}",
                valueColor = Color.White
            )

            // Rebuffer Count
            StatRow(
                icon = Icons.Default.Replay,
                label = "Rebuffers",
                value = if (rebufferCount == 0) "0 stalls (100% Smooth)" else "$rebufferCount stalls",
                valueColor = if (rebufferCount == 0) Color(0xFF4ADE80) else Color(0xFFF87171)
            )

            // CDN Host
            StatRow(
                icon = Icons.Default.Dns,
                label = "CDN Host",
                value = cdnHost,
                valueColor = Color.LightGray.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Action: Clear Cache
            Button(
                onClick = {
                    MediaCacheManager.clearCache(context)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Purge Media Byte Cache", fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun StatRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1.1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = Color.LightGray.copy(alpha = 0.9f)
            )
        }
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
