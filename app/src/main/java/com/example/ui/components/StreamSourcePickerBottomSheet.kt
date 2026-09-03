package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.util.StreamCategorizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSourcePickerBottomSheet(
    streamData: StreamData?,
    selectedOption: PlayableStreamOption?,
    onSelectOption: (PlayableStreamOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allOptions = remember(streamData) {
        streamData?.availableStreamOptions ?: emptyList()
    }

    var selectedSourceFilter by remember { mutableStateOf("All") }
    var selectedQualityFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    // Distinct sources present in the options
    val availableSources = remember(allOptions) {
        val sources = allOptions.map { it.detectedSourceName }.distinct().sorted()
        listOf("All") + sources
    }

    // Available quality categories present in options
    val qualityCategories = listOf("All", "Dolby Vision", "4K", "1080p", "720p", "480p", "HDR")

    // Filtered options based on source, quality, and search query
    val filteredOptions = remember(allOptions, selectedSourceFilter, selectedQualityFilter, searchQuery) {
        allOptions.filter { option ->
            val matchesSource = if (selectedSourceFilter == "All") true
            else option.detectedSourceName.equals(selectedSourceFilter, ignoreCase = true)

            val matchesQuality = when (selectedQualityFilter) {
                "All" -> true
                "Dolby Vision" -> option.detectedQualityCategory.equals("Dolby Vision", ignoreCase = true) ||
                        option.isDolbyVision || option.qualityLabel.contains("Dolby Vision", ignoreCase = true) ||
                        option.qualityLabel.contains(" DV", ignoreCase = true)
                "4K" -> option.detectedQualityCategory.equals("4K", ignoreCase = true) ||
                        option.qualityLabel.contains("2160", ignoreCase = true) || option.qualityLabel.contains("4K", ignoreCase = true)
                "1080p" -> option.detectedQualityCategory.equals("1080p", ignoreCase = true) ||
                        option.qualityLabel.contains("1080", ignoreCase = true)
                "720p" -> option.detectedQualityCategory.equals("720p", ignoreCase = true) ||
                        option.qualityLabel.contains("720", ignoreCase = true)
                "480p" -> option.detectedQualityCategory.equals("480p", ignoreCase = true) ||
                        option.qualityLabel.contains("480", ignoreCase = true)
                "HDR" -> option.detectedQualityCategory.equals("HDR", ignoreCase = true) ||
                        option.isHdr || option.qualityLabel.contains("HDR", ignoreCase = true)
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                option.qualityLabel.lowercase().contains(q) ||
                        option.releaseTitle.lowercase().contains(q) ||
                        option.detectedSourceName.lowercase().contains(q) ||
                        option.codec.lowercase().contains(q)
            }

            matchesSource && matchesQuality && matchesSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color(0xFF16161C),
        contentColor = Color.White,
        modifier = modifier.testTag("stream_source_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header: Title, Count, Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Streams & Sources",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${allOptions.size} streams available • Categorized",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Category 1: Sources (Torrentio, Vega, VidSrc, Seedr, YTS, etc.)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SOURCE PROVIDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.LightGray.copy(alpha = 0.6f)
                    )
                    if (selectedSourceFilter != "All" || selectedQualityFilter != "All") {
                        Text(
                            text = "Reset filters",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                selectedSourceFilter = "All"
                                selectedQualityFilter = "All"
                                searchQuery = ""
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableSources) { src ->
                        val isSelected = selectedSourceFilter == src
                        val count = if (src == "All") allOptions.size
                        else allOptions.count { it.detectedSourceName.equals(src, ignoreCase = true) }

                        val sourceColor = when {
                            src.contains("Vega", ignoreCase = true) -> Color(0xFF00E676)
                            src.contains("Torrentio", ignoreCase = true) -> Color(0xFF448AFF)
                            src.contains("VidSrc", ignoreCase = true) -> Color(0xFFFF9100)
                            src.contains("Seedr", ignoreCase = true) -> Color(0xFF00E5FF)
                            src.contains("YTS", ignoreCase = true) -> Color(0xFF26C6DA)
                            src.contains("Nyaa", ignoreCase = true) -> Color(0xFFFF4081)
                            else -> MaterialTheme.colorScheme.primary
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSourceFilter = src },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (src.contains("Vega", ignoreCase = true)) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.Black else sourceColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = src,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "($count)",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.Black.copy(alpha = 0.7f) else Color.Gray
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White,
                                selectedContainerColor = sourceColor,
                                selectedLabelColor = Color.Black
                            ),
                            border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Category 2: Quality (1080p, 720p, 480p, Dolby Vision, 4K, HDR)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "STREAM QUALITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.LightGray.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(qualityCategories) { q ->
                        val count = when (q) {
                            "All" -> allOptions.size
                            "Dolby Vision" -> allOptions.count {
                                it.detectedQualityCategory.equals("Dolby Vision", ignoreCase = true) ||
                                        it.isDolbyVision || it.qualityLabel.contains("Dolby Vision", ignoreCase = true) ||
                                        it.qualityLabel.contains(" DV", ignoreCase = true)
                            }
                            "4K" -> allOptions.count {
                                it.detectedQualityCategory.equals("4K", ignoreCase = true) ||
                                        it.qualityLabel.contains("2160", ignoreCase = true) || it.qualityLabel.contains("4K", ignoreCase = true)
                            }
                            "1080p" -> allOptions.count {
                                it.detectedQualityCategory.equals("1080p", ignoreCase = true) ||
                                        it.qualityLabel.contains("1080", ignoreCase = true)
                            }
                            "720p" -> allOptions.count {
                                it.detectedQualityCategory.equals("720p", ignoreCase = true) ||
                                        it.qualityLabel.contains("720", ignoreCase = true)
                            }
                            "480p" -> allOptions.count {
                                it.detectedQualityCategory.equals("480p", ignoreCase = true) ||
                                        it.qualityLabel.contains("480", ignoreCase = true)
                            }
                            "HDR" -> allOptions.count {
                                it.detectedQualityCategory.equals("HDR", ignoreCase = true) ||
                                        it.isHdr || it.qualityLabel.contains("HDR", ignoreCase = true)
                            }
                            else -> 0
                        }

                        // Only display filter chip if there are items or if it's "All"
                        if (count > 0 || q == "All") {
                            val isSelected = selectedQualityFilter == q
                            val isDolbyVision = q == "Dolby Vision"

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedQualityFilter = q },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isDolbyVision) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(if (isSelected) Color.Black else Color(0xFFD500F9))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "DV",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) Color(0xFFD500F9) else Color.White
                                                )
                                            }
                                        }
                                        Text(
                                            text = q,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "($count)",
                                            fontSize = 10.sp,
                                            color = if (isSelected) Color.Black.copy(alpha = 0.7f) else Color.Gray
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (isDolbyVision) Color(0xFF4A148C).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f),
                                    labelColor = Color.White,
                                    selectedContainerColor = if (isDolbyVision) Color(0xFFD500F9) else MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = if (isDolbyVision) Color.White else Color.Black
                                ),
                                border = if (isSelected) null else BorderStroke(
                                    1.dp,
                                    if (isDolbyVision) Color(0xFFD500F9).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Streams list
            if (filteredOptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterListOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No streams match your filter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Button(
                            onClick = {
                                selectedSourceFilter = "All"
                                selectedQualityFilter = "All"
                                searchQuery = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Reset Filters", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredOptions) { option ->
                        val isSelected = option == selectedOption ||
                                (selectedOption != null && option.videoUrl == selectedOption.videoUrl && option.videoUrl?.isNotBlank() == true)

                        StreamOptionCard(
                            option = option,
                            isSelected = isSelected,
                            onClick = {
                                onSelectOption(option)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamOptionCard(
    option: PlayableStreamOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val src = option.detectedSourceName
    val quality = option.detectedQualityCategory
    val size = option.detectedSize
    val seeders = option.detectedSeeders

    // Dynamic coloring based on source and quality
    val sourceColor = when {
        src.contains("Vega", ignoreCase = true) -> Color(0xFF00E676) // Emerald green
        src.contains("Torrentio", ignoreCase = true) -> Color(0xFF448AFF) // Neon blue
        src.contains("VidSrc", ignoreCase = true) -> Color(0xFFFF9100) // Vibrant orange
        src.contains("Seedr", ignoreCase = true) -> Color(0xFF00E5FF) // Cyan
        src.contains("YTS", ignoreCase = true) -> Color(0xFF26C6DA)
        src.contains("Nyaa", ignoreCase = true) -> Color(0xFFFF4081)
        else -> MaterialTheme.colorScheme.primary
    }

    val qualityBadgeColor = when {
        option.isDolbyVision || quality.contains("Dolby Vision", ignoreCase = true) -> Color(0xFFD500F9)
        quality.contains("4K", ignoreCase = true) -> Color(0xFFFF6D00)
        quality.contains("1080p", ignoreCase = true) -> Color(0xFF2979FF)
        quality.contains("720p", ignoreCase = true) -> Color(0xFF00B0FF)
        quality.contains("480p", ignoreCase = true) -> Color(0xFF9E9E9E)
        else -> Color(0xFF78909C)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .testTag("stream_option_item_${src}_${quality}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top row of pills: Source Badge, Quality Badge, Size, Seeds/Speed
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Source Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(sourceColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (src.contains("Vega", ignoreCase = true)) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = sourceColor,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = src,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = sourceColor
                            )
                        }
                    }

                    // Quality Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(qualityBadgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = quality,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    // Size Pill (if available)
                    if (size.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = size,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.LightGray
                            )
                        }
                    }

                    // Seeds / Cloud Stream Pill
                    if (seeders >= 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF00C853).copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                                Text(
                                    text = "$seeders seeds",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00E676)
                                )
                            }
                        }
                    } else if (src.contains("Vega", ignoreCase = true) || option.providerType == ProviderType.VEGA || option.providerType == ProviderType.DIRECT) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2979FF).copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Direct Stream",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF82B1FF)
                            )
                        }
                    }
                }

                // Full release label / details
                val displayLabel = if (option.releaseTitle.isNotBlank()) {
                    option.releaseTitle
                } else {
                    option.qualityLabel
                }

                Text(
                    text = displayLabel,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) Color.White else Color.LightGray,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right selection status
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "PLAYING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play this stream",
                        tint = Color.LightGray.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
