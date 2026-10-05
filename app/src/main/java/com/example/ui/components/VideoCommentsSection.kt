package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoComment
import java.util.regex.Pattern

private val YT_BLUE_LINK = Color(0xFF3EA6FF)
private val YT_DARK_SURFACE = Color(0xFF1E1E1E)
private val YT_DARK_CARD = Color(0xFF282828)
private val YT_SECONDARY_TEXT = Color(0xFFAAAAAA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCommentsSection(
    comments: List<VideoComment>,
    isLoading: Boolean = false,
    onAddComment: (String) -> Unit = {},
    onLikeComment: (String) -> Unit = {},
    onSeekToTimestamp: (Long) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var userCommentInput by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Top") } // Top, Newest
    val filterOptions = listOf("Top", "Newest")

    val sortedComments = remember(comments, selectedFilter) {
        when (selectedFilter) {
            "Newest" -> comments.reversed()
            else -> comments.sortedByDescending { it.likeCount }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Comments Header & Filter Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Comment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))

            // Refresh Button & Sort Pills
            IconButton(
                onClick = { onRefresh() },
                modifier = Modifier.size(32.dp),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh comments",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            // Sort Pills
            filterOptions.forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 12.sp) },
                    modifier = Modifier.padding(start = 4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Comment Input Field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "You",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            TextField(
                value = userCommentInput,
                onValueChange = { userCommentInput = it },
                placeholder = {
                    Text("Add a comment...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                singleLine = false,
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )

            if (userCommentInput.isNotBlank()) {
                IconButton(
                    onClick = {
                        onAddComment(userCommentInput)
                        userCommentInput = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Post Comment",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else if (sortedComments.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No comments loaded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap below to fetch YouTube comments or be the first to post!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { onRefresh() },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load / Refresh Comments")
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                sortedComments.forEach { comment ->
                    SingleCommentCard(
                        comment = comment,
                        onLikeClick = { onLikeComment(comment.id) },
                        onSeekToTimestamp = onSeekToTimestamp
                    )
                }
            }
        }
    }
}

/**
 * YouTube-style Single Comment Card with Author handle, time ago, styled interactive timestamps,
 * and like/dislike/reply metrics.
 */
@Composable
fun SingleCommentCard(
    comment: VideoComment,
    onLikeClick: () -> Unit,
    onSeekToTimestamp: (Long) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Author Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            if (!comment.authorAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = comment.authorAvatarUrl,
                    contentDescription = comment.authorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = comment.authorName.trimStart('@').take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Comment Body Column
        Column(modifier = Modifier.weight(1f)) {
            // Author Name / Handle + Time ago + Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val authorDisplay = if (comment.authorName.startsWith("@")) comment.authorName else "@${comment.authorName.replace(" ", "")}"
                Text(
                    text = authorDisplay,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFFCCCCCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = comment.timeAgo,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = YT_SECONDARY_TEXT
                )

                if (!comment.sourceBadge.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF333333)
                    ) {
                        Text(
                            text = comment.sourceBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFAAAAAA),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment Text with Parsed Clickable Timestamps
            val annotatedText = remember(comment.commentText) {
                buildAnnotatedStringWithTimestamps(comment.commentText)
            }

            val isLongComment = comment.commentText.length > 200 || comment.commentText.count { it == '\n' } > 3

            ClickableText(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onClick = { offset ->
                    val annotations = annotatedText.getStringAnnotations("TIMESTAMP", offset, offset)
                    if (annotations.isNotEmpty()) {
                        val seconds = annotations.first().item.toLongOrNull() ?: 0L
                        onSeekToTimestamp(seconds * 1000L)
                    } else if (isLongComment) {
                        isExpanded = !isExpanded
                    }
                }
            )

            if (isLongComment) {
                Text(
                    text = if (isExpanded) "Show less" else "Read more",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YT_SECONDARY_TEXT,
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Row: Like, Dislike, Replies
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Like Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLikeClick() }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (comment.isLikedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = if (comment.isLikedByMe) YT_BLUE_LINK else YT_SECONDARY_TEXT,
                        modifier = Modifier.size(15.dp)
                    )
                    if (comment.likeCount > 0) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = formatCount(comment.likeCount),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (comment.isLikedByMe) YT_BLUE_LINK else YT_SECONDARY_TEXT
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                // Dislike Button
                Icon(
                    imageVector = if (comment.isDislikedByMe) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = "Dislike",
                    tint = YT_SECONDARY_TEXT,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(20.dp))

                // Reply Button / Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { /* Expand / Add reply */ }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ModeComment,
                        contentDescription = "Reply",
                        tint = YT_SECONDARY_TEXT,
                        modifier = Modifier.size(14.dp)
                    )
                    if (!comment.totalReviewsCountText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = comment.totalReviewsCountText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = YT_BLUE_LINK
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds AnnotatedString identifying video timestamps (e.g., 0:30, 01:45, 1:15:30) and formatting
 * them with YouTube blue clickable links.
 */
private fun buildAnnotatedStringWithTimestamps(text: String): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        val pattern = Pattern.compile("\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\b")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val matchStr = matcher.group()

            val parts = matchStr.split(":")
            val totalSeconds = when (parts.size) {
                3 -> (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
                2 -> (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
                else -> 0L
            }

            addStyle(
                style = SpanStyle(
                    color = YT_BLUE_LINK,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.None
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = "TIMESTAMP",
                annotation = "$totalSeconds",
                start = start,
                end = end
            )
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1000.0)
        else -> "$count"
    }
}

/**
 * YouTube-style Full Comments Bottom Sheet / Panel
 */
@Composable
fun CommentsPanel(
    comments: List<VideoComment>,
    isLoading: Boolean = false,
    onAddComment: (String) -> Unit = {},
    onLikeComment: (String) -> Unit = {},
    onSeekToTimestamp: (Long) -> Unit = {},
    onRefresh: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var userCommentInput by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Top") }
    val filterOptions = listOf("Top", "Newest")
    val quickEmojis = listOf("❤️", "🔥", "👏", "😂", "😍", "🎉", "💡", "👍", "🙌")

    val sortedComments = remember(comments, selectedFilter) {
        when (selectedFilter) {
            "Newest" -> comments.reversed()
            else -> comments.sortedByDescending { it.likeCount }
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = Color(0xFF0F0F0F),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color(0xFF555555), RoundedCornerShape(2.dp))
                )
            }

            // Header Row: Title + Count + Sort Chips + Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comments",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                if (comments.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatCount(comments.size),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = YT_SECONDARY_TEXT
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sort Chips
                filterOptions.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color.White else Color(0xFF272727),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable { selectedFilter = filter }
                    ) {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

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

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

            // Comments List Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && comments.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading comments...",
                                fontSize = 13.sp,
                                color = YT_SECONDARY_TEXT
                            )
                        }
                    }
                } else if (sortedComments.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No comments yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Say something to start the conversation!",
                            fontSize = 13.sp,
                            color = YT_SECONDARY_TEXT
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = onRefresh,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Comments")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(sortedComments, key = { it.id }) { comment ->
                            SingleCommentCard(
                                comment = comment,
                                onLikeClick = { onLikeComment(comment.id) },
                                onSeekToTimestamp = { ms ->
                                    onSeekToTimestamp(ms)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

            // Sticky Bottom Comment Input Bar with Emoji Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181818))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Quick emoji row
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(quickEmojis) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { userCommentInput += emoji }
                                .padding(4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "You",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    TextField(
                        value = userCommentInput,
                        onValueChange = { userCommentInput = it },
                        placeholder = {
                            Text("Add a comment...", fontSize = 13.5.sp, color = YT_SECONDARY_TEXT)
                        },
                        singleLine = false,
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF222222),
                            unfocusedContainerColor = Color(0xFF222222),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                    )

                    if (userCommentInput.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                onAddComment(userCommentInput.trim())
                                userCommentInput = ""
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
