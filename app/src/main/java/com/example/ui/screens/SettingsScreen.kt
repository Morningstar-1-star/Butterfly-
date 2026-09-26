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
    GENERAL("General", "Theme, colors & layout preferences", Icons.Outlined.Palette),
    LANGUAGE("Language & Translation", "App language, auto-translation & original titles", Icons.Outlined.Translate),
    PLAYBACK("Playback", "Resolution, speed & seek gestures", Icons.Outlined.PlayCircle),
    ACCOUNTS_SOURCES("Accounts & Sources", "Tencent Video, YouTube, Google Drive, Crunchyroll, Hotstar & SonyLIV", Icons.Outlined.Hub),
    PROVIDERS("Content Sources", "Manage Tencent Video (v.qq.com), YouTube, Dailymotion & more", Icons.Outlined.Source),
    PROWLARR_INDEXERS("Prowlarr & Cardigann Indexers", "Manage Prowlarr V11 YAML indexers, test & sync", Icons.Outlined.Radar),
    SUBTITLE_PROVIDERS("Subtitle Providers", "Configure SubDL, OpenSubtitles, SubtitleCat & Bazarr plugins", Icons.Outlined.ClosedCaption),
    CLOUD_SOCIAL("Cloud & Social Sources", "Telegram, MEGA & Bunkr unified media library", Icons.Outlined.Cloud),
    BUNKR("Bunkr Albums & Direct CDN", "Manage Bunkr album URLs, auto-extract & sync", Icons.Outlined.CloudDownload),
    VEGA("Vega Movies & Series", "All 52+ in-app movie extensions & anime providers", Icons.Outlined.Movie),
    VIDSRC("VidSrc Sources", "VidLink, AutoEmbed, Smashy & cloud mirrors", Icons.Outlined.VideoLibrary),
    DECRYPTOR("Decryptor Sources", "Vidhide, Turbo, Nxsha & fast servers", Icons.Outlined.LockOpen),
    TMDB_EMBED("TMDB Sources", "VixSrc, Showbox, Videasy & VIP servers", Icons.Outlined.MovieCreation),
    ADULT_18("18+ Content", "Adult content mode & mature sources", Icons.Outlined.Explicit),
    SMART_SKIP("SponsorBlock", "Auto-skip sponsored segments, intros & filler", Icons.Outlined.FastForward),
    HISTORY_PRIVACY("History & Privacy", "Watch history, search cache & blocked channels", Icons.Outlined.History),
    BACKUP_RESTORE("Backup & Restore", "Export/import profile data & Google Drive sync", Icons.Outlined.CloudUpload),
    ADDITIONAL_SETTINGS("Additional Settings", "DNS & Network, API keys, Diagnostics & Battery Saver", Icons.Outlined.Tune),
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
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showAnimationDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showBatteryCapDialog by remember { mutableStateOf(false) }
    var showBatteryThresholdDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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
                    SettingsCategory.LANGUAGE,
                    SettingsCategory.PLAYBACK,
                    SettingsCategory.ACCOUNTS_SOURCES,
                    SettingsCategory.PROVIDERS,
                    SettingsCategory.PROWLARR_INDEXERS,
                    SettingsCategory.SUBTITLE_PROVIDERS,
                    SettingsCategory.VEGA,
                    SettingsCategory.VIDSRC,
                    SettingsCategory.DECRYPTOR,
                    SettingsCategory.TMDB_EMBED,
                    SettingsCategory.ADULT_18,
                    SettingsCategory.SMART_SKIP,
                    SettingsCategory.HISTORY_PRIVACY,
                    SettingsCategory.BACKUP_RESTORE,
                    SettingsCategory.ADDITIONAL_SETTINGS,
                    SettingsCategory.ABOUT
                )
                // ROOT YOUTUBE-STYLE SETTINGS LIST
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
                ) {
                    items(rootCategories) { category ->
                        val dynamicSubtitle = when (category) {
                            SettingsCategory.GENERAL -> if (themeMode == com.example.ui.ThemeMode.LIGHT) "Light Theme" else "AMOLED Dark"
                            SettingsCategory.LANGUAGE -> (if (appDisplayLanguage == "hi") "हिंदी (Hindi)" else "English") + if (autoTranslateMetadata) " • Auto-translate ON" else " • Auto-translate OFF"
                            SettingsCategory.BATTERY_SAVER -> if (isPowerSaveActive) "Active ($batteryLevel% • Eco Power Mode)" else "Optimizations, RAM & battery saver ($batteryLevel%)"
                            SettingsCategory.PLAYBACK -> "${defaultResolutionPref.value} • ${doubleTapSeekPref.intValue}s seek"
                            SettingsCategory.SUBTITLE_PROVIDERS -> "SubDL, OpenSubtitles, SubtitleCat & Bazarr"
                            SettingsCategory.VEGA -> "${viewModel.installedVegaProviders.collectAsState().value.size} extensions installed • Built-in"
                            SettingsCategory.VIDSRC -> "${viewModel.installedVidSrcProviders.collectAsState().value.size} servers active • Multi-mirror"
                            SettingsCategory.DECRYPTOR -> "${viewModel.installedDecryptorProviders.collectAsState().value.size} servers active • HLS decoder"
                            SettingsCategory.TMDB_EMBED -> "${viewModel.installedTMDBProviders.collectAsState().value.size} sources active • VIP extractors"
                            SettingsCategory.ADULT_18 -> if (adultContentEnabled) "Enabled (18+ sources only)" else "Disabled"
                            SettingsCategory.DNS_NETWORK -> if (viewModel.isSecureDnsEnabled.collectAsState().value) viewModel.selectedDnsProvider.collectAsState().value.displayName else "Disabled (ISP)"
                            else -> category.subtitle
                        }

                        YouTubeSettingsRow(
                            title = category.title,
                            subtitle = dynamicSubtitle,
                            icon = category.icon,
                            onClick = { currentCategory = category }
                        )
                    }
                }
            } else {
                // SUB-SCREEN DETAIL PAGES
                when (currentCategory) {
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
                                            YouTubeDetailRow(
                                                title = "Low Battery Trigger Threshold",
                                                subtitle = "$batterySaverLowThreshold% remaining battery",
                                                onClick = { showBatteryThresholdDialog = true }
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

                                        YouTubeDetailRow(
                                            title = "Resolution Cap in Saver Mode",
                                            subtitle = "$batterySaverResolutionCap (reduces video decode heat & network transfer)",
                                            onClick = { showBatteryCapDialog = true }
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
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                YouTubeDetailRow(
                                    title = "App Language",
                                    subtitle = if (appDisplayLanguage == "hi") "हिंदी (Hindi)" else "English",
                                    onClick = { showLanguageDialog = true }
                                )
                            }
                            item {
                                YouTubeSwitchRow(
                                    title = "Auto-translate Metadata",
                                    subtitle = "Automatically translate foreign titles & metadata without altering originals",
                                    checked = autoTranslateMetadata,
                                    onCheckedChange = { viewModel.setAutoTranslateMetadata(it) }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Theme",
                                    subtitle = if (themeMode == com.example.ui.ThemeMode.LIGHT) "Light Mode" else "AMOLED Dark",
                                    onClick = { showThemeDialog = true }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Secondary Accent Color",
                                    subtitle = accentColor.label,
                                    onClick = { showAccentDialog = true }
                                )
                            }
                            item {
                                val isOpeningAnimationEnabled by viewModel.isOpeningAnimationEnabled.collectAsState()
                                YouTubeSwitchRow(
                                    title = "Butterfly Opening Animation",
                                    subtitle = "Cinematic animated launch intro on app startup",
                                    checked = isOpeningAnimationEnabled,
                                    onCheckedChange = { viewModel.setOpeningAnimationEnabled(it) }
                                )
                            }
                            item {
                                val openingAnimationStyle by viewModel.openingAnimationStyle.collectAsState()
                                YouTubeDetailRow(
                                    title = "Opening Animation Style",
                                    subtitle = "${openingAnimationStyle.title} • ${openingAnimationStyle.subtitle}",
                                    onClick = { showAnimationDialog = true }
                                )
                            }
                            item {
                                val openingAnimationStyle by viewModel.openingAnimationStyle.collectAsState()
                                YouTubeDetailRow(
                                    title = "Preview Opening Animation",
                                    subtitle = "Test ${openingAnimationStyle.title} transition",
                                    onClick = {
                                        viewModel.setOpeningAnimationEnabled(true)
                                        viewModel.replayOpeningAnimation()
                                    }
                                )
                            }
                            item {
                                YouTubeSwitchRow(
                                    title = "Thumbnail Source Tags",
                                    subtitle = "Show provider badges (e.g. YouTube, Vimeo, 18+) on video cards",
                                    checked = showThumbnailTags,
                                    onCheckedChange = { viewModel.setShowThumbnailTags(it) }
                                )
                            }
                        }
                    }

                    SettingsCategory.LANGUAGE -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Language Selection Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "DISPLAY LANGUAGE",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeDetailRow(
                                            title = "App Interface Language",
                                            subtitle = if (appDisplayLanguage == "hi") "हिंदी (Hindi)" else "English (US/UK)",
                                            onClick = { showLanguageDialog = true }
                                        )
                                    }
                                }
                            }

                            // 2. Metadata Translation Controls Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "UNIVERSAL METADATA TRANSLATION",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "Auto-translate Metadata",
                                            subtitle = "Automatically detect foreign video/movie titles and translate to English & Hindi",
                                            checked = autoTranslateMetadata,
                                            onCheckedChange = { viewModel.setAutoTranslateMetadata(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Show Original Titles by Default",
                                            subtitle = "Always display untouched native titles (Japanese, Korean, Chinese, Arabic, etc.) alongside translations",
                                            checked = showOriginalTitles,
                                            onCheckedChange = { viewModel.setShowOriginalTitles(it) }
                                        )
                                    }
                                }
                            }

                            // 3. Engine Architecture & Features Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Translate,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Universal Language Core",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Zero Data Loss & Smart Detection",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Text(
                                            text = "• Default English: All foreign titles (Japanese, Korean, Chinese, Spanish, etc.) default to English.\n• Hindi Native Respect: Hindi titles are left in native Hindi untouched with zero translation overhead.\n• Clean Titles: Titles are displayed cleanly without secondary translation clutter.\n• High-Speed Local Caching: Translations are cached in Room DB for instant zero-latency loading.",
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val testOriginal = "進撃の巨人 The Final Season 完結編"
                                                    val result = com.example.util.UniversalTranslator.translateTitle(testOriginal)
                                                    Toast.makeText(
                                                        context,
                                                        "Original: $testOriginal\nEN: ${result.translatedEN}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Test Universal Translation Engine", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.PLAYBACK -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                YouTubeDetailRow(
                                    title = "Default Video Resolution",
                                    subtitle = defaultResolutionPref.value,
                                    onClick = { showResolutionDialog = true }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Double-Tap to Seek",
                                    subtitle = "${doubleTapSeekPref.intValue} seconds",
                                    onClick = { showSeekDialog = true }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Default Playback Speed",
                                    subtitle = "${defaultSpeed}x",
                                    onClick = { showSpeedDialog = true }
                                )
                            }
                            item {
                                YouTubeSwitchRow(
                                    title = "Disable Speed Adjustment for Music",
                                    subtitle = "Automatically resets playback speed to 1.0x on music streams",
                                    checked = disableSpeedForMusic,
                                    onCheckedChange = { coroutineScope.launch { playbackPrefs.setDisableSpeedForMusic(it) } }
                                )
                            }
                        }
                    }

                    SettingsCategory.ADULT_18 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                YouTubeSwitchRow(
                                    title = "18+ Adult Content Mode",
                                    subtitle = "Enable adult content mode (Home dropdown will show 18+ sources only)",
                                    checked = adultContentEnabled,
                                    onCheckedChange = { viewModel.setAdultContentEnabled(it) }
                                )
                            }
                            if (adultContentEnabled) {
                                item {
                                    Text(
                                        text = "ENABLED ADULT PROVIDERS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                val adultProviders = listOf(
                                    "xnxx" to "XNXX (HD Adult Video)",
                                    "hellporno" to "HellPorno (HD Streams)",
                                    "stripchat" to "Stripchat (Live Webcam Shows)",
                                    "chaturbate" to "Chaturbate (Live Webcam Cams)",
                                    "sextb" to "SEXТB (StreamTB)",
                                    "supjav" to "SupJav (FHD Stream)",
                                    "123av" to "123AV (JAV & Player)",
                                    "javtiful" to "Javtiful (JAV)",
                                    "jav_all" to "All JAV Sources",
                                    "pornhub" to "Pornhub",
                                    "xvideos" to "XVideos",
                                    "cam4" to "CAM4 (Live Shows)",
                                    "cammodels" to "CamModels (Live)",
                                    "noodlemagazine" to "NoodleMagazine",
                                    "thisvid" to "ThisVid",
                                    "tnaflix" to "TNAFlix",
                                    "spankbang" to "SpankBang",
                                    "playvid" to "Playvid",
                                    "txxx" to "TXXX",
                                    "eporner" to "Eporner",
                                    "hanime1" to "Hanime1 Anime",
                                    "redtube" to "RedTube",
                                    "xhamster" to "XHamster",
                                    "beeg" to "Beeg",
                                    "4tube" to "4tube",
                                    "rule34video" to "Rule34Video",
                                    "youporn" to "YouPorn"
                                )
                                items(adultProviders) { (id, name) ->
                                    val isEnabled = enabledProviderIds.contains(id)
                                    YouTubeSwitchRow(
                                        title = name,
                                        subtitle = "Catalog and streams from $name",
                                        checked = isEnabled,
                                        onCheckedChange = { viewModel.toggleProviderEnabled(id) }
                                    )
                                }
                            } else {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "18+ Adult Sources Inactive",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Turn on '18+ Adult Content Mode' above to activate sources: XNXX, HellPorno, Stripchat, Chaturbate, SEXТB, SupJav, 123AV, Pornhub, XVideos, and more.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.ACCOUNTS_SOURCES -> {
                        AccountsAndSourcesScreenContent(
                            modifier = Modifier.fillMaxSize(),
                            showHeader = false,
                            onClose = null
                        )
                    }

                    SettingsCategory.PROVIDERS -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable { currentCategory = SettingsCategory.ACCOUNTS_SOURCES }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Hub,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Accounts & Sources",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        text = "Grayjay Engine",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Log into YouTube, Google Drive, Crunchyroll, Hotstar & SonyLIV",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF673AB7).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF673AB7).copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable { currentCategory = SettingsCategory.PROWLARR_INDEXERS }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF673AB7).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Radar,
                                                contentDescription = null,
                                                tint = Color(0xFF9C27B0),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Prowlarr & Cardigann Indexers",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF9C27B0)
                                                ) {
                                                    Text(
                                                        text = "V11 YAML Engine",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Manage Prowlarr indexer definitions, test health & sync mirrors",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFE91E63).copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable { currentCategory = SettingsCategory.ADULT_18 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE91E63).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Explicit,
                                                contentDescription = null,
                                                tint = Color(0xFFE91E63),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "SEXТB & 18+ Adult Sources",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFE91E63)
                                                ) {
                                                    Text(
                                                        text = if (adultContentEnabled) "ENABLED" else "18+",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Manage SEXТB (StreamTB), JAV (123AV, Javtiful) & mature tube sources",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Decryptor Multi-Server Provider Card
                            item {
                                var decryptorEnabled by remember { mutableStateOf(com.example.util.AppConfig.isDecryptorEnabled()) }
                                var decryptorUrl by remember { mutableStateOf(com.example.util.AppConfig.getDecryptorBaseUrl()) }
                                var isEditingUrl by remember { mutableStateOf(false) }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Dns,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "Decryptor (Nxsha Multi-Server)",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF00E5FF)
                                                    ) {
                                                        Text(
                                                            text = "HLS ENGINE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.Black,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Extracts TMDB movies & TV shows into multi-server streams (Vidhide, Turbo, Nxsha Fast) for Media3 ExoPlayer",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = decryptorEnabled,
                                                onCheckedChange = {
                                                    decryptorEnabled = it
                                                    com.example.util.AppConfig.setDecryptorEnabled(context, it)
                                                }
                                            )
                                        }

                                        if (decryptorEnabled) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Endpoint: $decryptorUrl",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.LightGray,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(
                                                    onClick = { isEditingUrl = !isEditingUrl }
                                                ) {
                                                    Text(if (isEditingUrl) "Close" else "Edit URL", fontSize = 12.sp, color = Color(0xFF00E5FF))
                                                }
                                            }

                                            if (isEditingUrl) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                OutlinedTextField(
                                                    value = decryptorUrl,
                                                    onValueChange = {
                                                        decryptorUrl = it
                                                        com.example.util.AppConfig.setDecryptorBaseUrl(context, it)
                                                    },
                                                    label = { Text("Decryptor Backend URL") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // TMDB Embed Multi-Source Provider Card (13 Selectable Sources)
                            item {
                                var tmdbMasterEnabled by remember { mutableStateOf(com.example.extractor.tmdbembed.TMDBEmbedConfig.isMasterEnabled(context)) }
                                var defaultSource by remember { mutableStateOf(com.example.extractor.tmdbembed.TMDBEmbedConfig.getDefaultSource(context)) }
                                var fallbackEnabled by remember { mutableStateOf(com.example.extractor.tmdbembed.TMDBEmbedConfig.isFallbackEnabled(context)) }
                                var showSourceList by remember { mutableStateOf(false) }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF6200EE).copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(Color(0xFF6200EE), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "TMDB Embed (13 Sources)",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF6200EE)
                                                    ) {
                                                        Text(
                                                            text = "MULTI-SOURCE",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Extracts TMDB movies & TV via Showbox, VixSrc, NetMirror, Videasy, Vidlink, CastleTV & more",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = tmdbMasterEnabled,
                                                onCheckedChange = {
                                                    tmdbMasterEnabled = it
                                                    com.example.extractor.tmdbembed.TMDBEmbedConfig.setMasterEnabled(context, it)
                                                    viewModel.toggleProviderEnabled("tmdb_embed", it)
                                                }
                                            )
                                        }

                                        if (tmdbMasterEnabled) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Default Source Selection
                                            Text(
                                                text = "Default Primary Source: ${defaultSource.displayName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(com.example.extractor.tmdbembed.TMDBEmbedSource.allSources) { src ->
                                                    val isSelected = defaultSource == src
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            defaultSource = src
                                                            com.example.extractor.tmdbembed.TMDBEmbedConfig.setDefaultSource(context, src)
                                                        },
                                                        label = { Text(src.displayName, fontSize = 11.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = Color(0xFF6200EE),
                                                            selectedLabelColor = Color.White
                                                        )
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Fallback Toggle
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Auto Fallback to Other Sources",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "If default source fails, try next enabled sources automatically",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = fallbackEnabled,
                                                    onCheckedChange = {
                                                        fallbackEnabled = it
                                                        com.example.extractor.tmdbembed.TMDBEmbedConfig.setFallbackEnabled(context, it)
                                                    }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Toggle Source List Accordion
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showSourceList = !showSourceList }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (showSourceList) "Hide Per-Source Toggles ▲" else "Configure 13 Sources & Health Status ▼",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            if (showSourceList) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                com.example.extractor.tmdbembed.TMDBEmbedSource.allSources.forEach { src ->
                                                    var isSrcEnabled by remember {
                                                        mutableStateOf(com.example.extractor.tmdbembed.TMDBEmbedConfig.isSourceEnabled(context, src))
                                                    }
                                                    val health = com.example.extractor.tmdbembed.TMDBEmbedConfig.getSourceHealth(src)

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 3.dp)
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = src.displayName,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = if (src == defaultSource) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                                if (src == defaultSource) {
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "(Default)",
                                                                        fontSize = 10.sp,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                // Health badge
                                                                val badgeColor = when (health.statusText) {
                                                                    "Online" -> Color(0xFF00C853)
                                                                    "Degraded" -> Color(0xFFFF9100)
                                                                    "Failing" -> Color(0xFFFF1744)
                                                                    else -> Color.Gray
                                                                }
                                                                Surface(
                                                                    shape = RoundedCornerShape(3.dp),
                                                                    color = badgeColor.copy(alpha = 0.2f)
                                                                ) {
                                                                    Text(
                                                                        text = health.statusText,
                                                                        fontSize = 9.sp,
                                                                        color = badgeColor,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                text = "${if (src.supportsTv) "Movie & TV" else "Movie"} • Success: ${health.successCount}, Fail: ${health.failureCount}",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        Switch(
                                                            checked = isSrcEnabled,
                                                            onCheckedChange = {
                                                                isSrcEnabled = it
                                                                com.example.extractor.tmdbembed.TMDBEmbedConfig.setSourceEnabled(context, src, it)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Featured Tencent Video (v.qq.com) Card
                            item {
                                val isTencentEnabled = enabledProviderIds.contains("tencent")
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF0052D9).copy(alpha = 0.12f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        Color(0xFF0052D9).copy(alpha = 0.6f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF0052D9),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.LiveTv,
                                                            contentDescription = "Tencent Video",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "Tencent Video",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = Color(0xFF0052D9)
                                                        ) {
                                                            Text(
                                                                text = "v.qq.com",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Chinese VIP Dramas, Donghua & Movies",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Switch(
                                                checked = isTencentEnabled,
                                                onCheckedChange = { viewModel.toggleProviderEnabled("tencent") }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Stream official Donghua (Soul Land, Perfect World, Battle Through the Heavens, Joy of Life, The Untamed) & C-Dramas with 1080p HLS playback.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (!isTencentEnabled) {
                                                        viewModel.toggleProviderEnabled("tencent")
                                                    }
                                                    viewModel.setAdultContentEnabled(false)
                                                    viewModel.setActiveProvider("tencent")
                                                    currentCategory = null
                                                    viewModel.navigateToScreen(com.example.model.AppScreen.HOME)
                                                    Toast.makeText(context, "Switched to Tencent Video Home Feed", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0052D9)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Open on Homepage", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            val normalProviders = listOf(
                                "tencent" to "Tencent Video (v.qq.com)",
                                "youtube" to "YouTube",
                                "sonyliv" to "SonyLIV",
                                "hotstar" to "Hotstar / Jio",
                                "amazonminitv" to "Amazon miniTV",
                                "crunchyroll" to "Crunchyroll Anime",
                                "bilibili" to "Bilibili",
                                "dailymotion" to "Dailymotion",
                                "twitch" to "Twitch",
                                "bigo" to "Bigo Live",
                                "archive_org" to "Internet Archive",
                                "vimeo" to "Vimeo",
                                "bun-tel-meg" to "bun-tel-meg (Telegram, MEGA & Bunkr)",
                                "torrent" to "BitTorrent (P2P)",
                                "discoveryplus" to "Discovery+",
                                "disney" to "Disney / Disney+",
                                "hbo" to "HBO / Max",
                                "curiositystream" to "CuriosityStream",
                                "googledrive" to "Google Drive",
                                "imdb" to "IMDb (Top Movies & Trailers)",
                                "mxplayer" to "MX Player",
                                "popcorntv" to "PopcornTV",
                                "decryptor" to "Decryptor (Multi-Server HLS)",
                                "tmdb_embed" to "TMDB Embed (13 Multi-Sources)",
                                "vidsrc" to "VidSrc (Cloud Stream)"
                            )
                            items(normalProviders) { (id, name) ->
                                val isEnabled = enabledProviderIds.contains(id)
                                Column {
                                    YouTubeSwitchRow(
                                        title = name,
                                        subtitle = when (id) {
                                            "bilibili" -> "Bilibili anime, pop culture & dynamic Chinese video streams"
                                            "tencent" -> "Tencent Video (v.qq.com) Chinese dramas, anime & cinema series"
                                            "sonyliv" -> "SonyLIV TV shows, live sports, premium web series & cinema"
                                            "bigo" -> "Bigo Live interactive streams, global broadcasters & video rooms"
                                            "bun-tel-meg" -> "Telegram Channels, MEGA Folders & Bunkr Albums video links"
                                            "amazonminitv" -> "Amazon miniTV free web series, comedy, romance & drama"
                                            "discoveryplus" -> "Discovery+, Science, Animal Planet & TLC docu-series"
                                            "disney" -> "Disney, Pixar, Marvel, Star Wars & Nat Geo cinema trailers"
                                            "hbo" -> "HBO Originals, House of the Dragon, Game of Thrones & Max hits"
                                            "curiositystream" -> "CuriosityStream science, history, space & nature documentaries"
                                            "googledrive" -> "Stream public and synced Google Drive movies & shared videos"
                                            "imdb" -> "IMDb Top 250 releases, movie charts & HD trailers"
                                            "mxplayer" -> "MX Player OTT web series, short films & movies"
                                            "popcorntv" -> "PopcornTV blockbusters, open movies & 4K cinema releases"
                                            "decryptor" -> "Nxsha multi-server HLS engine: Vidhide, Turbo & Fast CDNs"
                                            "tmdb_embed" -> "13 Selectable Sources: VixSrc, NetMirror, Videasy, Vidlink, CastleTV & more"
                                            "vidsrc" -> "VidSrc high-speed cloud streams, auto-mirrors & HD movies"
                                            else -> "Streams from $name platform"
                                        },
                                        checked = isEnabled,
                                        onCheckedChange = { viewModel.toggleProviderEnabled(id) }
                                    )
                                    if (id == "bun-tel-meg" && isEnabled) {
                                        TextButton(
                                            onClick = { currentCategory = SettingsCategory.CLOUD_SOCIAL },
                                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                                        ) {
                                            Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Paste & Manage Links (Telegram, MEGA, Bunkr)")
                                        }
                                    }
                                }
                            }
                        }
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

                    SettingsCategory.VEGA -> {
                        val isVegaMasterEnabled by viewModel.isVegaMasterEnabled.collectAsState()
                        val installedVega by viewModel.installedVegaProviders.collectAsState()
                        val availableVega by viewModel.availableVegaProviders.collectAsState()
                        val isFetching by viewModel.isFetchingVegaProviders.collectAsState()
                        val healthMap by viewModel.providerHealthMap.collectAsState()
                        val isTestingHealth by viewModel.isTestingVegaHealth.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Master Vega Toggle Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isVegaMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setVegaMasterEnabled(!isVegaMasterEnabled) }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                            Text(
                                                text = "Enable Vega Extensions",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isVegaMasterEnabled) "Vega extensions are active. Providers will resolve catalog titles and media links." else "Vega is completely disabled by default. All background processing and server calls are stopped.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isVegaMasterEnabled,
                                            onCheckedChange = { viewModel.setVegaMasterEnabled(it) }
                                        )
                                    }
                                }
                            }

                            // In-App Native Engine Status Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "100% IN-APP NATIVE SOURCES",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "⚡ Built-in Native / 0s Delay",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "All 52+ Vega scrapers & extractors run locally inside Butterfly. Zero cold-start delay, 100% in-app execution, and direct stream link resolution.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 2. Installed Extensions Section
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "INSTALLED EXTENSIONS (${installedVega.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (installedVega.isNotEmpty()) {
                                            TextButton(
                                                onClick = { viewModel.uninstallAllVegaProviders() }
                                            ) {
                                                Text("Uninstall All", color = MaterialTheme.colorScheme.error)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        TextButton(
                                            onClick = { viewModel.testVegaProvidersHealth() },
                                            enabled = !isTestingHealth
                                        ) {
                                            if (isTestingHealth) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Testing...")
                                            } else {
                                                Text("Test Health")
                                            }
                                        }
                                    }
                                }
                            }

                            if (installedVega.isEmpty()) {
                                item {
                                    Text(
                                        text = "No Vega extensions installed yet. Browse available extensions below.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(installedVega) { vp ->
                                    val health = healthMap[vp.id]
                                    val subtitleText = if (health != null) {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"} • $health"
                                    } else {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"}"
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.toggleVegaProvider(vp.id, !vp.isEnabled) }
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = vp.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = vp.isEnabled,
                                                onCheckedChange = { viewModel.toggleVegaProvider(vp.id, it) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = { viewModel.uninstallVegaProvider(vp.id) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Uninstall ${vp.name}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Available Extensions Section
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AVAILABLE EXTENSIONS (${availableVega.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (availableVega.isNotEmpty()) {
                                        Button(
                                            onClick = { viewModel.installAllVegaProviders() },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Install All (50+)")
                                        }
                                    }
                                }
                            }

                            if (isFetching) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            } else {
                                items(availableVega) { vId ->
                                    val isInstalled = installedVega.any { it.id == vId }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = com.example.vega.VegaProviderClient.formatProviderDisplayName(vId),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (isInstalled) viewModel.uninstallVegaProvider(vId)
                                                else viewModel.installVegaProvider(vId)
                                            },
                                            colors = if (isInstalled) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                                        ) {
                                            Text(if (isInstalled) "Uninstall" else "Install")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.VIDSRC -> {
                        val isVidSrcMasterEnabled by viewModel.isVidSrcMasterEnabled.collectAsState()
                        val installedVidSrc by viewModel.installedVidSrcProviders.collectAsState()
                        val availableVidSrc = viewModel.availableVidSrcProviders
                        val healthMap by viewModel.vidSrcHealthMap.collectAsState()
                        val isTestingHealth by viewModel.isTestingVidSrcHealth.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Master VidSrc Toggle Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isVidSrcMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setVidSrcMasterEnabled(!isVidSrcMasterEnabled) }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                            Text(
                                                text = "Enable VidSrc Sources",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isVidSrcMasterEnabled) "VidSrc multi-server mirrors and WASM HLS decryptors are active." else "VidSrc is disabled. All cloud mirrors and direct stream extractions are stopped.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isVidSrcMasterEnabled,
                                            onCheckedChange = { viewModel.setVidSrcMasterEnabled(it) }
                                        )
                                    }
                                }
                            }

                            // In-App Native Engine Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "100% IN-APP VIDSRC SERVERS",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "⚡ Built-in Native / 0s Delay",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "On-device WebAssembly decryptor & 12+ high-speed cloud streaming mirrors. Direct master HLS resolution with zero cold-start delay.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Installed VidSrc Section
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "INSTALLED SERVERS (${installedVidSrc.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (installedVidSrc.isNotEmpty()) {
                                            TextButton(onClick = { viewModel.uninstallAllVidSrcProviders() }) {
                                                Text("Uninstall All", color = MaterialTheme.colorScheme.error)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        TextButton(
                                            onClick = { viewModel.testVidSrcHealth() },
                                            enabled = !isTestingHealth
                                        ) {
                                            if (isTestingHealth) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Testing...")
                                            } else {
                                                Text("Test Health")
                                            }
                                        }
                                    }
                                }
                            }

                            if (installedVidSrc.isEmpty()) {
                                item {
                                    Text(
                                        text = "No VidSrc servers installed. Choose from available servers below.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(installedVidSrc) { vp ->
                                    val health = healthMap[vp.id]
                                    val subtitleText = if (health != null) {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"} • $health"
                                    } else {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"}"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.toggleVidSrcProvider(vp.id, !vp.isEnabled) }
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = vp.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = vp.isEnabled,
                                                onCheckedChange = { viewModel.toggleVidSrcProvider(vp.id, it) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = { viewModel.uninstallVidSrcProvider(vp.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Uninstall ${vp.name}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Available VidSrc Section
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AVAILABLE SERVERS (${availableVidSrc.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Button(
                                        onClick = { viewModel.installAllVidSrcProviders() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Install All")
                                    }
                                }
                            }

                            items(availableVidSrc) { sInfo ->
                                val isInstalled = installedVidSrc.any { it.id == sInfo.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = sInfo.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = sInfo.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (isInstalled) viewModel.uninstallVidSrcProvider(sInfo.id)
                                            else viewModel.installVidSrcProvider(sInfo.id)
                                        },
                                        colors = if (isInstalled) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                                    ) {
                                        Text(if (isInstalled) "Uninstall" else "Install")
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.DECRYPTOR -> {
                        val isDecryptorMasterEnabled by viewModel.isDecryptorMasterEnabled.collectAsState()
                        val installedDecryptor by viewModel.installedDecryptorProviders.collectAsState()
                        val availableDecryptor = viewModel.availableDecryptorProviders
                        val healthMap by viewModel.decryptorHealthMap.collectAsState()
                        val isTestingHealth by viewModel.isTestingDecryptorHealth.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Master Decryptor Toggle Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDecryptorMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setDecryptorMasterEnabled(!isDecryptorMasterEnabled) }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                            Text(
                                                text = "Enable Decryptor Sources",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isDecryptorMasterEnabled) "Decryptor multi-server HLS extractors and cinema servers are active." else "Decryptor is disabled. All stream resolutions are stopped.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isDecryptorMasterEnabled,
                                            onCheckedChange = { viewModel.setDecryptorMasterEnabled(it) }
                                        )
                                    }
                                }
                            }

                            // In-App Native Engine Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "100% IN-APP DECRYPTOR SERVERS",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "⚡ Built-in Native / 0s Delay",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Direct HLS extraction across Vidhide, Turbo, Nxsha, Lulustream & Fast CDN. Zero buffering, adaptive quality, and instant start.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Installed Decryptor Section
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "INSTALLED SERVERS (${installedDecryptor.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (installedDecryptor.isNotEmpty()) {
                                            TextButton(onClick = { viewModel.uninstallAllDecryptorProviders() }) {
                                                Text("Uninstall All", color = MaterialTheme.colorScheme.error)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        TextButton(
                                            onClick = { viewModel.testDecryptorHealth() },
                                            enabled = !isTestingHealth
                                        ) {
                                            if (isTestingHealth) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Testing...")
                                            } else {
                                                Text("Test Health")
                                            }
                                        }
                                    }
                                }
                            }

                            if (installedDecryptor.isEmpty()) {
                                item {
                                    Text(
                                        text = "No Decryptor servers installed. Choose from available servers below.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(installedDecryptor) { vp ->
                                    val health = healthMap[vp.id]
                                    val subtitleText = if (health != null) {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"} • $health"
                                    } else {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"}"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.toggleDecryptorProvider(vp.id, !vp.isEnabled) }
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = vp.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = vp.isEnabled,
                                                onCheckedChange = { viewModel.toggleDecryptorProvider(vp.id, it) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = { viewModel.uninstallDecryptorProvider(vp.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Uninstall ${vp.name}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Available Decryptor Section
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AVAILABLE SERVERS (${availableDecryptor.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Button(
                                        onClick = { viewModel.installAllDecryptorProviders() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Install All")
                                    }
                                }
                            }

                            items(availableDecryptor) { sInfo ->
                                val isInstalled = installedDecryptor.any { it.id == sInfo.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = sInfo.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = sInfo.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (isInstalled) viewModel.uninstallDecryptorProvider(sInfo.id)
                                            else viewModel.installDecryptorProvider(sInfo.id)
                                        },
                                        colors = if (isInstalled) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                                    ) {
                                        Text(if (isInstalled) "Uninstall" else "Install")
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.TMDB_EMBED -> {
                        val isTMDBMasterEnabled by viewModel.isTMDBMasterEnabled.collectAsState()
                        val installedTMDB by viewModel.installedTMDBProviders.collectAsState()
                        val availableTMDB = viewModel.availableTMDBProviders
                        val healthMap by viewModel.tmdbHealthMap.collectAsState()
                        val isTestingHealth by viewModel.isTestingTMDBHealth.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Master TMDB Toggle Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isTMDBMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setTMDBMasterEnabled(!isTMDBMasterEnabled) }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                            Text(
                                                text = "Enable TMDB Sources",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isTMDBMasterEnabled) "TMDB multi-server embed & direct streams are active." else "TMDB sources are disabled. Embed and VIP stream resolvers are stopped.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isTMDBMasterEnabled,
                                            onCheckedChange = { viewModel.setTMDBMasterEnabled(it) }
                                        )
                                    }
                                }
                            }

                            // In-App Native Engine Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "100% IN-APP TMDB SOURCES",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "⚡ Built-in Native / 0s Delay",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "VixSrc, NetMirror, Videasy, Vidlink, Showbox & 13 VIP native scrapers. Fast playback with adaptive multi-resolution support.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Installed TMDB Section
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "INSTALLED SOURCES (${installedTMDB.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (installedTMDB.isNotEmpty()) {
                                            TextButton(onClick = { viewModel.uninstallAllTMDBProviders() }) {
                                                Text("Uninstall All", color = MaterialTheme.colorScheme.error)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        TextButton(
                                            onClick = { viewModel.testTMDBHealth() },
                                            enabled = !isTestingHealth
                                        ) {
                                            if (isTestingHealth) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Testing...")
                                            } else {
                                                Text("Test Health")
                                            }
                                        }
                                    }
                                }
                            }

                            if (installedTMDB.isEmpty()) {
                                item {
                                    Text(
                                        text = "No TMDB sources installed. Choose from available sources below.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(installedTMDB) { vp ->
                                    val health = healthMap[vp.id]
                                    val subtitleText = if (health != null) {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"} • $health"
                                    } else {
                                        "Status: ${if (vp.isEnabled) "Active" else "Disabled"}"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.toggleTMDBProvider(vp.id, !vp.isEnabled) }
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = vp.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = vp.isEnabled,
                                                onCheckedChange = { viewModel.toggleTMDBProvider(vp.id, it) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = { viewModel.uninstallTMDBProvider(vp.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Uninstall ${vp.name}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Available TMDB Section
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AVAILABLE SOURCES (${availableTMDB.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Button(
                                        onClick = { viewModel.installAllTMDBProviders() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Install All")
                                    }
                                }
                            }

                            items(availableTMDB) { sInfo ->
                                val isInstalled = installedTMDB.any { it.id == sInfo.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = sInfo.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = sInfo.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (isInstalled) viewModel.uninstallTMDBProvider(sInfo.id)
                                            else viewModel.installTMDBProvider(sInfo.id)
                                        },
                                        colors = if (isInstalled) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                                    ) {
                                        Text(if (isInstalled) "Uninstall" else "Install")
                                    }
                                }
                            }
                        }
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
                            SettingsCategory.DNS_NETWORK,
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
                                    SettingsCategory.DNS_NETWORK -> if (viewModel.isSecureDnsEnabled.collectAsState().value) viewModel.selectedDnsProvider.collectAsState().value.displayName else "Disabled (ISP)"
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
                        val isSecureDnsEnabled by viewModel.isSecureDnsEnabled.collectAsState()
                        val selectedDnsProvider by viewModel.selectedDnsProvider.collectAsState()
                        val dnsTestResult by viewModel.dnsTestResult.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                YouTubeSwitchRow(
                                    title = "Enable Secure DNS (DNS-over-HTTPS)",
                                    subtitle = "Encrypt domain queries to bypass ISP blocks and access video sources",
                                    checked = isSecureDnsEnabled,
                                    onCheckedChange = { viewModel.setSecureDnsEnabled(it) }
                                )
                            }
                            if (isSecureDnsEnabled) {
                                item {
                                    Text(
                                        text = "DNS PROVIDER",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                items(com.example.util.DnsProvider.values()) { provider ->
                                    val isSelected = (selectedDnsProvider == provider)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setSelectedDnsProvider(provider) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.setSelectedDnsProvider(provider) }
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = provider.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = provider.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Real DNS Resolution Test",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = testDnsDomainInput,
                                                onValueChange = { testDnsDomainInput = it },
                                                label = { Text("Test Domain") },
                                                placeholder = { Text("youtube.com") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { viewModel.runDnsDiagnosticTest(testDnsDomainInput) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Test DNS Resolution", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                            }

                                            if (dnsTestResult != null) {
                                                val res = dnsTestResult!!
                                                Spacer(modifier = Modifier.height(12.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                                        contentDescription = null,
                                                        tint = if (res.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (res.isSuccess) "Real Resolution Successful" else "Resolution Failed",
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (res.isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("Provider: ${res.providerName}", style = MaterialTheme.typography.bodySmall)
                                                Text("Protocol: ${res.protocol}", style = MaterialTheme.typography.bodySmall)
                                                Text("Latency: ${res.latencyMs} ms", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                Text("Target Domain: ${res.testedDomain}", style = MaterialTheme.typography.bodySmall)
                                                if (res.resolvedIps.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Resolved IP Addresses:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                    res.resolvedIps.take(5).forEach { ip ->
                                                        Text(" • $ip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                } else if (!res.errorMessage.isNullOrBlank()) {
                                                    Text("Error: ${res.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setThemeMode(com.example.ui.ThemeMode.AMOLED_DARK)
                                showThemeDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (themeMode == com.example.ui.ThemeMode.AMOLED_DARK),
                            onClick = {
                                viewModel.setThemeMode(com.example.ui.ThemeMode.AMOLED_DARK)
                                showThemeDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("AMOLED Dark (Default)")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setThemeMode(com.example.ui.ThemeMode.LIGHT)
                                showThemeDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (themeMode == com.example.ui.ThemeMode.LIGHT),
                            onClick = {
                                viewModel.setThemeMode(com.example.ui.ThemeMode.LIGHT)
                                showThemeDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Light Mode")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAnimationDialog) {
        val currentStyle by viewModel.openingAnimationStyle.collectAsState()
        AlertDialog(
            onDismissRequest = { showAnimationDialog = false },
            title = {
                Text(
                    text = "App Opening Animation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Select your preferred intro animation when starting Butterfly:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    MainViewModel.OpeningAnimationStyle.entries.forEach { styleOpt ->
                        val isSelected = (currentStyle == styleOpt)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setOpeningAnimationStyle(styleOpt)
                                },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setOpeningAnimationStyle(styleOpt)
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = styleOpt.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (styleOpt.badge != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = styleOpt.badge,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = styleOpt.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAnimationDialog = false
                        viewModel.setOpeningAnimationEnabled(true)
                        viewModel.replayOpeningAnimation()
                    }
                ) {
                    Text("Preview")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnimationDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showAccentDialog) {
        AlertDialog(
            onDismissRequest = { showAccentDialog = false },
            title = { Text("Secondary Accent Color") },
            text = {
                LazyColumn {
                    items(com.example.ui.AppAccentColor.values()) { colorOpt ->
                        val isSelected = (accentColor == colorOpt)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAccentColor(colorOpt)
                                    showAccentDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(colorOpt.color)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = colorOpt.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccentDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResolutionDialog) {
        val resolutions = listOf("Auto", "1080p", "720p", "480p", "360p")
        AlertDialog(
            onDismissRequest = { showResolutionDialog = false },
            title = { Text("Default Video Resolution") },
            text = {
                Column {
                    resolutions.forEach { res ->
                        val isSelected = (defaultResolutionPref.value == res)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    defaultResolutionPref.value = res
                                    val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
                                    sp.edit().putString("default_resolution", res).apply()
                                    showResolutionDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    defaultResolutionPref.value = res
                                    val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
                                    sp.edit().putString("default_resolution", res).apply()
                                    showResolutionDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(res)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResolutionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSeekDialog) {
        val seekOptions = listOf(5, 10, 15, 20, 30)
        AlertDialog(
            onDismissRequest = { showSeekDialog = false },
            title = { Text("Double-Tap Seek Duration") },
            text = {
                Column {
                    seekOptions.forEach { secs ->
                        val isSelected = (doubleTapSeekPref.intValue == secs)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    doubleTapSeekPref.intValue = secs
                                    val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
                                    sp.edit().putInt("double_tap_seek_seconds", secs).apply()
                                    showSeekDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    doubleTapSeekPref.intValue = secs
                                    val sp = context.getSharedPreferences("player_settings", android.content.Context.MODE_PRIVATE)
                                    sp.edit().putInt("double_tap_seek_seconds", secs).apply()
                                    showSeekDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("$secs seconds")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSpeedDialog) {
        val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Default Playback Speed") },
            text = {
                Column {
                    speedOptions.forEach { spd ->
                        val isSelected = (defaultSpeed == spd)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { playbackPrefs.setDefaultSpeed(spd) }
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch { playbackPrefs.setDefaultSpeed(spd) }
                                    showSpeedDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${spd}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBatteryCapDialog) {
        val caps = listOf("360p", "480p", "720p", "1080p")
        AlertDialog(
            onDismissRequest = { showBatteryCapDialog = false },
            title = { Text("Battery Saver Resolution Cap") },
            text = {
                Column {
                    caps.forEach { cap ->
                        val isSelected = (batterySaverResolutionCap == cap)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setBatterySaverResolutionCap(cap)
                                    showBatteryCapDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setBatterySaverResolutionCap(cap)
                                    showBatteryCapDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                when (cap) {
                                    "360p" -> "360p (Maximum Battery Saving)"
                                    "480p" -> "480p (Recommended SD)"
                                    "720p" -> "720p (HD Balanced)"
                                    else -> "1080p (Uncapped)"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatteryCapDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBatteryThresholdDialog) {
        val thresholds = listOf(10, 15, 20, 25, 30)
        AlertDialog(
            onDismissRequest = { showBatteryThresholdDialog = false },
            title = { Text("Low Battery Threshold") },
            text = {
                Column {
                    thresholds.forEach { thresh ->
                        val isSelected = (batterySaverLowThreshold == thresh)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setBatterySaverLowThreshold(thresh)
                                    showBatteryThresholdDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setBatterySaverLowThreshold(thresh)
                                    showBatteryThresholdDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("$thresh% remaining battery")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatteryThresholdDialog = false }) { Text("Cancel") }
            }
        )
    }

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

    if (showLanguageDialog) {
        val languages = listOf(
            "en" to "English (US / UK)",
            "hi" to "हिंदी (Hindi)"
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("App Display Language") },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        val isSelected = (appDisplayLanguage == code)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppDisplayLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setAppDisplayLanguage(code)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
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
}

@Composable
private fun YouTubeSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
