package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppScreen
import com.example.model.UserProfile

/**
 * YouTube-style clean and minimalist bottom navigation bar.
 * Dynamically displays the user's selected 3D avatar or uploaded gallery logo on the "You" tab,
 * with YouTube-style active selection border ring and spring touch animations.
 */
@Composable
fun LiquidGlassNavBar(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    userProfile: UserProfile? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavItem(
                    label = "Home",
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    isSelected = (currentScreen == AppScreen.HOME),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.HOME) }
                )

                NavItem(
                    label = "Explore",
                    selectedIcon = Icons.Filled.Explore,
                    unselectedIcon = Icons.Outlined.Explore,
                    isSelected = (currentScreen == AppScreen.EXPLORE),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.EXPLORE) }
                )

                NavItem(
                    label = "Subscriptions",
                    selectedIcon = Icons.Filled.Subscriptions,
                    unselectedIcon = Icons.Outlined.Subscriptions,
                    isSelected = (currentScreen == AppScreen.SUBSCRIPTIONS || currentScreen == AppScreen.LIBRARY),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.SUBSCRIPTIONS) }
                )

                YouNavItem(
                    userProfile = userProfile,
                    isSelected = (currentScreen == AppScreen.ACCOUNT),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.ACCOUNT) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_item_scale"
    )

    val activeColor = MaterialTheme.colorScheme.onBackground
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (isSelected) activeColor else inactiveColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) activeColor else inactiveColor,
                maxLines = 1
            )
        }
    }
}

/**
 * YouTube-style "You" tab item that reflects the user's customized 3D avatar / uploaded logo
 */
@Composable
private fun YouNavItem(
    userProfile: UserProfile?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "you_nav_item_scale"
    )

    val activeColor = MaterialTheme.colorScheme.onBackground
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    val avatarUrl = userProfile?.avatarUrl
    val presetId = userProfile?.avatarPreset
    val matchedPreset = remember(presetId) {
        BuiltinAvatarPresets.models.find { it.id == presetId }
    }
    val effectiveAvatarUrl = avatarUrl ?: matchedPreset?.imageUrl

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            if (!effectiveAvatarUrl.isNullOrBlank()) {
                // YouTube-style circular profile image with active selection border ring
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.8.dp,
                                    color = activeColor,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isSelected) 1.5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2C)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = effectiveAvatarUrl,
                        contentDescription = "You profile avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else if (matchedPreset != null) {
                // Gradient emoji fallback
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.8.dp,
                                    color = activeColor,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isSelected) 1.5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(matchedPreset.gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = matchedPreset.emoji, fontSize = 12.sp)
                }
            } else {
                // Generic AccountCircle icon
                Icon(
                    imageVector = if (isSelected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                    contentDescription = "You",
                    tint = if (isSelected) activeColor else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "You",
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) activeColor else inactiveColor,
                maxLines = 1
            )
        }
    }
}

