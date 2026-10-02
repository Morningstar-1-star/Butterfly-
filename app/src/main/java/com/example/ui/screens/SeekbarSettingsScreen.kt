package com.example.ui.screens

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.VideoHeatmap
import com.example.ui.player.YouTubePreciseSeekBar
import com.example.util.SeekbarPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekbarSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val seekbarPrefs = remember { SeekbarPreferences.getInstance(context) }

    val disablePreciseSeeking by seekbarPrefs.disablePreciseSeekingGesture.collectAsState()
    val enableCustomColor by seekbarPrefs.enableCustomSeekbarColor.collectAsState()
    val seekbarColorHex by seekbarPrefs.seekbarColorHex.collectAsState()
    val seekbarAccentColorHex by seekbarPrefs.seekbarAccentColorHex.collectAsState()
    val enableGradientProgress by seekbarPrefs.enableGradientProgress.collectAsState()
    val enableFullscreenLargeSeekbar by seekbarPrefs.enableFullscreenLargeSeekbar.collectAsState()
    val showHeatmapGraph by seekbarPrefs.showHeatmapGraph.collectAsState()
    val showChapterMarkers by seekbarPrefs.showChapterMarkers.collectAsState()
    val seekHapticsEnabled by seekbarPrefs.seekHapticsEnabled.collectAsState()
    val doubleTapSeekIntervalSecs by seekbarPrefs.doubleTapSeekIntervalSecs.collectAsState()

    val enableLivestreamDvr by seekbarPrefs.enableLivestreamDvr.collectAsState()
    val expandLivestreamDvrDuration by seekbarPrefs.expandLivestreamDvrDuration.collectAsState()
    val enableSlideToSeek by seekbarPrefs.enableSlideToSeek.collectAsState()
    val enableTapToSeek by seekbarPrefs.enableTapToSeek.collectAsState()
    val hidePlayerSeekbar by seekbarPrefs.hideVideoPlayerSeekbar.collectAsState()
    val hideThumbnailsSeekbar by seekbarPrefs.hideVideoThumbnailsSeekbar.collectAsState()
    val enableTopSpeedGesture by seekbarPrefs.enableTopSpeedGesture.collectAsState()

    var showPrimaryColorPicker by remember { mutableStateOf(false) }
    var showAccentColorPicker by remember { mutableStateOf(false) }

    // Live preview state
    val activePrimaryColor = remember(enableCustomColor, seekbarColorHex) {
        if (enableCustomColor) SeekbarPreferences.parseHexColor(seekbarColorHex, Color(0xFFFFD400))
        else Color(0xFFFFD400)
    }
    val activeAccentColor = remember(enableCustomColor, seekbarAccentColorHex) {
        if (enableCustomColor) SeekbarPreferences.parseHexColor(seekbarAccentColorHex, Color(0xFFFF4081))
        else Color(0xFFFF4081)
    }

    var previewScrubPosMs by remember { mutableLongStateOf(84000L) }
    val previewTotalDurMs = 240000L
    var previewSpeed by remember { mutableFloatStateOf(1.0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Seekbar & Gestures",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live Interactive Preview Header Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(activePrimaryColor)
                                )
                                Text(
                                    text = "LIVE SEEKBAR & GESTURE PREVIEW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f),
                                    letterSpacing = 0.8.sp
                                )
                            }

                            if (enableTopSpeedGesture) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.10f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = activePrimaryColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.2f", previewSpeed)}x Top Speed",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Interactive Mock Player Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0A0A0A))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Top swipe speed gesture simulation area
                            if (enableTopSpeedGesture) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .align(Alignment.TopCenter)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .pointerInput(previewSpeed) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val delta = dragAmount.x / 180f
                                                previewSpeed = (previewSpeed + delta).coerceIn(0.25f, 3.0f)
                                                if (seekHapticsEnabled) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Swipe here on top of player to adjust speed",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            // Seekbar preview component
                            if (!hidePlayerSeekbar) {
                                val dummyChapters = remember {
                                    listOf(
                                        com.example.extractor.chapters.VideoChapter("Intro", 0L),
                                        com.example.extractor.chapters.VideoChapter("Main Event", 60000L),
                                        com.example.extractor.chapters.VideoChapter("Climax", 140000L),
                                        com.example.extractor.chapters.VideoChapter("Ending", 200000L)
                                    )
                                }
                                val dummyHeatmap = remember {
                                    VideoHeatmap(
                                        points = listOf(0.1f, 0.2f, 0.4f, 0.8f, 1.0f, 0.6f, 0.3f, 0.2f, 0.5f, 0.7f, 0.4f, 0.1f),
                                        peakPositionMs = 80000L,
                                        peakFraction = 0.35f
                                    )
                                }

                                YouTubePreciseSeekBar(
                                    currentPositionMs = previewScrubPosMs,
                                    durationMs = previewTotalDurMs,
                                    bufferedPositionMs = 180000L,
                                    isControlsVisible = true,
                                    activeColor = activePrimaryColor,
                                    accentColor = activeAccentColor,
                                    thumbColor = activePrimaryColor,
                                    enableGradient = enableGradientProgress,
                                    showHeatmap = showHeatmapGraph,
                                    showChapters = showChapterMarkers,
                                    isLargeSeekbar = enableFullscreenLargeSeekbar,
                                    enableTapToSeek = enableTapToSeek,
                                    hapticsEnabled = seekHapticsEnabled,
                                    chapters = dummyChapters,
                                    heatmap = dummyHeatmap,
                                    onSeekStarted = {},
                                    onSeekScrubbing = { previewScrubPosMs = it },
                                    onSeekFinished = { previewScrubPosMs = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = "Seekbar is hidden in video player",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==================== SECTION: COLORS & VISUALS ====================
            item {
                SectionHeader(title = "Colors & Visual Styling")
            }

            // 1. Enable custom seekbar color
            item {
                SeekbarSettingSwitchRow(
                    title = "Enable custom seekbar color",
                    subtitle = "Customize the progress bar and accent hues with your favorite color palette",
                    checked = enableCustomColor,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setEnableCustomSeekbarColor(it)
                    }
                )
            }

            // 2. Seekbar color (Primary)
            item {
                val primaryColor = remember(seekbarColorHex) {
                    SeekbarPreferences.parseHexColor(seekbarColorHex, Color(0xFFFFD400))
                }
                SeekbarColorSettingRow(
                    title = "Seekbar color",
                    subtitle = "Set the primary fill color of the played video progress bar",
                    color = primaryColor,
                    enabled = enableCustomColor,
                    onClick = {
                        if (enableCustomColor) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showPrimaryColorPicker = true
                        } else {
                            Toast.makeText(context, "Enable custom seekbar color first", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 3. Seekbar accent color (Secondary)
            item {
                val accentColor = remember(seekbarAccentColorHex) {
                    SeekbarPreferences.parseHexColor(seekbarAccentColorHex, Color(0xFFFF4081))
                }
                SeekbarColorSettingRow(
                    title = "Seekbar accent color",
                    subtitle = "Set the secondary accent color for thumb glow, gradient blend, and highlights",
                    color = accentColor,
                    enabled = enableCustomColor,
                    onClick = {
                        if (enableCustomColor) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showAccentColorPicker = true
                        } else {
                            Toast.makeText(context, "Enable custom seekbar color first", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 4. Enable gradient progress
            item {
                SeekbarSettingSwitchRow(
                    title = "Enable dual-tone gradient progress",
                    subtitle = "Blends the primary color seamlessly into the accent color along the played track",
                    checked = enableGradientProgress,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setEnableGradientProgress(it)
                    }
                )
            }

            // 5. Enable fullscreen large seekbar
            item {
                SeekbarSettingSwitchRow(
                    title = "Enable fullscreen large seekbar",
                    subtitle = "Makes seekbar taller and more prominent in fullscreen mode for easier touch tracking",
                    checked = enableFullscreenLargeSeekbar,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setEnableFullscreenLargeSeekbar(it)
                    }
                )
            }

            // 6. Show most replayed heatmap graph
            item {
                SeekbarSettingSwitchRow(
                    title = "Show most replayed waveform graph",
                    subtitle = "Displays the YouTube heatmap curve above the seekbar showing the most watched moments",
                    checked = showHeatmapGraph,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setShowHeatmapGraph(it)
                    }
                )
            }

            // 7. Show chapter markers & dividers
            item {
                SeekbarSettingSwitchRow(
                    title = "Show chapter markers & dividers",
                    subtitle = "Divides the seekbar into chapter sections with clean dividing notches",
                    checked = showChapterMarkers,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setShowChapterMarkers(it)
                    }
                )
            }

            // ==================== SECTION: GESTURES & CONTROLS ====================
            item {
                SectionHeader(title = "Gestures & Seeking Controls")
            }

            // 8. Top Playback Speed Gesture
            item {
                SeekbarSettingSwitchRow(
                    title = "Top Playback Speed Gesture",
                    subtitle = "Swipe left/right horizontally across top of video player to adjust playback speed smoothly from 0.25x to 3.0x",
                    checked = enableTopSpeedGesture,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setEnableTopSpeedGesture(it)
                    }
                )
            }

            // 9. Disable precise seeking gesture
            item {
                SeekbarSettingSwitchRow(
                    title = "Disable precise seeking gesture",
                    subtitle = "Disables the precise fine-scrubbing mode that activates when pulling up the seekbar",
                    checked = disablePreciseSeeking,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setDisablePreciseSeekingGesture(it)
                    }
                )
            }

            // 10. Enable slide to seek
            item {
                SeekbarSettingSwitchRow(
                    title = "Enable slide to seek",
                    subtitle = "Enables slide to seek instead of 2x speed when pressing and dragging horizontally on the video player",
                    checked = enableSlideToSeek,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setEnableSlideToSeek(it)
                    }
                )
            }

            // 11. Enable tap to seek
            item {
                SeekbarSettingSwitchRow(
                    title = "Enable tap to seek",
                    subtitle = "Just tap anywhere along the seekbar track to jump immediately without dragging",
                    checked = enableTapToSeek,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setEnableTapToSeek(it)
                    }
                )
            }

            // 12. Seek haptic feedback
            item {
                SeekbarSettingSwitchRow(
                    title = "Seek & gesture haptic feedback",
                    subtitle = "Tactile vibration tick feedback when scrubbing across seekbar, jumping chapters, and adjusting speed",
                    checked = seekHapticsEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setSeekHapticsEnabled(it)
                    }
                )
            }

            // 13. Double tap seek interval
            item {
                DoubleTapIntervalSelectorRow(
                    currentIntervalSecs = doubleTapSeekIntervalSecs,
                    onIntervalSelected = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setDoubleTapSeekIntervalSecs(it)
                    }
                )
            }

            // ==================== SECTION: LIVESTREAM & DISPLAY ====================
            item {
                SectionHeader(title = "Livestream & Display Rules")
            }

            // 14. Enable livestream DVR
            item {
                SeekbarSettingSwitchRow(
                    title = "Enable livestream DVR",
                    subtitle = "Allows seeking on all livestreams, even those that normally restrict it",
                    checked = enableLivestreamDvr,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setEnableLivestreamDvr(it)
                    }
                )
            }

            // 15. Expand livestream DVR duration
            item {
                SeekbarSettingSwitchRow(
                    title = "Expand livestream DVR duration",
                    subtitle = "Allows you to rewind live broadcasts up to 7 days into the past",
                    checked = expandLivestreamDvrDuration,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setExpandLivestreamDvrDuration(it)
                    }
                )
            }

            // 16. Hide video player seekbar
            item {
                SeekbarSettingSwitchRow(
                    title = "Hide video player seekbar",
                    subtitle = "Hides the seekbar completely in player for an ultra-clean cinematic mode",
                    checked = hidePlayerSeekbar,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setHideVideoPlayerSeekbar(it)
                    }
                )
            }

            // 17. Hide video thumbnails seekbar
            item {
                SeekbarSettingSwitchRow(
                    title = "Hide video thumbnails seekbar",
                    subtitle = "Hides the watch progress bar shown on thumbnails of videos you have already watched",
                    checked = hideThumbnailsSeekbar,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        seekbarPrefs.setHideVideoThumbnailsSeekbar(it)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Primary Color Picker Dialog
    if (showPrimaryColorPicker) {
        HsvColorPickerDialog(
            title = "Seekbar Primary Color",
            initialColorHex = seekbarColorHex,
            defaultColorHex = SeekbarPreferences.DEFAULT_SEEKBAR_COLOR,
            onDismiss = { showPrimaryColorPicker = false },
            onColorSelected = { selectedHex ->
                seekbarPrefs.setSeekbarColorHex(selectedHex)
                showPrimaryColorPicker = false
            }
        )
    }

    // Accent Color Picker Dialog
    if (showAccentColorPicker) {
        HsvColorPickerDialog(
            title = "Seekbar Accent Color",
            initialColorHex = seekbarAccentColorHex,
            defaultColorHex = SeekbarPreferences.DEFAULT_SEEKBAR_ACCENT_COLOR,
            onDismiss = { showAccentColorPicker = false },
            onColorSelected = { selectedHex ->
                seekbarPrefs.setSeekbarAccentColorHex(selectedHex)
                showAccentColorPicker = false
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF2196F3),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SeekbarSettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2196F3),
                uncheckedThumbColor = Color(0xFFAAAAAA),
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

@Composable
private fun SeekbarColorSettingRow(
    title: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (enabled) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.3f),
                lineHeight = 16.sp
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (enabled) color else color.copy(alpha = 0.3f))
                .border(
                    BorderStroke(
                        1.5.dp,
                        if (enabled) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f)
                    ),
                    CircleShape
                )
        )
    }
}

