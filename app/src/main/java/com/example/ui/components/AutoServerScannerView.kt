package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NodeScanState
import com.example.model.ServerNode
import com.example.model.ServerScanState

@Composable
fun AutoServerScannerView(
    videoTitle: String,
    scanState: ServerScanState,
    onSelectNode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (scanState.nodes.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF141416).copy(alpha = 0.95f),
                        Color(0xFF0A0A0C).copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (scanState.isScanning) Color(0xFFFFD600).copy(alpha = 0.5f) else Color(0xFF00E5FF).copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
    ) {
        // Top Header Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = videoTitle.ifEmpty { "Torrent Stream Scanner" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scanState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color(0xFFFFD600),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = scanState.statusMessage,
                        fontSize = 11.sp,
                        color = if (scanState.isScanning) Color(0xFFFFD600) else Color(0xFF00E676),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Counter Badge: "3 ANALYZED    10 REMAINING"
            Surface(
                color = Color(0xFF1E1E24),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${scanState.analyzedCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD600)
                    )
                    Text(
                        text = " ANALYZED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "  /  ",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "${scanState.remainingCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (scanState.remainingCount > 0) Color(0xFF00E5FF) else Color(0xFF888888)
                    )
                    Text(
                        text = " REMAINING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Server Node Badges Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(scanState.nodes, key = { it.id }) { node ->
                val isSelected = (node.id == scanState.selectedNodeId)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (node.state == NodeScanState.SUCCESS) {
                                onSelectNode(node.id)
                            }
                        }
                        .padding(2.dp)
                ) {
                    // Circular Badge
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                when (node.state) {
                                    NodeScanState.SCANNING -> Color(0xFF1E1E22)
                                    NodeScanState.SUCCESS -> if (isSelected) Color(0xFF00C853) else Color(0xFF1B5E20)
                                    NodeScanState.FAILED -> Color(0xFFB71C1C)
                                }
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = when {
                                    isSelected -> Color(0xFFFFD600)
                                    node.state == NodeScanState.SCANNING -> Color(0xFFFFD600).copy(alpha = 0.4f)
                                    node.state == NodeScanState.SUCCESS -> Color(0xFF00E676).copy(alpha = 0.6f)
                                    else -> Color(0xFFFF5252).copy(alpha = 0.4f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (node.state) {
                            NodeScanState.SCANNING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFFFFD600),
                                    strokeWidth = 2.dp
                                )
                            }
                            NodeScanState.SUCCESS -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            NodeScanState.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Failed",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Node Server Name Pill
                    Surface(
                        color = when (node.state) {
                            NodeScanState.SUCCESS -> if (isSelected) Color(0xFF00E676) else Color(0xFF2E7D32).copy(alpha = 0.6f)
                            NodeScanState.FAILED -> Color(0xFFC62828).copy(alpha = 0.6f)
                            else -> Color(0xFF26262C)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.widthIn(min = 52.dp, max = 80.dp)
                    ) {
                        Text(
                            text = node.name,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
