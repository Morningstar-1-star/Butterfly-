package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterDialog(
    currentFilter: SearchFilterState,
    availableProviders: List<ProviderUiItem>,
    onDismiss: () -> Unit,
    onApply: (SearchFilterState) -> Unit,
    onReset: () -> Unit
) {
    var tempFilter by remember(currentFilter) { mutableStateOf(currentFilter) }

    var expandedMenu by remember { mutableStateOf<String?>(null) } // "type", "source", "duration", "uploadDate", "sortBy"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF141416)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search filters",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    if (tempFilter.isActive) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "${tempFilter.activeFilterCount} active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 1. TYPE SELECTOR
                FilterDropdownRow(
                    label = "Type",
                    selectedText = tempFilter.type.label,
                    onOpenMenu = { expandedMenu = "type" }
                ) {
                    DropdownMenu(
                        expanded = expandedMenu == "type",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(Color(0xFF222226))
                    ) {
                        SearchTypeFilter.values().forEach { typeOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = typeOption.label,
                                        color = if (tempFilter.type == typeOption) MaterialTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (tempFilter.type == typeOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    if (tempFilter.type == typeOption) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    tempFilter = tempFilter.copy(type = typeOption)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. SOURCE / PROVIDER SELECTOR (App Capabilities)
                val providerLabel = remember(tempFilter.sourceProviderId, availableProviders) {
                    if (tempFilter.sourceProviderId == "ALL") "All Sources"
                    else availableProviders.firstOrNull { it.id.equals(tempFilter.sourceProviderId, ignoreCase = true) }?.name
                        ?: tempFilter.sourceProviderId.replaceFirstChar { it.uppercase() }
                }
                FilterDropdownRow(
                    label = "Source",
                    selectedText = providerLabel,
                    onOpenMenu = { expandedMenu = "source" }
                ) {
                    DropdownMenu(
                        expanded = expandedMenu == "source",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(Color(0xFF222226))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "All Sources",
                                    color = if (tempFilter.sourceProviderId == "ALL") MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = if (tempFilter.sourceProviderId == "ALL") FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (tempFilter.sourceProviderId == "ALL") {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                tempFilter = tempFilter.copy(sourceProviderId = "ALL")
                                expandedMenu = null
                            }
                        )
                        availableProviders.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = provider.name,
                                        color = if (tempFilter.sourceProviderId.equals(provider.id, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (tempFilter.sourceProviderId.equals(provider.id, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    if (tempFilter.sourceProviderId.equals(provider.id, ignoreCase = true)) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    tempFilter = tempFilter.copy(sourceProviderId = provider.id)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. DURATION SELECTOR
                FilterDropdownRow(
                    label = "Duration",
                    selectedText = tempFilter.duration.label,
                    onOpenMenu = { expandedMenu = "duration" }
                ) {
                    DropdownMenu(
                        expanded = expandedMenu == "duration",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(Color(0xFF222226))
                    ) {
                        SearchDurationFilter.values().forEach { dur ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = dur.label,
                                        color = if (tempFilter.duration == dur) MaterialTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (tempFilter.duration == dur) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    if (tempFilter.duration == dur) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    tempFilter = tempFilter.copy(duration = dur)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. UPLOAD DATE SELECTOR
                FilterDropdownRow(
                    label = "Upload date",
                    selectedText = tempFilter.uploadDate.label,
                    onOpenMenu = { expandedMenu = "uploadDate" }
                ) {
                    DropdownMenu(
                        expanded = expandedMenu == "uploadDate",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(Color(0xFF222226))
                    ) {
                        SearchUploadDateFilter.values().forEach { uDate ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = uDate.label,
                                        color = if (tempFilter.uploadDate == uDate) MaterialTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (tempFilter.uploadDate == uDate) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    if (tempFilter.uploadDate == uDate) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    tempFilter = tempFilter.copy(uploadDate = uDate)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. PRIORITISE / SORT BY SELECTOR
                FilterDropdownRow(
                    label = "Prioritise",
                    selectedText = tempFilter.sortBy.label,
                    onOpenMenu = { expandedMenu = "sortBy" }
                ) {
                    DropdownMenu(
                        expanded = expandedMenu == "sortBy",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(Color(0xFF222226))
                    ) {
                        SearchSortFilter.values().forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sortOption.label,
                                        color = if (tempFilter.sortBy == sortOption) MaterialTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (tempFilter.sortBy == sortOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    if (tempFilter.sortBy == sortOption) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    tempFilter = tempFilter.copy(sortBy = sortOption)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // FEATURES PILL CHIPS SECTION
                Text(
                    text = "Features",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeaturePill(
                        label = "4K / UHD",
                        selected = tempFilter.is4kOnly,
                        onClick = { tempFilter = tempFilter.copy(is4kOnly = !tempFilter.is4kOnly) }
                    )
                    FeaturePill(
                        label = "Full HD (1080p)",
                        selected = tempFilter.isFullHdOnly,
                        onClick = { tempFilter = tempFilter.copy(isFullHdOnly = !tempFilter.isFullHdOnly) }
                    )
                    FeaturePill(
                        label = "Direct Stream",
                        selected = tempFilter.isDirectStreamOnly,
                        onClick = {
                            tempFilter = tempFilter.copy(
                                isDirectStreamOnly = !tempFilter.isDirectStreamOnly
                            )
                        }
                    )
                    FeaturePill(
                        label = "Subtitles / CC",
                        selected = tempFilter.isSubtitlesOnly,
                        onClick = { tempFilter = tempFilter.copy(isSubtitlesOnly = !tempFilter.isSubtitlesOnly) }
                    )
                    FeaturePill(
                        label = "Unwatched",
                        selected = tempFilter.isUnwatchedOnly,
                        onClick = {
                            tempFilter = tempFilter.copy(
                                isUnwatchedOnly = !tempFilter.isUnwatchedOnly,
                                isWatchedOnly = if (!tempFilter.isUnwatchedOnly) false else tempFilter.isWatchedOnly
                            )
                        }
                    )
                    FeaturePill(
                        label = "Watched",
                        selected = tempFilter.isWatchedOnly,
                        onClick = {
                            tempFilter = tempFilter.copy(
                                isWatchedOnly = !tempFilter.isWatchedOnly,
                                isUnwatchedOnly = if (!tempFilter.isWatchedOnly) false else tempFilter.isUnwatchedOnly
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF2A2A2E), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // BOTTOM ACTION BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tempFilter.isActive) {
                        TextButton(
                            onClick = {
                                tempFilter = SearchFilterState()
                                onReset()
                            }
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF388E3C).copy(alpha = 0.9f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onApply(tempFilter)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Apply", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterDropdownRow(
    label: String,
    selectedText: String,
    onOpenMenu: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenMenu() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedText,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        content()
    }
}

@Composable
private fun FeaturePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF333338)
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF1E1E22)
    val textColor = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}
