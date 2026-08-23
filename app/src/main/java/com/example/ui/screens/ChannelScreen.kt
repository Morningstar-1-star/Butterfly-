package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ChannelDetails
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.VideoCard
import com.example.util.ChannelLogoHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    viewModel: MainViewModel,
    onSelectVideo: (VideoItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 80.dp
) {
    val channelDetails by viewModel.channelDetails.collectAsState()
    val isChannelLoading by viewModel.isChannelLoading.collectAsState()
    val selectedChannelName by viewModel.selectedChannelName.collectAsState()
    val selectedChannelAvatarUrl by viewModel.selectedChannelAvatarUrl.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Home", "Videos", "Shorts", "Playlists", "About")
    var videoFilterChip by remember { mutableStateOf("Latest") }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showSubscribeMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var channelSearchQuery by remember { mutableStateOf("") }
    var showOptionsMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currentDetails = channelDetails ?: ChannelDetails(
        channelId = (selectedChannelName ?: "channel").lowercase().replace(" ", "_"),
        name = selectedChannelName ?: "Creator Channel",
        handle = "@${(selectedChannelName ?: "channel").replace(" ", "").lowercase()}",
        avatarUrl = selectedChannelAvatarUrl,
        subscriberCount = "Verified Creator",
        isSubscribed = viewModel.isSubscribed(selectedChannelName ?: "")
    )

    val displayedVideos = remember(currentDetails.videos, videoFilterChip, channelSearchQuery) {
        var list = currentDetails.videos
        if (channelSearchQuery.isNotBlank()) {
            list = list.filter { it.title.contains(channelSearchQuery, ignoreCase = true) }
        }
        when (videoFilterChip) {
            "Popular" -> list.sortedByDescending { it.viewCount }
            "Oldest" -> list.reversed()
            else -> list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = channelSearchQuery,
                            onValueChange = { channelSearchQuery = it },
                            placeholder = { Text("Search channel videos...", fontSize = 15.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = currentDetails.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            channelSearchQuery = ""
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search"
                        )
                    }
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More"
                            )
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Channel") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    showOptionsMenu = false
                                    val shareIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Check out ${currentDetails.name} on YouTube: https://www.youtube.com/${currentDetails.handle}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Channel"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentDetails.isSubscribed) "Unsubscribe" else "Subscribe") },
                                leadingIcon = { Icon(if (currentDetails.isSubscribed) Icons.Filled.NotificationsOff else Icons.Filled.Notifications, null) },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.toggleSubscription(currentDetails.name, currentDetails.avatarUrl, currentDetails.handle)
                                }
                            )
                        }
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
            // 1. Channel Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        )
                ) {
                    if (!currentDetails.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentDetails.bannerUrl,
                            contentDescription = "Channel Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                                )
                            )
                    )
                }
            }

            // 2. Channel Header Info
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentDetails.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentDetails.avatarUrl,
                                    contentDescription = currentDetails.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = currentDetails.name.take(2).uppercase(),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Name & Stats
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentDetails.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentDetails.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${currentDetails.handle} • ${currentDetails.subscriberCount} • ${currentDetails.videoCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Description snippet with "...more"
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showAboutSheet = true }
                            ) {
                                Text(
                                    text = currentDetails.description.take(65).trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = " ...more",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Subscribe Button (Full Width YouTube Style)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (currentDetails.isSubscribed) {
                            OutlinedButton(
                                onClick = { showSubscribeMenu = true },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = "Subscribed",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Subscribed",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSubscribeMenu,
                                onDismissRequest = { showSubscribeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Notifications") },
                                    leadingIcon = { Icon(Icons.Filled.NotificationsActive, null) },
                                    onClick = { showSubscribeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Personalized") },
                                    leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                                    onClick = { showSubscribeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null) },
                                    onClick = { showSubscribeMenu = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Unsubscribe", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showSubscribeMenu = false
                                        viewModel.toggleSubscription(currentDetails.name, currentDetails.avatarUrl, currentDetails.handle)
                                    }
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.toggleSubscription(currentDetails.name, currentDetails.avatarUrl, currentDetails.handle)
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Subscribe",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. Channel Tabs Row
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 1.dp
                        )
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
            }

            // 4. Tab Content
            when (selectedTabIndex) {
                0 -> { // HOME TAB
                    if (isChannelLoading && displayedVideos.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else if (displayedVideos.isEmpty()) {
                        item {
                            ChannelEmptyState(creatorName = currentDetails.name)
                        }
                    } else {
                        // Featured / Latest video
                        item {
                            val hero = displayedVideos.first()
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Latest Release",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                VideoCard(
                                    video = hero,
                                    watchProgressFraction = watchProgressMap[hero.id] ?: 0f,
                                    showProviderBadge = false,
                                    onClick = { onSelectVideo(hero) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Shorts Shelf if available
                        if (currentDetails.shorts.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ElectricBolt,
                                            contentDescription = null,
                                            tint = Color(0xFFFF0033),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Shorts",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(currentDetails.shorts) { short ->
                                            ShortsCard(
                                                video = short,
                                                onClick = { onSelectVideo(short) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Uploads Shelf
                        item {
                            Text(
                                text = "Videos",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(displayedVideos.drop(1)) { video ->
                            VideoCard(
                                video = video,
                                watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                                showProviderBadge = false,
                                onClick = { onSelectVideo(video) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                1 -> { // VIDEOS TAB
                    item {
                        // Sub-filter chips (Latest, Popular, Oldest)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Latest", "Popular", "Oldest").forEach { chip ->
                                item {
                                    FilterChip(
                                        selected = videoFilterChip == chip,
                                        onClick = { videoFilterChip = chip },
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

                    if (isChannelLoading && displayedVideos.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else if (displayedVideos.isEmpty()) {
                        item {
                            ChannelEmptyState(creatorName = currentDetails.name)
                        }
                    } else {
                        items(displayedVideos) { video ->
                            VideoCard(
                                video = video,
                                watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                                showProviderBadge = false,
                                onClick = { onSelectVideo(video) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                2 -> { // SHORTS TAB
                    val shortsList = currentDetails.shorts.ifEmpty { displayedVideos.take(9) }
                    if (shortsList.isEmpty()) {
                        item {
                            ChannelEmptyState(creatorName = currentDetails.name, message = "No Shorts uploaded yet.")
                        }
                    } else {
                        item {
                            Column(modifier = Modifier.padding(12.dp)) {
                                for (i in shortsList.indices step 2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val first = shortsList[i]
                                        ShortsGridItem(
                                            video = first,
                                            onClick = { onSelectVideo(first) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (i + 1 < shortsList.size) {
                                            val second = shortsList[i + 1]
                                            ShortsGridItem(
                                                video = second,
                                                onClick = { onSelectVideo(second) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }

                3 -> { // PLAYLISTS TAB
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Created Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This channel hasn't organized public playlists yet. Explore their latest uploads in the Videos tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                4 -> { // ABOUT TAB
                    item {
                        ChannelAboutSection(
                            details = currentDetails,
                            onShareClick = {
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out ${currentDetails.name} on YouTube: https://www.youtube.com/${currentDetails.handle}")
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Channel"))
                            }
                        )
                    }
                }
            }
        }
    }

    // Channel About Full Bottom Sheet
    if (showAboutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAboutSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            ChannelAboutSheetContent(
                details = currentDetails,
                onClose = { showAboutSheet = false }
            )
        }
    }
}

@Composable
private fun ShortsCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .width(140.dp)
            .height(240.dp)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 120f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.formattedViews.ifEmpty { "1.2M views" },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ShortsGridItem(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .height(260.dp)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 140f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.formattedViews.ifEmpty { "1.2M views" },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ChannelAboutSection(
    details: ChannelDetails,
    onShareClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Description",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = details.description.ifBlank { "No description available." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "More Info",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        AboutInfoRow(icon = Icons.Outlined.Language, label = "https://www.youtube.com/${details.handle}")
        AboutInfoRow(icon = Icons.Outlined.People, label = details.subscriberCount)
        AboutInfoRow(icon = Icons.Outlined.VideoLibrary, label = details.videoCount)
        AboutInfoRow(icon = Icons.Outlined.Visibility, label = details.totalViews)
        AboutInfoRow(icon = Icons.Outlined.CalendarMonth, label = details.joinedDate)

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onShareClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Channel", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChannelAboutSheetContent(
    details: ChannelDetails,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = details.description.ifBlank { "Official channel of ${details.name}." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))

        AboutInfoRow(icon = Icons.Outlined.Language, label = "https://www.youtube.com/${details.handle}")
        AboutInfoRow(icon = Icons.Outlined.People, label = details.subscriberCount)
        AboutInfoRow(icon = Icons.Outlined.VideoLibrary, label = details.videoCount)
        AboutInfoRow(icon = Icons.Outlined.Visibility, label = details.totalViews)
        AboutInfoRow(icon = Icons.Outlined.CalendarMonth, label = details.joinedDate)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AboutInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChannelEmptyState(creatorName: String, message: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message ?: "No videos found for $creatorName",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Try searching for another topic or pull down to refresh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
