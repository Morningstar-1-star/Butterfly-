package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.PoTokenDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableProviders by viewModel.availableProviders.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    val enabledProviders by viewModel.enabledProviderIds.collectAsState()

    // Expandable accordion section states
    var isSourcesExpanded by remember { mutableStateOf(true) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isGesturesExpanded by remember { mutableStateOf(false) }
    var isShortsExpanded by remember { mutableStateOf(false) }
    var isAppearanceExpanded by remember { mutableStateOf(false) }
    var isSecurityExpanded by remember { mutableStateOf(false) }

    // State Toggles for Player, Gestures, Shorts, Appearance
    var autoPlayEnabled by remember { mutableStateOf(true) }
    var universalPlayerMode by remember { mutableStateOf(true) }
    var defaultQuality by remember { mutableStateOf("1080p") }
    var seekDuration by remember { mutableStateOf("10 Seconds") }
    var gestureControlsEnabled by remember { mutableStateOf(true) }
    var showShortsSection by remember { mutableStateOf(true) }
    var autoPlayShorts by remember { mutableStateOf(true) }
    var amoledBlack by remember { mutableStateOf(true) }

    var showPoTokenDialog by remember { mutableStateOf(false) }

    val allExpanded = isSourcesExpanded && isPlayerExpanded && isGesturesExpanded && 
            isShortsExpanded && isAppearanceExpanded && isSecurityExpanded

    if (showPoTokenDialog) {
        PoTokenDialog(
            onDismiss = { showPoTokenDialog = false },
            onApplyToken = {}
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val target = !allExpanded
                        isSourcesExpanded = target
                        isPlayerExpanded = target
                        isGesturesExpanded = target
                        isShortsExpanded = target
                        isAppearanceExpanded = target
                        isSecurityExpanded = target
                    }) {
                        Text(
                            text = if (allExpanded) "Collapse All" else "Expand All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. SOURCES & PROVIDERS SETTINGS
            item {
                ExpandableSettingsCard(
                    title = "Sources & Extensions",
                    icon = Icons.Outlined.Extension,
                    isExpanded = isSourcesExpanded,
                    onToggleExpand = { isSourcesExpanded = !isSourcesExpanded },
                    badgeText = "${enabledProviders.size} Active"
                ) {
                    Text(
                        text = "Primary Feed Provider",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = (activeProviderId == "all"),
                                onClick = { viewModel.setActiveProvider("all") },
                                label = { Text("All Unified") },
                                leadingIcon = if (activeProviderId == "all") {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                        items(availableProviders.filter { it.id != "all" }) { provider ->
                            FilterChip(
                                selected = (activeProviderId == provider.id),
                                onClick = { viewModel.setActiveProvider(provider.id) },
                                label = { Text(provider.name) },
                                leadingIcon = if (activeProviderId == provider.id) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Enabled Media Extensions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    availableProviders.filter { it.id != "all" }.forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = provider.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = enabledProviders.contains(provider.id),
                                onCheckedChange = { viewModel.toggleProviderEnabled(provider.id) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.navigateToScreen(AppScreen.PROVIDERS) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage Extensions & Repos")
                    }
                }
            }

            // 2. PLAYER & AUTOPLAY SETTINGS
            item {
                ExpandableSettingsCard(
                    title = "Player & Playback",
                    icon = Icons.Outlined.PlayCircle,
                    isExpanded = isPlayerExpanded,
                    onToggleExpand = { isPlayerExpanded = !isPlayerExpanded },
                    badgeText = "Quality: $defaultQuality"
                ) {
                    SettingsSwitchRow(
                        title = "Autoplay Videos Automatically",
                        subtitle = "Start playing immediately upon selecting a video without manual play click",
                        checked = autoPlayEnabled,
                        onCheckedChange = { autoPlayEnabled = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    SettingsSwitchRow(
                        title = "Universal Player Mode",
                        subtitle = "Use standardized uniform controls and player skin across all sources",
                        checked = universalPlayerMode,
                        onCheckedChange = { universalPlayerMode = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Resolution Quality",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Preferred video stream resolution",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = {
                                defaultQuality = when (defaultQuality) {
                                    "1080p" -> "720p"
                                    "720p" -> "Auto"
                                    else -> "1080p"
                                }
                            },
                            label = { Text(defaultQuality, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            // 3. GESTURE CONTROLS
            item {
                ExpandableSettingsCard(
                    title = "Gesture Controls",
                    icon = Icons.Outlined.TouchApp,
                    isExpanded = isGesturesExpanded,
                    onToggleExpand = { isGesturesExpanded = !isGesturesExpanded },
                    badgeText = if (gestureControlsEnabled) "Enabled" else "Disabled"
                ) {
                    SettingsSwitchRow(
                        title = "Enable Touch Gestures",
                        subtitle = "Double tap to seek, swipe up/down for volume and brightness",
                        checked = gestureControlsEnabled,
                        onCheckedChange = { gestureControlsEnabled = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Double Tap Seek Duration",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Rewind / Fast forward interval",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = {
                                seekDuration = when (seekDuration) {
                                    "10 Seconds" -> "15 Seconds"
                                    "15 Seconds" -> "5 Seconds"
                                    else -> "10 Seconds"
                                }
                            },
                            label = { Text(seekDuration, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            // 4. SHORTS SETTINGS
            item {
                ExpandableSettingsCard(
                    title = "Shorts Configuration",
                    icon = Icons.Outlined.AppShortcut,
                    isExpanded = isShortsExpanded,
                    onToggleExpand = { isShortsExpanded = !isShortsExpanded },
                    badgeText = if (showShortsSection) "Active Feed" else "Hidden"
                ) {
                    SettingsSwitchRow(
                        title = "Show Shorts Section on Home Feed",
                        subtitle = "Display short-form video carousel at the top of main feed",
                        checked = showShortsSection,
                        onCheckedChange = { showShortsSection = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    SettingsSwitchRow(
                        title = "Autoplay Shorts in Feed",
                        subtitle = "Automatically start playing shorts when scrolling through feed",
                        checked = autoPlayShorts,
                        onCheckedChange = { autoPlayShorts = it }
                    )
                }
            }

            // 5. APPEARANCE & THEME SETTINGS
            item {
                val themeMode by viewModel.themeMode.collectAsState()
                val accentColor by viewModel.accentColor.collectAsState()

                ExpandableSettingsCard(
                    title = "Appearance & Theme",
                    icon = Icons.Outlined.Palette,
                    isExpanded = isAppearanceExpanded,
                    onToggleExpand = { isAppearanceExpanded = !isAppearanceExpanded },
                    badgeText = if (themeMode == com.example.ui.ThemeMode.LIGHT) "Light Theme" else "AMOLED Dark"
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = (themeMode == com.example.ui.ThemeMode.AMOLED_DARK),
                            onClick = { viewModel.setThemeMode(com.example.ui.ThemeMode.AMOLED_DARK) },
                            label = { Text("AMOLED Dark (Pitch Black)") },
                            leadingIcon = if (themeMode == com.example.ui.ThemeMode.AMOLED_DARK) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = (themeMode == com.example.ui.ThemeMode.LIGHT),
                            onClick = { viewModel.setThemeMode(com.example.ui.ThemeMode.LIGHT) },
                            label = { Text("Light Mode (White)") },
                            leadingIcon = if (themeMode == com.example.ui.ThemeMode.LIGHT) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Secondary Accent Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose a secondary accent color for controls, buttons, and highlights",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(com.example.ui.AppAccentColor.values()) { colorOpt ->
                            val isSelected = (accentColor == colorOpt)
                            Surface(
                                onClick = { viewModel.setAccentColor(colorOpt) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, colorOpt.color) else null
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(colorOpt.color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = colorOpt.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. PO TOKEN & API KEYS
            item {
                ExpandableSettingsCard(
                    title = "Security & API Tokens",
                    icon = Icons.Outlined.VpnKey,
                    isExpanded = isSecurityExpanded,
                    onToggleExpand = { isSecurityExpanded = !isSecurityExpanded },
                    badgeText = "PO Token Config"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPoTokenDialog = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "YouTube PO Token & Visitor Data",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configure PO Token and Visitor Data to bypass YouTube playback blocks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableSettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (badgeText != null) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
