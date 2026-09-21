package com.example.ui.components

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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProviderUiItem
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
    val enabledProviderIds by viewModel.enabledProviderIds.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredProviders = remember(availableProviders, searchQuery) {
        if (searchQuery.isBlank()) {
            availableProviders
        } else {
            availableProviders.filter {
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
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Title and Mode Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (adultContentEnabled) "18+ Adult Sources" else "Streaming Sources",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (adultContentEnabled) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (adultContentEnabled)
                            "${filteredProviders.size} mature sources active"
                        else
                            "${filteredProviders.size} mainstream OTT & video platforms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Mode switch chip
                Surface(
                    onClick = {
                        val nextMode = !adultContentEnabled
                        viewModel.setAdultContentEnabled(nextMode)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (adultContentEnabled) Color(0xFFE91E63).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (adultContentEnabled) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (adultContentEnabled) Icons.Default.Explicit else Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = if (adultContentEnabled) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (adultContentEnabled) "18+ Mode" else "Mainstream",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (adultContentEnabled) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Search Bar for sources
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search sources (e.g. Tencent, XNXX, Hotstar)...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Provider Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredProviders, key = { it.id }) { provider ->
                    val isSelected = (activeProviderId == provider.id)
                    val brandColor = getProviderBrandColor(provider.id, adultContentEnabled)

                    Surface(
                        onClick = {
                            viewModel.setActiveProvider(provider.id)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            brandColor.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(1.5.dp, brandColor)
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("source_item_${provider.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(brandColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getProviderIcon(provider.id),
                                    contentDescription = null,
                                    tint = brandColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) brandColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (provider.id == "tencent") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF0052D9)
                                        ) {
                                            Text(
                                                text = "v.qq.com",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontSize = 10.sp
                                            )
                                        }
                                    } else if (provider.id == "xnxx" || provider.id == "hellporno") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFE91E63)
                                        ) {
                                            Text(
                                                text = "HD Tube",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontSize = 10.sp
                                            )
                                        }
                                    } else if (provider.id == "stripchat" || provider.id == "chaturbate") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFF5722)
                                        ) {
                                            Text(
                                                text = "● LIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = provider.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    fontSize = 11.sp
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = brandColor,
                                    modifier = Modifier.size(20.dp)
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
        "all" -> if (isAdult) Color(0xFFE91E63) else Color(0xFF6200EE)
        "youtube" -> Color(0xFFFF0000)
        "tencent" -> Color(0xFF0052D9)
        "sonyliv" -> Color(0xFF003087)
        "hotstar" -> Color(0xFF001435)
        "crunchyroll" -> Color(0xFFF47521)
        "bilibili" -> Color(0xFF00A1D6)
        "twitch" -> Color(0xFF9146FF)
        "bigo" -> Color(0xFF00E5FF)
        "amazonminitv" -> Color(0xFFFF9900)
        "discoveryplus" -> Color(0xFF00838F)
        "disney" -> Color(0xFF113CCF)
        "hbo" -> Color(0xFF5822B4)
        "curiositystream" -> Color(0xFFFFCC00)
        "googledrive" -> Color(0xFF4285F4)
        "imdb" -> Color(0xFFF5C518)
        "mxplayer" -> Color(0xFF0084FF)
        "popcorntv" -> Color(0xFFFF3366)
        "decryptor" -> Color(0xFF00E5FF)
        "vidsrc" -> Color(0xFFFF9100)
        "bun-tel-meg", "bunkr" -> Color(0xFF229ED9)
        "torrent" -> Color(0xFF4CAF50)
        // 18+ sources
        "supjav" -> Color(0xFFFF4081)
        "sextb" -> Color(0xFFE91E63)
        "123av", "javtiful", "jav_all" -> Color(0xFFD81B60)
        "pornhub" -> Color(0xFFFF9900)
        "xvideos" -> Color(0xFFD32F2F)
        "xnxx" -> Color(0xFF00B0FF)
        "hellporno" -> Color(0xFFFF1744)
        "stripchat" -> Color(0xFFFF3D00)
        "chaturbate" -> Color(0xFFFF6D00)
        "cam4", "cammodels" -> Color(0xFF9C27B0)
        "noodlemagazine" -> Color(0xFFFFC107)
        "thisvid" -> Color(0xFF3F51B5)
        "spankbang" -> Color(0xFFE91E63)
        "hanime1" -> Color(0xFFFF80AB)
        "hqporner" -> Color(0xFF00E676)
        else -> if (isAdult) Color(0xFFE91E63) else Color(0xFF2196F3)
    }
}

private fun getProviderIcon(id: String): ImageVector {
    return when (id) {
        "all" -> Icons.Default.Layers
        "youtube" -> Icons.Default.VideoLibrary
        "tencent" -> Icons.Default.LiveTv
        "sonyliv", "hotstar", "amazonminitv" -> Icons.Default.Tv
        "crunchyroll", "hanime1" -> Icons.Default.Animation
        "bilibili", "vimeo", "dailymotion" -> Icons.Default.OndemandVideo
        "twitch", "bigo", "stripchat", "chaturbate", "cam4", "cammodels" -> Icons.Default.Sensors
        "discoveryplus" -> Icons.Default.Public
        "disney", "hbo", "popcorntv", "imdb" -> Icons.Default.Movie
        "curiositystream" -> Icons.Default.Science
        "googledrive", "bun-tel-meg", "bunkr" -> Icons.Default.CloudQueue
        "torrent" -> Icons.Default.Download
        "decryptor", "vidsrc" -> Icons.Default.PlayCircleOutline
        "sextb", "supjav", "123av", "javtiful", "jav_all" -> Icons.Default.Explicit
        "xnxx", "hellporno", "pornhub", "xvideos" -> Icons.Default.LocalFireDepartment
        else -> Icons.Default.PlayArrow
    }
}
