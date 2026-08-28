package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
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
    PLAYBACK("Playback", "Resolution, speed & seek gestures", Icons.Outlined.PlayCircle),
    PROVIDERS("Content Sources", "Manage YouTube, Dailymotion, BitTorrent & more", Icons.Outlined.Source),
    VEGA("Vega Movies & Series", "Movie extensions, anime providers & add-ons", Icons.Outlined.Movie),
    ADULT_18("18+ Content", "Adult content mode & mature sources", Icons.Outlined.Explicit),
    SMART_SKIP("Smart Skip & SponsorBlock", "Auto-skip sponsors, intros & previews", Icons.Outlined.FastForward),
    HISTORY_PRIVACY("History & Privacy", "Watch history, search cache & blocked channels", Icons.Outlined.History),
    BACKUP_RESTORE("Backup & Restore", "Export/import profile data & Google Drive sync", Icons.Outlined.CloudUpload),
    ABOUT("About Butterfly", "Version, legal & open-source details", Icons.Outlined.Info),
    ADDITIONAL_SETTINGS("Additional Settings", "DNS & Network, API keys, Diagnostics & Battery Saver", Icons.Outlined.Tune),

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
    var pasteJsonInput by remember { mutableStateOf("") }

    // Dialog state for selections
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showBatteryCapDialog by remember { mutableStateOf(false) }
    var showBatteryThresholdDialog by remember { mutableStateOf(false) }

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

    BackHandler(enabled = currentCategory != null) {
        if (parentCategory != null) {
            currentCategory = parentCategory
            parentCategory = null
        } else {
            currentCategory = null
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
                    SettingsCategory.PROVIDERS,
                    SettingsCategory.VEGA,
                    SettingsCategory.ADULT_18,
                    SettingsCategory.SMART_SKIP,
                    SettingsCategory.HISTORY_PRIVACY,
                    SettingsCategory.BACKUP_RESTORE,
                    SettingsCategory.ABOUT,
                    SettingsCategory.ADDITIONAL_SETTINGS
                )
                // ROOT YOUTUBE-STYLE SETTINGS LIST
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(rootCategories) { category ->
                        val dynamicSubtitle = when (category) {
                            SettingsCategory.GENERAL -> if (themeMode == com.example.ui.ThemeMode.LIGHT) "Light Theme" else "AMOLED Dark"
                            SettingsCategory.BATTERY_SAVER -> if (isPowerSaveActive) "Active ($batteryLevel% • Eco Power Mode)" else "Optimizations, RAM & battery saver ($batteryLevel%)"
                            SettingsCategory.PLAYBACK -> "${defaultResolutionPref.value} • ${doubleTapSeekPref.intValue}s seek"
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
                                YouTubeSwitchRow(
                                    title = "Thumbnail Source Tags",
                                    subtitle = "Show provider badges (e.g. YouTube, Vimeo, 18+) on video cards",
                                    checked = showThumbnailTags,
                                    onCheckedChange = { viewModel.setShowThumbnailTags(it) }
                                )
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
                                    "pornhub" to "Pornhub",
                                    "xvideos" to "XVideos",
                                    "eporner" to "Eporner",
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
                            }
                        }
                    }

                    SettingsCategory.PROVIDERS -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            val normalProviders = listOf(
                                "youtube" to "YouTube",
                                "archive_org" to "Internet Archive",
                                "dailymotion" to "Dailymotion",
                                "bilibili" to "Bilibili",
                                "vimeo" to "Vimeo",
                                "hotstar" to "Hotstar / Jio",
                                "torrent" to "BitTorrent (P2P)"
                            )
                            items(normalProviders) { (id, name) ->
                                val isEnabled = enabledProviderIds.contains(id)
                                YouTubeSwitchRow(
                                    title = name,
                                    subtitle = "Streams from $name platform",
                                    checked = isEnabled,
                                    onCheckedChange = { viewModel.toggleProviderEnabled(id) }
                                )
                            }
                        }
                    }

                    SettingsCategory.VEGA -> {
                        val installedVega by viewModel.installedVegaProviders.collectAsState()
                        val availableVega by viewModel.availableVegaProviders.collectAsState()
                        val isFetching by viewModel.isFetchingVegaProviders.collectAsState()
                        val currentServerUrl by viewModel.vegaServerUrl.collectAsState()
                        val healthMap by viewModel.providerHealthMap.collectAsState()
                        val isTestingHealth by viewModel.isTestingVegaHealth.collectAsState()

                        var serverUrlInput by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // 1. Server Settings Card
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
                                        Text(
                                            text = "VEGA MEDIASERVER CONFIGURATION",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = serverUrlInput,
                                            onValueChange = { serverUrlInput = it },
                                            label = { Text("Server Host URL") },
                                            placeholder = { Text("https://butterfly-mediaserver-1.onrender.com") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = { viewModel.updateVegaServerUrl(serverUrlInput) }
                                            ) {
                                                Text("Save & Connect")
                                            }
                                        }
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
                                    TextButton(
                                        onClick = { viewModel.testVegaProvidersHealth() },
                                        enabled = !isTestingHealth
                                    ) {
                                        if (isTestingHealth) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Testing...")
                                        } else {
                                            Text("Test Source Health")
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

                                    YouTubeSwitchRow(
                                        title = vp.name,
                                        subtitle = subtitleText,
                                        checked = vp.isEnabled,
                                        onCheckedChange = { viewModel.toggleVegaProvider(vp.id, it) }
                                    )
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

                    SettingsCategory.INTEGRATIONS -> {
                        var tmdbKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getTmdbApiKey()) }
                        var subdlKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getSubdlApiKey()) }
                        var openSubKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getOpenSubtitlesApiKey()) }
                        var torrentioUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getTorrentioBaseUrl()) }
                        var vegaUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getVegaServerUrl()) }
                        var debridKeyInput by remember { mutableStateOf(com.example.util.AppConfig.getDebridApiKey()) }
                        var poTokenServerUrlInput by remember { mutableStateOf(com.example.util.AppConfig.getPoTokenServerUrl()) }
                        var poTokenInput by remember { mutableStateOf(com.example.util.AppConfig.getCustomPoToken()) }

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

                            // Torrentio, Vega & Debrid Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "TORRENTIO, VEGA & DEBRID RESOLVERS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Configure custom Torrentio endpoints, Vega stream mirrors, or Stremio Real-Debrid / AllDebrid manifest tokens.\nTip: Users with Real-Debrid can set URL to https://torrentio.strem.fun/realdebrid=YOURAPIKEY or enter API key below.",
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
                                            value = vegaUrlInput,
                                            onValueChange = { vegaUrlInput = it },
                                            label = { Text("Vega Server / Add-on URL") },
                                            placeholder = { Text("https://vega.strem.fun") },
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
                                                    com.example.util.AppConfig.setVegaServerUrl(context, com.example.util.AppConfig.DEFAULT_VEGA_SERVER_URL)
                                                    torrentioUrlInput = com.example.util.AppConfig.DEFAULT_TORRENTIO_BASE_URL
                                                    vegaUrlInput = com.example.util.AppConfig.DEFAULT_VEGA_SERVER_URL
                                                    Toast.makeText(context, "Reset URLs to defaults", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Reset Defaults")
                                            }
                                            Button(
                                                onClick = {
                                                    com.example.util.AppConfig.setTorrentioBaseUrl(context, torrentioUrlInput)
                                                    com.example.util.AppConfig.setVegaServerUrl(context, vegaUrlInput)
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
                        val googleAccount by com.example.util.GoogleDriveSyncManager.accountState.collectAsState()
                        val syncStatus by com.example.util.GoogleDriveSyncManager.syncStatus.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                Text(
                                    text = "GOOGLE DRIVE CLOUD SYNC",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = if (googleAccount != null) "Backup to Google Drive" else "Connect Google Drive Account",
                                    subtitle = if (googleAccount != null) "Account: ${googleAccount?.email} • Status: $syncStatus" else "Sign in to back up history, bookmarks & playlists to cloud",
                                    onClick = {
                                        if (googleAccount != null) {
                                            coroutineScope.launch {
                                                val json = viewModel.exportUserDataJson()
                                                com.example.util.GoogleDriveSyncManager.backupToGoogleDrive(context, json)
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                com.example.util.GoogleDriveSyncManager.signInWithCredentialManager(context)
                                            }
                                        }
                                    }
                                )
                            }
                            if (googleAccount != null) {
                                item {
                                    YouTubeDetailRow(
                                        title = "Restore from Google Drive",
                                        subtitle = "Download and merge latest backup from your Google Drive",
                                        onClick = {
                                            coroutineScope.launch {
                                                val restored = viewModel.restoreGoogleDriveBackup()
                                                if (restored) {
                                                    Toast.makeText(context, "Google Drive data successfully restored!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "No backup file found or cloud error", Toast.LENGTH_SHORT).show()
                                                }
                                            }
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
                        val summaryText by com.example.util.AppEngineDiagnosticManager.overallDiagnosticSummary.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header Summary Card
                            item {
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
                                                    text = "App Repositories & Engine Diagnostics",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
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
                                                enabled = !isGlobalChecking,
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
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Add Custom Repo", fontSize = 13.sp)
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
                                    text = "Version 2.4.0 (Stable)",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "A modern, lightweight multimedia streaming and playback client engineered with Jetpack Compose & Media3 ExoPlayer.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
