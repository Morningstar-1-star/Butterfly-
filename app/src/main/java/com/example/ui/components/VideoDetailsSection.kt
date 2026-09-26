package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.CaptionOption
import com.example.model.CastMember
import com.example.model.MediaDetailInfo
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.ui.animation.bounceClick
import com.example.util.TMDBHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    onTitleDrag: ((deltaY: Float) -> Unit)? = null,
    onTitleDragEnd: ((totalDy: Float) -> Unit)? = null,
    commentsCount: Int = 0,
    topCommentSnippet: String? = null,
    modifier: Modifier = Modifier
) {
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var showMoreActionsSheet by remember { mutableStateOf(false) }
    var isQualityMenuExpanded by remember { mutableStateOf(false) }
    var isCaptionMenuExpanded by remember { mutableStateOf(false) }
    var selectedCastMemberForFilmography by remember { mutableStateOf<CastMember?>(null) }
    var showOriginalTitle by remember(streamData?.videoId, previewItem?.id) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var titleTranslation by remember(streamData?.videoId, previewItem?.id) {
        mutableStateOf<com.example.util.TranslationResult?>(null)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentVideoId = streamData?.videoId ?: previewItem?.id ?: ""
    val rawTitle = streamData?.title?.takeIf { it.isNotBlank() } ?: previewItem?.title?.takeIf { it.isNotBlank() } ?: "Loading video..."
    val currentChannelName = streamData?.channelName?.takeIf { it.isNotBlank() } ?: previewItem?.uploaderName?.takeIf { it.isNotBlank() } ?: "Video Creator"
    val currentChannelAvatarUrl = streamData?.channelAvatarUrl ?: previewItem?.uploaderAvatarUrl
    val currentSubscriberCountText = streamData?.subscriberCountText
    val currentViewCount = streamData?.viewCount ?: previewItem?.viewCount ?: 0L
    val currentUploadDate = streamData?.uploadDate ?: previewItem?.uploadDate
    val currentLikeCount = streamData?.likeCount ?: 0L
    val currentDescription = streamData?.description?.takeIf { it.isNotBlank() } ?: previewItem?.description
    val currentProviderId = streamData?.providerId ?: previewItem?.providerId

    LaunchedEffect(rawTitle) {
        if (rawTitle.isNotBlank() && rawTitle != "Loading video...") {
            titleTranslation = com.example.util.UniversalTranslator.translateTitle(rawTitle)
        }
    }

    val currentTitle = remember(rawTitle, showOriginalTitle, titleTranslation) {
        if (showOriginalTitle || titleTranslation == null) {
            rawTitle
        } else {
            if (titleTranslation?.detectedLanguage == "hi") {
                rawTitle
            } else {
                titleTranslation?.translatedEN?.takeIf { it.isNotBlank() } ?: rawTitle
            }
        }
    }

    var mediaDetails by remember(currentVideoId, currentTitle) {
        mutableStateOf<MediaDetailInfo?>(null)
    }

    LaunchedEffect(currentVideoId, currentTitle, currentProviderId) {
        if (currentTitle.isNotBlank()) {
            mediaDetails = TMDBHelper.fetchMediaDetails(
                rawTitle = currentTitle,
                videoId = currentVideoId,
                providerId = currentProviderId
            )
        }
    }

    val baseLikes = remember(currentLikeCount, currentViewCount, currentTitle) {
        if (currentLikeCount > 0) currentLikeCount
        else {
            val hash = kotlin.math.abs(currentTitle.hashCode())
            val estimated = if (currentViewCount > 0) (currentViewCount * 0.085).toLong() else (12500L + (hash % 85000))
            estimated.coerceAtLeast(397L)
        }
    }

    val formattedLikes = remember(baseLikes, isLiked) {
        val total = if (isLiked) baseLikes + 1 else baseLikes
        if (total >= 1_000_000) String.format("%.1fM", total / 1_000_000.0)
        else if (total >= 1000) "${total / 1000}K"
        else "$total"
    }

    val viewCountText = remember(currentViewCount) {
        if (currentViewCount > 0) {
            val count = currentViewCount
            if (count >= 1_000_000) "${String.format("%.1f", count / 1_000_000.0)}M views"
            else if (count >= 1_000) "${String.format("%.1f", count / 1000.0)}k views"
            else "$count views"
        } else {
            "8.8k views"
        }
    }

    val accurateDate = remember(currentUploadDate, mediaDetails) {
        val tmdbDate = mediaDetails?.releaseDateFormatted
        if (!tmdbDate.isNullOrBlank()) {
            tmdbDate
        } else if (!currentUploadDate.isNullOrBlank()) {
            val parsed = TMDBHelper.formatDateToLong(currentUploadDate)
            if (parsed.isNotBlank()) parsed else currentUploadDate
        } else {
            "10 hr ago"
        }
    }

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

    val handleName = remember(displayChannelName) {
        val clean = displayChannelName.replace(" ", "").uppercase()
        if (clean.startsWith("@")) clean else "@$clean"
    }

    val topTagsList = remember(streamData?.tags, mediaDetails?.genres) {
        val tags = streamData?.tags?.takeIf { it.isNotEmpty() } ?: mediaDetails?.genres ?: emptyList()
        tags.take(3)
    }

    val metadataLine = remember(handleName, formattedLikes, viewCountText, accurateDate, topTagsList) {
        val tagsStr = topTagsList.take(2).joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
        listOf(handleName, "$formattedLikes likes", viewCountText, accurateDate, tagsStr)
            .filter { it.isNotBlank() }
            .joinToString("  ") + " ...more"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 1. VIDEO TITLE (Clickable to open Description)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDescriptionSheet = true },
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = currentTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 23.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            val hasTitleTranslation = remember(titleTranslation, rawTitle) {
                titleTranslation != null &&
                titleTranslation?.detectedLanguage != "en" &&
                titleTranslation?.detectedLanguage != "hi" &&
                !titleTranslation?.translatedEN.isNullOrBlank() &&
                titleTranslation?.translatedEN != rawTitle
            }

            if (hasTitleTranslation && rawTitle.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { showOriginalTitle = !showOriginalTitle }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (showOriginalTitle) "EN" else "Orig",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. METADATA SUB-LINE: @Channel  Likes  Views  Time  #Tag ...more
        Text(
            text = metadataLine,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFAAAAAA)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDescriptionSheet = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. CHANNEL ROW (Avatar, Name, Subscribe Pill) & ACTION BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Channel Avatar + Name + Subscribe Button
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
                        .size(36.dp)
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

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = displayChannelName,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // YouTube Style Subscribe Pill
                Surface(
                    onClick = onSubscribeClick,
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSubscribed) Color(0xFF272727) else Color.White,
                    contentColor = if (isSubscribed) Color(0xFFAAAAAA) else Color.Black,
                    modifier = Modifier
                        .height(32.dp)
                        .bounceClick(scaleDown = 0.90f) { onSubscribeClick() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSubscribed) Color(0xFFAAAAAA) else Color.Black
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. ACTION BAR: Like / Dislike, Share, Three Dots (...), Servers, Thanks, Download, Save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Split Like / Dislike Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF272727),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLikeClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.White else Color(0xFFF1F1F1),
                            modifier = Modifier.size(17.dp)
                        )
                        if (formattedLikes.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedLikes,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = 0.20f))
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onDislikeClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = if (isDisliked) Color.White else Color(0xFFF1F1F1),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Share Pill
            ActionPill(
                icon = Icons.Outlined.Share,
                label = "Share",
                onClick = onShareClick
            )

            // Save Pill
            ActionPill(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isSaved) "Saved" else "Save",
                isActive = isSaved,
                onClick = onSaveClick
            )

            // Three Dots (...) More Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF272727),
                modifier = Modifier
                    .size(36.dp)
                    .bounceClick(scaleDown = 0.90f) { showMoreActionsSheet = true }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More actions",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Servers & Sources Pill
            if (onServersClick != null) {
                ActionPill(
                    icon = Icons.Default.Dns,
                    label = "Servers",
                    onClick = onServersClick
                )
            }

            // Thanks Pill
            ActionPill(
                icon = Icons.Outlined.VolunteerActivism,
                label = "Thanks",
                onClick = {
                    android.widget.Toast.makeText(context, "Thanks for supporting the creator!", android.widget.Toast.LENGTH_SHORT).show()
                }
            )

            // Download Pill
            ActionPill(
                icon = if (isDownloaded) Icons.Filled.CheckCircle else if (isDownloading) Icons.Default.Downloading else Icons.Outlined.Download,
                label = if (isDownloaded) "Downloaded" else if (isDownloading) "${(downloadProgress * 100).toInt()}%" else "Download",
                isActive = isDownloaded || isDownloading,
                onClick = onDownloadClick
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. QUALITY SELECTION PILL (Preserved as requested)
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = {
                    if (!streamData?.availableStreamOptions.isNullOrEmpty()) {
                        isQualityMenuExpanded = true
                    }
                },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1E1E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedOption?.qualityLabel ?: if (streamData == null) "Loading stream..." else "Adaptive HLS (Auto)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isQualityMenuExpanded && !streamData?.availableStreamOptions.isNullOrEmpty()) {
                StreamSourcePickerBottomSheet(
                    streamData = streamData,
                    selectedOption = selectedOption,
                    onSelectOption = { option ->
                        onSelectOption(option)
                        isQualityMenuExpanded = false
                    },
                    onDismiss = { isQualityMenuExpanded = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 6. COMMENTS PREVIEW CARD (Screenshot 1, 4, 5)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF212121),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCommentsClick() }
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val countStr = if (commentsCount > 0) "$commentsCount" else "43"
                    Text(
                        text = "Comments $countStr",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = null,
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = topCommentSnippet?.takeIf { it.isNotBlank() } ?: "Comment...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFFAAAAAA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // YOUTUBE DESCRIPTION MODAL BOTTOM SHEET (Screenshot 2 & 3)
    if (showDescriptionSheet) {
        DescriptionBottomSheet(
            title = currentTitle,
            channelName = displayChannelName,
            channelAvatarUrl = brandInfo.logoUrls.firstOrNull() ?: currentChannelAvatarUrl,
            subscriberCountText = displaySubCount,
            isSubscribed = isSubscribed,
            onSubscribeClick = onSubscribeClick,
            likesCountText = formattedLikes,
            viewsCountText = viewCountText.replace(" views", "").ifBlank { "8,841" },
            timeAgoText = accurateDate,
            exactDateText = currentUploadDate ?: accurateDate,
            fullDescription = (currentDescription ?: "").ifBlank { "Watch $currentTitle on Butterfly Player." },
            tags = topTagsList,
            streamData = streamData,
            previewItem = previewItem,
            onSeekTo = { targetMs -> com.example.ui.player.GlobalPlayerManager.seekTo(targetMs) },
            onChannelClick = { onChannelClick(displayChannelName) },
            onDismiss = { showDescriptionSheet = false }
        )
    }

    // MORE ACTIONS MODAL BOTTOM SHEET (Screenshot 1 & video)
    if (showMoreActionsSheet) {
        MoreActionsBottomSheet(
            onHype = {
                showMoreActionsSheet = false
                android.widget.Toast.makeText(context, "Hyped this video!", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDownload = {
                showMoreActionsSheet = false
                onDownloadClick()
            },
            onThanks = {
                showMoreActionsSheet = false
                android.widget.Toast.makeText(context, "Thanks sent to creator!", android.widget.Toast.LENGTH_SHORT).show()
            },
            onServers = {
                showMoreActionsSheet = false
                onServersClick?.invoke()
            },
            onSavePlaylist = {
                showMoreActionsSheet = false
                onSaveLongClick()
            },
            onReport = {
                showMoreActionsSheet = false
                android.widget.Toast.makeText(context, "Report submitted. Thank you for keeping Butterfly safe.", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showMoreActionsSheet = false }
        )
    }

    // CAST FILMOGRAPHY SHEET
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

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF272727),
        modifier = Modifier
            .height(36.dp)
            .bounceClick(scaleDown = 0.90f) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

/**
 * YouTube-style Modern Description Bottom Sheet (Screenshot 2 & 3)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionBottomSheet(
    title: String,
    channelName: String,
    channelAvatarUrl: String?,
    subscriberCountText: String?,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    likesCountText: String,
    viewsCountText: String,
    timeAgoText: String,
    exactDateText: String,
    fullDescription: String,
    tags: List<String>,
    streamData: StreamData? = null,
    previewItem: VideoItem? = null,
    onSeekTo: ((Long) -> Unit)? = null,
    onChannelClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isTextExpanded by remember { mutableStateOf(false) }
    val curTimelinePosMs by com.example.ui.player.GlobalPlayerManager.currentPositionMs.collectAsState()
    val totalTimelineDurMs by com.example.ui.player.GlobalPlayerManager.durationMs.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F0F),
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
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Description + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Description",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Full Video Title
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White,
                lineHeight = 23.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3 METRIC CARDS ROW: Likes | Views | Time Ago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Likes Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF272727),
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = likesCountText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Likes",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }

                // Views Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF272727),
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = viewsCountText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Views",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }

                // Time Ago Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF272727),
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = timeAgoText.take(6),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Ago",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // HASHTAGS ROW
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        val formattedTag = if (tag.startsWith("#")) tag else "#$tag"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF272727)
                        ) {
                            Text(
                                text = formattedTag,
                                fontSize = 12.sp,
                                color = Color(0xFF3EA6FF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // DESCRIPTION TEXT BOX
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF272727),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isTextExpanded = !isTextExpanded }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = fullDescription,
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 19.sp,
                        maxLines = if (isTextExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF383838),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isTextExpanded) "Show less" else "See more",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SCREENSHOTS & SCENE TIMING GALLERY (Directly in Description)
            InteractiveTimelinePreviewStrip(
                currentPositionMs = curTimelinePosMs,
                durationMs = totalTimelineDurMs,
                streamData = streamData,
                previewItem = previewItem,
                onSeekTo = { targetMs ->
                    onSeekTo?.invoke(targetMs) ?: com.example.ui.player.GlobalPlayerManager.seekTo(targetMs)
                },
                modifier = Modifier.fillMaxWidth(),
                initiallyExpanded = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // TRANSCRIPT SECTION
            Text(
                text = "Transcript",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Text(
                text = "Follow along using the transcript.",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF272727),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Show transcript",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CHANNEL INFO BANNER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onChannelClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channelAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channelAvatarUrl,
                            contentDescription = channelName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = channelName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channelName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = subscriberCountText ?: "Subscribers",
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }

                Surface(
                    onClick = onSubscribeClick,
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSubscribed) Color(0xFF272727) else Color.White
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSubscribed) Color(0xFFAAAAAA) else Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Channel Links Row: [▶ Videos] [👤 About] [📸 instagram.com]
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF272727),
                    modifier = Modifier.clickable { onChannelClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Videos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF272727),
                    modifier = Modifier.clickable { onChannelClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("About", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF272727)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = Color(0xFF3EA6FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("https://instagram.com", fontSize = 12.sp, color = Color(0xFF3EA6FF))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // VIDEO DETAILS FOOTER
            Text(
                text = "Video details",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Date", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                Spacer(modifier = Modifier.weight(1f))
                Text(exactDateText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Views", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                Spacer(modifier = Modifier.weight(1f))
                Text(viewsCountText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Likes", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                Spacer(modifier = Modifier.weight(1f))
                Text(likesCountText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * YouTube-style More Actions Bottom Sheet (Screenshot 1 & video)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreActionsBottomSheet(
    onHype: () -> Unit,
    onDownload: () -> Unit,
    onThanks: () -> Unit,
    onServers: () -> Unit,
    onSavePlaylist: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            MoreActionItem(
                icon = Icons.Outlined.AutoAwesome,
                label = "Hype",
                onClick = onHype
            )

            MoreActionItem(
                icon = Icons.Outlined.Download,
                label = "Download",
                onClick = onDownload
            )

            MoreActionItem(
                icon = Icons.Outlined.VolunteerActivism,
                label = "Thanks",
                onClick = onThanks
            )

            MoreActionItem(
                icon = Icons.Default.Dns,
                label = "Change Server / Sources",
                onClick = onServers
            )

            MoreActionItem(
                icon = Icons.Outlined.PlaylistAdd,
                label = "Save to playlist",
                onClick = onSavePlaylist
            )

            MoreActionItem(
                icon = Icons.Outlined.Flag,
                label = "Report",
                onClick = onReport
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MoreActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color.White
        )
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
