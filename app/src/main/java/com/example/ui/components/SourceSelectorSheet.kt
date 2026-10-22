package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectorSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredProviders = remember(availableProviders, searchQuery, adultContentEnabled) {
        val baseList = availableProviders.filter { provider ->
            if (adultContentEnabled) {
                provider.id != "tencent" && (provider.id == "all" || viewModel.isAdultProviderId(provider.id))
            } else {
                !viewModel.isAdultProviderId(provider.id)
            }
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.id.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0A0E),
        scrimColor = Color.Black.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Minimal Header & Mode Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (adultContentEnabled) "18+ Sources" else "Content Sources",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${filteredProviders.size} active sources",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 11.sp
                    )
                }

                // Minimal Mode Switch Pill
                Surface(
                    onClick = {
                        viewModel.setAdultContentEnabled(!adultContentEnabled)
                    },
                    shape = RoundedCornerShape(50),
                    color = if (adultContentEnabled) Color(0xFFE91E63).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        if (adultContentEnabled) Color(0xFFE91E63).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (adultContentEnabled) Icons.Default.Explicit else Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = if (adultContentEnabled) Color(0xFFE91E63) else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (adultContentEnabled) "18+ Mode" else "Mainstream",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (adultContentEnabled) Color(0xFFE91E63) else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Minimal Filter Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Filter sources...",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF14141C),
                    unfocusedContainerColor = Color(0xFF121218),
                    focusedBorderColor = Color.White.copy(alpha = 0.22f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.07f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Clean Minimal Provider List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredProviders, key = { it.id }) { provider ->
                    val isSelected = (activeProviderId == provider.id)
                    val brandColor = getProviderBrandColor(provider.id, adultContentEnabled)

                    Surface(
                        onClick = {
                            viewModel.setActiveProvider(provider.id)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            brandColor.copy(alpha = 0.14f)
                        } else {
                            Color(0xFF121218)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) brandColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("source_item_${provider.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SourceBrandLogo(
                                providerId = provider.id,
                                size = 32.dp,
                                isAdultMode = adultContentEnabled
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f)
                                    )
                                    if (provider.id == "stripchat" || provider.id == "chaturbate" || provider.id == "cam4") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFF3D00).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "LIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFFF6E40),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = provider.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.48f),
                                    maxLines = 1,
                                    fontSize = 11.sp
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = brandColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getProviderBrandColor(id: String, isAdult: Boolean): Color {
    return when (id) {
        "all" -> if (isAdult) Color(0xFFE91E63) else Color(0xFF7C4DFF)
        "youtube" -> Color(0xFFFF1744)
        "tencent" -> Color(0xFF2979FF)
        "bilibili" -> Color(0xFF00B8D4)
        "dailymotion" -> Color(0xFF448AFF)
        "archive_org", "archive" -> Color(0xFF8D6E63)
        "torrent" -> Color(0xFF00E676)
        "supjav", "supajav" -> Color(0xFFFF4081)
        "sextb" -> Color(0xFFE91E63)
        "123av", "javtiful", "jav_all" -> Color(0xFFD81B60)
        "pornhub" -> Color(0xFFFF9900)
        "xvideos" -> Color(0xFFFF5252)
        "xnxx" -> Color(0xFF00B0FF)
        "hellporno" -> Color(0xFFFF1744)
        "stripchat" -> Color(0xFFFF3D00)
        "chaturbate" -> Color(0xFFFF6D00)
        "cam4", "cammodels" -> Color(0xFFAB47BC)
        "noodlemagazine" -> Color(0xFFFFC107)
        "thisvid" -> Color(0xFF5C6BC0)
        "spankbang" -> Color(0xFFE91E63)
        "eporner" -> Color(0xFFFF5722)
        "hanime1" -> Color(0xFFFF80AB)
        else -> if (isAdult) Color(0xFFE91E63) else Color(0xFF7C4DFF)
    }
}
