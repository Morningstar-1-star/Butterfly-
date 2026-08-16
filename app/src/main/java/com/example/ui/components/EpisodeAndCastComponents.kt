package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.example.model.CastMember
import com.example.model.EpisodeItem
import com.example.model.SeriesSeason
import com.example.model.VideoComment
import com.example.ui.animation.bounceClick

@Composable
fun CastSection(
    castList: List<CastMember>,
    onCastClick: ((CastMember) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (castList.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            castList.forEach { member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { onCastClick?.invoke(member) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val avatarRequest = remember(member.avatarUrl) {
                        com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, member.avatarUrl, preferCompact = true)
                    }
                    if (!member.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = avatarRequest ?: member.avatarUrl,
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = member.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!member.role.isNullOrEmpty()) {
                            Text(
                                text = member.role,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class PlayerTab {
    SEASONS_EPISODES,
    RELATED,
    RECOMMENDED,
    REACTIONS,
    COMMENTS
}

@Composable
fun PlayerTabBar(
    selectedTab: PlayerTab,
    onTabSelected: (PlayerTab) -> Unit,
    showSeasonsTab: Boolean = true,
    showReactionsTab: Boolean = false,
    commentsCount: Int = 14,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSeasonsTab) {
            // Tab 1: SEASONS & EPISODES
            TabPill(
                label = "SEASONS & EPISODES",
                icon = Icons.Outlined.Tv,
                isSelected = selectedTab == PlayerTab.SEASONS_EPISODES,
                onClick = { onTabSelected(PlayerTab.SEASONS_EPISODES) }
            )
        }

        // Tab 2: Related
        TabPill(
            label = "Related",
            icon = null,
            isSelected = selectedTab == PlayerTab.RELATED,
            onClick = { onTabSelected(PlayerTab.RELATED) }
        )

        // Tab 3: Recommended
        TabPill(
            label = "Recommended",
            icon = null,
            isSelected = selectedTab == PlayerTab.RECOMMENDED,
            onClick = { onTabSelected(PlayerTab.RECOMMENDED) }
        )

        val formattedCount = remember(commentsCount) {
            when {
                commentsCount >= 1_000_000 -> "${String.format(java.util.Locale.US, "%.1f", commentsCount / 1_000_000.0)}M"
                commentsCount >= 1_000 -> "${String.format(java.util.Locale.US, "%.1f", commentsCount / 1_000.0)}K"
                else -> "$commentsCount"
            }
        }

        // Tab 4: Comments
        TabPill(
            label = "Comments ($formattedCount)",
            icon = Icons.Outlined.ChatBubbleOutline,
            isSelected = selectedTab == PlayerTab.COMMENTS,
            onClick = { onTabSelected(PlayerTab.COMMENTS) }
        )

        if (showReactionsTab) {
            // Tab 5: Reactions
            TabPill(
                label = "Reactions",
                icon = Icons.Outlined.VideoCameraFront,
                isSelected = selectedTab == PlayerTab.REACTIONS,
                onClick = { onTabSelected(PlayerTab.REACTIONS) }
            )
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SeasonsAndEpisodesView(
    seasons: List<SeriesSeason>,
    activeVideoId: String?,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        // Season Selector Pills
        if (seasons.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                seasons.forEachIndexed { index, season ->
                    FilterChip(
                        selected = index == selectedSeasonIndex,
                        onClick = { selectedSeasonIndex = index },
                        label = { Text(season.seasonName, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Episodes List
        if (currentSeason != null && currentSeason.episodes.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                currentSeason.episodes.forEach { episode ->
                    val isCurrentPlaying = activeVideoId != null && episode.id == activeVideoId

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEpisodeClick(episode) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentPlaying)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = if (isCurrentPlaying)
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail with play badge
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                            ) {
                                val epThumbRequest = remember(episode.thumbnailUrl) {
                                    com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, episode.thumbnailUrl, preferCompact = true)
                                }
                                if (!episode.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = epThumbRequest ?: episode.thumbnailUrl,
                                        contentDescription = episode.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // Duration Badge
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = episode.durationText,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                if (isCurrentPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "S${episode.seasonNumber}:E${episode.episodeNumber}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (isCurrentPlaying) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "NOW PLAYING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = episode.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = episode.viewsText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = if (isCurrentPlaying) Icons.Default.VolumeUp else Icons.Default.PlayCircleOutline,
                                contentDescription = "Play",
                                tint = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentsSectionView(
    comments: List<VideoComment>,
    onAddComment: (String) -> Unit = {},
    onSeekToTime: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    isTorrent: Boolean = false,
    isLoading: Boolean = false,
    totalReviewsCountText: String? = null
) {
    var commentList by remember(comments) { mutableStateOf(comments) }

    // Search & Tag Filter States
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    // Sort state: TOP, NEWEST, TIMED
    var activeSort by remember { mutableStateOf("TOP") }

    // State for liked/disliked comments
    val userLikes = remember { mutableStateMapOf<String, Int>() }
    val userDislikes = remember { mutableStateMapOf<String, Int>() }

    // Timestamp regex detector
    val timestampRegex = remember { Regex("""\b(\d{1,2}:)?\d{1,2}:\d{2}\b""") }

    // Extract dynamic popular keyword tags + Timed comments tag (BookMyShow style)
    val popularTags = remember(commentList) {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with",
            "by", "from", "up", "about", "into", "over", "after", "is", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did", "can", "could",
            "will", "would", "should", "i", "you", "he", "she", "it", "we", "they", "this",
            "that", "these", "those", "my", "your", "his", "her", "its", "our", "their", "what",
            "which", "who", "whom", "whose", "when", "where", "why", "how", "all", "any", "both",
            "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only",
            "own", "same", "so", "than", "too", "very", "just", "like", "movie", "film", "video",
            "watch", "watching", "watched", "really", "much", "even", "also", "get", "got", "one",
            "out", "see", "good", "great", "there", "their", "time", "than", "then", "make", "made"
        )

        val wordCounts = mutableMapOf<String, Int>()
        var timedCount = 0

        val curatedKeywords = listOf(
            "Climax", "Soundtrack", "BGM", "Acting", "VFX", "Cinematography",
            "Story", "Plot", "Ending", "Goosebumps", "Music", "Direction",
            "Visuals", "Funny", "Masterpiece", "Twist", "Song", "Voice",
            "Action", "Cast", "Emotion", "Dialogue", "Screenplay", "Animation"
        )

        commentList.forEach { c ->
            val fullText = "${c.reviewTitle ?: ""} ${c.commentText}"
            if (timestampRegex.containsMatchIn(fullText)) {
                timedCount++
            }

            curatedKeywords.forEach { keyword ->
                if (fullText.contains(keyword, ignoreCase = true)) {
                    wordCounts[keyword] = (wordCounts[keyword] ?: 0) + 1
                }
            }

            // Word frequency analysis for emerging topics
            fullText.split(Regex("[\\s,.;:!?\"'()\\[\\]{}<>\\-_/\\\\]+"))
                .map { it.trim().lowercase() }
                .filter { it.length in 4..16 && it !in stopWords && !it.all { ch -> ch.isDigit() } }
                .forEach { word ->
                    val capitalized = word.replaceFirstChar { it.uppercase() }
                    if (capitalized !in wordCounts) {
                        wordCounts[capitalized] = (wordCounts[capitalized] ?: 0) + 1
                    }
                }
        }

        val list = mutableListOf<Pair<String, Int>>()
        if (timedCount > 0) {
            list.add("⏱️ Timestamps" to timedCount)
        }

        wordCounts.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(12)
            .forEach { list.add(it.key to it.value) }

        list
    }

    // Filter and Sort comments list
    val filteredComments = remember(commentList, activeSort, searchQuery, selectedTag) {
        var result = commentList.asSequence()

        // 1. Filter by Search Query
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter { c ->
                c.authorName.lowercase().contains(q) ||
                    (c.reviewTitle?.lowercase()?.contains(q) == true) ||
                    c.commentText.lowercase().contains(q)
            }
        }

        // 2. Filter by Selected Tag
        if (!selectedTag.isNullOrBlank()) {
            val tag = selectedTag!!
            if (tag == "⏱️ Timestamps") {
                result = result.filter { c ->
                    timestampRegex.containsMatchIn("${c.reviewTitle ?: ""} ${c.commentText}")
                }
            } else {
                result = result.filter { c ->
                    val combined = "${c.reviewTitle ?: ""} ${c.commentText}"
                    combined.contains(tag, ignoreCase = true)
                }
            }
        }

        // 3. Sort Order
        when (activeSort) {
            "TIMED" -> result.filter { c ->
                timestampRegex.containsMatchIn("${c.reviewTitle ?: ""} ${c.commentText}")
            }.sortedByDescending { it.likeCount }
            "NEWEST" -> result.toList().reversed().asSequence()
            else -> result.sortedByDescending { it.likeCount } // TOP
        }.toList()
    }

    Column(modifier = modifier.fillMaxWidth()) {

        // TOP HEADER: Tab Selector + Search Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Filter Pills
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0A0C10),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // "Top" Tab
                    Surface(
                        onClick = { activeSort = "TOP" },
                        shape = RoundedCornerShape(20.dp),
                        color = if (activeSort == "TOP") Color(0xFFF5C518) else Color.Transparent
                    ) {
                        Text(
                            text = "Top Reviews",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeSort == "TOP") Color.Black else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // "Newest" Tab
                    Surface(
                        onClick = { activeSort = "NEWEST" },
                        shape = RoundedCornerShape(20.dp),
                        color = if (activeSort == "NEWEST") Color(0xFFF5C518) else Color.Transparent
                    ) {
                        Text(
                            text = "Newest",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeSort == "NEWEST") Color.Black else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // "Timed" Tab
                    Surface(
                        onClick = { activeSort = "TIMED" },
                        shape = RoundedCornerShape(20.dp),
                        color = if (activeSort == "TIMED") MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Text(
                            text = "⏱️ Timed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeSort == "TIMED") MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Search Icon Toggle Button (🔍)
            IconButton(
                onClick = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSearchActive || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF0A0C10))
                    .border(1.dp, if (isSearchActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search comments",
                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // EXPANDABLE SEARCH BAR
        AnimatedVisibility(visible = isSearchActive) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF141720),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search comments, reviews, keywords...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // POPULAR KEYWORD TAGS ROW (BookMyShow Style)
        if (popularTags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                // "All" Tag Chip
                item {
                    val isAllSelected = selectedTag == null
                    Surface(
                        onClick = { selectedTag = null },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isAllSelected) Color(0xFFF5C518).copy(alpha = 0.18f) else Color(0xFF10131B),
                        border = BorderStroke(
                            1.dp,
                            if (isAllSelected) Color(0xFFF5C518) else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = "All (${commentList.size})",
                            fontSize = 12.sp,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAllSelected) Color(0xFFF5C518) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Dynamic Popular Keyword Tag Chips
                items(popularTags) { (tag, count) ->
                    val isSelected = selectedTag == tag
                    val isTimestampTag = tag.startsWith("⏱️")
                    Surface(
                        onClick = {
                            selectedTag = if (isSelected) null else tag
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            isSelected && isTimestampTag -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            isSelected -> Color(0xFFF5C518).copy(alpha = 0.2f)
                            isTimestampTag -> Color(0xFF0D1B2A)
                            else -> Color(0xFF10131B)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSelected && isTimestampTag -> MaterialTheme.colorScheme.primary
                                isSelected -> Color(0xFFF5C518)
                                isTimestampTag -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else -> Color.White.copy(alpha = 0.1f)
                            }
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected && isTimestampTag -> MaterialTheme.colorScheme.primary
                                    isSelected -> Color(0xFFF5C518)
                                    isTimestampTag -> Color(0xFF64B5F6)
                                    else -> Color.White.copy(alpha = 0.85f)
                                }
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Filter Banner (if searching or tag selected)
        if (searchQuery.isNotEmpty() || selectedTag != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append("Showing ${filteredComments.size} reviews")
                        if (selectedTag != null) append(" for '$selectedTag'")
                        if (searchQuery.isNotBlank()) append(" matching \"$searchQuery\"")
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Text(
                    text = "Clear Filter",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            searchQuery = ""
                            selectedTag = null
                            isSearchActive = false
                        }
                        .padding(4.dp)
                )
            }
        }

        // Loading State
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFFF5C518))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading reviews...", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
                }
            }
        } else if (filteredComments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedTag != null) "No reviews matching your filters." else "No user reviews available.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    if (searchQuery.isNotEmpty() || selectedTag != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Reset filters",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    searchQuery = ""
                                    selectedTag = null
                                    isSearchActive = false
                                }
                                .padding(8.dp)
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredComments.forEach { comment ->
                    val extraLikes = userLikes[comment.id] ?: 0
                    val extraDislikes = userDislikes[comment.id] ?: 0

                    YouTubeStyleCommentItem(
                        comment = comment,
                        isTorrent = isTorrent,
                        isRevealed = true,
                        onToggleSpoiler = {},
                        extraLikes = extraLikes,
                        extraDislikes = extraDislikes,
                        onLikeClick = {
                            userLikes[comment.id] = if (extraLikes > 0) 0 else 1
                        },
                        onDislikeClick = {
                            userDislikes[comment.id] = if (extraDislikes > 0) 0 else 1
                        },
                        onSeekToTime = onSeekToTime
                    )
                }
            }
        }
    }
}

@Composable
fun YouTubeStyleCommentItem(
    comment: VideoComment,
    isTorrent: Boolean = false,
    isRevealed: Boolean = true,
    onToggleSpoiler: () -> Unit = {},
    extraLikes: Int = 0,
    extraDislikes: Int = 0,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onSeekToTime: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Parse timestamps (e.g., 01:23, 12:45, 1:04:20) in comment text
    val timestamps = remember(comment.commentText) {
        val regex = Regex("""\b((\d{1,2}):)?(\d{1,2}):(\d{2})\b""")
        regex.findAll(comment.commentText).mapNotNull { match ->
            val fullStr = match.value
            val parts = fullStr.split(":")
            val seconds = try {
                when (parts.size) {
                    2 -> parts[0].toLong() * 60 + parts[1].toLong()
                    3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                    else -> null
                }
            } catch (_: Exception) { null }
            if (seconds != null) fullStr to seconds else null
        }.distinctBy { it.first }.toList()
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF090A0E) // Pure deep AMOLED black card container
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Avatar + Username + Time + Rating Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar Picture or Initials
                if (!comment.authorAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = comment.authorAvatarUrl,
                        contentDescription = comment.authorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                } else {
                    val initialName = comment.authorName.removePrefix("@").trim()
                    val initial = if (initialName.isNotEmpty()) initialName.take(1).uppercase() else "U"
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFE50914), Color(0xFFB20710))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (comment.authorName.startsWith("@")) comment.authorName else "@${comment.authorName}"
                    Text(
                        text = displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = comment.timeAgo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                // Rating pill if present
                if (comment.rating != null && comment.rating > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5C518).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF5C518).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFF5C518),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", comment.rating),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF5C518)
                            )
                        }
                    }
                }
            }

            // Title headline
            val titleText = if (!comment.reviewTitle.isNullOrBlank()) {
                comment.reviewTitle
            } else {
                comment.commentText.take(70)
            }

            val hasBodyText = !comment.commentText.isNullOrBlank() &&
                (comment.reviewTitle.isNullOrBlank() || comment.commentText.trim() != comment.reviewTitle.trim())

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = titleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.95f),
                lineHeight = 18.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Explained body text shown ONLY when expanded
            if (isExpanded && hasBodyText) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = comment.commentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Interactive YouTube Timed Timestamps Row
            if (timestamps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(timestamps) { (timeStr, seconds) ->
                        Surface(
                            onClick = { onSeekToTime(seconds) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Jump to timestamp",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = timeStr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Toggle expand button
            if (hasBodyText) {
                Text(
                    text = if (isExpanded) "Show less" else "Read full review",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(top = 6.dp, bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Action Bar: Helpful / Unhelpful buttons
            val commentLikeScale by animateFloatAsState(
                targetValue = if (extraLikes > 0) 1.22f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "commentLikeScale"
            )
            val commentDislikeScale by animateFloatAsState(
                targetValue = if (extraDislikes > 0) 1.22f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "commentDislikeScale"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Helpful / Like Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .bounceClick(scaleDown = 0.85f) { onLikeClick() }
                ) {
                    Icon(
                        imageVector = if (extraLikes > 0) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Helpful",
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer {
                                scaleX = commentLikeScale
                                scaleY = commentLikeScale
                            },
                        tint = if (extraLikes > 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                    )
                    val totalLikes = comment.likeCount + extraLikes
                    if (totalLikes > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${formatLikeCount(totalLikes)} helpful",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (extraLikes > 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Unhelpful / Dislike Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .bounceClick(scaleDown = 0.85f) { onDislikeClick() }
                ) {
                    Icon(
                        imageVector = if (extraDislikes > 0) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Unhelpful",
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer {
                                scaleX = commentDislikeScale
                                scaleY = commentDislikeScale
                            },
                        tint = if (extraDislikes > 0) Color(0xFFFF5252) else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun formatLikeCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1000000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1000.0)
        else -> count.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastFilmographySheet(
    castMember: CastMember,
    onDismiss: () -> Unit,
    onSelectFilmographyItem: (com.example.model.CastFilmographyItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var filmography by remember { mutableStateOf<List<com.example.model.CastFilmographyItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(castMember.name, castMember.personId) {
        isLoading = true
        filmography = com.example.util.TMDBHelper.fetchFilmographyForPerson(castMember.name, castMember.personId)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!castMember.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = castMember.avatarUrl,
                        contentDescription = castMember.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = castMember.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = castMember.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!castMember.role.isNullOrEmpty()) {
                        Text(
                            text = "Role: ${castMember.role}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Filmography & Works",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading movies & TV series for ${castMember.name}...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filmography.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No filmography credits found for this actor.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Movies & TV Series (${filmography.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filmography) { item ->
                        Card(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable {
                                    onSelectFilmographyItem(item)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color.DarkGray)
                                ) {
                                    val posterReq = remember(item.posterUrl) {
                                        com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, item.posterUrl, preferCompact = true)
                                    }
                                    if (!item.posterUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = posterReq ?: item.posterUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    // Rating pill
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "★ ${String.format(java.util.Locale.US, "%.1f", item.voteAverage)}",
                                            color = Color(0xFFFFC107),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    // Year pill
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = item.releaseYear,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.character.isNotBlank()) {
                                        Text(
                                            text = "as ${item.character}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
