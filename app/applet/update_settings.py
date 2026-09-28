with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'r') as f:
    text = f.read()

# 1. State variables
target_state = '    val appDisplayLanguage by viewModel.appDisplayLanguage.collectAsState()'
new_states = '''    val prioritizeVideoQuality by playbackPrefs.prioritizeVideoQuality.collectAsState()
    val disableDrcAudio by playbackPrefs.disableDrcAudio.collectAsState()
    val disableHdrVideo by playbackPrefs.disableHdrVideo.collectAsState()
    val forceAvcCodec by playbackPrefs.forceAvcCodec.collectAsState()
    val videoCodecPreference by playbackPrefs.videoCodecPreference.collectAsState()
    val decoderMode by playbackPrefs.decoderMode.collectAsState()
    val forceOriginalAudioLanguage by playbackPrefs.forceOriginalAudioLanguage.collectAsState()
    val customPlaybackSpeedMenu by playbackPrefs.customPlaybackSpeedMenu.collectAsState()
    val restoreOldPlaybackSpeedMenu by playbackPrefs.restoreOldPlaybackSpeedMenu.collectAsState()
    val customPlaybackSpeeds by playbackPrefs.customPlaybackSpeeds.collectAsState()
    val tapAndHoldSpeed by playbackPrefs.tapAndHoldSpeed.collectAsState()
    val rememberPlaybackSpeed by playbackPrefs.rememberPlaybackSpeed.collectAsState()
    val speedChangeNotifications by playbackPrefs.speedChangeNotifications.collectAsState()

    var showCodecPreferenceDialog by remember { mutableStateOf(false) }
    var showDecoderModeDialog by remember { mutableStateOf(false) }
    var showTapHoldSpeedDialog by remember { mutableStateOf(false) }
    var showCustomSpeedsDialog by remember { mutableStateOf(false) }
    var customSpeedsInputText by remember { mutableStateOf(customPlaybackSpeeds) }

    val isAutoUpdateDaily by com.example.util.AppEngineDiagnosticManager.isAutoUpdateEnabled.collectAsState()
    val isSilentDownloadEnabled by com.example.util.AppEngineDiagnosticManager.isSilentDownloadEnabled.collectAsState()
    val lastAutoUpdateTimestamp by com.example.util.AppEngineDiagnosticManager.lastAutoUpdateTimestamp.collectAsState()

    val appDisplayLanguage by viewModel.appDisplayLanguage.collectAsState()'''

if target_state in text and 'prioritizeVideoQuality' not in text:
    text = text.replace(target_state, new_states, 1)
    print("Added state variables")

# 2. Update Root Categories List (single bold title, no second subtitle line)
old_root_start = 'if (currentCategory == null) {'
old_root_end = '// SUB-SCREEN DETAIL PAGES'

root_idx = text.find(old_root_start)
sub_idx = text.find(old_root_end)

if root_idx != -1 and sub_idx != -1:
    new_root_block = '''if (currentCategory == null) {
                val rootCategories = listOf(
                    SettingsCategory.GENERAL,
                    SettingsCategory.PLAYBACK,
                    SettingsCategory.ACCOUNTS_SOURCES,
                    SettingsCategory.PROVIDERS,
                    SettingsCategory.SUBTITLE_PROVIDERS,
                    SettingsCategory.SMART_SKIP,
                    SettingsCategory.HISTORY_PRIVACY,
                    SettingsCategory.BACKUP_RESTORE,
                    SettingsCategory.ADDITIONAL_SETTINGS,
                    SettingsCategory.ABOUT
                )
                // ROOT YOUTUBE-STYLE SETTINGS LIST (Single clean bold title only, no secondary subtitle)
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
                '''
    text = text[:root_idx] + new_root_block + text[sub_idx:]
    print("Replaced root categories list without secondary subtitle text")

# 3. Combine General & Language
gen_start = text.find('SettingsCategory.GENERAL -> {')
playback_start = text.find('SettingsCategory.PLAYBACK -> {')

