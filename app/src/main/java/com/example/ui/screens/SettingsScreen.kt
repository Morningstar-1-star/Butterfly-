package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

enum class SettingsCategory(val title: String, val subtitle: String, val icon: ImageVector) {
    GENERAL("General", "Theme, colors, language & layout preferences", Icons.Outlined.Palette),
    PLAYBACK("Playback", "Resolution, speed, seek gestures & Secure DNS", Icons.Outlined.PlayCircle),
    SEEKBAR("Seekbar", "Seek gestures, custom progress & accent colors, DVR & appearance", Icons.Outlined.LinearScale),
    ACCOUNTS_SOURCES("Accounts & Sources", "Tencent Video, YouTube, Google Drive, Crunchyroll, Hotstar & SonyLIV", Icons.Outlined.Hub),
    PROVIDERS("Content Sources & Providers", "Manage all 100+ sources: Normal, 18+, Vega, VidSrc, Decryptor, TMDB & Torrents", Icons.Outlined.Source),
    PROWLARR_INDEXERS("Prowlarr & Cardigann Indexers", "Manage Prowlarr V11 YAML indexers, test & sync", Icons.Outlined.Radar),
    SUBTITLE_PROVIDERS("Subtitle Providers", "Configure SubDL, OpenSubtitles, SubtitleCat & Bazarr plugins", Icons.Outlined.ClosedCaption),
    CLOUD_SOCIAL("Cloud & Social Sources", "Telegram, MEGA & Bunkr unified media library", Icons.Outlined.Cloud),
    BUNKR("Bunkr Albums & Direct CDN", "Manage Bunkr album URLs, auto-extract & sync", Icons.Outlined.CloudDownload),
    VEGA("Vega Movies & Series", "All 52+ in-app movie extensions & anime providers", Icons.Outlined.Movie),
    VIDSRC("VidSrc Sources", "VidLink, AutoEmbed, Smashy & cloud mirrors", Icons.Outlined.VideoLibrary),
    DECRYPTOR("Decryptor Sources", "Vidhide, Turbo, Nxsha & fast servers", Icons.Outlined.LockOpen),
    TMDB_EMBED("TMDB Sources", "VixSrc, Showbox, Videasy & VIP servers", Icons.Outlined.MovieCreation),
    NUVIO_PROVIDERS("Nuvio Providers", "UHDMovies, MoviesMod, MoviesDrive, 4KHDHub & 29+ dynamic scrapers", Icons.Outlined.Extension),
    ADULT_18("18+ Content", "Adult content mode & mature sources", Icons.Outlined.Explicit),
    SMART_SKIP("SponsorBlock", "Auto-skip sponsored segments, intros & filler", Icons.Outlined.FastForward),
    HISTORY_PRIVACY("History & Privacy", "Watch history, search cache & blocked channels", Icons.Outlined.History),
    BACKUP_RESTORE("Backup & Restore", "Export/import profile data & Google Drive sync", Icons.Outlined.CloudUpload),
    ADDITIONAL_SETTINGS("Additional Settings", "API keys, Diagnostics & Battery Saver", Icons.Outlined.Tune),
    ABOUT("About Butterfly", "Version, legal & open-source details", Icons.Outlined.Info),

    // Sub-categories housed exclusively inside ADDITIONAL_SETTINGS
    BATTERY_SAVER("Battery Saver & Performance", "Power saving mode, RAM & speed optimizations", Icons.Outlined.Bolt),
    INTEGRATIONS("API Keys & Integrations", "TMDB, Subtitles, Debrid & PoToken config", Icons.Outlined.Key),
    DNS_NETWORK("DNS & Network", "Secure DNS-over-HTTPS & ISP unblocking", Icons.Outlined.Dns),
    DIAGNOSTICS("Diagnostics & Tests", "yt-dlp engine status & provider health test", Icons.Outlined.BugReport)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var parentCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    var showAddRepoDialog by remember { mutableStateOf(false) }
    var customRepoInput by remember { mutableStateOf("") }
    var customRepoNameInput by remember { mutableStateOf("") }
    var testDnsDomainInput by remember { mutableStateOf("youtube.com") }

    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val enabledProviderIds by viewModel.enabledProviderIds.collectAsState()

    val isPowerSaveActive by viewModel.isPowerSaveActive.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isBatteryCharging by viewModel.isBatteryCharging.collectAsState()
    val batterySaverManualEnabled by viewModel.batterySaverManualEnabled.collectAsState()
    val batterySaverAutoOnLow by viewModel.batterySaverAutoOnLow.collectAsState()
    val batterySaverLowThreshold by viewModel.batterySaverLowBatteryThreshold.collectAsState()
    val batterySaverResolutionCap by viewModel.batterySaverResolutionCap.collectAsState()
    val batterySaverDisableAmbient by viewModel.batterySaverDisableAmbient.collectAsState()
    val batterySaverLowPowerTorrent by viewModel.batterySaverLowPowerTorrent.collectAsState()
    val batterySaverDisableAnimations by viewModel.batterySaverDisableAnimations.collectAsState()
    val batterySaverPureBlackAmoled by viewModel.batterySaverPureBlackAmoled.collectAsState()
    val batterySaverAudioOnlyForMusic by viewModel.batterySaverAudioOnlyForMusic.collectAsState()
    val appCacheSizeBytes by viewModel.appCacheSizeBytes.collectAsState()

    val playbackPrefs = remember { com.example.util.PlaybackPreferences.getInstance(context) }
    val defaultSpeed by playbackPrefs.defaultSpeed.collectAsState()
    val disableSpeedForMusic by playbackPrefs.disableSpeedForMusic.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showSupabaseSettingsDialog by remember { mutableStateOf(false) }
    var pasteJsonInput by remember { mutableStateOf("") }

    // Dialog state for selections
    var showSecureDnsDialog by remember { mutableStateOf(false) }
    var dnsDropdownExpanded by remember { mutableStateOf(false) }
    var showCustomDnsDialog by remember { mutableStateOf(false) }
    val currentCustomDnsUrl by viewModel.customDnsUrl.collectAsState()
    var customDnsInputText by remember(currentCustomDnsUrl) { mutableStateOf(currentCustomDnsUrl) }

    val prioritizeVideoQuality by playbackPrefs.prioritizeVideoQuality.collectAsState()
    val disableDrcAudio by playbackPrefs.disableDrcAudio.collectAsState()
    val disableHdrVideo by playbackPrefs.disableHdrVideo.collectAsState()
    val forceAvcCodec by playbackPrefs.forceAvcCodec.collectAsState()
    val videoCodecPreference by playbackPrefs.videoCodecPreference.collectAsState()
    val decoderMode by playbackPrefs.decoderMode.collectAsState()
    val forceOriginalAudioLanguage by playbackPrefs.forceOriginalAudioLanguage.collectAsState()
    val customPlaybackSpeedMenu by playbackPrefs.customPlaybackSpeedMenu.collectAsState()
    val customPlaybackSpeeds by playbackPrefs.customPlaybackSpeeds.collectAsState()
    val tapAndHoldSpeed by playbackPrefs.tapAndHoldSpeed.collectAsState()
    val rememberPlaybackSpeed by playbackPrefs.rememberPlaybackSpeed.collectAsState()
    val speedChangeNotifications by playbackPrefs.speedChangeNotifications.collectAsState()
    val ambientModeEnabled by playbackPrefs.ambientModeEnabled.collectAsState()
    val loopVideoEnabled by playbackPrefs.loopVideoEnabled.collectAsState()

    val appDisplayLanguage by viewModel.appDisplayLanguage.collectAsState()
    val autoTranslateMetadata by viewModel.autoTranslateMetadata.collectAsState()
    val showOriginalTitles by viewModel.showOriginalTitles.collectAsState()

