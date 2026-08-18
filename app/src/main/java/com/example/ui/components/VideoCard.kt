package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import com.example.model.VideoItem
import kotlinx.coroutines.launch

import com.example.ui.animation.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watchProgressFraction: Float = 0f,
    onPlayNextInQueue: ((VideoItem) -> Unit)? = null,
    onSaveToWatchLater: ((VideoItem) -> Unit)? = null,
    onSaveToPlaylist: ((VideoItem) -> Unit)? = null,
    onDownload: ((VideoItem) -> Unit)? = null,
    onShare: ((VideoItem) -> Unit)? = null,
    onNotInterested: ((VideoItem) -> Unit)? = null,
    onReport: ((VideoItem) -> Unit)? = null,
    onChannelClick: ((String) -> Unit)? = null
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val videoTagPrefs = remember { com.example.util.VideoTagPreferences.getInstance(context) }
    val hideAllTags by videoTagPrefs.hideAllTags.collectAsState()
    val hiddenTags by videoTagPrefs.hiddenTags.collectAsState()

    val providerBadgeInfo = remember(video.providerId, video.title, video.uploaderName, video.uploadDate, video.id) {
        val pid = (video.providerId ?: "").lowercase()
        val titleLower = video.title.lowercase()
        val uploaderLower = video.uploaderName.lowercase()
        val uploadDateLower = (video.uploadDate ?: "").lowercase()

        val name = when {
            pid == "jikan_anime" || pid == "nyaa" || titleLower.contains("anime") || uploaderLower.contains("anime") || uploaderLower.contains("ghibli") || uploaderLower.contains("toei") || uploaderLower.contains("mappa") || uploaderLower.contains("aniplex") -> "Anime"
            pid.contains("apijav") || pid.contains("eporner") || pid.contains("porn") || pid.contains("hentai") || pid.contains("javinfo") -> "18+"
            video.id.startsWith("tv_") || (pid.contains("eztv") && (titleLower.contains("s0") || titleLower.contains("season"))) -> "Series"
            video.id.startsWith("movie_") || video.id.replace("tmdb_", "").all { it.isDigit() } || pid in listOf("tmdb", "tmdb_movies") -> "Movie"
            pid == "archive_org" -> "Archive"
            pid == "youtube" -> "YouTube"
            pid == "dailymotion" -> "Dailymotion"
            pid == "vimeo" -> "Vimeo"
            else -> "Video"
        }

        val bgColor = when (name) {
            "Movie", "Movies" -> Color(0xFFE5A00D)
            "Series" -> Color(0xFF0288D1)
            "Anime" -> Color(0xFF9C27B0)
            "18+" -> Color(0xFFC2185B)
            "YouTube" -> Color(0xFFFF0000)
            else -> Color(0xFF1976D2)
        }
        Pair(name, bgColor)
    }

    val seriesPillText: String? = null

    val thumbnailImageRequest = remember(video.thumbnailUrl) {
        com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, video.thumbnailUrl, crossfadeMillis = 100)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.97f) { onClick() },
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Thumbnail container with Duration Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (thumbnailImageRequest != null) {
                    AsyncImage(
                        model = thumbnailImageRequest,
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
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                if (video.displayDuration.isNotEmpty()) {
                    Text(
                        text = video.displayDuration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Series Season/Episode Pill badge on bottom-left of thumbnail
                if (!seriesPillText.isNullOrEmpty()) {
                    Text(
                        text = seriesPillText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                val isTagVisible = remember(hideAllTags, hiddenTags, providerBadgeInfo.first) {
                    videoTagPrefs.isTagVisible(providerBadgeInfo.first)
                }

                if (!video.providerId.isNullOrEmpty() && isTagVisible) {
                    Text(
                        text = providerBadgeInfo.first,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(
                                color = providerBadgeInfo.second.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Red YouTube-style Watch Progress Bar on bottom of thumbnail
                if (watchProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color.DarkGray.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(watchProgressFraction.coerceIn(0.01f, 1f))
                                .background(Color.Red)
                        )
                    }
                }
            }

            // Info Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Channel Logo or Avatar or Fallback
                val brandInfo = remember(video.uploaderName, video.uploaderAvatarUrl, video.title) {
                    com.example.util.ChannelLogoHelper.getBrandInfo(video.uploaderName, video.uploaderAvatarUrl, video.title)
                }

                val targetChannelName = remember(video.uploaderName, brandInfo.brandName) {
                    if (video.uploaderName.isBlank() || video.uploaderName.lowercase().contains("tv network") || video.uploaderName == "T") brandInfo.brandName else video.uploaderName
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(brandInfo.backgroundColor)
                        .then(
                            if (onChannelClick != null && targetChannelName.isNotBlank()) {
                                Modifier.clickable { onChannelClick(targetChannelName) }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (brandInfo.logoUrls.isNotEmpty()) {
                        val primaryUrl = brandInfo.logoUrls.first()
                        val logoImageRequest = remember(primaryUrl) {
                            ImageRequest.Builder(context)
                                .data(primaryUrl)
                                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .crossfade(false)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build()
                        }

                        AsyncImage(
                            model = logoImageRequest,
                            contentDescription = video.uploaderName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Text(
                            text = brandInfo.brandShortText,
                            color = brandInfo.textColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (brandInfo.brandShortText.length > 3) 8.sp else 10.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = targetChannelName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onChannelClick != null && targetChannelName.isNotBlank()) {
                            Modifier.clickable { onChannelClick(targetChannelName) }
                        } else Modifier
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = buildString {
                            if (video.formattedViews.isNotEmpty()) {
                                append(video.formattedViews)
                            }
                            if (!video.uploadDate.isNullOrEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(video.uploadDate)
                            } else if (!seriesPillText.isNullOrEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(seriesPillText)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // THREE-DOTS CONTEXT MENU BUTTON
                IconButton(
                    onClick = { showBottomSheet = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Video options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.PlaylistPlay,
                    label = "Play next in queue",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onPlayNextInQueue?.invoke(video)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.WatchLater,
                    label = "Save to Watch Later",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onSaveToWatchLater?.invoke(video)
                            Toast.makeText(context, "Saved to Watch Later", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.BookmarkBorder,
                    label = "Save to playlist",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onSaveToPlaylist?.invoke(video)
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.FileDownload,
                    label = "Download video",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            if (onDownload != null) {
                                onDownload.invoke(video)
                            } else {
                                com.example.util.VideoActionHelper.downloadVideo(context, video)
                            }
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            if (onShare != null) {
                                onShare.invoke(video)
                            } else {
                                com.example.util.VideoActionHelper.shareVideo(context, video)
                            }
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.Block,
                    label = "Not interested",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            if (onNotInterested != null) {
                                onNotInterested.invoke(video)
                            } else {
                                com.example.util.NotInterestedManager.markNotInterested(context, video.id, video.uploaderName)
                            }
                            Toast.makeText(context, "We won't recommend this video again", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.Flag,
                    label = "Report",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            if (onReport != null) {
                                onReport.invoke(video)
                            } else {
                                Toast.makeText(context, "Report submitted. Thank you for your feedback.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoOptionMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