if gen_start != -1 and playback_start != -1:
    new_general_block = '''SettingsCategory.GENERAL,
                    SettingsCategory.LANGUAGE -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section 1: DISPLAY LANGUAGE & UNIVERSAL TRANSLATION
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "LANGUAGE & TRANSLATION",
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

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Auto-translate Metadata",
                                            subtitle = "Automatically translate foreign titles & metadata without altering originals",
                                            checked = autoTranslateMetadata,
                                            onCheckedChange = { viewModel.setAutoTranslateMetadata(it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Show Original Native Titles",
                                            subtitle = "Always show untouched original titles (Japanese, Korean, Chinese, Arabic) alongside translations",
                                            checked = showOriginalTitles,
                                            onCheckedChange = { viewModel.setShowOriginalTitles(it) }
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Outlined.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Universal Translation Engine", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "• Foreign titles auto-translate to English/Hindi.\\n• Native Hindi titles are kept untouched.\\n• Instant zero-latency caching via local DB.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FilledTonalButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val testOriginal = "進撃の巨人 The Final Season 完結編"
                                                            val result = com.example.util.UniversalTranslator.translateTitle(testOriginal)
                                                            Toast.makeText(context, "Original: $testOriginal\\nEN: ${result.translatedEN}", Toast.LENGTH_LONG).show()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Test Translation Engine", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Section 2: APPEARANCE & THEME
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "APPEARANCE & THEME",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeDetailRow(
                                            title = "Theme",
                                            subtitle = if (themeMode == com.example.ui.ThemeMode.LIGHT) "Light Mode" else "AMOLED Dark",
                                            onClick = { showThemeDialog = true }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeDetailRow(
                                            title = "Secondary Accent Color",
                                            subtitle = accentColor.label,
                                            onClick = { showAccentDialog = true }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        val isOpeningAnimationEnabled by viewModel.isOpeningAnimationEnabled.collectAsState()
                                        YouTubeSwitchRow(
                                            title = "Butterfly Opening Animation",
                                            subtitle = "Cinematic animated launch intro on app startup",
                                            checked = isOpeningAnimationEnabled,
                                            onCheckedChange = { viewModel.setOpeningAnimationEnabled(it) }
                                        )

                                        if (isOpeningAnimationEnabled) {
                                            val openingAnimationStyle by viewModel.openingAnimationStyle.collectAsState()
                                            YouTubeDetailRow(
                                                title = "Opening Animation Style",
                                                subtitle = "${openingAnimationStyle.title} • ${openingAnimationStyle.subtitle}",
                                                onClick = { showAnimationDialog = true }
                                            )
                                        }

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

                    '''
    text = text[:gen_start] + new_general_block + text[playback_start:]
    print("Combined General & Language settings")

# 4. Replace PLAYBACK Category
pb_start = text.find('SettingsCategory.PLAYBACK -> {')
adult_start = text.find('SettingsCategory.ADULT_18 -> {')

