package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    var isDnsExpanded by remember { mutableStateOf(true) }
    var isProvidersExpanded by remember { mutableStateOf(true) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSmartSkipExpanded by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    var isDataBackupExpanded by remember { mutableStateOf(false) }
    var isYtDlpExpanded by remember { mutableStateOf(false) }
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }
    var showFullSponsorBlockScreen by remember { mutableStateOf(false) }

    var showPasteImportDialog by remember { mutableStateOf(false) }
    var pasteJsonInput by remember { mutableStateOf("") }

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
                        Toast.makeText(context, "Profile & Watch Data saved successfully!", Toast.LENGTH_LONG).show()
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
                                "Imported ${summary.historyCount} history items, ${summary.likedCount} liked & ${summary.playlistCount} playlists!",
                                Toast.LENGTH_LONG
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

    LaunchedEffect(Unit) {
        com.example.extractor.YtDlpUpdateManager.refreshVersion(context)
    }

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
                        subtitle = "Enable adult and mature streaming options (Hides normal sources when active)",
                        checked = adultContentEnabled,
                        onCheckedChange = { viewModel.setAdultContentEnabled(it) }
                    )
                }
            }

            // 1B. SECURE DNS & DOH (BYPASS ISP BLOCKS)
            item {
                val isSecureDnsEnabled by viewModel.isSecureDnsEnabled.collectAsState()
                val selectedDnsProvider by viewModel.selectedDnsProvider.collectAsState()
                val customDnsUrl by viewModel.customDnsUrl.collectAsState()
                val dnsTestResult by viewModel.dnsTestResult.collectAsState()

                var customUrlInput by remember(customDnsUrl) { mutableStateOf(customDnsUrl) }

                ExpandableSettingsCard(
                    title = "Secure DNS (Bypass ISP Blocks)",
                    icon = Icons.Outlined.Dns,
                    isExpanded = isDnsExpanded,
                    onToggleExpand = { isDnsExpanded = !isDnsExpanded },
                    badgeText = if (isSecureDnsEnabled) selectedDnsProvider.displayName else "Disabled (ISP)"
                ) {
                    SettingsSwitchRow(
                        title = "Enable Secure DNS (DNS-over-HTTPS)",
                        subtitle = "Encrypts domain lookups over HTTPS to bypass ISP domain sinkholes and access blocked providers (e.g. Pornhub, XHamster, adult CDNs).",
                        checked = isSecureDnsEnabled,
                        onCheckedChange = { viewModel.setSecureDnsEnabled(it) }
                    )

                    AnimatedVisibility(visible = isSecureDnsEnabled) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            Text(
                                text = "Choose Secure DNS Provider",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Selecting an encrypted DNS server hides your requests from your local ISP and unblocks video sources.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                com.example.util.DnsProvider.values().forEach { provider ->
                                    val isSelected = (selectedDnsProvider == provider)
                                    Surface(
                                        onClick = { viewModel.setSelectedDnsProvider(provider) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { viewModel.setSelectedDnsProvider(provider) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = provider.displayName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (provider == com.example.util.DnsProvider.CLOUDFLARE) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.primary
                                                        ) {
                                                            Text(
                                                                text = "Recommended",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = provider.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedDnsProvider == com.example.util.DnsProvider.CUSTOM) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = customUrlInput,
                                    onValueChange = {
                                        customUrlInput = it
                                        viewModel.setCustomDnsUrl(it)
                                    },
                                    label = { Text("Custom DoH URL") },
                                    placeholder = { Text("https://dns.nextdns.io/doh") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { viewModel.setCustomDnsUrl(customUrlInput) }) {
                                            Icon(Icons.Default.Check, contentDescription = "Save")
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "DNS Resolution Diagnostic Test",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Test if your chosen DNS server can resolve blocked domains (e.g. pornhub.com) and measure latency.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { viewModel.runDnsDiagnosticTest("pornhub.com") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test DNS Resolution (pornhub.com)")
                            }

                            dnsTestResult?.let { result ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (result.isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                                contentDescription = null,
                                                tint = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = if (result.isSuccess) "DNS Resolution Successful!" else "DNS Resolution Failed",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Active Provider: ${result.providerName} | Latency: ${result.latencyMs} ms",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (result.isSuccess) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Resolved IP Addresses:\n${result.resolvedIps.joinToString("\n")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            result.errorMessage?.let { err ->
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Error: $err",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1B. SOURCE SELECTION & TOGGLES
            item {
                val enabledProviderIds by viewModel.enabledProviderIds.collectAsState()
                val adultEnabled by viewModel.adultContentEnabled.collectAsState()

                val availableSourcesList = remember(adultEnabled) {
                    if (adultEnabled) {
                        listOf(
                            Pair("pornhub", "Pornhub"),
                            Pair("eporner", "Eporner"),
                            Pair("xvideos", "XVideos"),
                            Pair("4tube", "4Tube"),
                            Pair("beeg", "Beeg"),
                            Pair("rule34video", "Rule34Video"),
                            Pair("redtube", "RedTube"),
                            Pair("xhamster", "xHamster"),
                            Pair("youporn", "YouPorn")
                        )
                    } else {
                        listOf(
                            Pair("youtube", "YouTube"),
                            Pair("archive_org", "Internet Archive"),
                            Pair("torrent", "Torrent Media"),
                            Pair("dailymotion", "Dailymotion"),
                            Pair("bilibili", "Bilibili"),
                            Pair("vimeo", "Vimeo"),
                            Pair("hotstar", "Hotstar")
                        )
                    }
                }

                ExpandableSettingsCard(
                    title = "Source Selection & Management",
                    icon = Icons.Outlined.Tune,
                    isExpanded = isProvidersExpanded,
                    onToggleExpand = { isProvidersExpanded = !isProvidersExpanded },
                    badgeText = "${enabledProviderIds.filter { it != "all" }.size} Enabled"
                ) {
                    Text(
                        text = if (adultEnabled) "Active 18+ Adult Sources" else "Active Normal Media Sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manually select which video sources are enabled or disabled.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    availableSourcesList.forEach { (pid, pName) ->
                        val isChecked = enabledProviderIds.contains(pid)
                        SettingsSwitchRow(
                            title = pName,
                            subtitle = if (isChecked) "Source enabled" else "Source disabled",
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleProviderEnabled(pid, it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            // 2. VEGA PROVIDERS & EXTENSIONS
            item {
                val installedVegaProviders by viewModel.installedVegaProviders.collectAsState()
                val availableVegaProviders by viewModel.availableVegaProviders.collectAsState()
                val isFetchingVegaProviders by viewModel.isFetchingVegaProviders.collectAsState()
                val vegaProviderError by viewModel.vegaProviderError.collectAsState()

                ExpandableSettingsCard(
                    title = "Providers",
                    icon = Icons.Outlined.Extension,
                    isExpanded = isProvidersExpanded,
                    onToggleExpand = { isProvidersExpanded = !isProvidersExpanded },
                    badgeText = "${installedVegaProviders.size} Installed"
                ) {
                    // SECTION: Installed Providers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Installed Providers",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "${installedVegaProviders.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (installedVegaProviders.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No Vega providers installed yet. Select from available providers below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            installedVegaProviders.forEach { prov ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Widgets,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = prov.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "VEGA",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = prov.isEnabled,
                                                onCheckedChange = { isChecked ->
                                                    viewModel.toggleVegaProvider(prov.id, isChecked)
                                                },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.uninstallVegaProvider(prov.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Uninstall ${prov.name}",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // SECTION: Available Providers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Available Providers",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Providers from server",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.fetchAvailableVegaProviders() },
                            modifier = Modifier.size(32.dp),
                            enabled = !isFetchingVegaProviders
                        ) {
                            if (isFetchingVegaProviders) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh available providers",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (vegaProviderError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = vegaProviderError ?: "Error loading providers",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.fetchAvailableVegaProviders() }
                                ) {
                                    Text("Retry", fontSize = 12.sp)
                                }
                            }
                        }
                    } else if (availableVegaProviders.isEmpty() && isFetchingVegaProviders) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Discovering providers...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (availableVegaProviders.isEmpty()) {
                        Text(
                            text = "No providers available on server at this moment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableVegaProviders.forEach { rawId ->
                                val cleanId = rawId.trim().lowercase()
                                val isInstalled = installedVegaProviders.any { it.id.equals(cleanId, ignoreCase = true) }
                                val formattedName = com.example.vega.VegaProviderClient.formatProviderDisplayName(cleanId)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = formattedName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "ID: $cleanId",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (isInstalled) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Installed",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.installVegaProvider(cleanId) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Install",
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
                }
            }

            // 3. PLAYBACK & PLAYER PREFERENCES
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

            // 5. PROFILE DATA & BACKUP (IMPORT / EXPORT)
            item {
                ExpandableSettingsCard(
                    title = "Profile Data & Backup",
                    icon = Icons.Outlined.Backup,
                    isExpanded = isDataBackupExpanded,
                    onToggleExpand = { isDataBackupExpanded = !isDataBackupExpanded },
                    badgeText = "Import/Export"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Export or import your watch history, playlists, liked videos, watch later, blocklists, and AI recommendation taste profiles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                    createDocumentLauncher.launch("butterfly_profile_backup_$timestamp.json")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { openDocumentLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val jsonStr = viewModel.exportUserDataJson()
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Butterfly User Profile", jsonStr)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Profile JSON copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy JSON", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showPasteImportDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste JSON", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 6. YT-DLP CORE ENGINE
            item {
                val installedVer by com.example.extractor.YtDlpUpdateManager.installedVersion.collectAsState()
                val wrapperVer by com.example.extractor.YtDlpUpdateManager.wrapperVersion.collectAsState()
                val engineVer by com.example.extractor.YtDlpUpdateManager.engineVersion.collectAsState()
                val remoteVer by com.example.extractor.YtDlpUpdateManager.latestRemoteVersion.collectAsState()
                val updateState by com.example.extractor.YtDlpUpdateManager.updateState.collectAsState()
                val isAutoUpdate by com.example.extractor.YtDlpUpdateManager.isAutoUpdateEnabled.collectAsState()

                ExpandableSettingsCard(
                    title = "yt-dlp Core Engine",
                    icon = Icons.Outlined.SystemUpdate,
                    isExpanded = isYtDlpExpanded,
                    onToggleExpand = { isYtDlpExpanded = !isYtDlpExpanded },
                    badgeText = "v$wrapperVer"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Powered by dev.ffmpegkit-maintained:yt-dlp-android (yt-dlp core engine). Serves as the universal extraction engine and YouTube fallback resolver.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Current Version & Refresh
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Android wrapper: $wrapperVer",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Installed engine: ${engineVer ?: "2024.12.13"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!remoteVer.isNullOrBlank() && remoteVer != "Checking...") {
                                        Text(
                                            text = "Latest upstream release: $remoteVer",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            com.example.extractor.YtDlpUpdateManager.refreshVersion(context)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Version Info",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Auto-update switch
                        SettingsSwitchRow(
                            title = "Auto-Check Updates on Launch",
                            subtitle = "Silently check and refresh yt-dlp core engine on application startup",
                            checked = isAutoUpdate,
                            onCheckedChange = { com.example.extractor.YtDlpUpdateManager.setAutoUpdateEnabled(it) }
                        )

                        // Update state feedback banner
                        when (val state = updateState) {
                            is com.example.extractor.YtDlpUpdateManager.UpdateState.Updating -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Checking and updating yt-dlp engine...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            is com.example.extractor.YtDlpUpdateManager.UpdateState.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            is com.example.extractor.YtDlpUpdateManager.UpdateState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = state.errorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }

                        // Manual Update Button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    com.example.extractor.YtDlpUpdateManager.checkForUpdates(context, isManual = true)
                                }
                            },
                            enabled = updateState !is com.example.extractor.YtDlpUpdateManager.UpdateState.Updating,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (updateState is com.example.extractor.YtDlpUpdateManager.UpdateState.Updating) "Updating yt-dlp..." else "Update yt-dlp Engine",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Action to test extractors
                        OutlinedButton(
                            onClick = {
                                isDiagnosticsExpanded = true
                                diagnosticsList.filter { !it.isAdult || adultContentEnabled }.forEach { diag ->
                                    testProvider(diag)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Test All Platform Extractors", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 6. SOURCE DIAGNOSTICS
            item {
                ExpandableSettingsCard(
                    title = "Source Diagnostics",
                    icon = Icons.Outlined.CheckCircle,
                    isExpanded = isDiagnosticsExpanded,
                    onToggleExpand = { isDiagnosticsExpanded = !isDiagnosticsExpanded },
                    badgeText = "15 Sources"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Test and inspect status of all platform extractors.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    diagnosticsList.filter { !it.isAdult || adultContentEnabled }.forEach { diag ->
                                        testProvider(diag)
                                    }
                                },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Test All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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

    if (showPasteImportDialog) {
        AlertDialog(
            onDismissRequest = { showPasteImportDialog = false },
            title = { Text("Paste Profile JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste your exported JSON backup data below to restore watch history, playlists, liked videos, and AI recommendation taste profiles.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pasteJsonInput,
                        onValueChange = { pasteJsonInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("Paste JSON string here...") },
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = pasteJsonInput.trim()
                        if (input.isNotBlank()) {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val summary = viewModel.importUserDataJson(input)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        showPasteImportDialog = false
                                        pasteJsonInput = ""
                                        Toast.makeText(
                                            context,
                                            "Successfully imported ${summary.historyCount} history items & ${summary.playlistCount} playlists!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Invalid JSON format: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Import & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteImportDialog = false }) {
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