    val defaultResolutionPref = remember {
        val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
        mutableStateOf(sp.getString("default_resolution", "1080p") ?: "1080p")
    }
    val doubleTapSeekPref = remember {
        val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
        mutableIntStateOf(sp.getInt("double_tap_seek_seconds", 10))
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val jsonStr = viewModel.exportUserDataJson()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(jsonStr.toByteArray(Charsets.UTF_8))
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val jsonStr = context.contentResolver.openInputStream(uri)?.use { isStream ->
                        isStream.bufferedReader().use { it.readText() }
                    } ?: ""

                    if (jsonStr.isNotBlank()) {
                        val summary = viewModel.importUserDataJson(jsonStr)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Imported ${summary.historyCount} history items & ${summary.likedCount} liked videos!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    BackHandler(enabled = true) {
        if (parentCategory != null) {
            currentCategory = parentCategory
            parentCategory = null
        } else if (currentCategory != null) {
            currentCategory = null
        } else {
            onBackClick()
        }
    }

    LaunchedEffect(Unit) {
        com.example.extractor.YtDlpUpdateManager.refreshVersion(context)
        com.example.util.AppEngineDiagnosticManager.init(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentCategory?.title ?: "Settings",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (parentCategory != null) {
                            currentCategory = parentCategory
                            parentCategory = null
                        } else if (currentCategory != null) {
                            currentCategory = null
                        } else {
                            onBackClick()
                        }
                    }) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentCategory == null) {
                val rootCategories = listOf(
                    SettingsCategory.GENERAL,
                    SettingsCategory.PLAYBACK,
                    SettingsCategory.SEEKBAR,
                    SettingsCategory.ACCOUNTS_SOURCES,
                    SettingsCategory.PROVIDERS,
                    SettingsCategory.SUBTITLE_PROVIDERS,
                    SettingsCategory.SMART_SKIP,
                    SettingsCategory.HISTORY_PRIVACY,
                    SettingsCategory.BACKUP_RESTORE,
                    SettingsCategory.ADDITIONAL_SETTINGS,
                    SettingsCategory.ABOUT
                )
                // ROOT YOUTUBE-STYLE SETTINGS LIST (Single clean bold title with icon)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
                ) {
                    items(rootCategories) { category ->
                        YouTubeSettingsRow(
                            title = category.title,
                            subtitle = null,
                            icon = category.icon,
                            onClick = { currentCategory = category }
                        )
                    }
                }
            } else {
                // SUB-SCREEN DETAIL PAGES
                when (currentCategory) {
                    SettingsCategory.SEEKBAR -> {
                        SeekbarSettingsScreen(
                            onBackClick = { currentCategory = null }
                        )
                    }

                    SettingsCategory.PROWLARR_INDEXERS -> {
                        TorrentIndexersScreen(
                            onBackClick = { currentCategory = null }
                        )
                    }

                    SettingsCategory.SUBTITLE_PROVIDERS -> {
                        SubtitleProvidersSettingsScreen(
                            onBackClick = { currentCategory = null }
                        )
                    }

                    SettingsCategory.BATTERY_SAVER -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Live Power & Battery Status Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isPowerSaveActive) 
                                            Color(0xFF1B5E20).copy(alpha = 0.25f) 
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isPowerSaveActive) Color(0xFF4CAF50).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isPowerSaveActive) Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isBatteryCharging) Icons.Default.Bolt else Icons.Outlined.Bolt,
                                                        contentDescription = "Battery Status",
                                                        tint = if (isPowerSaveActive) Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = "$batteryLevel% Battery",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (isBatteryCharging) "⚡ Charging" else "Discharging",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isBatteryCharging) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Status Badge
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = if (isPowerSaveActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isPowerSaveActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            ) {
                                                Text(
                                                    text = if (isPowerSaveActive) "⚡ SAVER ON" else "STANDARD",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Progress Bar
                                        LinearProgressIndicator(
                                            progress = { (batteryLevel / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = when {
                                                batteryLevel <= 15 -> Color(0xFFEF5350)
                                                batteryLevel <= 30 -> Color(0xFFFFA726)
                                                isPowerSaveActive -> Color(0xFF66BB6A)
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }

                            // 2. Main Master Controls Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "POWER SAVING ENGINE",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "Enable Battery Saver Mode",
                                            subtitle = "Caps resolution, stops GPU ambient glow, limits background P2P traffic & saves RAM",
                                            checked = batterySaverManualEnabled,
                                            onCheckedChange = { viewModel.setBatterySaverManual(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Auto-Enable on Low Battery",
                                            subtitle = "Engages automatically when battery is ≤ $batterySaverLowThreshold% or Android Power Saver is on",
                                            checked = batterySaverAutoOnLow,
                                            onCheckedChange = { viewModel.setBatterySaverAutoOnLow(it) }
                                        )

                                        if (batterySaverAutoOnLow) {
                                            SettingsMiniPopupRow(
                                                title = "Low Battery Trigger Threshold",
                                                subtitle = "$batterySaverLowThreshold% remaining battery",
                                                options = listOf(10, 15, 20, 25, 30),
                                                selectedOption = batterySaverLowThreshold,
                                                onOptionSelected = { viewModel.setBatterySaverLowThreshold(it) },
                                                optionLabel = { "$it% remaining battery" }
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Playback & Video Optimization Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "STREAMING & MEDIA SAVINGS",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        SettingsMiniPopupRow(
                                            title = "Resolution Cap in Saver Mode",
                                            subtitle = "$batterySaverResolutionCap (reduces video decode heat & network transfer)",
                                            options = listOf("360p", "480p", "720p", "1080p"),
                                            selectedOption = batterySaverResolutionCap,
                                            onOptionSelected = { viewModel.setBatterySaverResolutionCap(it) },
                                            optionLabel = { cap ->
                                                when (cap) {
                                                    "360p" -> "360p (Maximum Battery Saving)"
                                                    "480p" -> "480p (Recommended SD)"
                                                    "720p" -> "720p (HD Balanced)"
                                                    else -> "1080p (Uncapped)"
                                                }
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Disable Ambient Video Glow",
                                            subtitle = "Stops dynamic thumbnail color extraction and shader rendering to save GPU power",
                                            checked = batterySaverDisableAmbient,
                                            onCheckedChange = { viewModel.setBatterySaverDisableAmbient(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Audio-Only Music Mode",
                                            subtitle = "Plays audio stream without video on detected music tracks (saves ~80% battery & data)",
                                            checked = batterySaverAudioOnlyForMusic,
                                            onCheckedChange = { viewModel.setBatterySaverAudioOnlyForMusic(it) }
                                        )
                                    }
                                }
                            }

                            // 4. Hardware & Torrent Performance Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "HARDWARE & PERFORMANCE",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "P2P Low-Power Mode",
                                            subtitle = "Restricts BitTorrent swarm peer connections to 30 and throttles background upload",
                                            checked = batterySaverLowPowerTorrent,
                                            onCheckedChange = { viewModel.setBatterySaverLowPowerTorrent(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "True AMOLED Pitch Black",
                                            subtitle = "Uses pure #000000 background to turn off individual OLED display pixels",
                                            checked = batterySaverPureBlackAmoled,
                                            onCheckedChange = { viewModel.setBatterySaverPureBlackAmoled(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Reduce UI Animations",
                                            subtitle = "Disables heavy transition effects for instant responsiveness and lower CPU overhead",
                                            checked = batterySaverDisableAnimations,
                                            onCheckedChange = { viewModel.setBatterySaverDisableAnimations(it) }
                                        )
                                    }
                                }
                            }

                            // 5. App Lightness & Storage Booster Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "APP LIGHTNESS & RAM OPTIMIZER",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        val cacheMb = appCacheSizeBytes / (1024f * 1024f)
                                        Text(
                                            text = "Temporary Cache & Storage: ${String.format("%.1f MB", cacheMb)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Clearing thumbnail cache, video buffers, and temporary chunks frees device memory and makes the app start faster and feel lighter.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                                        )

                                        Button(
                                            onClick = {
                                                val freedBytes = viewModel.clearAppCache()
                                                val freedMb = freedBytes / (1024f * 1024f)
                                                Toast.makeText(
                                                    context,
                                                    if (freedBytes > 0) "Freed ${String.format("%.1f MB", freedMb)}! Butterfly is now lighter and faster."
                                                    else "Caches are already clean and optimized!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CleaningServices,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Clean Cache & Boost Speed", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.GENERAL -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Language & Translation Section Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "LANGUAGE & TRANSLATION",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        SettingsMiniPopupRow(
                                            title = "App Interface Language",
                                            subtitle = if (appDisplayLanguage == "hi") "हिंदी (Hindi)" else "English (US / UK)",
                                            options = listOf("en", "hi"),
                                            selectedOption = appDisplayLanguage,
                                            onOptionSelected = { viewModel.setAppDisplayLanguage(it) },
                                            optionLabel = { code -> if (code == "hi") "हिंदी (Hindi)" else "English (US / UK)" }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Auto-translate Metadata",
                                            subtitle = "Automatically detect foreign titles and translate to selected language",
                                            checked = autoTranslateMetadata,
                                            onCheckedChange = { viewModel.setAutoTranslateMetadata(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Show Original Native Titles",
                                            subtitle = "Always display untouched native titles (Japanese, Korean, Chinese, Hindi) alongside translations",
                                            checked = showOriginalTitles,
                                            onCheckedChange = { viewModel.setShowOriginalTitles(it) }
                                        )
                                    }
                                }
                            }

                            // 2. Appearance & Theme Section Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "APPEARANCE & THEME",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        SettingsMiniPopupRow(
                                            title = "Theme",
                                            subtitle = if (themeMode == com.example.ui.ThemeMode.LIGHT) "Light Mode" else "AMOLED Dark",
                                            options = listOf(com.example.ui.ThemeMode.AMOLED_DARK, com.example.ui.ThemeMode.LIGHT),
                                            selectedOption = themeMode,
                                            onOptionSelected = { viewModel.setThemeMode(it) },
                                            optionLabel = { mode -> if (mode == com.example.ui.ThemeMode.LIGHT) "Light Mode" else "AMOLED Dark (Default)" }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        SettingsMiniPopupRow(
                                            title = "Secondary Accent Color",
                                            subtitle = accentColor.label,
                                            options = com.example.ui.AppAccentColor.values().toList(),
                                            selectedOption = accentColor,
                                            onOptionSelected = { viewModel.setAccentColor(it) },
                                            optionLabel = { it.label },
                                            leadingIcon = { colorOpt ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(colorOpt.color)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        val isOpeningAnimationEnabled by viewModel.isOpeningAnimationEnabled.collectAsState()
                                        YouTubeSwitchRow(
                                            title = "Butterfly Opening Animation",
                                            subtitle = "Cinematic animated launch intro on app startup",
                                            checked = isOpeningAnimationEnabled,
                                            onCheckedChange = { viewModel.setOpeningAnimationEnabled(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        val openingAnimationStyle by viewModel.openingAnimationStyle.collectAsState()
                                        SettingsMiniPopupRow(
                                            title = "Opening Animation Style",
                                            subtitle = "${openingAnimationStyle.title} • ${openingAnimationStyle.subtitle}",
                                            options = MainViewModel.OpeningAnimationStyle.entries,
                                            selectedOption = openingAnimationStyle,
                                            onOptionSelected = { viewModel.setOpeningAnimationStyle(it) },
                                            optionLabel = { "${it.title} (${it.subtitle})" }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeDetailRow(
                                            title = "Preview Opening Animation",
                                            subtitle = "Test ${openingAnimationStyle.title} transition",
                                            onClick = {
                                                viewModel.setOpeningAnimationEnabled(true)
                                                viewModel.replayOpeningAnimation()
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Thumbnail Source Tags",
                                            subtitle = "Show provider badges (e.g. YouTube, Vimeo, 18+) on video cards",
                                            checked = showThumbnailTags,
                                            onCheckedChange = { viewModel.setShowThumbnailTags(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.PLAYBACK -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. VIDEO QUALITY & CODEC EFFICIENCY CARD
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "VIDEO QUALITY & CODEC EFFICIENCY",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "Prioritize Video Quality",
                                            subtitle = "Prioritize highest stream bitrate, higher fps and best profile",
                                            checked = prioritizeVideoQuality,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setPrioritizeVideoQuality(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        SettingsMiniPopupRow(
                                            title = "Video Codec Preference",
                                            subtitle = when (videoCodecPreference) {
                                                "AVC_H264" -> "Force AVC / H.264 (Maximum Compatibility & Low Power)"
                                                "HEVC_H265" -> "Prefer HEVC / H.265 (High Efficiency)"
                                                "VP9" -> "Prefer Google VP9"
                                                "AV1" -> "Prefer AV1 (Next-Gen)"
                                                else -> "Auto (Optimal Hardware Codec Selection)"
                                            },
                                            options = listOf("AUTO", "AVC_H264", "HEVC_H265", "VP9", "AV1"),
                                            selectedOption = videoCodecPreference,
                                            onOptionSelected = { coroutineScope.launch { playbackPrefs.setVideoCodecPreference(it) } },
                                            optionLabel = { code ->
                                                when (code) {
                                                    "AVC_H264" -> "Force AVC / H.264 (Cool & Battery Friendly)"
                                                    "HEVC_H265" -> "Prefer HEVC / H.265 (High Efficiency)"
                                                    "VP9" -> "Prefer Google VP9"
                                                    "AV1" -> "Prefer AV1 (Next-Gen High Quality)"
                                                    else -> "Auto (Optimal Hardware Selection)"
                                                }
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        SettingsMiniPopupRow(
                                            title = "Hardware Decoder Engine",
                                            subtitle = when (decoderMode) {
                                                "SOFTWARE" -> "Software Decoder (CPU Fallback)"
                                                "EXO_MEDIACODEC" -> "ExoPlayer Direct MediaCodec"
                                                else -> "Hardware Acceleration (GPU MediaCodec - Recommended)"
                                            },
                                            options = listOf("HARDWARE", "SOFTWARE", "EXO_MEDIACODEC"),
                                            selectedOption = decoderMode,
                                            onOptionSelected = { coroutineScope.launch { playbackPrefs.setDecoderMode(it) } },
                                            optionLabel = { mode ->
                                                when (mode) {
                                                    "SOFTWARE" -> "Software Decoder (CPU Fallback)"
                                                    "EXO_MEDIACODEC" -> "Force ExoPlayer MediaCodec Pipeline"
                                                    else -> "Hardware Acceleration (GPU MediaCodec)"
                                                }
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Disable HDR Video",
                                            subtitle = "Tone maps HDR to SDR for lower power usage, battery saving and cool playback",
                                            checked = disableHdrVideo,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setDisableHdrVideo(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Force AVC (H.264) Codec",
                                            subtitle = "Forces standard H.264 for maximum hardware decoder stability and lowest heat",
                                            checked = forceAvcCodec,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setForceAvcCodec(it) } }
                                        )
                                    }
                                }
                            }

                            // 2. AUDIO & STREAM TRACKS CARD
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "AUDIO & STREAM TRACKS",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "Disable DRC (Dynamic Range Compression)",
                                            subtitle = "Disables audio DRC compression to maintain original uncompressed dynamic range",
                                            checked = disableDrcAudio,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setDisableDrcAudio(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Force Original Audio Language",
                                            subtitle = "Always default to the content's native/original audio stream instead of dubs",
                                            checked = forceOriginalAudioLanguage,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setForceOriginalAudioLanguage(it) } }
                                        )
                                    }
                                }
                            }

                            // 3. PLAYBACK SPEED & GESTURE CONTROLS CARD
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "PLAYBACK SPEED & GESTURES",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        SettingsMiniPopupRow(
                                            title = "Default Playback Speed",
                                            subtitle = "${defaultSpeed}x",
                                            options = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
                                            selectedOption = defaultSpeed,
                                            onOptionSelected = { coroutineScope.launch { playbackPrefs.setDefaultSpeed(it) } },
                                            optionLabel = { "${it}x" }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Custom Playback Speed Menu",
                                            subtitle = "Enables advanced granular speed slider (0.1x to 4.0x) in player",
                                            checked = customPlaybackSpeedMenu,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setCustomPlaybackSpeedMenu(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        SettingsMiniPopupRow(
                                            title = "Tap & Hold Speed Boost",
                                            subtitle = "${tapAndHoldSpeed}x (Speed when holding finger down on video)",
                                            options = listOf(1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f),
                                            selectedOption = tapAndHoldSpeed,
                                            onOptionSelected = { coroutineScope.launch { playbackPrefs.setTapAndHoldSpeed(it) } },
                                            optionLabel = { "${it}x speed" }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Remember Playback Speed",
                                            subtitle = "Saves custom playback speed per video or session",
                                            checked = rememberPlaybackSpeed,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setRememberPlaybackSpeed(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Speed Change HUD Notifications",
                                            subtitle = "Shows on-screen toast & badge indicator when speed is adjusted",
                                            checked = speedChangeNotifications,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setSpeedChangeNotifications(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Disable Speed Adjustment for Music",
                                            subtitle = "Automatically resets playback speed to 1.0x on detected music streams",
                                            checked = disableSpeedForMusic,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setDisableSpeedForMusic(it) } }
                                        )
                                    }
                                }
                            }

                            // 4. RESOLUTION, AMBIENT & SEEKING CARD
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "DISPLAY, AMBIENT & SEEKING",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        SettingsMiniPopupRow(
                                            title = "Default Video Resolution",
                                            subtitle = defaultResolutionPref.value,
                                            options = listOf("Auto", "1080p", "720p", "480p", "360p"),
                                            selectedOption = defaultResolutionPref.value,
                                            onOptionSelected = { res ->
                                                defaultResolutionPref.value = res
                                                val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
                                                sp.edit().putString("default_resolution", res).apply()
                                            },
                                            optionLabel = { it }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        SettingsMiniPopupRow(
                                            title = "Double-Tap to Seek",
                                            subtitle = "${doubleTapSeekPref.intValue} seconds",
                                            options = listOf(5, 10, 15, 20, 30),
                                            selectedOption = doubleTapSeekPref.intValue,
                                            onOptionSelected = { secs ->
                                                doubleTapSeekPref.intValue = secs
                                                val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
                                                sp.edit().putInt("double_tap_seek_seconds", secs).apply()
                                            },
                                            optionLabel = { "$it seconds" }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Ambient Mode Glow",
                                            subtitle = "Dynamic glowing effect around video player matching video colors",
                                            checked = ambientModeEnabled,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setAmbientModeEnabled(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Loop Video by Default",
                                            subtitle = "Automatically repeat video when playback reaches end",
                                            checked = loopVideoEnabled,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setLoopVideoEnabled(it) } }
                                        )
                                    }
                                }
                            }

                            // 5. SECURE DNS & NETWORK PROTECTION CARD
                            item {
                                val isDnsEnabled by viewModel.isSecureDnsEnabled.collectAsState()
                                val isMaxProtection by viewModel.isMaxProtection.collectAsState()
                                val activeDnsProvider by viewModel.selectedDnsProvider.collectAsState()

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        // Header Row: "Secure DNS" + Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Secure DNS",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Switch(
                                                checked = isDnsEnabled,
                                                onCheckedChange = { viewModel.setSecureDnsEnabled(it) }
                                            )
                                        }

                                        if (isDnsEnabled) {
                                            Spacer(modifier = Modifier.height(14.dp))

                                            // Default Protection Option
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.setMaxProtection(false) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = !isMaxProtection,
                                                    onClick = { viewModel.setMaxProtection(false) }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Default Protection",
                                                        fontWeight = FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Butterfly ensures optimal connectivity whenever possible",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Max Protection Option
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.setMaxProtection(true) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isMaxProtection,
                                                    onClick = { viewModel.setMaxProtection(true) }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Max Protection",
                                                        fontWeight = FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Butterfly uses only the DNS servers you select",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            // Selector Box triggering the MINI POPUP (DropdownMenu)
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            dnsDropdownExpanded = true
                                                        },
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 14.dp, vertical = 13.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val prefix = if (!isMaxProtection) "Auto" else "Max"
                                                        val providerTitle = if (activeDnsProvider == com.example.util.DnsProvider.CUSTOM) "Custom Service Provider" else activeDnsProvider.displayName
                                                        Text(
                                                            text = "$prefix($providerTitle)",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.UnfoldMore,
                                                            contentDescription = "Select DNS Provider",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }

                                                // MINI POPUP (DropdownMenu styled exactly like browser settings)
                                                MaterialTheme(
                                                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                                                ) {
                                                    DropdownMenu(
                                                        expanded = dnsDropdownExpanded,
                                                        onDismissRequest = { dnsDropdownExpanded = false },
                                                        modifier = Modifier
                                                            .widthIn(min = 250.dp, max = 320.dp)
                                                            .background(Color(0xFF242428), RoundedCornerShape(16.dp))
                                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                                            .padding(vertical = 4.dp)
                                                    ) {
                                                        val availableDnsList = listOf(
                                                            com.example.util.DnsProvider.GOOGLE,
                                                            com.example.util.DnsProvider.CLOUDFLARE,
                                                            com.example.util.DnsProvider.OPENDNS,
                                                            com.example.util.DnsProvider.CLEANBROWSING,
                                                            com.example.util.DnsProvider.ADGUARD,
                                                            com.example.util.DnsProvider.CUSTOM
                                                        )

                                                        availableDnsList.forEach { provider ->
                                                            val isSelected = (activeDnsProvider == provider)

                                                            DropdownMenuItem(
                                                                text = {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Text(
                                                                            text = provider.displayName,
                                                                            fontSize = 14.sp,
                                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                                                        )
                                                                        if (isSelected) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Check,
                                                                                contentDescription = "Selected",
                                                                                tint = MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(18.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                                onClick = {
                                                                    dnsDropdownExpanded = false
                                                                    if (provider == com.example.util.DnsProvider.CUSTOM) {
                                                                        showCustomDnsDialog = true
                                                                    } else {
                                                                        viewModel.setSecureDnsEnabled(true)
                                                                        viewModel.setSelectedDnsProvider(provider)
                                                                    }
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Determines how to connect to websites over a secure connection.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.ADULT_18 -> {
                        UnifiedContentSourcesScreen(
                            viewModel = viewModel,
                            onNavigateToCategory = { currentCategory = it }
                        )
                    }

                    SettingsCategory.ACCOUNTS_SOURCES -> {
                        AccountsAndSourcesScreenContent(
                            modifier = Modifier.fillMaxSize(),
                            showHeader = false,
                            onClose = null
                        )
                    }

                    SettingsCategory.PROVIDERS -> {
                        UnifiedContentSourcesScreen(
                            viewModel = viewModel,
                            onNavigateToCategory = { currentCategory = it }
                        )
                    }
                    SettingsCategory.CLOUD_SOCIAL -> {
                        CloudSocialSettingsScreen(
                            onNavigateBack = { currentCategory = null }
                        )
                    }

                    SettingsCategory.BUNKR -> {
                        val bunkrRepo = remember { com.example.bunkr.repository.BunkrRepository.getInstance(context) }
                        val bunkrAlbums by bunkrRepo.allAlbums.collectAsState(initial = emptyList())
                        val bunkrFiles by bunkrRepo.allFiles.collectAsState(initial = emptyList())
                        val isBunkrScanning by bunkrRepo.isScanning.collectAsState()
                        var bunkrInputText by remember { mutableStateOf("") }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Overview Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Bunkr Album & File Integration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Paste album URLs (https://bunkr.cr/a/...) or file URLs (https://bunkr.cr/f/...). Butterfly will auto-crawl album items, resolve direct CDN streams, and cache metadata.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${bunkrAlbums.size} Saved Albums", fontWeight = FontWeight.SemiBold)
                                            Text("${bunkrFiles.size} Total Videos", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            // 2. Multiline Input Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Add / Import Bunkr URLs", fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = bunkrInputText,
                                            onValueChange = { bunkrInputText = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp),
                                            placeholder = { Text("Paste album/file URLs (one per line):\nhttps://bunkr.cr/a/cmFzH1Cf\nhttps://bunkr.cr/f/DJOH6o7Gg5UeN") },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = clipboard.primaryClip
                                                    if (clip != null && clip.itemCount > 0) {
                                                        val txt = clip.getItemAt(0).text?.toString() ?: ""
                                                        if (txt.isNotBlank()) {
                                                            bunkrInputText = if (bunkrInputText.isBlank()) txt else "$bunkrInputText\n$txt"
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Paste All")
                                            }

                                            Button(
                                                onClick = {
                                                    val input = bunkrInputText.trim()
                                                    if (input.isNotBlank()) {
                                                        bunkrInputText = ""
                                                        coroutineScope.launch {
                                                            Toast.makeText(context, "Scanning Bunkr URLs...", Toast.LENGTH_SHORT).show()
                                                            val report = bunkrRepo.importUrls(input)
                                                            Toast.makeText(context, "Added ${report.totalAlbumsProcessed} albums (${report.totalItemsDiscovered} videos)", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                enabled = !isBunkrScanning && bunkrInputText.isNotBlank(),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (isBunkrScanning) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                                } else {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add & Scan")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Batch Actions Row
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                Toast.makeText(context, "Rescanning all albums...", Toast.LENGTH_SHORT).show()
                                                bunkrRepo.refreshAlbums()
                                            }
                                        },
                                        enabled = !isBunkrScanning && bunkrAlbums.isNotEmpty(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Rescan All")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                bunkrRepo.clearAll()
                                                Toast.makeText(context, "Cleared Bunkr cache", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear All")
                                    }
                                }
                            }

                            // 4. List of saved albums
                            if (bunkrAlbums.isNotEmpty()) {
                                item {
                                    Text("Saved Bunkr Albums (${bunkrAlbums.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }

                                items(bunkrAlbums, key = { it.albumId }) { album ->
                                    val count = bunkrFiles.count { it.albumId == album.albumId }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(album.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("$count videos • ${album.sourceUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        bunkrRepo.refreshAlbums(listOf(album.albumId))
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                                            }
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        bunkrRepo.deleteAlbum(album.albumId)
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.VEGA,
                    SettingsCategory.VIDSRC,
                    SettingsCategory.DECRYPTOR,
                    SettingsCategory.TMDB_EMBED -> {
                        UnifiedContentSourcesScreen(
                            viewModel = viewModel,
                            onNavigateToCategory = { currentCategory = it }
                        )
                    }

                    SettingsCategory.NUVIO_PROVIDERS -> {
                        NuvioProvidersSettingsScreen(
                            viewModel = viewModel,
                            onBack = { currentCategory = null }
                        )
                    }

                    SettingsCategory.INTEGRATIONS -> {
                        var tmdbKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getTmdbApiKey()) }
                        var subdlKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getSubdlApiKey()) }
                        var openSubKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getOpenSubtitlesApiKey()) }
                        var torrentioUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getTorrentioBaseUrl()) }
                        var debridKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getDebridApiKey()) }
                        var poTokenServerUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getPoTokenServerUrl()) }
                        var poTokenInput by remember { mutableStateOf(com.example.util.AppConfig.getCustomPoToken()) }

                        // MediaFlow Proxy states
                        var mediaFlowEnabled by remember { mutableStateOf(com.example.util.AppConfig.isMediaFlowEnabled()) }
                        var mediaFlowServerUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getMediaFlowServerUrl()) }
                        var mediaFlowPasswordInput by remember { mutableStateOf(com.example.util.AppConfig.getMediaFlowApiPassword()) }
                        var mediaFlowLightMode by remember { mutableStateOf(com.example.util.AppConfig.isMediaFlowLightMode()) }
                        var mediaFlowFallbackToDirect by remember { mutableStateOf(com.example.util.AppConfig.isMediaFlowFallbackToDirect()) }
                        var mediaFlowProxyAllStreams by remember { mutableStateOf(com.example.util.AppConfig.isMediaFlowProxyAllStreams()) }
                        var mediaFlowPasswordVisible by remember { mutableStateOf(false) }
                        var isTestingMediaFlow by remember { mutableStateOf(false) }
                        var mediaFlowHealthStatus by remember { mutableStateOf<com.example.remote.MediaFlowProxyHelper.HealthStatus?>(null) }

                        // JAVapi & Stream Indexers states
                        var javapiEnabled by remember { mutableStateOf(com.example.util.AppConfig.isJavapiEnabled()) }
                        var javapiServerUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getJavapiServerUrl()) }
                        var yarrEnabled by remember { mutableStateOf(com.example.util.AppConfig.isYarrEnabled()) }
                        var yarrServerUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getYarrServerUrl()) }
                        var magnetioEnabled by remember { mutableStateOf(com.example.util.AppConfig.isMagnetioEnabled()) }

                        // Javinizer-Go states
                        var javinizerEnabled by remember { mutableStateOf(com.example.util.AppConfig.isJavinizerEnabled()) }
                        var javinizerUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getJavinizerApiUrl()) }
                        var javinizerTimeoutStr by remember { mutableStateOf(com.example.util.AppConfig.getJavinizerTimeoutSeconds().toString()) }
                        var javinizerFallback by remember { mutableStateOf(com.example.util.AppConfig.isJavinizerFallbackEnabled()) }
                        var isTestingJavinizer by remember { mutableStateOf(false) }
                        var javinizerHealthResult by remember { mutableStateOf<com.example.metadata.providers.JavinizerGoMetadataProvider.Companion.JavinizerHealthResult?>(null) }
                        var testSampleCode by remember { mutableStateOf("IPX-535") }
                        var sampleLookupStatus by remember { mutableStateOf<String?>(null) }
                        var isTestingSample by remember { mutableStateOf(false) }

                        // SOCKS5 Proxy states
                        var proxyEnabled by remember { mutableStateOf(com.example.util.AppConfig.isTorrentProxyEnabled()) }
                        var proxyHost by remember { mutableStateOf(com.example.util.AppConfig.getTorrentProxyHost()) }
                        var proxyPortStr by remember { mutableStateOf(com.example.util.AppConfig.getTorrentProxyPort().toString()) }
                        var proxyUser by remember { mutableStateOf(com.example.util.AppConfig.getTorrentProxyUser()) }
                        var proxyPass by remember { mutableStateOf(com.example.util.AppConfig.getTorrentProxyPass()) }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // JAVINIZER-GO Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "JAVINIZER-GO METADATA ENGINE (v1.5.1+)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Connects Butterfly to a real Javinizer-Go REST backend service to scrape titles, high-res covers, actress profiles & sample previews.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = javinizerEnabled,
                                                onCheckedChange = {
                                                    javinizerEnabled = it
                                                    com.example.util.AppConfig.setJavinizerEnabled(context, it)
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedTextField(
                                            value = javinizerUrlInput,
                                            onValueChange = { javinizerUrlInput = it },
                                            label = { Text("Javinizer-Go REST API Base URL") },
                                            placeholder = { Text("http://localhost:8765 or http://192.168.1.50:8765") },
                                            singleLine = true,
                                            enabled = javinizerEnabled,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = javinizerTimeoutStr,
                                                onValueChange = { javinizerTimeoutStr = it.filter { c -> c.isDigit() } },
                                                label = { Text("Timeout (sec)") },
                                                placeholder = { Text("15") },
                                                singleLine = true,
                                                enabled = javinizerEnabled,
                                                modifier = Modifier.weight(0.4f)
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .weight(0.6f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable(enabled = javinizerEnabled) {
                                                        javinizerFallback = !javinizerFallback
                                                        com.example.util.AppConfig.setJavinizerFallbackEnabled(context, javinizerFallback)
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = javinizerFallback,
                                                    onCheckedChange = {
                                                        javinizerFallback = it
                                                        com.example.util.AppConfig.setJavinizerFallbackEnabled(context, it)
                                                    },
                                                    enabled = javinizerEnabled
                                                )
                                                Text(
                                                    text = "Cascade fallback if offline",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (javinizerEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Diagnostics & Health Results
                                        if (javinizerHealthResult != null) {
                                            val res = javinizerHealthResult!!
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (res.isSuccess) Color(0xFF1B5E20).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (res.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                                            contentDescription = null,
                                                            tint = if (res.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = if (res.isSuccess) "Service Online (${res.serverVersion ?: "v1.5.1+"})" else "Service Offline",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (res.isSuccess) Color(0xFF81C784) else MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                    Text(
                                                        text = res.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }

                                        // Test Scrape Result
                                        if (sampleLookupStatus != null) {
                                            Text(
                                                text = sampleLookupStatus!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        isTestingJavinizer = true
                                                        javinizerHealthResult = null
                                                        val prov = com.example.metadata.providers.JavinizerGoMetadataProvider()
                                                        val timeoutInt = javinizerTimeoutStr.toIntOrNull() ?: 15
                                                        val result = prov.testHealth(
                                                            customBaseUrl = javinizerUrlInput,
                                                            customTimeoutSec = timeoutInt
                                                        )
                                                        javinizerHealthResult = result
                                                        isTestingJavinizer = false
                                                    }
                                                },
                                                enabled = javinizerEnabled && !isTestingJavinizer
                                            ) {
                                                if (isTestingJavinizer) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Testing...")
                                                } else {
                                                    Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Test Health")
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val timeoutInt = javinizerTimeoutStr.toIntOrNull() ?: 15
                                                    com.example.util.AppConfig.setJavinizerEnabled(context, javinizerEnabled)
                                                    com.example.util.AppConfig.setJavinizerApiUrl(context, javinizerUrlInput)
                                                    com.example.util.AppConfig.setJavinizerTimeoutSeconds(context, timeoutInt)
                                                    com.example.util.AppConfig.setJavinizerFallbackEnabled(context, javinizerFallback)
                                                    Toast.makeText(context, "Javinizer-Go settings saved!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Save")
                                            }
                                        }
                                    }
                                }
                            }
                            // TMDB Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "THE MOVIE DATABASE (TMDB)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Powers movie & series posters, summaries, cast metadata, and IMDb ID resolution for Torrentio & Vega sources.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                        OutlinedTextField(
                                            value = tmdbKeyInput,
                                            onValueChange = { tmdbKeyInput = it },
                                            label = { Text("TMDB API Key (v3 auth)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    com.example.util.AppConfig.setTmdbApiKey(context, com.example.util.AppConfig.DEFAULT_TMDB_API_KEY)
                                                    tmdbKeyInput = com.example.util.AppConfig.DEFAULT_TMDB_API_KEY
                                                    Toast.makeText(context, "Reset TMDB key to default", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Reset Default")
                                            }
                                            Button(
                                                onClick = {
                                                    com.example.util.AppConfig.setTmdbApiKey(context, tmdbKeyInput)
                                                    Toast.makeText(context, "TMDB Key saved!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Save")
                                            }
                                        }
                                    }
                                }
                            }

                            // Torrent Pipeline Debugger Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "TORRENT STREAMING PIPELINE DEBUGGER",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Inspect live swarm telemetry, piece buffer window, HTTP 206 server status, and test magnet playback.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.navigateToScreen(com.example.model.AppScreen.TORRENT_DEBUG) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Open")
                                            }
                                        }
                                    }
                                }
                            }

                            // Torrentio, Vega & Debrid Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "TORRENTIO & DEBRID RESOLVERS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Configure custom Torrentio endpoints or Stremio Real-Debrid / AllDebrid manifest tokens.\nTip: Users with Real-Debrid can set URL to https://torrentio.strem.fun/realdebrid=YOURAPIKEY or enter API key below.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                        OutlinedTextField(
                                            value = torrentioUrlInput,
                                            onValueChange = { torrentioUrlInput = it },
                                            label = { Text("Torrentio Base URL / Manifest") },
                                            placeholder = { Text("https://torrentio.strem.fun") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = debridKeyInput,
                                            onValueChange = { debridKeyInput = it },
                                            label = { Text("Real-Debrid / AllDebrid API Key (Optional)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    com.example.util.AppConfig.setTorrentioBaseUrl(context, com.example.util.AppConfig.DEFAULT_TORRENTIO_BASE_URL)
                                                    torrentioUrlInput = com.example.util.AppConfig.DEFAULT_TORRENTIO_BASE_URL
                                                    Toast.makeText(context, "Reset URLs to defaults", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Reset Defaults")
                                            }
                                            Button(
                                                onClick = {
                                                    com.example.util.AppConfig.setTorrentioBaseUrl(context, torrentioUrlInput)
                                                    com.example.util.AppConfig.setDebridApiKey(context, debridKeyInput)
                                                    Toast.makeText(context, "Streaming providers saved!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Save")
                                            }
                                        }
                                    }
                                }
                            }

                            // SOCKS5 BitTorrent Proxy Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "BITTORRENT SOCKS5 PROXY",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Bypass ISP DHT & tracker blocking on throttled cellular or regional networks.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = proxyEnabled,
                                                onCheckedChange = { proxyEnabled = it }
                                            )
                                        }

                                        if (proxyEnabled) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = proxyHost,
                                                    onValueChange = { proxyHost = it },
                                                    label = { Text("Proxy Host / IP") },
                                                    placeholder = { Text("127.0.0.1 or proxy.org") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(0.7f)
                                                )
                                                OutlinedTextField(
                                                    value = proxyPortStr,
                                                    onValueChange = { proxyPortStr = it.filter { ch -> ch.isDigit() } },
                                                    label = { Text("Port") },
                                                    placeholder = { Text("1080") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(0.3f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = proxyUser,
                                                    onValueChange = { proxyUser = it },
                                                    label = { Text("Username (Optional)") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = proxyPass,
                                                    onValueChange = { proxyPass = it },
                                                    label = { Text("Password (Optional)") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    val portInt = proxyPortStr.toIntOrNull() ?: 1080
                                                    com.example.util.AppConfig.setTorrentProxyConfig(
                                                        context = context,
                                                        enabled = proxyEnabled,
                                                        host = proxyHost,
                                                        port = portInt,
                                                        user = proxyUser,
                                                        pass = proxyPass
                                                    )
                                                    // Apply immediately to running Libtorrent engine
                                                    if (proxyEnabled && proxyHost.isNotBlank()) {
                                                        com.example.torrent.core.LibtorrentEngine.getInstance(context)
                                                            .setProxy(proxyHost, portInt, proxyUser.ifBlank { null }, proxyPass.ifBlank { null })
                                                        Toast.makeText(context, "SOCKS5 Proxy configured and applied!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        com.example.torrent.core.LibtorrentEngine.getInstance(context)
                                                            .setProxy("", 0)
                                                        Toast.makeText(context, "Proxy disabled (Direct mode)", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("Save Proxy Settings")
                                            }
                                        }
                                    }
                                }
                            }

                            // Subtitle Keys Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "SUBTITLE PROVIDER KEYS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = subdlKeyInput,
                                            onValueChange = { subdlKeyInput = it },
                                            label = { Text("SubDL API Key") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = openSubKeyInput,
                                            onValueChange = { openSubKeyInput = it },
                                            label = { Text("OpenSubtitles API Key") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    com.example.util.AppConfig.setSubdlApiKey(context, subdlKeyInput)
                                                    com.example.util.AppConfig.setOpenSubtitlesApiKey(context, openSubKeyInput)
                                                    Toast.makeText(context, "Subtitle API keys saved!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Save Subtitle Keys")
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedButton(
                                            onClick = { currentCategory = SettingsCategory.SUBTITLE_PROVIDERS },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ClosedCaption,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Manage Subtitle Plugins & Bazarr Providers")
                                        }
                                    }
                                }
                            }

                            // YouTube PoToken Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "YOUTUBE IDENTITY & POTOKEN",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Proof-of-Origin Token (PO Token) generator server or custom manual token to bypass YouTube 403 / bot detection.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                        OutlinedTextField(
                                            value = poTokenServerUrlInput,
                                            onValueChange = { poTokenServerUrlInput = it },
                                            label = { Text("PO Token Server URL") },
                                            placeholder = { Text(com.example.BuildConfig.PO_TOKEN_SERVER_URL) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = poTokenInput,
                                            onValueChange = { poTokenInput = it },
                                            label = { Text("Custom Manual PoToken (Optional)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    com.example.util.AppConfig.setPoTokenServerUrl(context, poTokenServerUrlInput)
                                                    com.example.util.AppConfig.setCustomPoToken(context, poTokenInput)
                                                    Toast.makeText(context, "PoToken configuration saved!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Save PoToken")
                                            }
                                        }
                                    }
                                }
                            }

                            // MediaFlow Proxy Middleware Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "MEDIAFLOW-PROXY-LIGHT STREAM MIDDLEWARE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Proxies extracted HLS (.m3u8), DASH (.mpd), and direct video streams through MediaFlow Proxy (or Light mode) with dynamic Referer/Cookie header rewriting and CORS bypass.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = mediaFlowEnabled,
                                                onCheckedChange = {
                                                    mediaFlowEnabled = it
                                                    com.example.util.AppConfig.setMediaFlowEnabled(context, it)
                                                }
                                            )
                                        }

                                        if (mediaFlowEnabled) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedTextField(
                                                value = mediaFlowServerUrlInput,
                                                onValueChange = { mediaFlowServerUrlInput = it },
                                                label = { Text("MediaFlow Server URL") },
                                                placeholder = { Text("http://192.168.1.50:8888 or https://mediaflow.domain.com") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = mediaFlowPasswordInput,
                                                onValueChange = { mediaFlowPasswordInput = it },
                                                label = { Text("API Password / Token (Optional)") },
                                                placeholder = { Text("Secret token") },
                                                singleLine = true,
                                                visualTransformation = if (mediaFlowPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    IconButton(onClick = { mediaFlowPasswordVisible = !mediaFlowPasswordVisible }) {
                                                        Icon(
                                                            imageVector = if (mediaFlowPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                            contentDescription = if (mediaFlowPasswordVisible) "Hide password" else "Show password"
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "MediaFlow-Light Mode",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Direct header forwarding with low latency and zero unnecessary transcoding",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = mediaFlowLightMode,
                                                    onCheckedChange = {
                                                        mediaFlowLightMode = it
                                                        com.example.util.AppConfig.setMediaFlowLightMode(context, it)
                                                    }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Fallback to Direct Playback",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Automatically attempts direct stream playback if the proxy is offline or encounters an error",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = mediaFlowFallbackToDirect,
                                                    onCheckedChange = {
                                                        mediaFlowFallbackToDirect = it
                                                        com.example.util.AppConfig.setMediaFlowFallbackToDirect(context, it)
                                                    }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Proxy All Remote Streams",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "When disabled, only streams requiring custom headers/CORS bypass are proxied",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = mediaFlowProxyAllStreams,
                                                    onCheckedChange = {
                                                        mediaFlowProxyAllStreams = it
                                                        com.example.util.AppConfig.setMediaFlowProxyAllStreams(context, it)
                                                    }
                                                )
                                            }

                                            // Health check / Test connection status badge
                                            mediaFlowHealthStatus?.let { status ->
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = when {
                                                        status.isOnline -> MaterialTheme.colorScheme.primaryContainer
                                                        status.isAuthError -> MaterialTheme.colorScheme.tertiaryContainer
                                                        else -> MaterialTheme.colorScheme.errorContainer
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = when {
                                                                status.isOnline -> Icons.Default.CheckCircle
                                                                status.isAuthError -> Icons.Default.Lock
                                                                else -> Icons.Default.Warning
                                                            },
                                                            contentDescription = null,
                                                            tint = when {
                                                                status.isOnline -> MaterialTheme.colorScheme.primary
                                                                status.isAuthError -> MaterialTheme.colorScheme.tertiary
                                                                else -> MaterialTheme.colorScheme.error
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Text(
                                                                text = if (status.isOnline) "Status: Online (${status.serverVersion ?: "MediaFlow"})" else "Status: Error",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = status.message,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            isTestingMediaFlow = true
                                                            mediaFlowHealthStatus = com.example.remote.MediaFlowProxyHelper.testHealth(
                                                                customUrl = mediaFlowServerUrlInput,
                                                                customPassword = mediaFlowPasswordInput
                                                            )
                                                            isTestingMediaFlow = false
                                                        }
                                                    },
                                                    enabled = !isTestingMediaFlow && mediaFlowServerUrlInput.isNotBlank(),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    if (isTestingMediaFlow) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Testing...")
                                                    } else {
                                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Test Connection")
                                                    }
                                                }

                                                Button(
                                                    onClick = {
                                                        com.example.util.AppConfig.setMediaFlowEnabled(context, mediaFlowEnabled)
                                                        com.example.util.AppConfig.setMediaFlowServerUrl(context, mediaFlowServerUrlInput)
                                                        com.example.util.AppConfig.setMediaFlowApiPassword(context, mediaFlowPasswordInput)
                                                        com.example.util.AppConfig.setMediaFlowLightMode(context, mediaFlowLightMode)
                                                        com.example.util.AppConfig.setMediaFlowFallbackToDirect(context, mediaFlowFallbackToDirect)
                                                        com.example.util.AppConfig.setMediaFlowProxyAllStreams(context, mediaFlowProxyAllStreams)
                                                        Toast.makeText(context, "MediaFlow Proxy settings saved!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Save MediaFlow")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Multi-Indexer & Stream Aggregators Card (AIOStreams / YARR / Magnetio / JAVapi)
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "UNIVERSAL STREAM INDEXERS & METADATA (AIOSTREAMS)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Enables high-speed multi-indexer search (1337x, TGx, Nyaa, EZTV, YTS, YARR, Magnetio) and REST metadata scrapers.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Magnetio Toggle
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Magnetio Multi-Indexer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Text("Parallel 1337x & TorrentGalaxy torrent scraper with deduplication", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = magnetioEnabled,
                                                onCheckedChange = {
                                                    magnetioEnabled = it
                                                    com.example.util.AppConfig.setMagnetioEnabled(context, it)
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // YARR Toggle & URL
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("YARR Torrent Aggregator", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Text("High-performance Stremio torrent aggregation endpoint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = yarrEnabled,
                                                onCheckedChange = {
                                                    yarrEnabled = it
                                                    com.example.util.AppConfig.setYarrEnabled(context, it)
                                                }
                                            )
                                        }

                                        if (yarrEnabled) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = yarrServerUrlInput,
                                                onValueChange = { yarrServerUrlInput = it },
                                                label = { Text("YARR Server URL") },
                                                placeholder = { Text(com.example.resolver.providers.YarrSourceProvider.DEFAULT_BASE_URL) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // JAVapi Toggle & URL
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("JAVapi REST Metadata", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Text("Online JAV metadata fallback provider for rich titles and covers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = javapiEnabled,
                                                onCheckedChange = {
                                                    javapiEnabled = it
                                                    com.example.util.AppConfig.setJavapiEnabled(context, it)
                                                }
                                            )
                                        }

                                        if (javapiEnabled) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = javapiServerUrlInput,
                                                onValueChange = { javapiServerUrlInput = it },
                                                label = { Text("JAVapi Base URL") },
                                                placeholder = { Text(com.example.util.AppConfig.DEFAULT_JAVAPI_SERVER_URL) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    com.example.util.AppConfig.setMagnetioEnabled(context, magnetioEnabled)
                                                    com.example.util.AppConfig.setYarrEnabled(context, yarrEnabled)
                                                    com.example.util.AppConfig.setYarrServerUrl(context, yarrServerUrlInput)
                                                    com.example.util.AppConfig.setJavapiEnabled(context, javapiEnabled)
                                                    com.example.util.AppConfig.setJavapiServerUrl(context, javapiServerUrlInput)
                                                    Toast.makeText(context, "Indexers and metadata engines updated!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Save Aggregators")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.SMART_SKIP -> {
                        com.example.smartskip.SponsorBlockSettingsScreen(
                            onBackClick = { currentCategory = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    SettingsCategory.HISTORY_PRIVACY -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                YouTubeDetailRow(
                                    title = "Clear Watch History",
                                    subtitle = "Delete all locally recorded watch records",
                                    onClick = { showClearHistoryDialog = true }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Clear Search History",
                                    subtitle = "Delete recent query history and search cache",
                                    onClick = { showClearSearchDialog = true }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Blocked Channels",
                                    subtitle = "Manage hidden creators and channels",
                                    onClick = {
                                        Toast.makeText(context, "No blocked channels currently", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    SettingsCategory.ADDITIONAL_SETTINGS -> {
                        val subItems = listOf(
                            SettingsCategory.INTEGRATIONS,
                            SettingsCategory.DIAGNOSTICS,
                            SettingsCategory.BATTERY_SAVER
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                Text(
                                    text = "ADDITIONAL SETTINGS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                            items(subItems) { cat ->
                                val dynamicSub = when (cat) {
                                    SettingsCategory.BATTERY_SAVER -> if (isPowerSaveActive) "Active ($batteryLevel%)" else "Optimizations & battery saver ($batteryLevel%)"
                                    else -> cat.subtitle
                                }
                                YouTubeSettingsRow(
                                    title = cat.title,
                                    subtitle = dynamicSub,
                                    icon = cat.icon,
                                    onClick = {
                                        parentCategory = SettingsCategory.ADDITIONAL_SETTINGS
                                        currentCategory = cat
                                    }
                                )
                            }
                        }
                    }

                    SettingsCategory.DNS_NETWORK -> {
                        com.example.ui.components.SecureDnsSelectionDialog(
                            viewModel = viewModel,
                            onDismiss = { currentCategory = null }
                        )
                    }

                    SettingsCategory.BACKUP_RESTORE -> {
                        val supabaseLoggedIn by com.example.supabase.SupabaseAuthManager.isLoggedIn.collectAsState()
                        val supabaseUser by com.example.supabase.SupabaseAuthManager.currentUser.collectAsState()
                        val supabaseSyncState by com.example.supabase.SupabaseSyncManager.syncState.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                Text(
                                    text = "SUPABASE CLOUD SYNC & BACKUP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = if (supabaseLoggedIn) "Supabase Account" else "Connect Supabase Account",
                                    subtitle = if (supabaseLoggedIn) "Connected: ${supabaseUser?.email} • Status: ${supabaseSyncState.syncMessage}" else "Sign in to sync history, bookmarks, likes, playlists & preferences across devices",
                                    onClick = {
                                        showSupabaseSettingsDialog = true
                                    }
                                )
                            }
                            if (supabaseLoggedIn) {
                                item {
                                    YouTubeDetailRow(
                                        title = "Sync Now with Supabase",
                                        subtitle = if (supabaseSyncState.isSyncing) "Syncing in progress..." else "Perform full bidirectional sync with Supabase cloud",
                                        onClick = {
                                            com.example.supabase.SupabaseSyncManager.triggerSync(forceFull = true)
                                            Toast.makeText(context, "Syncing with Supabase...", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                item {
                                    YouTubeSwitchRow(
                                        title = "Automatic Cloud Sync",
                                        subtitle = "Continuously sync changes in the background when online",
                                        checked = supabaseSyncState.autoSyncEnabled,
                                        onCheckedChange = { enabled: Boolean ->
                                            com.example.supabase.SupabaseSyncManager.setAutoSyncEnabled(enabled)
                                        }
                                    )
                                }
                            }

                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }

                            item {
                                Text(
                                    text = "LOCAL JSON BACKUP & RESTORE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Export Profile & Watch Data",
                                    subtitle = "Save bookmarks, history & playlists to a JSON file",
                                    onClick = { createDocumentLauncher.launch("butterfly_backup.json") }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Import Profile & Watch Data",
                                    subtitle = "Restore data from a JSON file on your device",
                                    onClick = { openDocumentLauncher.launch("application/json") }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Paste JSON Backup Directly",
                                    subtitle = "Paste raw JSON text to restore data instantly",
                                    onClick = { showPasteImportDialog = true }
                                )
                            }
                        }
                    }

                    SettingsCategory.DIAGNOSTICS -> {
                        val repoList by com.example.util.AppEngineDiagnosticManager.repoList.collectAsState()
                        val isGlobalChecking by com.example.util.AppEngineDiagnosticManager.isGlobalChecking.collectAsState()
                        val isGlobalUpdating by com.example.util.AppEngineDiagnosticManager.isGlobalUpdating.collectAsState()
                        val summaryText by com.example.util.AppEngineDiagnosticManager.overallDiagnosticSummary.collectAsState()
                        val componentTestResults by com.example.util.AppEngineDiagnosticManager.componentTestResults.collectAsState()
                        val isTestingComponents by com.example.util.AppEngineDiagnosticManager.isTestingComponents.collectAsState()
                        val hasAvailableUpdates = repoList.any { it.status == com.example.util.RepoUpdateStatus.UPDATE_AVAILABLE }
                        val availableUpdatesCount = repoList.count { it.status == com.example.util.RepoUpdateStatus.UPDATE_AVAILABLE }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section 0: DAILY GITHUB BACKGROUND AUTO-UPDATE
                            item {
                                val isAutoUpdateEnabled by com.example.util.AppEngineDiagnosticManager.isAutoUpdateEnabled.collectAsState()
                                val isSilentDownloadEnabled by com.example.util.AppEngineDiagnosticManager.isSilentDownloadEnabled.collectAsState()
                                val lastUpdateTimestamp by com.example.util.AppEngineDiagnosticManager.lastAutoUpdateTimestamp.collectAsState()
                                val lastCheckedFormatted = if (lastUpdateTimestamp > 0) {
                                    val date = java.util.Date(lastUpdateTimestamp)
                                    val sdf = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                                    sdf.format(date)
                                } else "Never checked"

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "AUTOMATIC BACKGROUND UPDATES",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "Daily Background Auto-Update",
                                            subtitle = "Automatically check GitHub repositories & core engines once a day in background",
                                            checked = isAutoUpdateEnabled,
                                            onCheckedChange = { com.example.util.AppEngineDiagnosticManager.setAutoUpdateEnabled(context, it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Auto-Download & Install Silently",
                                            subtitle = "Silently download latest scrapers, yt-dlp signatures and indexer definitions without prompts",
                                            checked = isSilentDownloadEnabled,
                                            onCheckedChange = { com.example.util.AppEngineDiagnosticManager.setSilentDownloadEnabled(context, it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeDetailRow(
                                            title = "Last Background Update Check",
                                            subtitle = "$lastCheckedFormatted • Tap to trigger manual background sync now",
                                            onClick = {
                                                com.example.util.AppEngineDiagnosticManager.checkAndRunDailyAutoUpdate(context)
                                                Toast.makeText(context, "Checking GitHub repositories in background...", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            // Section 1: LIVE COMPONENT & PROVIDER TESTS
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "LIVE PROVIDER & COMPONENT TEST SUITE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Test real connections, middleware proxy, scrapers, AI transcribe & streaming pipelines",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = {
                                                com.example.util.AppEngineDiagnosticManager.runAllLiveComponentDiagnostics(context)
                                            },
                                            enabled = !isTestingComponents,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            if (isTestingComponents) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Testing All Live Modules...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                            } else {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Run All Live Diagnostics & Component Tests", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Component Test Results (If any)
                            if (componentTestResults.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "TEST RESULTS (${componentTestResults.count { it.isSuccess }}/${componentTestResults.size} PASS)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (componentTestResults.all { it.isSuccess }) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                items(componentTestResults) { testRes ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (testRes.isSuccess) Color(0xFF4CAF50).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = if (testRes.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = if (testRes.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = testRes.componentName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (testRes.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                    modifier = Modifier.padding(start = 8.dp)
                                                ) {
                                                    Text(
                                                        text = if (testRes.latencyMs > 0) "${testRes.latencyMs} ms" else "Direct",
                                                        color = if (testRes.isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = testRes.statusSummary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (testRes.isSuccess) Color(0xFF388E3C) else MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = testRes.details,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Section 2: APP REPOSITORIES & ENGINES HEADER
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "REPOSITORIES & ENGINE RELEASES",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = summaryText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    com.example.util.AppEngineDiagnosticManager.checkAllUpdates(context)
                                                },
                                                enabled = !isGlobalChecking && !isGlobalUpdating,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (isGlobalChecking) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Checking...", fontSize = 13.sp)
                                                } else {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Check All Updates", fontSize = 13.sp)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = { showAddRepoDialog = true },
                                                enabled = !isGlobalUpdating,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Add Custom Repo", fontSize = 13.sp)
                                            }
                                        }

                                        // Single Button to Update All Sources At Once
                                        Spacer(modifier = Modifier.height(10.dp))
                                        if (hasAvailableUpdates || isGlobalUpdating) {
                                            Button(
                                                onClick = {
                                                    com.example.util.AppEngineDiagnosticManager.updateAllRepos(context)
                                                },
                                                enabled = !isGlobalUpdating && !isGlobalChecking,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFFFB300),
                                                    contentColor = Color(0xFF1E1B16)
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                if (isGlobalUpdating) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color(0xFF1E1B16)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Updating All Sources & Engines...",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (availableUpdatesCount > 0) "Update All Sources ($availableUpdatesCount Available)" else "Update All Sources",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    com.example.util.AppEngineDiagnosticManager.updateAllRepos(context)
                                                },
                                                enabled = !isGlobalUpdating && !isGlobalChecking,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Update & Sync All Sources",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Repositories and Engines List
                            items(repoList) { repo ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = repo.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "GitHub: ${repo.repoOwnerRepo}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            // Status Badge
                                            val badgeBg = when (repo.status) {
                                                com.example.util.RepoUpdateStatus.UPDATE_AVAILABLE -> Color(0xFFFFF3CD)
                                                com.example.util.RepoUpdateStatus.UP_TO_DATE -> Color(0xFFD1E7DD)
                                                com.example.util.RepoUpdateStatus.CHECKING, com.example.util.RepoUpdateStatus.UPDATING -> Color(0xFFCFF4FC)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                            val badgeText = when (repo.status) {
                                                com.example.util.RepoUpdateStatus.UPDATE_AVAILABLE -> "Update: ${repo.latestRemoteVersion}"
                                                com.example.util.RepoUpdateStatus.UP_TO_DATE -> "Up to date"
                                                com.example.util.RepoUpdateStatus.CHECKING -> "Checking..."
                                                com.example.util.RepoUpdateStatus.UPDATING -> "Updating..."
                                                com.example.util.RepoUpdateStatus.ERROR -> "Check Failed"
                                                else -> if (repo.isHealthOk) "Active" else "Warning"
                                            }
                                            val badgeColor = when (repo.status) {
                                                com.example.util.RepoUpdateStatus.UPDATE_AVAILABLE -> Color(0xFF856404)
                                                com.example.util.RepoUpdateStatus.UP_TO_DATE -> Color(0xFF0F5132)
                                                com.example.util.RepoUpdateStatus.CHECKING, com.example.util.RepoUpdateStatus.UPDATING -> Color(0xFF055160)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = badgeBg,
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    color = badgeColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = repo.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Installed: ${repo.installedVersion}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Date: ${repo.installedDate}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Latest: ${repo.latestRemoteVersion ?: "Unknown"}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (repo.latestRemoteVersion != null && repo.latestRemoteVersion != repo.installedVersion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Release Date: ${repo.latestReleaseDate ?: "N/A"}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    com.example.util.AppEngineDiagnosticManager.checkRepoUpdate(repo.id)
                                                },
                                                modifier = Modifier.height(36.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Text("Check Version", fontSize = 12.sp)
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Button(
                                                onClick = {
                                                    com.example.util.AppEngineDiagnosticManager.triggerRepoUpdate(context, repo.id)
                                                },
                                                modifier = Modifier.height(36.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Text(
                                                    text = if (repo.status == com.example.util.RepoUpdateStatus.UPDATE_AVAILABLE) "Update Now" else "Re-sync Engine",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.ABOUT -> {
                        val updateState by com.example.util.AppUpdateManager.updateState.collectAsState()
                        val isSyncingScripts by com.example.util.AppUpdateManager.isSyncingScripts.collectAsState()
                        val scope = rememberCoroutineScope()
                        var otaSyncMessage by remember { mutableStateOf<String?>(null) }
                        var showWhatsNewDialog by remember { mutableStateOf(false) }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                com.example.ui.components.ThemedButterflyLogo(size = 72.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Butterfly",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Version ${com.example.util.AppUpdateManager.currentVersionName} (Build ${com.example.util.AppUpdateManager.currentVersionCode})",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = { showWhatsNewDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("What's New in v${com.example.util.AppUpdateManager.currentVersionName}")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "A modern, lightweight multimedia streaming and playback client engineered with Jetpack Compose & Media3 ExoPlayer.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                // 1. APP UPDATES CARD (GitHub Releases with Detailed What's New Changelog)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Column {
                                                Text(
                                                    text = "App Updates",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = "Source: Morningstar-1-star/Butterfly-",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        when (val state = updateState) {
                                            is com.example.util.UpdateCheckState.Idle -> {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                             com.example.util.AppUpdateManager.checkForUpdates(context)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Check for Updates")
                                                }
                                            }

                                            is com.example.util.UpdateCheckState.Checking -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                    Text("Checking GitHub for latest release...", fontSize = 14.sp)
                                                }
                                            }

                                            is com.example.util.UpdateCheckState.UpToDate -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                                    Text("You are on the latest version (v${state.currentVersion})", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            com.example.util.AppUpdateManager.checkForUpdates(context)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Check Again")
                                                }
                                            }

                                            is com.example.util.UpdateCheckState.UpdateAvailable -> {
                                                val release = state.release
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(14.dp)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Text("🚀 New Version: v${release.versionName}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                                            if (release.apkSize > 0) {
                                                                val mb = String.format("%.1f MB", release.apkSize / (1024.0 * 1024.0))
                                                                Text("($mb)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text("What's New:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        if (release.changelogHighlights.isNotEmpty()) {
                                                            release.changelogHighlights.forEach { item ->
                                                                Row(
                                                                    modifier = Modifier.padding(vertical = 2.dp),
                                                                    verticalAlignment = Alignment.Top,
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                ) {
                                                                    Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                                    Text(
                                                                        text = item,
                                                                        fontSize = 12.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        lineHeight = 16.sp
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            Text(
                                                                text = release.releaseNotes.take(300),
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            com.example.util.AppUpdateManager.downloadAndInstall(context, release)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Download & Install (v${release.versionName})")
                                                }
                                            }

                                            is com.example.util.UpdateCheckState.Downloading -> {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    val mbDownloaded = String.format("%.1f", state.bytesDownloaded / (1024.0 * 1024.0))
                                                    val mbTotal = String.format("%.1f", state.totalBytes / (1024.0 * 1024.0))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Downloading Update...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                        Text("${state.progressPercent}% ($mbDownloaded / $mbTotal MB)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    LinearProgressIndicator(
                                                        progress = { state.progressPercent / 100f },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }

                                            is com.example.util.UpdateCheckState.ReadyToInstall -> {
                                                Text("Download completed! Ready to install update.", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        com.example.util.AppUpdateManager.triggerApkInstallation(context, state.apkFile)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Install Update Now")
                                                }
                                            }

                                            is com.example.util.UpdateCheckState.Error -> {
                                                Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            com.example.util.AppUpdateManager.checkForUpdates(context)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Retry Check")
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 2. DYNAMIC SCRIPT & PROVIDER OVER-THE-AIR (OTA) SYNC CARD
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = null,
                                                tint = Color(0xFF00E676)
                                            )
                                            Column {
                                                Text(
                                                    text = "Instant Scraper & Provider Sync (OTA)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = "Update streaming rules & source mirrors without APK download",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (otaSyncMessage != null) {
                                            Text(
                                                text = otaSyncMessage!!,
                                                fontSize = 12.sp,
                                                color = Color(0xFF00E676),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val res = com.example.util.AppUpdateManager.syncDynamicScripts(context)
                                                    otaSyncMessage = res.message
                                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = !isSyncingScripts,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            if (isSyncingScripts) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Syncing Scrapers...")
                                            } else {
                                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Sync Scraper Rules (Files Only)")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showWhatsNewDialog) {
                            com.example.ui.components.WhatsNewDialog(
                                onDismiss = { showWhatsNewDialog = false }
                            )
                        }
                    }
                    null -> {}
                }
            }
        }
    }

    // DIALOGS
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Watch History?") },
            text = { Text("This will remove all videos from your local watch history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearWatchHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearSearchDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchDialog = false },
            title = { Text("Clear Search History?") },
            text = { Text("This will clear all recent searches.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRecentSearches()
                        showClearSearchDialog = false
                        Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPasteImportDialog) {
        AlertDialog(
            onDismissRequest = { showPasteImportDialog = false },
            title = { Text("Paste Backup JSON") },
            text = {
                OutlinedTextField(
                    value = pasteJsonInput,
                    onValueChange = { pasteJsonInput = it },
                    label = { Text("JSON text") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pasteJsonInput.isNotBlank()) {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val sum = viewModel.importUserDataJson(pasteJsonInput)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        showPasteImportDialog = false
                                        pasteJsonInput = ""
                                        Toast.makeText(context, "Imported ${sum.historyCount} items!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSupabaseSettingsDialog) {
        com.example.ui.components.SupabaseAuthDialog(
            onDismiss = { showSupabaseSettingsDialog = false }
        )
    }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Custom GitHub Repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter any GitHub repository (e.g., owner/repo) to add it to Diagnostics, check its version, and receive live update tags.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customRepoInput,
                        onValueChange = { customRepoInput = it },
                        label = { Text("GitHub Owner/Repo") },
                        placeholder = { Text("e.g. yt-dlp/yt-dlp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customRepoNameInput,
                        onValueChange = { customRepoNameInput = it },
                        label = { Text("Display Name (Optional)") },
                        placeholder = { Text("e.g. My Custom Extractor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customRepoInput.isNotBlank()) {
                            com.example.util.AppEngineDiagnosticManager.addCustomRepo(
                                context,
                                customRepoInput,
                                customRepoNameInput
                            )
                            customRepoInput = ""
                            customRepoNameInput = ""
                            showAddRepoDialog = false
                            Toast.makeText(context, "Added custom repository to Diagnostics!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add Repo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCustomDnsDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDnsDialog = false },
            title = { Text("Custom Service Provider") },
            text = {
                Column {
                    Text(
                        "Enter your custom DNS-over-HTTPS (DoH) endpoint URL:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = customDnsInputText,
                        onValueChange = { customDnsInputText = it },
                        label = { Text("DoH URL") },
                        placeholder = { Text("https://dns.nextdns.io/doh") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customDnsInputText.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.setCustomDnsUrl(trimmed)
                            viewModel.setSelectedDnsProvider(com.example.util.DnsProvider.CUSTOM)
                            viewModel.setSecureDnsEnabled(true)
                            showCustomDnsDialog = false
                            Toast.makeText(context, "Custom DNS applied", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDnsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSecureDnsDialog) {
        com.example.ui.components.SecureDnsSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showSecureDnsDialog = false }
        )
    }
}

@Composable
private fun YouTubeSettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle.isNullOrBlank()) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun YouTubeDetailRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun YouTubeSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
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

@Composable
private fun <T> SettingsMiniPopupRow(
    title: String,
    subtitle: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (T) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        MaterialTheme(
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .widthIn(min = 250.dp, max = 340.dp)
                    .background(Color(0xFF242428), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(vertical = 4.dp)
            ) {
                options.forEach { option ->
                    val isSelected = (option == selectedOption)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    leadingIcon?.invoke(option)
                                    Text(
                                        text = optionLabel(option),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onOptionSelected(option)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
