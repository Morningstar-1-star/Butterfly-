package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.DefaultSuggestedChannels
import com.example.model.SubscribedChannel
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: MainViewModel,
    onSelectVideo: (VideoItem) -> Unit,
    onChannelClick: (String, String?) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 80.dp
) {
    val subscribedChannels by viewModel.subscribedChannels.collectAsState()
    val selectedChannelId by viewModel.selectedSubscriptionChannelId.collectAsState()
    val filterChip by viewModel.subscriptionFilterChip.collectAsState()
    val subscriptionVideos by viewModel.subscriptionVideos.collectAsState()
    val isSubscriptionLoading by viewModel.isSubscriptionLoading.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val trendingVideos by viewModel.trendingVideos.collectAsState()

    var showManageSubscriptionsSheet by remember { mutableStateOf(false) }

    val filterChips = listOf("All", "Today", "Videos", "Shorts", "Live", "Unwatched")

    // Filter videos by selected creator if one is picked from the top carousel
    val activeVideos = remember(subscriptionVideos, trendingVideos, selectedChannelId, subscribedChannels, filterChip) {
        val basePool = if (subscriptionVideos.isNotEmpty()) subscriptionVideos else trendingVideos
        val selectedSub = subscribedChannels.firstOrNull { it.id == selectedChannelId }

        val creatorFiltered = if (selectedSub != null) {
            basePool.filter {
                it.uploaderName.equals(selectedSub.name, ignoreCase = true) ||
                it.uploaderName.contains(selectedSub.name, ignoreCase = true) ||
                selectedSub.name.contains(it.uploaderName, ignoreCase = true)
            }
        } else {
            basePool
        }

        when (filterChip) {
            "Shorts" -> creatorFiltered.filter { it.durationSeconds in 1..65 }
            "Videos" -> creatorFiltered.filterNot { it.durationSeconds in 1..65 }
            "Unwatched" -> creatorFiltered.filter { (watchProgressMap[it.id] ?: 0f) < 0.1f }
            else -> creatorFiltered
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Subscriptions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { viewModel.loadSubscriptionFeed() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh Feed"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = bottomPadding + 32.dp)
        ) {
            // 1. TOP SUBSCRIBED CHANNELS CAROUSEL
            if (subscribedChannels.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(subscribedChannels) { channel ->
                            val isSelected = selectedChannelId == channel.id
                            SubscriptionChannelAvatarItem(
                                channel = channel,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        // Open full channel screen on second tap
                                        onChannelClick(channel.name, channel.avatarUrl)
                                    } else {
                                        viewModel.selectSubscriptionChannel(channel.id)
                                    }
                                },
                                onLongClick = {
                                    onChannelClick(channel.name, channel.avatarUrl)
                                }
                            )
                        }

                        item {
                            // "All" Button at end of carousel
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showManageSubscriptionsSheet = true }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ALL",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Manage",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. SUB-FILTER CHIPS ROW
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filterChips.forEach { chip ->
                            item {
                                FilterChip(
                                    selected = filterChip == chip,
                                    onClick = { viewModel.setSubscriptionFilterChip(chip) },
                                    label = { Text(chip, fontSize = 13.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                                        selectedLabelColor = MaterialTheme.colorScheme.background,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = null
                                )
                            }
                        }
                    }
                }
            }

            // 3. MAIN SUBSCRIPTION FEED
            if (isSubscriptionLoading && activeVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (subscribedChannels.isEmpty()) {
                // EMPTY STATE & SUGGESTED CHANNELS
                item {
                    SubscriptionEmptyStateWithRecommendations(
                        suggestedChannels = DefaultSuggestedChannels.list,
                        onSubscribe = { channel ->
                            viewModel.toggleSubscription(channel.name, channel.avatarUrl, channel.handle)
                        },
                        onChannelClick = onChannelClick
                    )
                }
            } else if (activeVideos.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Subscriptions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No updates in this filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'All' filter or select another creator to explore their latest uploads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(activeVideos, key = { "sub_${it.id}" }) { video ->
                    VideoCard(
                        video = video,
                        watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                        showProviderBadge = false,
                        onClick = { onSelectVideo(video) },
                        onChannelClick = { chName ->
                            val matchingSub = subscribedChannels.firstOrNull { it.name.equals(chName, ignoreCase = true) }
                            onChannelClick(chName, matchingSub?.avatarUrl ?: video.uploaderAvatarUrl)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // MANAGE ALL SUBSCRIPTIONS MODAL SHEET
    if (showManageSubscriptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManageSubscriptionsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            ManageSubscriptionsSheetContent(
                channels = subscribedChannels,
                onChannelClick = { ch ->
                    showManageSubscriptionsSheet = false
                    onChannelClick(ch.name, ch.avatarUrl)
                },
                onToggleNotification = { id ->
                    viewModel.toggleSubscriptionNotification(id)
                },
                onUnsubscribe = { ch ->
                    viewModel.toggleSubscription(ch.name, ch.avatarUrl, ch.handle)
                },
                onClose = { showManageSubscriptionsSheet = false }
            )
        }
    }
}

@Composable
private fun SubscriptionChannelAvatarItem(
    channel: SubscribedChannel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(58.dp),
            contentAlignment = Alignment.Center
        ) {
            // Avatar with highlight border if selected
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                        }
                    )
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Unread update indicator dot
            if (channel.hasUnreadUpdates && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SubscriptionEmptyStateWithRecommendations(
    suggestedChannels: List<SubscribedChannel>,
    onSubscribe: (SubscribedChannel) -> Unit,
    onChannelClick: (String, String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Icon(
            imageVector = Icons.Outlined.Subscriptions,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Don't miss a thing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Subscribe to your favorite creators to see their latest videos and updates here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Suggested Channels",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))

        suggestedChannels.forEach { channel ->
            SuggestedChannelCard(
                channel = channel,
                onSubscribe = { onSubscribe(channel) },
                onChannelClick = { onChannelClick(channel.name, channel.avatarUrl) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SuggestedChannelCard(
    channel: SubscribedChannel,
    onSubscribe: () -> Unit,
    onChannelClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChannelClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.subscriberCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (channel.description.isNotBlank()) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSubscribe,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Subscribe", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ManageSubscriptionsSheetContent(
    channels: List<SubscribedChannel>,
    onChannelClick: (SubscribedChannel) -> Unit,
    onToggleNotification: (String) -> Unit,
    onUnsubscribe: (SubscribedChannel) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "All Subscriptions (${channels.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(channels) { channel ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onChannelClick(channel) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!channel.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.avatarUrl,
                                contentDescription = channel.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = channel.name.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channel.subscriberCount,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { onToggleNotification(channel.id) }) {
                        Icon(
                            imageVector = if (channel.notificationEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                            contentDescription = "Notifications",
                            tint = if (channel.notificationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { onUnsubscribe(channel) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Unsubscribe",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
            }
        }
    }
}
