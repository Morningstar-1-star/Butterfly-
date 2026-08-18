package com.example.ui.screens

import android.widget.Toast
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
import com.example.model.ProviderUiItem
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

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

    val watchHistory by viewModel.watchHistory.collectAsState()
    val likedVideoIds by viewModel.likedVideoIds.collectAsState()
    val dislikedVideoIds by viewModel.dislikedVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val notInterestedChannels by viewModel.notInterestedChannels.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val watchLaterList by viewModel.watchLaterList.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    // Expandable accordion section states:
    var isAppearanceExpanded by remember { mutableStateOf(true) }
    var isPersonalizationExpanded by remember { mutableStateOf(false) }
    var isTagsExpanded by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSponsorBlockExpanded by remember { mutableStateOf(false) }
    var isGesturesExpanded by remember { mutableStateOf(false) }
    var isShortsExpanded by remember { mutableStateOf(false) }
    var isSourcesExpanded by remember { mutableStateOf(false) }
    var isCloudFoldersExpanded by remember { mutableStateOf(false) }

    // Cloud Folders & Channels State
    val context = androidx.compose.ui.platform.LocalContext.current
    var telegramChannels by remember { mutableStateOf(com.example.util.CloudFoldersSettingsManager.getTelegramChannelUrls(context)) }
    var megaFolders by remember { mutableStateOf(com.example.util.CloudFoldersSettingsManager.getMegaFolderUrls(context)) }
    var newTelegramInput by remember { mutableStateOf("") }
    var newMegaInput by remember { mutableStateOf("") }

    // Video Tag Preferences State
    val tagPrefs = remember { com.example.util.VideoTagPreferences.getInstance(context) }
    val hideAllTags by tagPrefs.hideAllTags.collectAsState()
    val hiddenTags by tagPrefs.hiddenTags.collectAsState()

    // SponsorBlock Preferences State
    val sbPrefs = remember { com.example.sponsorblock.SponsorBlockPreferences.getInstance(context) }
    val sbEnabled by sbPrefs.isEnabled.collectAsState()
    val sbShowUndo by sbPrefs.showUndoSkipNotification.collectAsState()
    val sbCompactSkip by sbPrefs.useCompactSkipButton.collectAsState()
    val sbAutoHide by sbPrefs.autoHideSkipButton.collectAsState()
    val sbApiUrl by sbPrefs.apiUrl.collectAsState()
    val sbSkippedCount by sbPrefs.skippedSegmentsCount.collectAsState()
    val sbSkippedTime by sbPrefs.skippedTimeSeconds.collectAsState()
    var sbApiUrlInput by remember(sbApiUrl) { mutableStateOf(sbApiUrl) }

    // Playback Preferences State
    val playbackPrefs = remember { com.example.util.PlaybackPreferences.getInstance(context) }
    val forceCustomSpeed by playbackPrefs.forceCustomSpeed.collectAsState()
    val defaultSpeed by playbackPrefs.defaultSpeed.collectAsState()
    val disableSpeedForMusic by playbackPrefs.disableSpeedForMusic.collectAsState()
    var customSpeedInputText by remember(defaultSpeed) { mutableStateOf(if (defaultSpeed == 1.0f) "" else defaultSpeed.toString()) }

    // State Toggles for Player, Gestures, Shorts
    var autoPlayEnabled by remember { mutableStateOf(true) }
    var universalPlayerMode by remember { mutableStateOf(true) }
    var defaultQuality by remember { mutableStateOf("1080p") }
    var gestureControlsEnabled by remember { mutableStateOf(true) }
    var showShortsSection by remember { mutableStateOf(true) }
    var autoPlayShorts by remember { mutableStateOf(true) }

    val allExpanded = isAppearanceExpanded && isPersonalizationExpanded && isTagsExpanded && isPlayerExpanded && isSponsorBlockExpanded &&
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
                        isPersonalizationExpanded = target
                        isTagsExpanded = target
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

            // 1.5. TELEGRAM CHANNELS & MEGA FOLDERS
            item {
                ExpandableSettingsCard(
                    title = "Telegram Channels & Mega Folders",
                    icon = Icons.Outlined.Cloud,
                    isExpanded = isCloudFoldersExpanded,
                    onToggleExpand = { isCloudFoldersExpanded = !isCloudFoldersExpanded },
                    badgeText = "${telegramChannels.size} Channels, ${megaFolders.size} Folders"
                ) {
                    // --- TELEGRAM SECTION ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Telegram Public Channels",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Add single or multiple channels/handles (separated by spaces or newlines)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (telegramChannels.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    com.example.util.CloudFoldersSettingsManager.clearAllTelegramChannels(context)
                                    telegramChannels = com.example.util.CloudFoldersSettingsManager.getTelegramChannelUrls(context)
                                    Toast.makeText(context, "All Telegram channels deleted", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTelegramInput,
                            onValueChange = { newTelegramInput = it },
                            placeholder = { Text("e.g. @channel or t.me/s/channel") },
                            modifier = Modifier.weight(1f),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                if (newTelegramInput.isNotBlank()) {
                                    val added = com.example.util.CloudFoldersSettingsManager.addMultipleTelegramChannelUrls(context, newTelegramInput)
                                    telegramChannels = com.example.util.CloudFoldersSettingsManager.getTelegramChannelUrls(context)
                                    newTelegramInput = ""
                                    if (added > 0) {
                                        Toast.makeText(context, "Added $added channel(s)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Channel already exists or invalid", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (telegramChannels.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "No Telegram channels added yet. Enter @channel or public channel links above to stream videos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        telegramChannels.forEach { channelUrl ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = channelUrl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = {
                                            com.example.util.CloudFoldersSettingsManager.removeTelegramChannelUrl(context, channelUrl)
                                            telegramChannels = com.example.util.CloudFoldersSettingsManager.getTelegramChannelUrls(context)
                                            Toast.makeText(context, "Channel permanently removed", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- MEGA FOLDERS SECTION ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mega Folder Links",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Add single or multiple Mega folder links (separated by spaces or newlines)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (megaFolders.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    com.example.util.CloudFoldersSettingsManager.clearAllMegaFolders(context)
                                    megaFolders = com.example.util.CloudFoldersSettingsManager.getMegaFolderUrls(context)
                                    Toast.makeText(context, "All Mega folders deleted", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newMegaInput,
                            onValueChange = { newMegaInput = it },
                            placeholder = { Text("https://mega.nz/folder/...") },
                            modifier = Modifier.weight(1f),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                if (newMegaInput.isNotBlank()) {
                                    val added = com.example.util.CloudFoldersSettingsManager.addMultipleMegaFolderUrls(context, newMegaInput)
                                    megaFolders = com.example.util.CloudFoldersSettingsManager.getMegaFolderUrls(context)
                                    newMegaInput = ""
                                    if (added > 0) {
                                        Toast.makeText(context, "Added $added Mega folder(s)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Folder already added or invalid", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (megaFolders.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "No Mega folders added yet. Paste https://mega.nz/folder/... links above to stream videos inside the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        megaFolders.forEach { folderUrl ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = folderUrl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = {
                                            com.example.util.CloudFoldersSettingsManager.removeMegaFolderUrl(context, folderUrl)
                                            megaFolders = com.example.util.CloudFoldersSettingsManager.getMegaFolderUrls(context)
                                            Toast.makeText(context, "Mega folder permanently removed", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. PERSONALIZATION DATA (IMPORT / EXPORT / SYNC)
            item {
                var showExportDialog by remember { mutableStateOf(false) }
                var showImportDialog by remember { mutableStateOf(false) }
                var showClearDialog by remember { mutableStateOf(false) }
                var exportJsonText by remember { mutableStateOf("") }
                var importJsonInput by remember { mutableStateOf("") }
                var importSummary by remember { mutableStateOf<com.example.util.PersonalizationDataManager.ImportSummary?>(null) }
                val coroutineScope = rememberCoroutineScope()

                val stats = remember(
                    watchHistory,
                    likedVideoIds,
                    dislikedVideoIds,
                    notInterestedVideoIds,
                    notInterestedChannels,
                    recentSearches,
                    watchLaterList,
                    userPlaylists
                ) {
                    com.example.util.PersonalizationDataManager.getStats(
                        watchHistory = watchHistory,
                        likedVideoIds = likedVideoIds,
                        dislikedVideoIds = dislikedVideoIds,
                        notInterestedVideoIds = notInterestedVideoIds,
                        notInterestedChannels = notInterestedChannels,
                        recentSearches = recentSearches,
                        watchLaterList = watchLaterList,
                        userPlaylists = userPlaylists
                    )
                }

                if (showExportDialog) {
                    AlertDialog(
                        onDismissRequest = { showExportDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Personalization Data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Your likes, watch history, search history, not-interested blocks, playlists, and taste signals have been packaged into JSON format.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = exportJsonText,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    com.example.util.PersonalizationDataManager.copyToClipboard(context, exportJsonText)
                                    Toast.makeText(context, "Copied JSON to clipboard!", Toast.LENGTH_SHORT).show()
                                    showExportDialog = false
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy JSON")
                            }
                        },
                        dismissButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        com.example.util.PersonalizationDataManager.shareJson(context, exportJsonText)
                                        showExportDialog = false
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share")
                                }
                                TextButton(onClick = { showExportDialog = false }) {
                                    Text("Close")
                                }
                            }
                        }
                    )
                }

                if (showImportDialog) {
                    AlertDialog(
                        onDismissRequest = { showImportDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Personalization Data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Paste previously exported Butterfly personalization JSON below or paste from clipboard:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = importJsonInput,
                                    onValueChange = { importJsonInput = it },
                                    placeholder = { Text("Paste JSON here...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val clipText = com.example.util.PersonalizationDataManager.readFromClipboard(context)
                                        if (!clipText.isNullOrBlank()) {
                                            importJsonInput = clipText
                                            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste from Clipboard")
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val result = com.example.util.PersonalizationDataManager.importFromJson(context, viewModel, importJsonInput)
                                        importSummary = result
                                        showImportDialog = false
                                        if (result.success) {
                                            Toast.makeText(context, "Data imported successfully!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Import failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = importJsonInput.isNotBlank()
                            ) {
                                Text("Import & Apply")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showImportDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (importSummary != null) {
                    val summary = importSummary!!
                    AlertDialog(
                        onDismissRequest = { importSummary = null },
                        title = {
                            Text(
                                text = if (summary.success) "Import Succeeded" else "Import Failed",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            if (summary.success) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Successfully imported personalization data:", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("• ${summary.likesCount} Liked Videos", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${summary.dislikesCount} Disliked Videos", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${summary.watchHistoryCount} Watch History Items", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${summary.notInterestedCount} Not Interested Videos", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${summary.blockedChannelsCount} Blocked Channels", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${summary.searchHistoryCount} Search History Queries", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${summary.bookmarksCount} Saved Bookmarks", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${summary.playlistsCount} Custom Playlists", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Recommendation pipeline and feed updated.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Text("Error: ${summary.errorMessage}", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        confirmButton = {
                            Button(onClick = { importSummary = null }) {
                                Text("Done")
                            }
                        }
                    )
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("Reset Personalization Data?") },
                        text = { Text("This will clear all local likes, watch history, search history, not-interested blocks, and custom playlists.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.clearHistory()
                                        com.example.util.NotInterestedManager.clearAll(context)
                                        viewModel.setNotInterestedData(emptySet<String>(), emptySet<String>())
                                        viewModel.setLikedVideoIds(emptySet<String>())
                                        viewModel.setDislikedVideoIds(emptySet<String>())
                                        viewModel.clearRecentSearches()
                                        showClearDialog = false
                                        Toast.makeText(context, "Personalization data cleared", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear Everything")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                ExpandableSettingsCard(
                    title = "Personalization Data",
                    icon = Icons.Outlined.ManageAccounts,
                    isExpanded = isPersonalizationExpanded,
                    onToggleExpand = { isPersonalizationExpanded = !isPersonalizationExpanded },
                    badgeText = "${stats.likesCount} Likes • ${stats.watchHistoryCount} Watched • ${stats.notInterestedCount} Not Int."
                ) {
                    Text(
                        text = "Import & Export User Signals",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Backup or restore your likes, dislikes, Not Interested videos, blocked channels, watch/search history, interactions, preferences, and recommendation signals for accurate, personalized recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats Badges Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PersonalizationStatBadge("Likes", "${stats.likesCount}", Icons.Default.ThumbUp, Modifier.weight(1f))
                        PersonalizationStatBadge("Dislikes", "${stats.dislikesCount}", Icons.Default.ThumbDown, Modifier.weight(1f))
                        PersonalizationStatBadge("Watched", "${stats.watchHistoryCount}", Icons.Default.History, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Stats Badges Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PersonalizationStatBadge("Not Interested", "${stats.notInterestedCount}", Icons.Default.Block, Modifier.weight(1f))
                        PersonalizationStatBadge("Blocked Ch.", "${stats.blockedChannelsCount}", Icons.Default.Cancel, Modifier.weight(1f))
                        PersonalizationStatBadge("Playlists", "${stats.playlistsCount}", Icons.Default.PlaylistPlay, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Export & Import Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    exportJsonText = com.example.util.PersonalizationDataManager.exportToJson(context, viewModel)
                                    showExportDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Data")
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboardText = com.example.util.PersonalizationDataManager.readFromClipboard(context)
                                if (!clipboardText.isNullOrBlank() && clipboardText.contains("Butterfly")) {
                                    importJsonInput = clipboardText
                                }
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Data")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Personalization Data", fontSize = 12.sp)
                    }
                }
            }

            // 3. VIDEO TAGS & BADGES
            item {
                val tagBadgeText = when {
                    hideAllTags -> "All Hidden"
                    hiddenTags.isNotEmpty() -> "${hiddenTags.size} Hidden"
                    else -> "Visible"
                }

                ExpandableSettingsCard(
                    title = "Video Tags & Badges",
                    icon = Icons.Outlined.LocalOffer,
                    isExpanded = isTagsExpanded,
                    onToggleExpand = { isTagsExpanded = !isTagsExpanded },
                    badgeText = tagBadgeText
                ) {
                    SettingsSwitchRow(
                        title = "Hide All Video Tags",
                        subtitle = "Hide all corner tags (Dailymotion, YouTube, Anime, Series, Movies, etc.) from video cards",
                        checked = hideAllTags,
                        onCheckedChange = { tagPrefs.setHideAllTags(it) }
                    )

                    if (!hideAllTags) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hide Specific Tags",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Toggle individual tags to hide or show on video thumbnails",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (hiddenTags.isNotEmpty()) {
                                TextButton(onClick = { tagPrefs.unhideAllSpecificTags() }) {
                                    Text("Unhide All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val availableTags = remember {
                            listOf(
                                Triple("Dailymotion", Color(0xFF1976D2), "Dailymotion video source"),
                                Triple("YouTube", Color(0xFFFF0000), "YouTube video source"),
                                Triple("Anime", Color(0xFF9C27B0), "Anime & animated content"),
                                Triple("Series", Color(0xFF0288D1), "TV shows & serial episodes"),
                                Triple("Movie", Color(0xFFE5A00D), "Full movies & feature films"),
                                Triple("18+", Color(0xFFC2185B), "Adult & mature catalog"),
                                Triple("Archive", Color(0xFF1976D2), "Internet Archive public media"),
                                Triple("Vimeo", Color(0xFF1976D2), "Vimeo video source")
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableTags.forEach { (tagName, tagColor, tagDesc) ->
                                val isHidden = tagPrefs.isTagHidden(tagName)
                                Surface(
                                    color = if (isHidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = tagName,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(
                                                        color = if (isHidden) Color.Gray.copy(alpha = 0.5f) else tagColor.copy(alpha = 0.95f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = if (tagName == "Movie") "Movies" else tagName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = tagDesc,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isHidden) "Hidden" else "Shown",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isHidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Switch(
                                                checked = !isHidden,
                                                onCheckedChange = { isShown ->
                                                    tagPrefs.setTagHidden(tagName, !isShown)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. PLAYER & PLAYBACK SETTINGS
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // DEFAULT PLAYBACK SPEED SECTION
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "DEFAULT PLAYBACK SPEED",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            SettingsSwitchRow(
                                title = "Force Custom Speed",
                                subtitle = "Every video opens at the speed specified below",
                                checked = forceCustomSpeed,
                                onCheckedChange = { playbackPrefs.setForceCustomSpeed(it) }
                            )

                            val presetSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(presetSpeeds) { speed ->
                                    val isSelected = (defaultSpeed == speed && customSpeedInputText.isBlank()) ||
                                            (customSpeedInputText.toFloatOrNull() == speed)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            customSpeedInputText = ""
                                            playbackPrefs.setDefaultSpeed(speed)
                                            playbackPrefs.setForceCustomSpeed(true)
                                        },
                                        label = { Text("${speed}×", fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = customSpeedInputText,
                                onValueChange = { input ->
                                    customSpeedInputText = input
                                    val parsed = input.toFloatOrNull()
                                    if (parsed != null && parsed >= 0.1f && parsed <= 16.0f) {
                                        playbackPrefs.setDefaultSpeed(parsed)
                                        playbackPrefs.setForceCustomSpeed(true)
                                    }
                                },
                                placeholder = { Text("Custom, e.g. 1.35 or 5") },
                                trailingIcon = {
                                    if (customSpeedInputText.isNotEmpty()) {
                                        IconButton(onClick = { customSpeedInputText = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Text(
                                text = "Tap a preset, or type any custom value (0.1–16×) and it overrides the presets.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            SettingsSwitchRow(
                                title = "🎵 Disable Custom Speed for Music",
                                subtitle = "Detected music videos play at 1.0× automatically",
                                checked = disableSpeedForMusic,
                                onCheckedChange = { playbackPrefs.setDisableSpeedForMusic(it) }
                            )

                            Button(
                                onClick = {
                                    val parsed = customSpeedInputText.toFloatOrNull()
                                    if (parsed != null && parsed >= 0.1f && parsed <= 16.0f) {
                                        playbackPrefs.setDefaultSpeed(parsed)
                                        playbackPrefs.setForceCustomSpeed(true)
                                    }
                                    Toast.makeText(
                                        context,
                                        "Playback speed saved: ${if (forceCustomSpeed) "${defaultSpeed}x" else "Default (1.0x)"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Speed", fontWeight = FontWeight.Bold)
                            }
                        }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Provider Sources & Plugins (${availableProviders.filter { it.id != "all" }.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Toggle providers & run live diagnostic health checks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        var isTestingInSettings by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isTestingInSettings = true
                                    viewModel.runAllDiagnostics("IPX-800")
                                    isTestingInSettings = false
                                }
                            },
                            enabled = !isTestingInSettings,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isTestingInSettings) "Testing..." else "Test All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val providerCategories = remember(availableProviders) {
                        val providersList: List<ProviderUiItem> = availableProviders
                        providersList.filter { it.id != "all" }.groupBy { it.category }
                    }

                    for ((categoryName, categoryProviders) in providerCategories) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )

                        for (provider in categoryProviders) {
                            val statusColor = when (provider.statusState) {
                                com.example.model.ProviderStatusState.ACTIVE -> Color(0xFF4CAF50)
                                com.example.model.ProviderStatusState.DEGRADED -> Color(0xFFFF9800)
                                com.example.model.ProviderStatusState.NO_RESULT -> Color.Gray
                                com.example.model.ProviderStatusState.BLOCKED,
                                com.example.model.ProviderStatusState.ERROR -> Color(0xFFF44336)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = provider.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = statusColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (provider.isEnabled) provider.statusMessage else "Disabled",
                                                color = statusColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = provider.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Switch(
                                    checked = enabledProviders.contains(provider.id),
                                    onCheckedChange = { viewModel.toggleProviderEnabled(provider.id) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    val orionKey by viewModel.orionApiKey.collectAsState()
                    var orionInput by remember(orionKey) { mutableStateOf(orionKey) }

                    val cometUrl by viewModel.cometUrl.collectAsState()
                    var cometInput by remember(cometUrl) { mutableStateOf(cometUrl) }

                    val mediaFusionUrl by viewModel.mediaFusionUrl.collectAsState()
                    var mediaFusionInput by remember(mediaFusionUrl) { mutableStateOf(mediaFusionUrl) }

                    val zileanUrl by viewModel.zileanUrl.collectAsState()
                    var zileanInput by remember(zileanUrl) { mutableStateOf(zileanUrl) }

                    val apijavUrl by viewModel.apijavUrl.collectAsState()
                    var apijavInput by remember(apijavUrl) { mutableStateOf(apijavUrl) }

                    val javinfoUrl by viewModel.javinfoUrl.collectAsState()
                    var javinfoInput by remember(javinfoUrl) { mutableStateOf(javinfoUrl) }

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

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

                        Column {
                            Text(
                                text = "APIJAV Server Endpoint",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "REST endpoint for apiJAV WordPress video streams",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = apijavInput,
                                onValueChange = {
                                    apijavInput = it
                                    viewModel.updateApijavUrl(it)
                                },
                                placeholder = { Text("https://apijav.com") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "JavInfo API Endpoint",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Metadata & stream index for Asian Cinema & JAV codes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = javinfoInput,
                                onValueChange = {
                                    javinfoInput = it
                                    viewModel.updateJavinfoUrl(it)
                                },
                                placeholder = { Text("https://javinfo.dev") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
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

@Composable
private fun PersonalizationStatBadge(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
