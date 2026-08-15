package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppScreen
import com.example.model.SubscribedChannel
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.util.ChannelLogoHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: MainViewModel,
    onSelectVideo: (VideoItem) -> Unit,
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 108.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 160.dp
) {
    val subscribedChannels by viewModel.subscribedChannels.collectAsState()
    val selectedChannelId by viewModel.selectedSubscriptionChannelId.collectAsState()
    val activeFilterChip by viewModel.subscriptionFilterChip.collectAsState()

    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val recommendedVideos by viewModel.recommendedVideos.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()

    var showManageSheet by remember { mutableStateOf(false) }
    var selectedVideoForMenu by remember { mutableStateOf<VideoItem?>(null) }
    var showMenuBottomSheet by remember { mutableStateOf(false) }

    // Only filter videos strictly from subscribed channels (NO demo feed)
    val feedVideos = remember(
        subscribedChannels,
        selectedChannelId,
        activeFilterChip,
        trendingVideos,
        searchResults,
        recommendedVideos,
        adultContentEnabled
    ) {
        if (subscribedChannels.isEmpty()) {
            return@remember emptyList<VideoItem>()
        }

        val allPool = (trendingVideos + recommendedVideos + searchResults)
            .distinctBy { "${it.providerId}_${it.id}" }
            .filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }

        val selectedChannel = subscribedChannels.find { it.id == selectedChannelId }

        val byChannel = if (selectedChannel != null) {
            val sName = selectedChannel.name.trim().lowercase()
            allPool.filter { item ->
                val uName = item.uploaderName.trim().lowercase()
                uName.contains(sName) || sName.contains(uName)
            }
        } else {
            val subNames = subscribedChannels.map { it.name.trim().lowercase() }
            allPool.filter { item ->
                val uName = item.uploaderName.trim().lowercase()
                subNames.any { uName.contains(it) || it.contains(uName) }
            }
        }

        // Apply filter chip
        when (activeFilterChip) {
            "Shorts" -> byChannel.filter { it.durationSeconds in 1..90 || it.title.contains("#shorts", ignoreCase = true) }
            "Live" -> byChannel.filter { it.title.contains("live", ignoreCase = true) || it.uploadDate?.contains("stream", ignoreCase = true) == true }
            "Podcasts" -> byChannel.filter { it.title.contains("podcast", ignoreCase = true) || it.title.contains("episode", ignoreCase = true) }
            "Today" -> byChannel.take(8)
            else -> byChannel
        }
    }

    val filterChips = listOf("All", "Today", "Videos", "Shorts", "Live", "Podcasts")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (subscribedChannels.isEmpty()) {
            // Pristine Empty State - User is following nobody
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topPadding,
                        bottom = bottomPadding,
                        start = 32.dp,
                        end = 32.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Subscriptions,
                        contentDescription = "Subscriptions",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Don't miss a new video",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Subscribe to channels to see their latest updates and videos appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.navigateToScreen(AppScreen.HOME) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Explore Videos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // User has active subscriptions
            val pullRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isLoadingTrending,
                onRefresh = { viewModel.refreshFeed() },
                state = pullRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = isLoadingTrending,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPadding + 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topPadding,
                        bottom = bottomPadding
                    )
                ) {
                    // 1. TOP SUBSCRIBED CHANNELS AVATAR CAROUSEL
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp)
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(subscribedChannels, key = { it.id }) { channel ->
                                    val isSelected = (selectedChannelId == channel.id)
                                    ChannelAvatarBadge(
                                        channel = channel,
                                        isSelected = isSelected,
                                        onClick = {
                                            viewModel.selectSubscriptionChannel(channel.id)
                                        }
                                    )
                                }

                                // "All" button at the end to view and manage all subscriptions
                                item {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        TextButton(
                                            onClick = { showManageSheet = true },
                                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                        ) {
                                            Text(
                                                text = "All",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. FILTER CHIPS ROW
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            items(filterChips) { chip ->
                                val isSelected = (activeFilterChip == chip)
                                FilterChipPill(
                                    label = chip,
                                    isSelected = isSelected,
                                    onClick = { viewModel.setSubscriptionFilterChip(chip) }
                                )
                            }
                        }
                    }

                    // 3. VIDEO FEED ITEMS FOR SUBSCRIBED CHANNELS
                    if (feedVideos.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.VideoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val selectedCh = subscribedChannels.find { it.id == selectedChannelId }
                                    Text(
                                        text = if (selectedCh != null) "No videos found for ${selectedCh.name}" else "No new videos from your subscriptions",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Try switching back to 'All' or pulling down to refresh.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (selectedChannelId != null || activeFilterChip != "All") {
                                        Button(
                                            onClick = {
                                                viewModel.selectSubscriptionChannel(null)
                                                viewModel.setSubscriptionFilterChip("All")
                                            }
                                        ) {
                                            Text("Show All Subscriptions")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(feedVideos, key = { "${it.providerId}_${it.id}" }) { video ->
                            SubscriptionVideoCard(
                                video = video,
                                isSubscribed = viewModel.isSubscribed(video.uploaderName),
                                onClick = { onSelectVideo(video) },
                                onMoreClick = {
                                    selectedVideoForMenu = video
                                    showMenuBottomSheet = true
                                },
                                onChannelClick = { ch -> viewModel.openChannel(ch) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Manage Subscriptions Bottom Sheet
    if (showManageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ManageSubscriptionsSheetContent(
                channels = subscribedChannels,
                onToggleNotification = { viewModel.toggleSubscriptionNotification(it) },
                onUnsubscribe = { channel ->
                    viewModel.toggleSubscription(channel.name)
                },
                onClose = { showManageSheet = false }
            )
        }
    }

    // Video Context Menu Bottom Sheet
    if (showMenuBottomSheet && selectedVideoForMenu != null) {
        val targetVideo = selectedVideoForMenu!!
        ModalBottomSheet(
            onDismissRequest = { showMenuBottomSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = targetVideo.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                ListItem(
                    headlineContent = { Text("Save to Watch later") },
                    leadingContent = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.addToWatchLater(targetVideo)
                        showMenuBottomSheet = false
                    }
                )

                ListItem(
                    headlineContent = { Text("Add to playlist") },
                    leadingContent = { Icon(Icons.Outlined.PlaylistAdd, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showMenuBottomSheet = false
                    }
                )

                ListItem(
                    headlineContent = { Text("Not interested") },
                    leadingContent = { Icon(Icons.Outlined.NotInterested, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.markNotInterested(targetVideo)
                        showMenuBottomSheet = false
                    }
                )

                val isChannelSub = viewModel.isSubscribed(targetVideo.uploaderName)
                ListItem(
                    headlineContent = {
                        Text(if (isChannelSub) "Unsubscribe from ${targetVideo.uploaderName}" else "Subscribe to ${targetVideo.uploaderName}")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (isChannelSub) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.toggleSubscription(targetVideo.uploaderName, targetVideo.thumbnailUrl)
                        showMenuBottomSheet = false
                    }
                )
            }
        }
    }
}

/**
 * Top Avatar Badge with unread notification dot and channel name underneath
 * (Directly recreating the YouTube top carousel in the uploaded screenshot).
 */
@Composable
fun ChannelAvatarBadge(
    channel: SubscribedChannel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val yellowAccent = Color(0xFFFFD600)
    val avatarBg = remember(channel.name, channel.avatarUrl) {
        ChannelLogoHelper.getBrandInfo(channel.name, channel.avatarUrl).backgroundColor
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ring when selected
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.border(2.dp, yellowAccent, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(if (isSelected) 46.dp else 50.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 46.dp else 50.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(avatarBg, Color(0xFF111111)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.name.take(2).uppercase(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Blue Dot Indicator (Matches the YouTube screenshot's blue unread dot)
            if (channel.hasUnreadUpdates) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0070FF)) // Vivid blue dot
                        .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = channel.name,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) yellowAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp
        )
    }
}

/**
 * Filter Chip Pill (All, Today, Videos, Shorts, Live, Podcasts, Posts)
 */
@Composable
fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val yellowAccent = Color(0xFFFFD600)
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .height(34.dp)
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * Full-width YouTube-style Subscriptions Video Card
 */
@Composable
fun SubscriptionVideoCard(
    video: VideoItem,
    isSubscribed: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null
) {
    val brandInfo = remember(video.uploaderName, video.uploaderAvatarUrl, video.title) {
        ChannelLogoHelper.getBrandInfo(video.uploaderName, video.uploaderAvatarUrl, video.title)
    }

    val targetChannelName = remember(video.uploaderName, brandInfo.brandName) {
        if (video.uploaderName.isBlank() || video.uploaderName.lowercase().contains("tv network") || video.uploaderName == "T" || video.uploaderName == "Hollywood Cinema") brandInfo.brandName else video.uploaderName
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(bottom = 18.dp)
    ) {
        // Thumbnail with Duration Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF1E1E1E))
        ) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            // Duration badge in bottom right
            val durationText = if (video.displayDuration.isNotBlank()) video.displayDuration else formatDuration(video.durationSeconds)
            if (durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Details Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar / Logo
            val firstLogo = brandInfo.logoUrls.firstOrNull()
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(brandInfo.backgroundColor)
                    .clickable(enabled = onChannelClick != null) { onChannelClick?.invoke(targetChannelName) },
                contentAlignment = Alignment.Center
            ) {
                if (!firstLogo.isNullOrBlank()) {
                    AsyncImage(
                        model = firstLogo,
                        contentDescription = targetChannelName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Text(
                        text = targetChannelName.take(1).uppercase(),
                        color = brandInfo.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title and Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = targetChannelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onChannelClick != null) {
                        Modifier.clickable { onChannelClick(targetChannelName) }
                    } else Modifier
                )

                Spacer(modifier = Modifier.height(2.dp))

                val metaText = buildString {
                    if (video.formattedViews.isNotEmpty()) {
                        append(video.formattedViews)
                    } else if (video.viewCount != null && video.viewCount > 0) {
                        append(formatViews(video.viewCount))
                    }
                    if (!video.uploadDate.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(video.uploadDate)
                    }
                }

                if (metaText.isNotEmpty()) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3-Dots Action Menu
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Manage Subscriptions Sheet (View all subscribed channels with notifications and unsubscribe)
 */
@Composable
fun ManageSubscriptionsSheetContent(
    channels: List<SubscribedChannel>,
    onToggleNotification: (String) -> Unit,
    onUnsubscribe: (SubscribedChannel) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All Subscriptions (${channels.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!channel.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.avatarUrl,
                                contentDescription = channel.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ChannelLogoHelper.getBrandInfo(channel.name, channel.avatarUrl).backgroundColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = channel.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = channel.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = channel.subscriberCount,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onToggleNotification(channel.id) }) {
                            Icon(
                                imageVector = if (channel.notificationEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                contentDescription = "Notifications",
                                tint = if (channel.notificationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { onUnsubscribe(channel) },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Subscribed", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

private fun formatViews(views: Long): String {
    return when {
        views >= 1_000_000 -> String.format("%.1fM views", views / 1_000_000.0)
        views >= 1_000 -> String.format("%.1fk views", views / 1_000.0)
        else -> "$views views"
    }
}
