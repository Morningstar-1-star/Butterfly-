package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()

    // Expandable accordion section states:
    var isAppearanceExpanded by remember { mutableStateOf(true) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSponsorBlockExpanded by remember { mutableStateOf(false) }
    var isGesturesExpanded by remember { mutableStateOf(false) }
    var isShortsExpanded by remember { mutableStateOf(false) }
    var isSourcesExpanded by remember { mutableStateOf(false) }

    // SponsorBlock Preferences State
    val context = androidx.compose.ui.platform.LocalContext.current
    val sbPrefs = remember { com.example.sponsorblock.SponsorBlockPreferences.getInstance(context) }
    val sbEnabled by sbPrefs.isEnabled.collectAsState()
    val sbShowUndo by sbPrefs.showUndoSkipNotification.collectAsState()
    val sbCompactSkip by sbPrefs.useCompactSkipButton.collectAsState()
    val sbAutoHide by sbPrefs.autoHideSkipButton.collectAsState()
    val sbApiUrl by sbPrefs.apiUrl.collectAsState()
    val sbSkippedCount by sbPrefs.skippedSegmentsCount.collectAsState()
    val sbSkippedTime by sbPrefs.skippedTimeSeconds.collectAsState()
    var sbApiUrlInput by remember(sbApiUrl) { mutableStateOf(sbApiUrl) }

    // State Toggles for Player, Gestures, Shorts
    var autoPlayEnabled by remember { mutableStateOf(true) }
    var universalPlayerMode by remember { mutableStateOf(true) }
    var defaultQuality by remember { mutableStateOf("1080p") }
    var gestureControlsEnabled by remember { mutableStateOf(true) }
    var showShortsSection by remember { mutableStateOf(true) }
    var autoPlayShorts by remember { mutableStateOf(true) }

    val allExpanded = isAppearanceExpanded && isPlayerExpanded && isSponsorBlockExpanded &&
            isGesturesExpanded && isShortsExpanded && isSourcesExpanded

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
                        isAppearanceExpanded = target
                        isPlayerExpanded = target
                        isSponsorBlockExpanded = target
                        isGesturesExpanded = target
                        isShortsExpanded = target
                        isSourcesExpanded = target
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
            // 1. APPEARANCE & THEME (FIRST)
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
                            label = { Text("AMOLED Dark") },
                            leadingIcon = if (themeMode == com.example.ui.ThemeMode.AMOLED_DARK) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = (themeMode == com.example.ui.ThemeMode.LIGHT),
                            onClick = { viewModel.setThemeMode(com.example.ui.ThemeMode.LIGHT) },
                            label = { Text("Light Mode") },
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
                                            .background(colorOpt.color)
                                            .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (colorOpt == com.example.ui.AppAccentColor.MONOCHROME) Color.Black else Color.Black,
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "18+ Adult Content",
                        subtitle = "Enable adult catalog, steamy movies, and JAV releases",
                        checked = adultContentEnabled,
                        onCheckedChange = { viewModel.setAdultContentEnabled(it) }
                    )
                }
            }

            // 2. PLAYER & PLAYBACK SETTINGS
            item {
                ExpandableSettingsCard(
                    title = "Player Configuration",
                    icon = Icons.Outlined.PlayCircle,
                    isExpanded = isPlayerExpanded,
                    onToggleExpand = { isPlayerExpanded = !isPlayerExpanded },
                    badgeText = "Quality: $defaultQuality"
                ) {
                    SettingsSwitchRow(
                        title = "Autoplay Videos Automatically",
                        subtitle = "Start playing immediately upon selecting a video",
                        checked = autoPlayEnabled,
                        onCheckedChange = { autoPlayEnabled = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    SettingsSwitchRow(
                        title = "Universal Player Mode",
                        subtitle = "Use standardized controls across all video sources",
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

            // 3. SMART SKIP & SPONSORBLOCK CONFIGURATION
            item {
                ExpandableSettingsCard(
                    title = "Smart Skip & SponsorBlock",
                    icon = Icons.Outlined.Shield,
                    isExpanded = isSponsorBlockExpanded,
                    onToggleExpand = { isSponsorBlockExpanded = !isSponsorBlockExpanded },
                    badgeText = if (sbEnabled) "Active" else "Disabled"
                ) {
                    SettingsSwitchRow(
                        title = "Enable Smart Skip & SponsorBlock",
                        subtitle = "Automatically skip YouTube sponsors, anime OP/EDs, TV intros, and movie musical songs",
                        checked = sbEnabled,
                        onCheckedChange = { sbPrefs.setEnabled(it) }
                    )

                    if (sbEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        val platformGroups = remember {
                            listOf(
                                "YouTube (SponsorBlock API)" to listOf(
                                    com.example.sponsorblock.model.SponsorBlockCategory.SPONSOR,
                                    com.example.sponsorblock.model.SponsorBlockCategory.SELFPROMO,
                                    com.example.sponsorblock.model.SponsorBlockCategory.INTERACTION,
                                    com.example.sponsorblock.model.SponsorBlockCategory.HIGHLIGHT,
                                    com.example.sponsorblock.model.SponsorBlockCategory.INTRO,
                                    com.example.sponsorblock.model.SponsorBlockCategory.OUTRO,
                                    com.example.sponsorblock.model.SponsorBlockCategory.PREVIEW,
                                    com.example.sponsorblock.model.SponsorBlockCategory.FILLER,
                                    com.example.sponsorblock.model.SponsorBlockCategory.TANGENT
                                ),
                                "Anime (AniSkip API)" to listOf(
                                    com.example.sponsorblock.model.SponsorBlockCategory.ANIME_OP,
                                    com.example.sponsorblock.model.SponsorBlockCategory.ANIME_ED,
                                    com.example.sponsorblock.model.SponsorBlockCategory.ANIME_RECAP
                                ),
                                "TV Series (TheIntroDB API)" to listOf(
                                    com.example.sponsorblock.model.SponsorBlockCategory.TV_INTRO,
                                    com.example.sponsorblock.model.SponsorBlockCategory.TV_CREDITS
                                ),
                                "Movies (Stream Chapters & Songs)" to listOf(
                                    com.example.sponsorblock.model.SponsorBlockCategory.MOVIE_SONG
                                )
                            )
                        }

                        platformGroups.forEach { (platformTitle, categories) ->
                            Text(
                                text = platformTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )

                            categories.forEach { category ->
                                val currentAction = sbPrefs.getCategoryAction(category)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(category.color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    SingleChoiceSegmentedButtonRow {
                                        val actions = com.example.sponsorblock.model.SponsorBlockAction.values()
                                        actions.forEachIndexed { index, action ->
                                            SegmentedButton(
                                                selected = (currentAction == action),
                                                onClick = { sbPrefs.setCategoryAction(category, action) },
                                                shape = SegmentedButtonDefaults.itemShape(index = index, count = actions.size)
                                            ) {
                                                Text(
                                                    text = when(action) {
                                                        com.example.sponsorblock.model.SponsorBlockAction.AUTO_SKIP -> "Skip"
                                                        com.example.sponsorblock.model.SponsorBlockAction.MANUAL_SKIP -> "Button"
                                                        com.example.sponsorblock.model.SponsorBlockAction.DISABLE -> "Off"
                                                    },
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = "Player Overlay Controls",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsSwitchRow(
                            title = "Show Undo Skip Banner",
                            subtitle = "Display a quick notification to undo automatic segment skips",
                            checked = sbShowUndo,
                            onCheckedChange = { sbPrefs.setShowUndoSkipNotification(it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsSwitchRow(
                            title = "Compact Skip Button",
                            subtitle = "Reduce manual skip button size on player screen",
                            checked = sbCompactSkip,
                            onCheckedChange = { sbPrefs.setUseCompactSkipButton(it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsSwitchRow(
                            title = "Auto-Hide Skip Button",
                            subtitle = "Hide manual skip button after 5 seconds",
                            checked = sbAutoHide,
                            onCheckedChange = { sbPrefs.setAutoHideSkipButton(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Column {
                            Text(
                                text = "SponsorBlock Server Endpoint",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = sbApiUrlInput,
                                onValueChange = {
                                    sbApiUrlInput = it
                                    sbPrefs.setApiUrl(it)
                                },
                                placeholder = { Text("https://sponsor.ajay.app") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        val totalMinutes = (sbSkippedTime / 60.0).toLong()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Skipped $sbSkippedCount segments",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Saved ~$totalMinutes minutes of time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (sbSkippedCount > 0) {
                                    TextButton(onClick = { sbPrefs.resetStats() }) {
                                        Text("Reset", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. GESTURE CONTROLS
            item {
                ExpandableSettingsCard(
                    title = "Gestures & Controls",
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
                        val context = androidx.compose.ui.platform.LocalContext.current
                        var currentSeekSecs by remember { mutableStateOf(com.example.util.DebridSettingsManager.getDoubleTapSeekSecs(context)) }
                        val seekLabel = "${currentSeekSecs} Seconds"
                        AssistChip(
                            onClick = {
                                val next = when (currentSeekSecs) {
                                    10 -> 15
                                    15 -> 5
                                    else -> 10
                                }
                                currentSeekSecs = next
                                com.example.util.DebridSettingsManager.setDoubleTapSeekSecs(context, next)
                            },
                            label = { Text(seekLabel, fontWeight = FontWeight.Bold) }
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
                        subtitle = "Display short-form video carousel on main feed",
                        checked = showShortsSection,
                        onCheckedChange = { showShortsSection = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    SettingsSwitchRow(
                        title = "Autoplay Shorts in Feed",
                        subtitle = "Automatically start playing shorts when scrolling",
                        checked = autoPlayShorts,
                        onCheckedChange = { autoPlayShorts = it }
                    )
                }
            }

            // 5. SOURCES & EXTENSIONS (IN THE END)
            item {
                ExpandableSettingsCard(
                    title = "Sources & Plugins",
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    val torBoxKey by viewModel.torBoxApiKey.collectAsState()
                    var keyInput by remember(torBoxKey) { mutableStateOf(torBoxKey) }

                    val javInfoKey by viewModel.javInfoApiKey.collectAsState()
                    var javInfoInput by remember(javInfoKey) { mutableStateOf(javInfoKey) }

                    val orionKey by viewModel.orionApiKey.collectAsState()
                    var orionInput by remember(orionKey) { mutableStateOf(orionKey) }

                    val cometUrl by viewModel.cometUrl.collectAsState()
                    var cometInput by remember(cometUrl) { mutableStateOf(cometUrl) }

                    val mediaFusionUrl by viewModel.mediaFusionUrl.collectAsState()
                    var mediaFusionInput by remember(mediaFusionUrl) { mutableStateOf(mediaFusionUrl) }

                    val zileanUrl by viewModel.zileanUrl.collectAsState()
                    var zileanInput by remember(zileanUrl) { mutableStateOf(zileanUrl) }

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(
                                text = "TorBox Debrid API Key",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Required to stream cached torrent magnets directly via Debrid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = {
                                    keyInput = it
                                    viewModel.updateTorBoxApiKey(it)
                                },
                                placeholder = { Text("Enter TorBox API Key...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "JavInfo API Key",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Required for JavInfo API lookups & magnet link extraction",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = javInfoInput,
                                onValueChange = {
                                    javInfoInput = it
                                    viewModel.updateJavInfoApiKey(it)
                                },
                                placeholder = { Text("Enter JavInfo API Key...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Orion Stremio API Key",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Required for Orion indexer queries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = orionInput,
                                onValueChange = {
                                    orionInput = it
                                    viewModel.updateOrionApiKey(it)
                                },
                                placeholder = { Text("Enter Orion API Key...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Comet Stremio Endpoint",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = cometInput,
                                onValueChange = {
                                    cometInput = it
                                    viewModel.updateCometUrl(it)
                                },
                                placeholder = { Text("https://comet.elfhosted.com") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "MediaFusion Stremio Endpoint",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = mediaFusionInput,
                                onValueChange = {
                                    mediaFusionInput = it
                                    viewModel.updateMediaFusionUrl(it)
                                },
                                placeholder = { Text("https://mediafusion.elfhosted.com") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Zilean DMM Indexer Endpoint",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = zileanInput,
                                onValueChange = {
                                    zileanInput = it
                                    viewModel.updateZileanUrl(it)
                                },
                                placeholder = { Text("https://zilean.elfhosted.com") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
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
