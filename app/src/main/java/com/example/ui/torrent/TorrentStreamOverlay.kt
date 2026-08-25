package com.example.ui.torrent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.torrent.model.TorrentEngineState
import com.example.torrent.model.TorrentEngineStats

@Composable
fun TorrentStreamOverlay(
    stats: TorrentEngineStats,
    modifier: Modifier = Modifier
) {
    if (stats.state == TorrentEngineState.IDLE) return

    var isExpanded by remember { mutableStateOf(false) }

    val formattedSpeed = remember(stats.downloadSpeedBps) {
        val speedMb = stats.downloadSpeedBps / (1024.0 * 1024.0)
        if (speedMb >= 1.0) {
            String.format("%.1f MB/s", speedMb)
        } else {
            String.format("%d KB/s", stats.downloadSpeedBps / 1024)
        }
    }

    Box(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            // Summary Pill Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live Indicator Dot
                val dotColor = when (stats.state) {
                    TorrentEngineState.STREAMING -> Color(0xFF00E676)
                    TorrentEngineState.BUFFERING -> Color(0xFFFFB300)
                    TorrentEngineState.CONNECTING_TRACKERS, TorrentEngineState.FETCHING_METADATA -> Color(0xFF29B6F6)
                    TorrentEngineState.ERROR -> Color(0xFFFF5252)
                    else -> Color.Gray
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                Text(
                    text = "P2P Stream: $formattedSpeed",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )

                Text(
                    text = "${stats.connectedPeers} peers",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Expanded Telemetry Detail
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .widthIn(min = 200.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "File: ${stats.activeFileName}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buffer Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Playback Buffer",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${(stats.bufferProgress * 100).toInt()}%",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { stats.bufferProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF00E676),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Active Seeders: ${stats.activeSeeders}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Pieces: ${stats.downloadedPiecesCount}/${stats.totalPieces}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
