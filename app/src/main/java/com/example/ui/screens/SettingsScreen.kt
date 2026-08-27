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
    ADULT_18("18+ Content", "Adult content mode & mature sources", Icons.Outlined.Explicit),
    PROVIDERS("Content Sources", "Manage YouTube, Dailymotion, BitTorrent & more", Icons.Outlined.Source),
    VEGA("Vega Movies & Series", "Movie extensions, anime providers & add-ons", Icons.Outlined.Movie),
    SMART_SKIP("Smart Skip & SponsorBlock", "Auto-skip sponsors, intros & previews", Icons.Outlined.FastForward),
    HISTORY_PRIVACY("History & Privacy", "Watch history, search cache & blocked channels", Icons.Outlined.History),
    DNS_NETWORK("DNS & Network", "Secure DNS-over-HTTPS & ISP unblocking", Icons.Outlined.Dns),
    BACKUP_RESTORE("Backup & Restore", "Export/import profile data & Google Drive sync", Icons.Outlined.CloudUpload),
    DIAGNOSTICS("Diagnostics & Tests", "yt-dlp engine status & provider health test", Icons.Outlined.BugReport),
    ABOUT("About Butterfly", "Version, legal & open-source details", Icons.Outlined.Info)
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

    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val enabledProviderIds by viewModel.enabledProviderIds.collectAsState()

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
        currentCategory = null
    }

    LaunchedEffect(Unit) {
        com.example.extractor.YtDlpUpdateManager.refreshVersion(context)
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
                        if (currentCategory != null) {
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
                // ROOT YOUTUBE-STYLE SETTINGS LIST
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(SettingsCategory.values()) { category ->
                        val dynamicSubtitle = when (category) {
                            SettingsCategory.GENERAL -> if (themeMode == com.example.ui.ThemeMode.LIGHT) "Light Theme" else "AMOLED Dark"
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
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.runDnsDiagnosticTest("pornhub.com") },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    ) {
                                        Text("Test DNS Resolution")
                                    }
                                    if (dnsTestResult != null) {
                                        val res = dnsTestResult!!
                                        Text(
                                            text = if (res.isSuccess) "Resolved in ${res.latencyMs}ms via ${res.providerName}: ${res.resolvedIps.take(3).joinToString()}" else "Resolution failed: ${res.errorMessage}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (res.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.BACKUP_RESTORE -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
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
                        val engineVer by com.example.extractor.YtDlpUpdateManager.engineVersion.collectAsState()
                        val latestRemoteVer by com.example.extractor.YtDlpUpdateManager.latestRemoteVersion.collectAsState()
                        val updateState by com.example.extractor.YtDlpUpdateManager.updateState.collectAsState()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                YouTubeDetailRow(
                                    title = "yt-dlp Core Engine Status",
                                    subtitle = "Active Engine: ${engineVer ?: "Checking..."} | Upstream Latest: ${latestRemoteVer ?: "Checking..."}",
                                    onClick = {
                                        coroutineScope.launch {
                                            com.example.extractor.YtDlpUpdateManager.refreshVersion(context)
                                            Toast.makeText(context, "Engine: ${com.example.extractor.YtDlpUpdateManager.engineVersion.value}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            item {
                                YouTubeDetailRow(
                                    title = "Update yt-dlp Engine Now",
                                    subtitle = when (updateState) {
                                        is com.example.extractor.YtDlpUpdateManager.UpdateState.Checking -> "Downloading latest yt-dlp binary from GitHub..."
                                        is com.example.extractor.YtDlpUpdateManager.UpdateState.Success -> (updateState as com.example.extractor.YtDlpUpdateManager.UpdateState.Success).message
                                        is com.example.extractor.YtDlpUpdateManager.UpdateState.Error -> "Error: ${(updateState as com.example.extractor.YtDlpUpdateManager.UpdateState.Error).errorMessage}"
                                        else -> "Fetch and install the latest official yt-dlp release directly into internal app storage"
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Updating yt-dlp engine...", Toast.LENGTH_SHORT).show()
                                            com.example.extractor.YtDlpUpdateManager.updateYtDlpEngine(context) { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )
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