if pb_start != -1 and adult_start != -1:
    new_playback_block = '''SettingsCategory.PLAYBACK -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section 1: VIDEO QUALITY & CODEC EFFICIENCY (From Image 2 & User Request 2)
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "VIDEO & CODEC EFFICIENCY",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "Prioritize video quality",
                                            subtitle = "Prioritizes the highest quality video over codec efficiency",
                                            checked = prioritizeVideoQuality,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setPrioritizeVideoQuality(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Disable DRC audio",
                                            subtitle = "Disables Dynamic Range Compression for raw audio dynamics",
                                            checked = disableDrcAudio,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setDisableDrcAudio(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Disable HDR video",
                                            subtitle = "Plays standard SDR streams to save tone-mapping GPU load & battery",
                                            checked = disableHdrVideo,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setDisableHdrVideo(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Force AVC (H.264) codec",
                                            subtitle = "Lighter decoder, maximum hardware compatibility and lower battery drain",
                                            checked = forceAvcCodec,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setForceAvcCodec(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        val codecDisplay = when (videoCodecPreference) {
                                            "AVC" -> "Force AVC / H.264 (Light & Fast)"
                                            "HEVC" -> "Prefer HEVC / H.265 (High Efficiency)"
                                            "VP9" -> "Prefer VP9 (YouTube Native)"
                                            "AV1" -> "Prefer AV1 (Next-Gen)"
                                            else -> "Auto (Best Hardware Match)"
                                        }
                                        YouTubeDetailRow(
                                            title = "Video Codec Preference",
                                            subtitle = codecDisplay,
                                            onClick = { showCodecPreferenceDialog = true }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        val decoderDisplay = when (decoderMode) {
                                            "SOFTWARE" -> "Software Decoder (Safe Mode / Low RAM)"
                                            "TUNNELING" -> "MediaCodec Tunneling (Ultra-Low Latency)"
                                            else -> "Hardware Accelerated (MediaCodec)"
                                        }
                                        YouTubeDetailRow(
                                            title = "Hardware Decoder Engine",
                                            subtitle = decoderDisplay,
                                            onClick = { showDecoderModeDialog = true }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Force original audio language",
                                            subtitle = "Selects original native audio track over dubbed languages",
                                            checked = forceOriginalAudioLanguage,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setForceOriginalAudioLanguage(it) } }
                                        )
                                    }
                                }
                            }

                            // Section 2: PLAYBACK SPEED & GESTURE CONTROLS (From Image 2)
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "PLAYBACK SPEED & GESTURES",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeSwitchRow(
                                            title = "Custom playback speed menu",
                                            subtitle = "Shows granular custom speed options in the player speed sheet",
                                            checked = customPlaybackSpeedMenu,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setCustomPlaybackSpeedMenu(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Restore old playback speed menu",
                                            subtitle = "Uses classic speed selector list",
                                            checked = restoreOldPlaybackSpeedMenu,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setRestoreOldPlaybackSpeedMenu(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeDetailRow(
                                            title = "Custom playback speeds",
                                            subtitle = customPlaybackSpeeds,
                                            onClick = {
                                                customSpeedsInputText = customPlaybackSpeeds
                                                showCustomSpeedsDialog = true
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeDetailRow(
                                            title = "Tap and hold playback speed",
                                            subtitle = if (tapAndHoldSpeed <= 0f) "Disabled (0x)" else "Set to ${tapAndHoldSpeed}x (Hold screen)",
                                            onClick = { showTapHoldSpeedDialog = true }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeDetailRow(
                                            title = "Default playback speed",
                                            subtitle = "${defaultSpeed}x",
                                            onClick = { showSpeedDialog = true }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Remember playback speed",
                                            subtitle = "Applies your manual speed selection to all future videos",
                                            checked = rememberPlaybackSpeed,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setRememberPlaybackSpeed(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Speed change notifications",
                                            subtitle = "Shows a notification when a new default playback speed is saved",
                                            checked = speedChangeNotifications,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setSpeedChangeNotifications(it) } }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Disable Speed Adjustment for Music",
                                            subtitle = "Automatically resets playback speed to 1.0x on music streams",
                                            checked = disableSpeedForMusic,
                                            onCheckedChange = { coroutineScope.launch { playbackPrefs.setDisableSpeedForMusic(it) } }
                                        )
                                    }
                                }
                            }

                            // Section 3: RESOLUTION & SEEKING
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "RESOLUTION & SEEKING",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )

                                        YouTubeDetailRow(
                                            title = "Default Video Resolution",
                                            subtitle = defaultResolutionPref.value,
                                            onClick = { showResolutionDialog = true }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeDetailRow(
                                            title = "Double-Tap to Seek",
                                            subtitle = "${doubleTapSeekPref.intValue} seconds",
                                            onClick = { showSeekDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    '''
    text = text[:pb_start] + new_playback_block + text[adult_start:]
    print("Replaced Playback settings block")

# 5. Add Background Daily Update Card to DIAGNOSTICS
diag_target = '// Section 1: LIVE COMPONENT & PROVIDER TESTS'
diag_replacement = '''// Section 0: DAILY AUTOMATED BACKGROUND UPDATER (From User Request 4)
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
                                                    text = "AUTOMATED BACKGROUND UPDATES",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Daily auto-checks GitHub releases and auto-downloads repository & engine updates in background",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        YouTubeSwitchRow(
                                            title = "Daily Background Auto-Update",
                                            subtitle = "Automatically check for new GitHub releases & extractor engine updates once a day",
                                            checked = isAutoUpdateDaily,
                                            onCheckedChange = { com.example.util.AppEngineDiagnosticManager.setAutoUpdateEnabled(context, it) }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        YouTubeSwitchRow(
                                            title = "Silent Auto-Download & Install",
                                            subtitle = "Silently download and update all 19+ engines and extractors when updates are found",
                                            checked = isSilentDownloadEnabled,
                                            onCheckedChange = { com.example.util.AppEngineDiagnosticManager.setSilentDownloadEnabled(context, it) }
                                        )

                                        val syncText = if (lastAutoUpdateTimestamp > 0L) {
                                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                            "Last background check: " + sdf.format(java.util.Date(lastAutoUpdateTimestamp))
                                        } else "Next background check scheduled within 24h"

                                        Text(
                                            text = syncText,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Section 1: LIVE COMPONENT & PROVIDER TESTS'''

