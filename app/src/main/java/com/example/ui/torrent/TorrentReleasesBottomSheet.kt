package com.example.ui.torrent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.EpisodeItem
import com.example.model.SeriesSeason
import com.example.torrent.model.TorrentRelease
import com.example.torrent.provider.MediaIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentReleasesBottomSheet(
    mediaIdentity: MediaIdentity,
    posterUrl: String? = null,
    backdropUrl: String? = null,
    seasons: List<SeriesSeason> = emptyList(),
    releases: List<TorrentRelease>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectRelease: (TorrentRelease) -> Unit,
    onSelectEpisode: (season: Int, episode: Int) -> Unit = { _, _ -> }
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedQualityFilter by remember { mutableStateOf("All") }
    var selectedSeason by remember(mediaIdentity.season) { mutableStateOf(mediaIdentity.season ?: 1) }
    var selectedEpisode by remember(mediaIdentity.episode) { mutableStateOf(mediaIdentity.episode ?: 1) }

    val isTvSeries = mediaIdentity.mediaType.equals("tv", ignoreCase = true) || seasons.isNotEmpty()

    val filteredReleases = remember(releases, selectedQualityFilter) {
        when (selectedQualityFilter) {
            "4K" -> releases.filter { it.quality.contains("4K", ignoreCase = true) || it.quality.contains("2160", ignoreCase = true) }
            "1080p" -> releases.filter { it.quality.contains("1080", ignoreCase = true) }
            "720p" -> releases.filter { it.quality.contains("720", ignoreCase = true) }
            "HDR / DV" -> releases.filter { it.hdr.isNotBlank() }
            "x265 HEVC" -> releases.filter { it.codec.contains("265", ignoreCase = true) || it.codec.contains("hevc", ignoreCase = true) }
            else -> releases
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 1. HERO HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = mediaIdentity.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 90.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaIdentity.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = if (isTvSeries) "TV Series" else "Movie",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!mediaIdentity.year.isNullOrBlank()) {
                            Text(
                                text = mediaIdentity.year,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!mediaIdentity.imdbId.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF5C518),
                                contentColor = Color.Black
                            ) {
                                Text(
                                    text = mediaIdentity.imdbId,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (isTvSeries) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Season $selectedSeason • Episode $selectedEpisode",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. SEASON & EPISODE SELECTOR (For TV Series)
            if (isTvSeries && seasons.isNotEmpty()) {
                val currentSeasonObj = seasons.find { it.seasonNumber == selectedSeason } ?: seasons.firstOrNull()
                val episodes = currentSeasonObj?.episodes ?: emptyList()

                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    // Season Chips
                    Text(text = "Seasons", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(seasons) { season ->
                            FilterChip(
                                selected = selectedSeason == season.seasonNumber,
                                onClick = {
                                    selectedSeason = season.seasonNumber
                                    selectedEpisode = 1
                                    onSelectEpisode(selectedSeason, 1)
                                },
                                label = { Text("Season ${season.seasonNumber}") }
                            )
                        }
                    }

                    // Episode Chips
                    if (episodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Episodes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(episodes) { ep ->
                                FilterChip(
                                    selected = selectedEpisode == ep.episodeNumber,
                                    onClick = {
                                        selectedEpisode = ep.episodeNumber
                                        onSelectEpisode(selectedSeason, ep.episodeNumber)
                                    },
                                    label = { Text("EP ${ep.episodeNumber}") }
                                )
                            }
                        }
                    }
                }
            }

            // 3. QUALITY FILTER CHIPS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "4K", "1080p", "720p", "HDR / DV", "x265 HEVC").forEach { filter ->
                    FilterChip(
                        selected = selectedQualityFilter == filter,
                        onClick = { selectedQualityFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 4. RELEASES LIST / LOADING / EMPTY STATE
            if (isLoading && releases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching high-speed torrent providers...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredReleases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (releases.isEmpty()) "No torrent streams found" else "No releases match filter \"$selectedQualityFilter\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(filteredReleases, key = { it.infoHash }) { release ->
                        TorrentReleaseCard(
                            release = release,
                            onClick = { onSelectRelease(release) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TorrentReleaseCard(
    release: TorrentRelease,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Quality badge + Provider + Seeders
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quality Badge
                    val qualityColor = when {
                        release.quality.contains("4K", ignoreCase = true) || release.quality.contains("2160", ignoreCase = true) -> Color(0xFFE040FB)
                        release.quality.contains("1080", ignoreCase = true) -> Color(0xFF00E676)
                        else -> Color(0xFF29B6F6)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = qualityColor.copy(alpha = 0.2f),
                        contentColor = qualityColor
                    ) {
                        Text(
                            text = release.quality,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // HDR Badge
                    if (release.hdr.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF9100).copy(alpha = 0.2f),
                            contentColor = Color(0xFFFF9100)
                        ) {
                            Text(
                                text = release.hdr,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Codec Badge
                    if (release.codec.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = release.codec,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Provider Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = release.provider,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Release Title
            Text(
                text = release.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Meta Row: Size, Seeders, Audio, Play action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Seeders
                    val seederColor = if (release.seeders > 20) Color(0xFF00E676) else if (release.seeders > 5) Color(0xFFFFB300) else Color(0xFFFF5252)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = seederColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${release.seeders} seeds",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = seederColor
                        )
                    }

                    // Size
                    if (release.formattedSize.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = release.formattedSize,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Audio
                    if (release.audioChannels.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = release.audioChannels,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Play Button
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
