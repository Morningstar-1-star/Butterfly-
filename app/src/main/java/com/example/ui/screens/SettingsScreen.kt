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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    var isAppearanceExpanded by remember { mutableStateOf(true) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSmartSkipExpanded by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }
    var showFullSponsorBlockScreen by remember { mutableStateOf(false) }

    if (showFullSponsorBlockScreen) {
        com.example.smartskip.SponsorBlockSettingsScreen(
            onBackClick = { showFullSponsorBlockScreen = false },
            modifier = modifier
        )
        return
    }

    class ProviderDiag(
        val id: String,
        val name: String,
        val isAdult: Boolean,
        statusStr: String = "UNTESTED",
        detailStr: String = ""
    ) {
        var status by mutableStateOf(statusStr)
        var resultDetail by mutableStateOf(detailStr)
    }

    val diagnosticsList = remember {
        mutableStateListOf(
            ProviderDiag("youtube", "YouTube", false),
            ProviderDiag("archive_org", "Internet Archive", false),
            ProviderDiag("dailymotion", "Dailymotion", false),
            ProviderDiag("bilibili", "Bilibili", false),
            ProviderDiag("vimeo", "Vimeo", false),
            ProviderDiag("eporner", "Eporner", true),
            ProviderDiag("pornhub", "Pornhub", true),
            ProviderDiag("xvideos", "XVideos", true),
            ProviderDiag("4tube", "4tube", true),
            ProviderDiag("beeg", "Beeg", true),
            ProviderDiag("rule34video", "Rule34Video", true),
            ProviderDiag("redtube", "RedTube", true),
            ProviderDiag("xhamster", "XHamster", true),
            ProviderDiag("youporn", "YouPorn", true)
        )
    }

    fun testProvider(diag: ProviderDiag) {
        diag.status = "TESTING"
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var errorMsg = ""
            var formatInfo = ""
            var isWorking = false
            try {
                when (diag.id) {
                    "youtube" -> {
                        val items = com.example.extractor.YouTubeExtractorHelper.fetchYouTubeTrending(context)
                        if (items.isNotEmpty()) {
                            val result = com.example.extractor.YouTubeExtractorHelper.resolveStream(items[0].id, context, "youtube")
                            when (result) {
                                is com.example.extractor.YouTubeExtractorHelper.ExtractionResult.Success -> {
                                    val sd = result.streamData
                                    val opt = sd.selectedStreamOption ?: sd.availableStreamOptions.firstOrNull { !it.videoUrl.isNullOrBlank() }
                                    if (opt != null && !opt.videoUrl.isNullOrBlank()) {
                                        isWorking = true
                                        val mime = opt.format.ifBlank { "mp4" }
                                        formatInfo = "Format: ${opt.qualityLabel} ($mime) | Direct URL: ${opt.videoUrl.take(40)}..."
                                    } else {
                                        errorMsg = "No direct playable media format URL found"
                                    }
                                }
                                is com.example.extractor.YouTubeExtractorHelper.ExtractionResult.Error -> {
                                    errorMsg = result.errorDetails.message
                                }
                            }
                        } else {
                            errorMsg = "No YouTube trending items found"
                        }
                    }
                    "archive_org" -> {
                        val items = com.example.extractor.ArchiveOrgProvider.getHome(1)
                        if (items.isNotEmpty()) {
                            val streamData = com.example.extractor.ArchiveOrgProvider.getStreamData(items[0].id)
                            if (streamData != null && ((streamData.videoUrl?.isNotBlank() == true) || streamData.availableStreamOptions.isNotEmpty())) {
                                val opt = streamData.availableStreamOptions.firstOrNull() ?: com.example.model.PlayableStreamOption(qualityLabel = "Direct", format = "mp4", isMuxed = true, videoUrl = streamData.videoUrl ?: "")
                                if (opt.videoUrl?.isNotBlank() == true) {
                                    isWorking = true
                                    formatInfo = "Format: ${opt.qualityLabel} (${opt.format}) | Direct URL: ${opt.videoUrl?.take(40)}..."
                                } else {
                                    errorMsg = "Archive stream URL is blank"
                                }
                            } else {
                                errorMsg = "No Archive stream data resolved"
                            }
                        } else {
                            errorMsg = "No Archive items found"
                        }
                    }
                    "eporner" -> {
                        val testUrl = "https://www.eporner.com/video-3746271/"
                        val streamData = com.example.extractor.EpornerProvider.getStreamData(testUrl)
                        if (streamData != null && streamData.videoUrl?.isNotBlank() == true) {
                            isWorking = true
                            formatInfo = "Format: ${streamData.selectedStreamOption?.qualityLabel ?: "HD"} (mp4) | Direct URL: ${streamData.videoUrl?.take(40)}..."
                        } else {
                            val extractionResult = com.example.extractor.YtDlpResolver.extractStreamInfo(context, testUrl)
                            when (extractionResult) {
                                is com.example.extractor.YouTubeExtractorHelper.ExtractionResult.Success -> {
                                    val sd = extractionResult.streamData
                                    val option = sd.availableStreamOptions.firstOrNull { !it.videoUrl.isNullOrBlank() }
                                    if (option != null && option.videoUrl?.isNotBlank() == true) {
                                        isWorking = true
                                        formatInfo = "Format: ${option.qualityLabel} (${option.format}) | Direct URL: ${option.videoUrl?.take(40)}..."
                                    } else if (sd.videoUrl?.isNotBlank() == true) {
                                        isWorking = true
                                        formatInfo = "Format: Direct (mp4) | Direct URL: ${sd.videoUrl?.take(40)}..."
                                    } else {
                                        errorMsg = "Eporner extraction returned no playable media formats"
                                    }
                                }
                                is com.example.extractor.YouTubeExtractorHelper.ExtractionResult.Error -> {
                                    errorMsg = extractionResult.errorDetails.message
                                }
                            }
                        }
                    }
                    else -> {
                        val testUrl = when (diag.id) {
                            "dailymotion" -> "https://www.dailymotion.com/video/x8n2202"
                            "vimeo" -> "https://vimeo.com/76979871"
                            "bilibili" -> "https://www.bilibili.com/video/BV1xx411c7m9"
                            "pornhub" -> "https://www.pornhub.com/view_video.php?viewkey=ph5bc340904031a"
                            "xvideos" -> "https://www.xvideos.com/video.uuhbcpf9a4c/test"
                            "4tube" -> "https://www.4tube.com/videos/243681/test"
                            "beeg" -> "https://beeg.com/"
                            "rule34video" -> "https://rule34video.com/"
                            "redtube" -> "https://www.redtube.com/"
                            "xhamster" -> "https://xhamster.com/"
                            "youporn" -> "https://www.youporn.com/"
                            else -> ""
                        }
                        if (testUrl.isBlank()) {
                            errorMsg = "Unknown test URL for provider ${diag.id}"
                        } else {
                            val extractionResult = com.example.extractor.YtDlpResolver.extractStreamInfo(context, testUrl)
                            when (extractionResult) {
                                is com.example.extractor.YouTubeExtractorHelper.ExtractionResult.Success -> {
                                    val sd = extractionResult.streamData
                                    val option = sd.availableStreamOptions.firstOrNull { !it.videoUrl.isNullOrBlank() }
                                    if (option != null && option.videoUrl?.isNotBlank() == true) {
                                        isWorking = true
                                        formatInfo = "Format: ${option.qualityLabel} (${option.format}) | Direct URL: ${option.videoUrl?.take(40)}..."
                                    } else if (sd.videoUrl?.isNotBlank() == true) {
                                        isWorking = true
                                        formatInfo = "Format: Direct (mp4) | Direct URL: ${sd.videoUrl?.take(40)}..."
                                    } else {
                                        errorMsg = "No playable media format found in extraction output"
                                    }
                                }
                                is com.example.extractor.YouTubeExtractorHelper.ExtractionResult.Error -> {
                                    errorMsg = extractionResult.errorDetails.message
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: e.javaClass.simpleName
            }

            val elapsed = System.currentTimeMillis() - start
            val timeStr = String.format(java.util.Locale.US, "%.1fs", elapsed / 1000f)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                diag.status = if (isWorking) "WORKING" else "FAILED"
                diag.resultDetail = if (isWorking) "$timeStr - $formatInfo" else "$timeStr - $errorMsg"
            }
        }
    }

    // Playback Preferences State
    val playbackPrefs = remember { com.example.util.PlaybackPreferences.getInstance(context) }
    val defaultSpeed by playbackPrefs.defaultSpeed.collectAsState()
    val disableSpeedForMusic by playbackPrefs.disableSpeedForMusic.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }

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
            // 1. APPEARANCE & THEME
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "Thumbnail Source Tags",
                        subtitle = "Show provider badges (e.g. YouTube, Vimeo, Dailymotion, 18+) on video cards",
                        checked = showThumbnailTags,
                        onCheckedChange = { viewModel.setShowThumbnailTags(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "18+ Adult Content",
                        subtitle = "Enable adult and mature streaming options",
                        checked = adultContentEnabled,
                        onCheckedChange = { viewModel.setAdultContentEnabled(it) }
                    )
                }
            }

            // 2. PLAYBACK & PLAYER PREFERENCES
            item {
                ExpandableSettingsCard(
                    title = "Playback & Player",
                    icon = Icons.Outlined.PlayCircle,
                    isExpanded = isPlayerExpanded,
                    onToggleExpand = { isPlayerExpanded = !isPlayerExpanded },
                    badgeText = "${defaultSpeed}x Speed"
                ) {
                    Text(
                        text = "Default Playback Speed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val speedPresets = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        speedPresets.forEach { speed ->
                            val isSelected = (defaultSpeed == speed)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    playbackPrefs.setDefaultSpeed(speed)
                                    Toast.makeText(context, "Default speed set to ${speed}x", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("${speed}x", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "Preserve 1.0x Speed for Music",
                        subtitle = "Automatically resets playback speed to normal 1.0x when playing music tracks",
                        checked = disableSpeedForMusic,
                        onCheckedChange = {
                            playbackPrefs.setDisableSpeedForMusic(it)
                        }
                    )
                }
            }

            // 3. SMART SKIP / SPONSORBLOCK
            item {
                val smartSkipPrefs = remember(context) { com.example.smartskip.SmartSkipPreferences.getInstance(context) }
                val isSmartSkipOn by smartSkipPrefs.isSmartSkipEnabled.collectAsState()
                val showNotice by smartSkipPrefs.skipNotification.collectAsState()

                ExpandableSettingsCard(
                    title = "Smart Skip / SponsorBlock",
                    icon = Icons.Outlined.FastForward,
                    isExpanded = isSmartSkipExpanded,
                    onToggleExpand = { isSmartSkipExpanded = !isSmartSkipExpanded },
                    badgeText = if (isSmartSkipOn) "Active (4 Sources)" else "Disabled"
                ) {
                    SettingsSwitchRow(
                        title = "Enable Smart Skip",
                        subtitle = "Automatically skip or show skip buttons for sponsors, intros, outros, and recaps across all supported platforms",
                        checked = isSmartSkipOn,
                        onCheckedChange = { smartSkipPrefs.setMasterEnabled(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    SettingsSwitchRow(
                        title = "Show Skip Notification",
                        subtitle = "Display a small toast notification when a segment is automatically skipped",
                        checked = showNotice,
                        onCheckedChange = { smartSkipPrefs.setSkipNotification(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Text(
                        text = "Supported Sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("YouTube (SponsorBlock)", "Bilibili", "Anime (AniSkip)", "Movies/TV (IntroDB)").forEach { src ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = src,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showFullSponsorBlockScreen = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configure Segments & Advanced Rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 4. HISTORY & DATA
            item {
                ExpandableSettingsCard(
                    title = "History & Storage",
                    icon = Icons.Outlined.History,
                    isExpanded = isHistoryExpanded,
                    onToggleExpand = { isHistoryExpanded = !isHistoryExpanded },
                    badgeText = "${watchHistory.size} Videos • ${recentSearches.size} Searches"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearHistoryDialog = true }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Clear Watch History",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${watchHistory.size} videos in history",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear History",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearSearchDialog = true }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Clear Search History",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${recentSearches.size} search queries saved",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Searches",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 4. SOURCE DIAGNOSTICS
            item {
                ExpandableSettingsCard(
                    title = "Source Diagnostics",
                    icon = Icons.Outlined.CheckCircle,
                    isExpanded = isDiagnosticsExpanded,
                    onToggleExpand = { isDiagnosticsExpanded = !isDiagnosticsExpanded },
                    badgeText = "15 Sources"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Test and inspect status of all platform extractors and resolvers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        diagnosticsList.forEach { diag ->
                            val isDisabled = diag.isAdult && !adultContentEnabled
                            val statusDisplay = when {
                                isDisabled -> "⚪ DISABLED"
                                diag.status == "WORKING" -> "🟢 WORKING   ${diag.resultDetail}"
                                diag.status == "TESTING" -> "🟡 TESTING..."
                                diag.status == "FAILED" -> "🔴 FAILED    ${diag.resultDetail}"
                                else -> "⚪ UNTESTED"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = diag.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = statusDisplay,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                            diag.status == "WORKING" -> Color(0xFF2E7D32)
                                            diag.status == "FAILED" -> MaterialTheme.colorScheme.error
                                            diag.status == "TESTING" -> Color(0xFFF57C00)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Button(
                                    onClick = { testProvider(diag) },
                                    enabled = !isDisabled && diag.status != "TESTING",
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text(text = "Test", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 5. ABOUT & EXTRACTOR INFO
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Direct Extractor Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Direct stream playback powered by native Media3/ExoPlayer and NewPipe direct extractor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Watch History?") },
            text = { Text("This will permanently remove all items from your watch history.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.clearHistory()
                            showClearHistoryDialog = false
                            Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearSearchDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchDialog = false },
            title = { Text("Clear Search History?") },
            text = { Text("This will remove all recent search queries.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.clearRecentSearches()
                            showClearSearchDialog = false
                            Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
