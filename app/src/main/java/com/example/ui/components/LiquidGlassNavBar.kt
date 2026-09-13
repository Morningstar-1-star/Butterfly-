package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Subscriptions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppScreen
import com.example.model.UserProfile

/**
 * YouTube-style ultra-minimalist bottom navigation bar.
 * Clean, pure-white rounded icons without text labels, featuring smooth rounded
 * corners and dynamic 3D avatar / profile support on the "You" tab.
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
                color = Color.White.copy(alpha = 0.12f),
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
                    selectedIcon = Icons.Rounded.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    isSelected = (currentScreen == AppScreen.HOME),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.HOME) }
                )

                NavItem(
                    label = "Explore",
                    selectedIcon = Icons.Rounded.Explore,
                    unselectedIcon = Icons.Outlined.Explore,
                    isSelected = (currentScreen == AppScreen.EXPLORE),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.EXPLORE) }
                )

                NavItem(
                    label = "Subscriptions",
                    selectedIcon = Icons.Rounded.Subscriptions,
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
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_item_scale"
    )

    // Pure white icons matching the YouTube app UI
    val iconColor = Color.White

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

/**
 * YouTube-style "You" tab item with pure white styling and rounded profile avatar
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
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "you_nav_item_scale"
    )

    val activeColor = Color.White
    val inactiveColor = Color.White

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
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center
        ) {
            if (!effectiveAvatarUrl.isNullOrBlank()) {
                // YouTube-style circular profile image with active white selection border ring
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = activeColor,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isSelected) 2.dp else 0.dp)
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
                        .size(26.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = activeColor,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isSelected) 2.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(matchedPreset.gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = matchedPreset.emoji, fontSize = 13.sp)
                }
            } else {
                // Pure white rounded AccountCircle icon
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.AccountCircle else Icons.Outlined.AccountCircle,
                    contentDescription = "You",
                    tint = if (isSelected) activeColor else inactiveColor,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}