if diag_target in text and 'AUTOMATED BACKGROUND UPDATES' not in text:
    text = text.replace(diag_target, diag_replacement, 1)
    print("Added Background Updater Card to Diagnostics")

# 6. Add Dialogs at bottom of SettingsScreen
last_dialog_target = 'if (showClearSearchDialog) {'
new_dialogs = '''    if (showCodecPreferenceDialog) {
        val codecOptions = listOf(
            "AUTO" to "Auto (Best Hardware Match)",
            "AVC" to "Force AVC / H.264 (Light, Battery Saver & Most Compatible)",
            "HEVC" to "Prefer HEVC / H.265 (High Efficiency 4K/1080p)",
            "VP9" to "Prefer VP9 (YouTube Native)",
            "AV1" to "Prefer AV1 (Next-Gen Ultra Compression)"
        )
        AlertDialog(
            onDismissRequest = { showCodecPreferenceDialog = false },
            title = { Text("Video Codec Preference") },
            text = {
                LazyColumn {
                    items(codecOptions) { (key, label) ->
                        val isSelected = (videoCodecPreference == key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { playbackPrefs.setVideoCodecPreference(key) }
                                    showCodecPreferenceDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch { playbackPrefs.setVideoCodecPreference(key) }
                                    showCodecPreferenceDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCodecPreferenceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDecoderModeDialog) {
        val decoderOptions = listOf(
            "HARDWARE" to "Hardware Accelerated (MediaCodec - Recommended)",
            "SOFTWARE" to "Software Decoder (Safe Mode / Low RAM devices)",
            "TUNNELING" to "MediaCodec Tunneling (Ultra-Low Latency & High FPS)"
        )
        AlertDialog(
            onDismissRequest = { showDecoderModeDialog = false },
            title = { Text("Hardware Decoder Engine") },
            text = {
                Column {
                    decoderOptions.forEach { (key, label) ->
                        val isSelected = (decoderMode == key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { playbackPrefs.setDecoderMode(key) }
                                    showDecoderModeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch { playbackPrefs.setDecoderMode(key) }
                                    showDecoderModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDecoderModeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTapHoldSpeedDialog) {
        val tapHoldOptions = listOf(0.0f to "Disabled (0x)", 1.5f to "1.5x Multiplier", 2.0f to "2.0x Multiplier (Standard)", 2.5f to "2.5x Multiplier", 3.0f to "3.0x Multiplier")
        AlertDialog(
            onDismissRequest = { showTapHoldSpeedDialog = false },
            title = { Text("Tap and Hold Playback Speed") },
            text = {
                Column {
                    tapHoldOptions.forEach { (spd, label) ->
                        val isSelected = (tapAndHoldSpeed == spd)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { playbackPrefs.setTapAndHoldSpeed(spd) }
                                    showTapHoldSpeedDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch { playbackPrefs.setTapAndHoldSpeed(spd) }
                                    showTapHoldSpeedDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTapHoldSpeedDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomSpeedsDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSpeedsDialog = false },
            title = { Text("Custom Playback Speeds") },
            text = {
                Column {
                    Text("Enter comma-separated speed multipliers:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSpeedsInputText,
                        onValueChange = { customSpeedsInputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customSpeedsInputText.isNotBlank()) {
                            coroutineScope.launch { playbackPrefs.setCustomPlaybackSpeeds(customSpeedsInputText.trim()) }
                        }
                        showCustomSpeedsDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSpeedsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearSearchDialog) {'''

if last_dialog_target in text and 'showCodecPreferenceDialog' not in text:
    text = text.replace(last_dialog_target, new_dialogs, 1)
    print("Added Codec, Decoder, Speed dialogs")

with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'w') as f:
    f.write(text)

print("All modifications in SettingsScreen.kt written successfully!")