@Composable
private fun DoubleTapIntervalSelectorRow(
    currentIntervalSecs: Int,
    onIntervalSelected: (Int) -> Unit
) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = "Double-tap fast seek interval",
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Seconds skipped on double tapping left or right side of video player",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { sec ->
                val isSelected = sec == currentIntervalSecs
                Surface(
                    onClick = { onIntervalSelected(sec) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF2196F3) else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${sec}s",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Authentic HSV Color Picker Dialog with Preset Swatches, 2D Saturation-Value box, Hue Slider, Hex Code,
 * and Reset / Cancel / OK buttons.
 */
@Composable
fun HsvColorPickerDialog(
    title: String,
    initialColorHex: String,
    defaultColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val initialColor = remember(initialColorHex) {
        SeekbarPreferences.parseHexColor(initialColorHex, Color(0xFFFFD400))
    }
    val hsv = remember(initialColor) {
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsvArr)
        hsvArr
    }

    var hue by remember { mutableFloatStateOf(hsv[0]) } // 0f..360f
    var sat by remember { mutableFloatStateOf(hsv[1]) } // 0f..1f
    var value by remember { mutableFloatStateOf(hsv[2]) } // 0f..1f
    var hexInput by remember {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2]))
        mutableStateOf(String.format("#%06X", 0xFFFFFF and argb))
    }

    val presetColors = listOf(
        "#FFD400" to "YouTube Yellow",
        "#FF4081" to "Electric Rose",
        "#00E5FF" to "Neon Cyan",
        "#7C4DFF" to "Electric Purple",
        "#00E676" to "Emerald Neon",
        "#FF9100" to "Sunset Orange",
        "#FF1744" to "Crimson Red",
        "#FFFFFF" to "Pure White"
    )

    fun applyColorHex(hex: String) {
        val parsed = SeekbarPreferences.parseHexColor(hex, Color(0xFFFFD400))
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed.toArgb(), hsvArr)
        hue = hsvArr[0]
        sat = hsvArr[1]
        value = hsvArr[2]
        hexInput = hex.uppercase()
    }

    LaunchedEffect(hue, sat, value) {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        hexInput = String.format("#%06X", 0xFFFFFF and argb)
    }

    val currentColor = remember(hue, sat, value) {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        Color(argb)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141414),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Quick Preset Swatches
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetColors.forEach { (phex, _) ->
                        val pColor = SeekbarPreferences.parseHexColor(phex, Color.White)
                        val isSelected = hexInput.equals(phex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(pColor)
                                .border(
                                    BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.3f)),
                                    CircleShape
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    applyColorHex(phex)
                                }
                        )
                    }
                }

                // 2D Saturation-Value Color Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    val pureHueColor = remember(hue) {
                        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                        Color(argb)
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(hue) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    sat = (offset.x / w).coerceIn(0f, 1f)
                                    value = (1f - (offset.y / h)).coerceIn(0f, 1f)
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                            .pointerInput(hue) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    sat = (change.position.x / w).coerceIn(0f, 1f)
                                    value = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        // 1. Pure Hue solid background
                        drawRect(pureHueColor)
                        // 2. Horizontal White to Transparent Gradient (Saturation)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.White, Color.Transparent)
                            )
                        )
                        // 3. Vertical Transparent to Black Gradient (Value)
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black)
                            )
                        )

                        // 4. Draw Cursor Thumb Handle
                        val cursorX = sat * size.width
                        val cursorY = (1f - value) * size.height
                        drawCircle(
                            color = Color.Black,
                            radius = 12.dp.toPx(),
                            center = Offset(cursorX, cursorY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(cursorX, cursorY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Rainbow Hue Slider Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    val rainbowColors = remember {
                        listOf(
                            Color(0xFFFF0000),
                            Color(0xFFFFFF00),
                            Color(0xFF00FF00),
                            Color(0xFF00FFFF),
                            Color(0xFF0000FF),
                            Color(0xFFFF00FF),
                            Color(0xFFFF0000)
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    hue = ((offset.x / w) * 360f).coerceIn(0f, 360f)
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    hue = ((change.position.x / w) * 360f).coerceIn(0f, 360f)
                                }
                            }
                    ) {
                        drawRect(brush = Brush.horizontalGradient(rainbowColors))

                        // Draw Hue Thumb Slider Indicator
                        val thumbX = ((hue / 360f) * size.width).coerceIn(0f, size.width)
                        val thumbY = size.height / 2f
                        drawCircle(
                            color = Color.Black,
                            radius = 12.dp.toPx(),
                            center = Offset(thumbX, thumbY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(thumbX, thumbY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color Swatch & Hex Code Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), CircleShape)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        modifier = Modifier
                            .width(130.dp)
                            .border(BorderStroke(0.dp, Color.Transparent))
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = hexInput,
                            onValueChange = { newText ->
                                hexInput = newText
                                val parsed = SeekbarPreferences.parseHexColor(newText, currentColor)
                                val hsvArr = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed.toArgb(), hsvArr)
                                hue = hsvArr[0]
                                sat = hsvArr[1]
                                value = hsvArr[2]
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Underline for hex input
                Box(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 40.dp)
                        .width(130.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Action Buttons: Reset Color | Cancel | OK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset color
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            applyColorHex(defaultColorHex)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF242424),
                        contentColor = Color.White,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "Reset color", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Cancel
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF242424),
                        contentColor = Color.White,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // OK
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onColorSelected(hexInput)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        contentColor = Color.Black,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "OK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}
