package com.example.sponsorblock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
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
import com.example.sponsorblock.SponsorBlockPreferences
import com.example.sponsorblock.model.SponsorBlockAction
import com.example.sponsorblock.model.SponsorBlockCategory
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorBlockSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { SponsorBlockPreferences.getInstance(context) }

    val isEnabled by prefs.isEnabled.collectAsState()
    val showVotingButton by prefs.showVotingButton.collectAsState()
    val useCompactSkipButton by prefs.useCompactSkipButton.collectAsState()
    val autoHideSkipButton by prefs.autoHideSkipButton.collectAsState()
    val skipButtonDurationSeconds by prefs.skipButtonDurationSeconds.collectAsState()
    val showUndoSkipNotification by prefs.showUndoSkipNotification.collectAsState()
    val skipNotificationDurationSeconds by prefs.skipNotificationDurationSeconds.collectAsState()
    val showVideoLengthWithoutSegments by prefs.showVideoLengthWithoutSegments.collectAsState()
    val useSquareLayout by prefs.useSquareLayout.collectAsState()

    val showConnectionErrorAlerts by prefs.showConnectionErrorAlerts.collectAsState()
    val enableSkipCountTracking by prefs.enableSkipCountTracking.collectAsState()
    val minimumSegmentDurationSeconds by prefs.minimumSegmentDurationSeconds.collectAsState()
    val privateUserId by prefs.privateUserId.collectAsState()
    val apiUrl by prefs.apiUrl.collectAsState()
    val timeAdjustmentStepMs by prefs.timeAdjustmentStepMs.collectAsState()

    val skippedSegmentsCount by prefs.skippedSegmentsCount.collectAsState()
    val skippedTimeSeconds by prefs.skippedTimeSeconds.collectAsState()

    // Dialog States
    var selectedCategoryForEdit by remember { mutableStateOf<SponsorBlockCategory?>(null) }
    var showApiUrlDialog by remember { mutableStateOf(false) }
    var showMinDurationDialog by remember { mutableStateOf(false) }
    var showTimeStepDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }
    var showSubmissionGuidelinesDialog by remember { mutableStateOf(false) }
    var showSkipButtonDurationDialog by remember { mutableStateOf(false) }
    var showSkipNotificationDurationDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SponsorBlock",
                        fontWeight = FontWeight.Normal,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Enable SponsorBlock Master Toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { prefs.setEnabled(!isEnabled) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable SponsorBlock",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SponsorBlock is a crowdsourced system for skipping annoying parts of YouTube videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { prefs.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF3B82F6)
                        )
                    )
                }
            }

            // APPEARANCE SECTION
            item {
                SectionHeader(title = "Appearance")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingSwitchItem(
                        title = "Show voting button",
                        subtitle = "Adds a button to upvote or downvote segments",
                        checked = showVotingButton,
                        onCheckedChange = { prefs.setShowVotingButton(it) }
                    )

                    SettingSwitchItem(
                        title = "Use compact Skip button",
                        subtitle = "Reduces the width of the Skip button to save space",
                        checked = useCompactSkipButton,
                        onCheckedChange = { prefs.setUseCompactSkipButton(it) }
                    )

                    SettingSwitchItem(
                        title = "Automatically hide Skip button",
                        subtitle = "Hides the Skip button after a few seconds instead of keeping it visible for the entire segment",
                        checked = autoHideSkipButton,
                        onCheckedChange = { prefs.setAutoHideSkipButton(it) }
                    )

                    SettingClickItem(
                        title = "Skip button duration",
                        subtitle = "Set how long the Skip buttons remains visible (${skipButtonDurationSeconds}s)",
                        onClick = { showSkipButtonDurationDialog = true }
                    )

                    SettingSwitchItem(
                        title = "Show undo skip notification",
                        subtitle = "Shows a notification allowing you to tap to undo an automatic skip",
                        checked = showUndoSkipNotification,
                        onCheckedChange = { prefs.setShowUndoSkipNotification(it) }
                    )

                    SettingClickItem(
                        title = "Skip notification duration",
                        subtitle = "Set how long the undo notification remains visible (${skipNotificationDurationSeconds}s)",
                        onClick = { showSkipNotificationDurationDialog = true }
                    )

                    SettingSwitchItem(
                        title = "Show video length without segments",
                        subtitle = "Subtracts all segments in total video duration shown on the seekbar",
                        checked = showVideoLengthWithoutSegments,
                        onCheckedChange = { prefs.setShowVideoLengthWithoutSegments(it) }
                    )

                    SettingSwitchItem(
                        title = "Use square layout",
                        subtitle = "Uses rounded corners for SponsorBlock buttons and controls",
                        checked = useSquareLayout,
                        onCheckedChange = { prefs.setUseSquareLayout(it) }
                    )
                }
            }

            // CHANGE SEGMENT BEHAVIOR SECTION
            item {
                SectionHeader(title = "Change segment behavior")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SponsorBlockCategory.values().forEach { category ->
                        val action = prefs.getCategoryAction(category)
                        CategoryBehaviorItem(
                            category = category,
                            action = action,
                            onClick = { selectedCategoryForEdit = category }
                        )
                    }
                }
            }

            // GENERAL SECTION
            item {
                SectionHeader(title = "General")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingClickItem(
                        title = "Time adjustment step",
                        subtitle = "Seek amount of the time adjustment buttons (${timeAdjustmentStepMs} milliseconds)",
                        onClick = { showTimeStepDialog = true }
                    )

                    SettingClickItem(
                        title = "Submission guidelines",
                        subtitle = "Guidelines contain rules and tips for creating new segments",
                        onClick = { showSubmissionGuidelinesDialog = true }
                    )

                    SettingSwitchItem(
                        title = "Show connection error alerts",
                        subtitle = "Shows a notification when the SponsorBlock server is unavailable",
                        checked = showConnectionErrorAlerts,
                        onCheckedChange = { prefs.setShowConnectionErrorAlerts(it) }
                    )

                    SettingSwitchItem(
                        title = "Enable skip count tracking",
                        subtitle = "Sends skip counts to the SponsorBlock leaderboard to track how much time is saved",
                        checked = enableSkipCountTracking,
                        onCheckedChange = { prefs.setEnableSkipCountTracking(it) }
                    )

                    SettingClickItem(
                        title = "Minimum segment duration",
                        subtitle = "Segments shorter than this value (in seconds) will not be shown or skipped (${minimumSegmentDurationSeconds}s)",
                        onClick = { showMinDurationDialog = true }
                    )

                    SettingClickItem(
                        title = "Your private user ID",
                        subtitle = "This should be kept private. This is like a password and should not be shared with anyone. Tap to copy or regenerate.",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SponsorBlock Private User ID", privateUserId))
                            Toast.makeText(context, "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingClickItem(
                        title = "Change API URL",
                        subtitle = "The address SponsorBlock uses to make calls to the server ($apiUrl)",
                        onClick = { showApiUrlDialog = true }
                    )

                    SettingClickItem(
                        title = "Import / Export settings",
                        subtitle = "Your SponsorBlock JSON configuration that can be imported / exported to Morphe and other SponsorBlock platforms",
                        onClick = { showImportExportDialog = true }
                    )
                }
            }

            // STATS SECTION
            item {
                SectionHeader(title = "Stats")
            }

            item {
                val totalMinutes = (skippedTimeSeconds / 60.0).toLong()
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141414))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "You've skipped $skippedSegmentsCount segments",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "That's $hours hours $minutes minutes saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    if (skippedSegmentsCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { prefs.resetStats() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                        ) {
                            Text("Reset Stats")
                        }
                    }
                }
            }

            // ABOUT SECTION
            item {
                SectionHeader(title = "About")
            }

            item {
                SettingClickItem(
                    title = "sponsor.ajay.app",
                    subtitle = "Data is provided by the SponsorBlock API. Tap here to learn more and see downloads for other platforms",
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://sponsor.ajay.app")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Category Action Dialog
    selectedCategoryForEdit?.let { category ->
        var tempAction by remember { mutableStateOf(prefs.getCategoryAction(category)) }
        AlertDialog(
            onDismissRequest = { selectedCategoryForEdit = null },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(category.color)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(category.title, color = Color.White, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SponsorBlockAction.values().forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tempAction = action
                                    prefs.setCategoryAction(category, action)
                                    selectedCategoryForEdit = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (tempAction == action),
                                onClick = {
                                    tempAction = action
                                    prefs.setCategoryAction(category, action)
                                    selectedCategoryForEdit = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF3B82F6))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(action.title, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(action.description, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCategoryForEdit = null }) {
                    Text("Close", color = Color(0xFF3B82F6))
                }
            }
        )
    }

    // API URL Dialog
    if (showApiUrlDialog) {
        var inputUrl by remember { mutableStateOf(apiUrl) }
        AlertDialog(
            onDismissRequest = { showApiUrlDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Change API URL", color = Color.White) },
            text = {
                Column {
                    Text("Specify custom SponsorBlock server URL:", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            prefs.setApiUrl(inputUrl.trim())
                        }
                        showApiUrlDialog = false
                    }
                ) {
                    Text("Save", color = Color(0xFF3B82F6))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiUrlDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Min Duration Dialog
    if (showMinDurationDialog) {
        var inputDuration by remember { mutableStateOf(minimumSegmentDurationSeconds.toString()) }
        AlertDialog(
            onDismissRequest = { showMinDurationDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Minimum Segment Duration", color = Color.White) },
            text = {
                Column {
                    Text("Segments shorter than this value (in seconds) will be ignored:", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputDuration,
                        onValueChange = { inputDuration = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inputDuration.toFloatOrNull()?.let {
                            prefs.setMinimumSegmentDurationSeconds(it.coerceAtLeast(0f))
                        }
                        showMinDurationDialog = false
                    }
                ) {
                    Text("Save", color = Color(0xFF3B82F6))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMinDurationDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Skip Duration Dialog
    if (showSkipButtonDurationDialog) {
        var duration by remember { mutableStateOf(skipButtonDurationSeconds) }
        AlertDialog(
            onDismissRequest = { showSkipButtonDurationDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Skip Button Duration", color = Color.White) },
            text = {
                Column {
                    Text("How many seconds the skip button remains visible: ${duration}s", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = duration.toFloat(),
                        onValueChange = { duration = it.toInt() },
                        valueRange = 2f..15f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF3B82F6),
                            activeTrackColor = Color(0xFF3B82F6)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.setSkipButtonDurationSeconds(duration)
                        showSkipButtonDurationDialog = false
                    }
                ) {
                    Text("Save", color = Color(0xFF3B82F6))
                }
            }
        )
    }

    // Skip Notification Duration Dialog
    if (showSkipNotificationDurationDialog) {
        var duration by remember { mutableStateOf(skipNotificationDurationSeconds) }
        AlertDialog(
            onDismissRequest = { showSkipNotificationDurationDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Undo Notification Duration", color = Color.White) },
            text = {
                Column {
                    Text("How long the undo notification remains visible: ${duration}s", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = duration.toFloat(),
                        onValueChange = { duration = it.toInt() },
                        valueRange = 2f..10f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF3B82F6),
                            activeTrackColor = Color(0xFF3B82F6)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.setSkipNotificationDurationSeconds(duration)
                        showSkipNotificationDurationDialog = false
                    }
                ) {
                    Text("Save", color = Color(0xFF3B82F6))
                }
            }
        )
    }

    // Time Adjustment Step Dialog
    if (showTimeStepDialog) {
        var stepMs by remember { mutableStateOf(timeAdjustmentStepMs) }
        AlertDialog(
            onDismissRequest = { showTimeStepDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Time Adjustment Step", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2000L, 5000L, 10000L, 15000L, 30000L).forEach { ms ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    stepMs = ms
                                    prefs.setTimeAdjustmentStepMs(ms)
                                    showTimeStepDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (stepMs == ms),
                                onClick = {
                                    stepMs = ms
                                    prefs.setTimeAdjustmentStepMs(ms)
                                    showTimeStepDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF3B82F6))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${ms / 1000} seconds ($ms ms)", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeStepDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Import / Export JSON Dialog
    if (showImportExportDialog) {
        var jsonText by remember { mutableStateOf(prefs.exportSettingsJson()) }
        AlertDialog(
            onDismissRequest = { showImportExportDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Import / Export Configuration", color = Color.White) },
            text = {
                Column {
                    Text("Copy or paste SponsorBlock JSON settings:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = prefs.importSettingsJson(jsonText)
                        if (success) {
                            Toast.makeText(context, "SponsorBlock configuration imported successfully!", Toast.LENGTH_SHORT).show()
                            showImportExportDialog = false
                        } else {
                            Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Import JSON", color = Color(0xFF3B82F6))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SponsorBlock Config", jsonText))
                        Toast.makeText(context, "Settings copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy JSON", color = Color.White)
                }
            }
        )
    }

    // Submission Guidelines Dialog
    if (showSubmissionGuidelinesDialog) {
        AlertDialog(
            onDismissRequest = { showSubmissionGuidelinesDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Submission Guidelines", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Only submit segments that match SponsorBlock category definitions precisely.", color = Color.LightGray, fontSize = 13.sp)
                    Text("2. Do not include important video content or conclusions inside sponsor segments.", color = Color.LightGray, fontSize = 13.sp)
                    Text("3. Keep segment boundaries tight and accurate to the nearest second.", color = Color.LightGray, fontSize = 13.sp)
                    Text("4. Community submissions are subject to crowdsourced voting and moderation.", color = Color.LightGray, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubmissionGuidelinesDialog = false }) {
                    Text("Got it", color = Color(0xFF3B82F6))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Color(0xFF3B82F6),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF3B82F6)
            )
        )
    }
}

@Composable
private fun SettingClickItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun CategoryBehaviorItem(
    category: SponsorBlockCategory,
    action: SponsorBlockAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 2,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(category.color)
        )
    }
}
