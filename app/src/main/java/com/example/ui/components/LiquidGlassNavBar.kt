package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen

@Composable
fun LiquidGlassNavBar(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 1.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
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
                    label = "Library",
                    selectedIcon = Icons.Filled.Subscriptions,
                    unselectedIcon = Icons.Outlined.Subscriptions,
                    isSelected = (currentScreen == AppScreen.SUBSCRIPTIONS),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.SUBSCRIPTIONS) }
                )

                NavItem(
                    label = "Account",
                    selectedIcon = Icons.Filled.AccountCircle,
                    unselectedIcon = Icons.Outlined.AccountCircle,
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
    val isPressed = interactionSource.collectIsPressedAsState().value

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_scale"
    )

    val yellowAccent = Color(0xFFFFD600)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 26.dp)
                        .background(yellowAccent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = selectedIcon,
                        contentDescription = label,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = unselectedIcon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) yellowAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
