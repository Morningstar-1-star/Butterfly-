package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val shimmerTransition = rememberInfiniteTransition(label = "FeedShimmerTransition")
    val translateAnim by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FeedShimmerTranslate"
    )

    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.run {
        (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5
    }

    val baseColor = if (isDark) Color(0xFF1E1F24) else Color(0xFFE2E4E9)
    val highlightColor = if (isDark) Color(0xFF2C2D35) else Color(0xFFF2F4F8)

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 600f, translateAnim - 600f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
fun FeedSkeletonLoading(
    modifier: Modifier = Modifier,
    itemCount: Int = 4
) {
    val shimmerBrush = rememberShimmerBrush()

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        repeat(itemCount) {
            VideoCardSkeleton(
                shimmerBrush = shimmerBrush,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
fun VideoCardSkeleton(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Video Thumbnail Skeleton (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(shimmerBrush)
            )

            // Video Info Metadata Skeleton
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Channel Avatar Skeleton
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title and details lines
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp)
                ) {
                    // Title Line 1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title Line 2
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Channel name & Metadata Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}
