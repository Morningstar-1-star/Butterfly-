package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.CaptionOption
import com.example.model.CastMember
import com.example.model.MediaDetailInfo
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoTrailerClip
import com.example.ui.animation.bounceClick
import com.example.util.TMDBHelper

enum class MediaSubTab {
    CAST_AND_CREW,
    SCREENSHOTS,
    TRAILERS_AND_CLIPS
}

@Composable
fun VideoDetailsSection(
    streamData: StreamData? = null,
    previewItem: com.example.model.VideoItem? = null,
    selectedOption: PlayableStreamOption?,
    selectedCaption: CaptionOption?,
    onSelectOption: (PlayableStreamOption) -> Unit,
    onSelectCaption: (CaptionOption?) -> Unit,
    onTagClick: ((String) -> Unit)? = null,
    isLiked: Boolean = false,
    isDisliked: Boolean = false,
    isSaved: Boolean = false,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onSaveLongClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onCommentsClick: () -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    isSubscribed: Boolean = false,
    onSubscribeClick: () -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    onDownloadClick: () -> Unit = {},
    onServersClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isQualityMenuExpanded by remember { mutableStateOf(false) }
    var isCaptionMenuExpanded by remember { mutableStateOf(false) }
    var selectedSubTab by remember { mutableStateOf(MediaSubTab.CAST_AND_CREW) }
    var zoomScreenshotUrl by remember { mutableStateOf<String?>(null) }
    var selectedCastMemberForFilmography by remember { mutableStateOf<CastMember?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentVideoId = streamData?.videoId ?: previewItem?.id ?: ""
    val currentTitle = streamData?.title?.takeIf { it.isNotBlank() } ?: previewItem?.title?.takeIf { it.isNotBlank() } ?: "Loading video..."
    val currentChannelName = streamData?.channelName?.takeIf { it.isNotBlank() } ?: previewItem?.uploaderName?.takeIf { it.isNotBlank() } ?: "Video Creator"
    val currentChannelAvatarUrl = streamData?.channelAvatarUrl ?: previewItem?.uploaderAvatarUrl
    val currentSubscriberCountText = streamData?.subscriberCountText
    val currentViewCount = streamData?.viewCount ?: previewItem?.viewCount ?: 0L
    val currentUploadDate = streamData?.uploadDate ?: previewItem?.uploadDate
    val currentLikeCount = streamData?.likeCount ?: 0L
    val currentDescription = streamData?.description?.takeIf { it.isNotBlank() } ?: previewItem?.description
    val currentProviderId = streamData?.providerId ?: previewItem?.providerId

    var forceTmdbLookup by remember(currentVideoId, currentTitle) { mutableStateOf(false) }
    var mediaDetails by remember(currentVideoId, currentTitle, forceTmdbLookup) {
        mutableStateOf<MediaDetailInfo?>(null)
    }

    LaunchedEffect(currentVideoId, currentTitle, currentProviderId, forceTmdbLookup) {
        if (currentTitle.isNotBlank()) {
            mediaDetails = TMDBHelper.fetchMediaDetails(
                rawTitle = currentTitle,
                videoId = currentVideoId,
                providerId = currentProviderId,
                forceTmdb = forceTmdbLookup
            )
        } else {
            mediaDetails = null
        }
    }

    val baseLikes = remember(currentLikeCount, currentViewCount, currentTitle) {
        if (currentLikeCount > 0) currentLikeCount
        else {
            val hash = kotlin.math.abs(currentTitle.hashCode())
            val estimated = if (currentViewCount > 0) (currentViewCount * 0.085).toLong() else (12500L + (hash % 85000))
            estimated.coerceAtLeast(1840L)
        }
    }

    val baseDislikes = remember(baseLikes, currentTitle) {
        val hash = kotlin.math.abs(currentTitle.hashCode())
        val estimated = (baseLikes * 0.028).toLong() + (hash % 350)
        estimated.coerceAtLeast(42L)
    }

    val formattedLikes = remember(baseLikes, isLiked) {
        val total = if (isLiked) baseLikes + 1 else baseLikes
        if (total >= 1_000_000) String.format("%.1fM", total / 1_000_000.0)
        else if (total >= 1000) "${total / 1000}K"
        else "$total"
    }

    val formattedDislikes = remember(baseDislikes, isDisliked) {
        val total = if (isDisliked) baseDislikes + 1 else baseDislikes
        if (total >= 1_000_000) String.format("%.1fM", total / 1_000_000.0)
        else if (total >= 1000) "${total / 1000}K"
        else "$total"
    }

    val accurateDate = remember(currentUploadDate, mediaDetails) {
        val tmdbDate = mediaDetails?.releaseDateFormatted
        if (!tmdbDate.isNullOrBlank()) {
            tmdbDate
        } else if (!currentUploadDate.isNullOrBlank()) {
            val parsed = TMDBHelper.formatDateToLong(currentUploadDate)
            if (parsed.isNotBlank()) parsed else currentUploadDate
        } else {
            ""
        }
    }

    val viewCountText = remember(currentViewCount) {
        if (currentViewCount > 0) {
            val count = currentViewCount
            if (count >= 1_000_000) "${String.format("%.1f", count / 1_000_000.0)}M views"
            else if (count >= 1_000) "${count / 1_000}K views"
            else "$count views"
        } else {
            ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Video Title (1 line max under player)
        Text(
            text = currentTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 25.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Views & Exact Release Date
        val metadataSubText = remember(viewCountText, accurateDate) {
            listOf(viewCountText, accurateDate).filter { it.isNotBlank() }.joinToString(" • ")
        }
        if (metadataSubText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = metadataSubText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Channel Info Row with Brand/Studio Logo support
        val brandInfo = remember(currentChannelName, currentChannelAvatarUrl, currentTitle) {
            com.example.util.ChannelLogoHelper.getBrandInfo(currentChannelName, currentChannelAvatarUrl, currentTitle)
        }
        val displayChannelName = remember(currentChannelName, brandInfo.brandName) {
            if (currentChannelName.isBlank() || currentChannelName.lowercase().contains("tv network") || currentChannelName == "T") {
                brandInfo.brandName
            } else {
                currentChannelName
            }
        }
        val displaySubCount = remember(currentSubscriberCountText, brandInfo.subscriberCountText) {
            if (!currentSubscriberCountText.isNullOrEmpty() && currentSubscriberCountText != "Subscribers") {
                currentSubscriberCountText
            } else {
                brandInfo.subscriberCountText
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (displayChannelName.isNotBlank()) onChannelClick(displayChannelName) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val logoUrl = brandInfo.logoUrls.firstOrNull() ?: currentChannelAvatarUrl
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(brandInfo.backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (!logoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = displayChannelName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = brandInfo.brandShortText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = brandInfo.textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayChannelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = displaySubCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Subscribe Button with animated color & bell notification toggle
            val subBgColor by animateColorAsState(
                targetValue = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "subBgColor"
            )
            val subContentColor by animateColorAsState(
                targetValue = if (isSubscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "subContentColor"
            )

            Surface(
                onClick = onSubscribeClick,
                shape = RoundedCornerShape(24.dp),
                color = subBgColor,
                contentColor = subContentColor,
                shadowElevation = if (isSubscribed) 0.dp else 4.dp,
                border = if (isSubscribed) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                modifier = Modifier
                    .bounceClick(scaleDown = 0.88f) { onSubscribeClick() }
            ) {
                AnimatedContent(
                    targetState = isSubscribed,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.82f)) togetherWith
                                (fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.15f))
                    },
                    label = "subscribeContentTransition",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) { subscribed ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (subscribed) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Subscribed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Subscribed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = subContentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Subscribe",
                                tint = subContentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Subscribe",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = subContentColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Liquid Glass Action Bar with Spring Interactive Buttons
        val likeIconColor by animateColorAsState(
            targetValue = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "likeColor"
        )
        val dislikeIconColor by animateColorAsState(
            targetValue = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "dislikeColor"
        )

        val likeScale by animateFloatAsState(
            targetValue = if (isLiked) 1.18f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "likeScale"
        )
        val dislikeScale by animateFloatAsState(
            targetValue = if (isDisliked) 1.18f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "dislikeScale"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Split Like / Dislike Liquid Glass Pill with Tactile Spring Bounce
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                tonalElevation = 2.dp,
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    // Like Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .bounceClick(scaleDown = 0.85f) { onLikeClick() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = likeIconColor,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = likeScale
                                    scaleY = likeScale
                                }
                        )
                        if (formattedLikes.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedLikes,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = likeIconColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    // Dislike Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .bounceClick(scaleDown = 0.85f) { onDislikeClick() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = dislikeIconColor,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = dislikeScale
                                    scaleY = dislikeScale
                                }
                        )
                        if (formattedDislikes.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedDislikes,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = dislikeIconColor
                            )
                        }
                    }
                }
            }

            // Share Pill with bouncy tap
            ActionPill(
                icon = Icons.Outlined.Share,
                label = "Share",
                onClick = onShareClick
            )

            // Save Pill with animated Bookmark & Color
            ActionPill(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isSaved) "Saved" else "Save",
                iconTint = if (isSaved) MaterialTheme.colorScheme.primary else null,
                containerColor = if (isSaved) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else null,
                isActive = isSaved,
                onClick = onSaveClick
            )

            // Download Pill with dynamic progress / active state
            ActionPill(
                icon = if (isDownloaded) Icons.Filled.CheckCircle else if (isDownloading) Icons.Default.Downloading else Icons.Outlined.Download,
                label = if (isDownloaded) "Downloaded" else if (isDownloading) "${(downloadProgress * 100).toInt()}%" else "Download",
                iconTint = if (isDownloaded || isDownloading) MaterialTheme.colorScheme.primary else null,
                containerColor = if (isDownloaded || isDownloading) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else null,
                isActive = isDownloaded || isDownloading,
                onClick = onDownloadClick
            )

            // Servers & Sources Pill (Unified Vega + Torrent Resolver)
            if (onServersClick != null) {
                ActionPill(
                    icon = Icons.Default.Dns,
                    label = "Servers",
                    iconTint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    onClick = onServersClick
                )
            }

            // Thanks Pill
            ActionPill(
                icon = Icons.Outlined.VolunteerActivism,
                label = "Thanks"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quality and Caption Selectors Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = {
                        if (!streamData?.availableStreamOptions.isNullOrEmpty()) {
                            isQualityMenuExpanded = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedOption?.qualityLabel ?: if (streamData == null) "Loading stream..." else "1080p • Auto",
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!streamData?.availableStreamOptions.isNullOrEmpty()) {
                    DropdownMenu(
                        expanded = isQualityMenuExpanded,
                        onDismissRequest = { isQualityMenuExpanded = false }
                    ) {
                        streamData!!.availableStreamOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.qualityLabel,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSelectOption(option)
                                    isQualityMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (streamData?.captionOptions?.isNotEmpty() == true) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isCaptionMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedCaption?.languageName ?: "Captions Off",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = isCaptionMenuExpanded,
                        onDismissRequest = { isCaptionMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Captions Off") },
                            onClick = {
                                onSelectCaption(null)
                                isCaptionMenuExpanded = false
                            }
                        )
                        streamData.captionOptions.forEach { caption ->
                            DropdownMenuItem(
                                text = { Text(caption.languageName) },
                                onClick = {
                                    onSelectCaption(caption)
                                    isCaptionMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. UNIFIED "DESCRIPTION" CARD
        val plotText = if (mediaDetails != null && !mediaDetails?.plotOverview.isNullOrBlank()) {
            mediaDetails!!.plotOverview
        } else {
            (currentDescription ?: "").ifBlank {
                "Watch $currentTitle on ${currentChannelName.ifBlank { "Butterfly Player" }}."
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isDescriptionExpanded = !isDescriptionExpanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (isDescriptionExpanded) "Show Less" else "Show More",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC107) // Gold accent
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = plotText,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (mediaDetails == null && TMDBHelper.isWebOrAdultProvider(currentProviderId) && isDescriptionExpanded && !forceTmdbLookup) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { forceTmdbLookup = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Search TMDB Movie Details (For Trailers)", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Top Cast (Always visible directly in description without needing to click Show More)
                val castList = mediaDetails?.cast ?: emptyList()
                if (castList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Top Cast",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${castList.size} actors",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(castList, key = { it.personId ?: it.name }) { member ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(96.dp)
                                    .clickable { selectedCastMemberForFilmography = member },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth()
                                ) {
                                    val avatarUrl = member.avatarUrl
                                    if (!avatarUrl.isNullOrBlank()) {
                                        val imgReq = remember(avatarUrl) {
                                            com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, avatarUrl, preferCompact = true)
                                        }
                                        AsyncImage(
                                            model = imgReq ?: avatarUrl,
                                            contentDescription = member.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = member.name.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!member.role.isNullOrBlank()) {
                                        Text(
                                            text = member.role,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Detailed Metadata, Director, Box Office for Media
                if (mediaDetails != null) {
                    val screenshots = mediaDetails?.screenshots ?: emptyList()
                    val clips = mediaDetails?.clipsAndTrailers ?: emptyList()

                    // Expanded Details (Crew, Box Office, Financials, Media Gallery)
                    if (isDescriptionExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Crew Details (Director, Writer, Studio)
                        if (!mediaDetails?.director.isNullOrBlank() || !mediaDetails?.writer.isNullOrBlank() || !mediaDetails?.studioOrCollection.isNullOrBlank()) {
                            Text(
                                text = "Director & Key Crew",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (!mediaDetails?.director.isNullOrBlank()) {
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "Director: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mediaDetails!!.director,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!mediaDetails?.writer.isNullOrBlank()) {
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "Writer: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mediaDetails!!.writer,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!mediaDetails?.studioOrCollection.isNullOrBlank()) {
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "Studio / Network: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mediaDetails!!.studioOrCollection,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Box Office & Financials (if available)
                        if (!mediaDetails?.budget.isNullOrBlank() || !mediaDetails?.boxOffice.isNullOrBlank() || !mediaDetails?.status.isNullOrBlank()) {
                            Text(
                                text = "Box Office & Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (!mediaDetails?.budget.isNullOrBlank()) {
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "Budget: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mediaDetails!!.budget!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!mediaDetails?.boxOffice.isNullOrBlank()) {
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "Box Office / Revenue: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mediaDetails!!.boxOffice!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!mediaDetails?.status.isNullOrBlank()) {
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "Status: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mediaDetails!!.status!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Badges row (Rating, Year, Genres)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!mediaDetails?.ratingText.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF673AB7),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = mediaDetails!!.ratingText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (accurateDate.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = accurateDate,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            mediaDetails?.genres?.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Screenshots Gallery inside Description
                        if (screenshots.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Screenshots & Gallery",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(screenshots, key = { it }) { imageUrl ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .width(180.dp)
                                            .height(105.dp)
                                            .clickable { zoomScreenshotUrl = imageUrl },
                                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            val ssRequest = remember(imageUrl) {
                                                com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, imageUrl)
                                            }
                                            AsyncImage(
                                                model = ssRequest ?: imageUrl,
                                                contentDescription = "Scene Screenshot",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                    .padding(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "Zoom",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Trailers & Clips inside Description
                        if (clips.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Trailers & Clips",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                clips.forEach { clip ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .width(200.dp)
                                            .clickable {
                                                if (!clip.youtubeKey.isNullOrEmpty()) {
                                                    onTagClick?.invoke(clip.title)
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(110.dp)
                                                    .background(Color.Black)
                                            ) {
                                                val clipThumbRequest = remember(clip.thumbnailUrl) {
                                                    com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, clip.thumbnailUrl, preferCompact = true)
                                                }
                                                if (!clip.thumbnailUrl.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = clipThumbRequest ?: clip.thumbnailUrl,
                                                        contentDescription = clip.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.25f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayCircleFilled,
                                                        contentDescription = "Play Clip",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = clip.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Screenshot Lightbox Preview Dialog
    zoomScreenshotUrl?.let { url ->
        Dialog(onDismissRequest = { zoomScreenshotUrl = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Box(modifier = Modifier.padding(8.dp)) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Full Screenshot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    IconButton(
                        onClick = { zoomScreenshotUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // CAST FILMOGRAPHY BOTTOM SHEET
        if (selectedCastMemberForFilmography != null) {
            CastFilmographyBottomSheet(
                castMember = selectedCastMemberForFilmography!!,
                onDismiss = { selectedCastMemberForFilmography = null },
                onWorkClick = { workTitle ->
                    selectedCastMemberForFilmography = null
                    onTagClick?.invoke(workTitle)
                }
            )
        }
    }
}

@Composable
private fun MediaSubTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color? = null,
    containerColor: Color? = null,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val animatedBg by animateColorAsState(
        targetValue = containerColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "actionPillBg"
    )
    val animatedTint by animateColorAsState(
        targetValue = iconTint ?: MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "actionPillTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "actionPillIconScale"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = animatedBg,
        tonalElevation = if (isActive) 4.dp else 1.dp,
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
        modifier = Modifier
            .height(38.dp)
            .bounceClick(scaleDown = 0.90f) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animatedTint,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = animatedTint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastFilmographyBottomSheet(
    castMember: CastMember,
    onDismiss: () -> Unit,
    onWorkClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var filmography by remember(castMember.name) { mutableStateOf<List<com.example.model.CastFilmographyItem>>(emptyList()) }
    var isLoading by remember(castMember.name) { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(castMember) {
        isLoading = true
        filmography = TMDBHelper.fetchFilmographyForPerson(castMember.name, castMember.personId)
        isLoading = false
    }

    val filteredWorks = remember(filmography, selectedFilter) {
        when (selectedFilter) {
            "Movies" -> filmography.filter { it.mediaType.equals("movie", ignoreCase = true) }
            "TV Series" -> filmography.filter { it.mediaType.equals("tv", ignoreCase = true) }
            else -> filmography
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                BottomSheetDefaults.DragHandle()
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // CAST PROFILE HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarUrl = castMember.avatarUrl
                if (!avatarUrl.isNullOrBlank()) {
                    val imgReq = remember(avatarUrl) {
                        com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, avatarUrl, preferCompact = true)
                    }
                    AsyncImage(
                        model = imgReq ?: avatarUrl,
                        contentDescription = castMember.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFFC107), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .border(2.dp, Color(0xFFFFC107), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = castMember.name.take(1).uppercase(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = castMember.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!castMember.role.isNullOrBlank()) {
                        Text(
                            text = "Starred as ${castMember.role}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = if (filmography.isNotEmpty()) "${filmography.size} Known Works" else "Filmography & Works",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // FILTER CHIPS (All, Movies, TV Series)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Movies", "TV Series").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFFC107),
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (filteredWorks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No titles found for ${castMember.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredWorks.size, key = { filteredWorks[it].id }) { idx ->
                        val work = filteredWorks[idx]
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWorkClick(work.title) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .background(Color.Black.copy(alpha = 0.4f))
                                ) {
                                    val poster = work.posterUrl ?: work.backdropUrl
                                    if (!poster.isNullOrBlank()) {
                                        AsyncImage(
                                            model = poster,
                                            contentDescription = work.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    // Rating & Type Badges overlay
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (work.mediaType.equals("tv", true)) "TV" else "MOVIE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                        if (work.voteAverage > 0.0) {
                                            Text(
                                                text = "★ ${String.format("%.1f", work.voteAverage)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFD700),
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = work.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (work.character.isNotBlank()) {
                                        Text(
                                            text = "as ${work.character}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = work.releaseYear,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


