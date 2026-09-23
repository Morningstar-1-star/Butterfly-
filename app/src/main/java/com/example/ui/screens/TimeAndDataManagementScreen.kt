package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.util.DailyTimeStat
import com.example.util.DataUsageSummary
import com.example.util.MonthlyTimeSummary
import com.example.util.MonthlyWeekStat
import com.example.util.SourceDataUsage
import com.example.util.TimeAndDataManager
import com.example.util.TimeManagementSettings
import com.example.util.WeeklyTimeSummary
import kotlin.math.max

enum class TimeManagementTab {
    WEEKLY,
    MONTHLY,
    DATA_USAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeAndDataManagementScreen(
    onBackClick: () -> Unit,
    viewModel: MainViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        TimeAndDataManager.init(context)
        TimeAndDataManager.refreshAllStats()
    }

    val weeklySummary by TimeAndDataManager.weeklySummary.collectAsState()
    val monthlySummary by TimeAndDataManager.monthlySummary.collectAsState()
    val dataUsageSummary by TimeAndDataManager.dataUsageSummary.collectAsState()
    val settings by TimeAndDataManager.settings.collectAsState()

    var activeTab by remember { mutableStateOf(TimeManagementTab.WEEKLY) }
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }
    var selectedWeekIndex by remember { mutableStateOf<Int?>(null) }

    var showBreakReminderDialog by remember { mutableStateOf(false) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    var showDataLimitDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Time management",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
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
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. SEGMENTED TAB SELECTOR (Weekly / Monthly / Data Usage)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TabSegmentButton(
                        title = "Weekly",
                        icon = Icons.Outlined.DateRange,
                        isSelected = activeTab == TimeManagementTab.WEEKLY,
                        onClick = {
                            activeTab = TimeManagementTab.WEEKLY
                            selectedDayIndex = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TabSegmentButton(
                        title = "Monthly",
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = activeTab == TimeManagementTab.MONTHLY,
                        onClick = {
                            activeTab = TimeManagementTab.MONTHLY
                            selectedWeekIndex = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TabSegmentButton(
                        title = "Data Usage",
                        icon = Icons.Outlined.DataUsage,
                        isSelected = activeTab == TimeManagementTab.DATA_USAGE,
                        onClick = { activeTab = TimeManagementTab.DATA_USAGE },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. TAB CONTENT
            when (activeTab) {
                TimeManagementTab.WEEKLY -> {
                    item {
                        WeeklyTabContent(
                            weeklySummary = weeklySummary,
                            selectedIndex = selectedDayIndex,
                            onSelectIndex = { selectedDayIndex = it }
                        )
                    }
                }
                TimeManagementTab.MONTHLY -> {
                    item {
                        MonthlyTabContent(
                            monthlySummary = monthlySummary,
                            selectedIndex = selectedWeekIndex,
                            onSelectIndex = { selectedWeekIndex = it }
                        )
                    }
                }
                TimeManagementTab.DATA_USAGE -> {
                    item {
                        DataUsageTabContent(
                            dataUsageSummary = dataUsageSummary,
                            dataSaverEnabled = settings.dataSaverEnabled,
                            onToggleDataSaver = {
                                TimeAndDataManager.updateSettings(settings.copy(dataSaverEnabled = it))
                            }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }

            // 3. TOOLS TO MANAGE YOUR TIME & DATA (Settings & Wellbeing)
            item {
                Text(
                    text = "Tools to manage your time & data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Break Reminder
            item {
                ManagementSettingRow(
                    icon = Icons.Outlined.Timer,
                    title = "Remind me to take a break",
                    subtitle = if (settings.breakReminderMinutes > 0) "Every ${TimeAndDataManager.formatDuration(settings.breakReminderMinutes)}" else "Off",
                    checked = settings.breakReminderMinutes > 0,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            showBreakReminderDialog = true
                        } else {
                            TimeAndDataManager.updateSettings(settings.copy(breakReminderMinutes = 0))
                        }
                    },
                    onClick = { showBreakReminderDialog = true }
                )
            }

            // Bedtime Reminder
            item {
                val startFormatted = String.format("%02d:%02d", settings.bedtimeStartHour, settings.bedtimeStartMinute)
                val endFormatted = String.format("%02d:%02d", settings.bedtimeEndHour, settings.bedtimeEndMinute)
                ManagementSettingRow(
                    icon = Icons.Outlined.Bedtime,
                    title = "Remind me when it's bedtime",
                    subtitle = if (settings.bedtimeReminderEnabled) "$startFormatted – $endFormatted" else "Off",
                    checked = settings.bedtimeReminderEnabled,
                    onCheckedChange = { isChecked ->
                        TimeAndDataManager.updateSettings(settings.copy(bedtimeReminderEnabled = isChecked))
                        if (isChecked) showBedtimeDialog = true
                    },
                    onClick = { showBedtimeDialog = true }
                )
            }

            // Data Saver Mode
            item {
                ManagementSettingRow(
                    icon = Icons.Outlined.Savings,
                    title = "Data Saver mode",
                    subtitle = "Prioritizes low-bandwidth streams to save mobile data",
                    checked = settings.dataSaverEnabled,
                    onCheckedChange = { isChecked ->
                        TimeAndDataManager.updateSettings(settings.copy(dataSaverEnabled = isChecked))
                    }
                )
            }

            // Mobile Data Limit Warning
            item {
                ManagementSettingRow(
                    icon = Icons.Outlined.Speed,
                    title = "Daily data limit warning",
                    subtitle = if (settings.dataLimitMb > 0) "Alert at ${settings.dataLimitMb} MB/day" else "Off",
                    checked = settings.dataLimitMb > 0,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            showDataLimitDialog = true
                        } else {
                            TimeAndDataManager.updateSettings(settings.copy(dataLimitMb = 0))
                        }
                    },
                    onClick = { showDataLimitDialog = true }
                )
            }

            // Autoplay on Wi-Fi Only
            item {
                ManagementSettingRow(
                    icon = Icons.Outlined.Wifi,
                    title = "Autoplay on Wi-Fi only",
                    subtitle = "Prevents automatic video buffering over cellular",
                    checked = settings.wifiOnlyAutoplay,
                    onCheckedChange = { isChecked ->
                        TimeAndDataManager.updateSettings(settings.copy(wifiOnlyAutoplay = isChecked))
                    }
                )
            }

            // Reset Statistics
            item {
                Surface(
                    onClick = { showResetConfirmDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Reset Stats",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset time & data statistics",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Clear recorded daily watch duration and bandwidth logs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // DIALOG: BREAK REMINDER PICKER
    if (showBreakReminderDialog) {
        val options = listOf(15, 30, 45, 60, 90, 120, 180)
        AlertDialog(
            onDismissRequest = { showBreakReminderDialog = false },
            title = { Text("Reminder frequency", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Choose how frequently you'd like a prompt to take a screen break:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    options.forEach { mins ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    TimeAndDataManager.updateSettings(settings.copy(breakReminderMinutes = mins))
                                    showBreakReminderDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.breakReminderMinutes == mins,
                                onClick = {
                                    TimeAndDataManager.updateSettings(settings.copy(breakReminderMinutes = mins))
                                    showBreakReminderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Every ${TimeAndDataManager.formatDuration(mins)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBreakReminderDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // DIALOG: BEDTIME SCHEDULE
    if (showBedtimeDialog) {
        var startH by remember { mutableStateOf(settings.bedtimeStartHour) }
        var endH by remember { mutableStateOf(settings.bedtimeEndHour) }
        AlertDialog(
            onDismissRequest = { showBedtimeDialog = false },
            title = { Text("Bedtime schedule", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Set the hours you plan to wind down and sleep:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Start time:", style = MaterialTheme.typography.bodyLarge)
                        FilledTonalButton(onClick = {
                            startH = (startH + 1) % 24
                        }) {
                            Text(String.format("%02d:00", startH))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("End time:", style = MaterialTheme.typography.bodyLarge)
                        FilledTonalButton(onClick = {
                            endH = (endH + 1) % 24
                        }) {
                            Text(String.format("%02d:00", endH))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    TimeAndDataManager.updateSettings(
                        settings.copy(
                            bedtimeReminderEnabled = true,
                            bedtimeStartHour = startH,
                            bedtimeEndHour = endH
                        )
                    )
                    showBedtimeDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: DATA LIMIT WARNING
    if (showDataLimitDialog) {
        val limits = listOf(250, 500, 1000, 2000, 5000)
        AlertDialog(
            onDismissRequest = { showDataLimitDialog = false },
            title = { Text("Daily mobile data limit", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Get notified when daily streaming crosses this threshold:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    limits.forEach { mb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    TimeAndDataManager.updateSettings(settings.copy(dataLimitMb = mb))
                                    showDataLimitDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.dataLimitMb == mb,
                                onClick = {
                                    TimeAndDataManager.updateSettings(settings.copy(dataLimitMb = mb))
                                    showDataLimitDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (mb >= 1000) "${mb / 1000} GB per day" else "$mb MB per day",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDataLimitDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // DIALOG: RESET CONFIRMATION
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset all statistics?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will erase your recorded watch time, daily breakdowns, and source bandwidth numbers. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        TimeAndDataManager.resetAllStatistics()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Weekly Tab matching the exact YouTube design from the user's screenshot.
 */
@Composable
private fun WeeklyTabContent(
    weeklySummary: WeeklyTimeSummary,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Hero Daily Average Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "${weeklySummary.dailyAverageMinutes} min daily average",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isIncrease = weeklySummary.comparisonPercentFromLastWeek >= 0
                Icon(
                    imageVector = if (isIncrease) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${kotlin.math.abs(weeklySummary.comparisonPercentFromLastWeek)}% from last week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive 7-Day Bar Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(horizontal = 16.dp)
        ) {
            val stats = weeklySummary.dailyStats
            val maxMinutes = max(60, (stats.maxOfOrNull { it.minutesWatched } ?: 60) + 15)
            val benchmarkInterval = when {
                maxMinutes <= 60 -> 15
                maxMinutes <= 120 -> 30
                else -> 60
            }
            val benchmarkValues = (benchmarkInterval..maxMinutes step benchmarkInterval).toList().take(4)

            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val onBackgroundColor = MaterialTheme.colorScheme.onBackground
            val barColor = Color(0xFF3EA6FF) // YouTube Signature Blue
            val selectedBarColor = Color(0xFF60BAFF)
            val barTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(stats) {
                        detectTapGestures { offset ->
                            val chartHeight = size.height - 40.dp.toPx()
                            val chartWidth = size.width - 60.dp.toPx()
                            val slotWidth = chartWidth / max(1, stats.size)
                            val tappedIdx = (offset.x / slotWidth).toInt().coerceIn(0, stats.size - 1)
                            onSelectIndex(if (selectedIndex == tappedIdx) null else tappedIdx)
                        }
                    }
            ) {
                val bottomLabelY = size.height - 20.dp.toPx()
                val chartBottom = size.height - 35.dp.toPx()
                val chartTop = 15.dp.toPx()
                val availableHeight = chartBottom - chartTop
                val chartWidth = size.width - 60.dp.toPx()
                val barSlotWidth = chartWidth / max(1, stats.size)
                val barWidth = barSlotWidth * 0.55f

                // Draw Horizontal Benchmark Grid Lines
                benchmarkValues.forEach { benchmark ->
                    val frac = (benchmark.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                    val lineY = chartBottom - (availableHeight * frac)

                    drawLine(
                        color = gridColor,
                        start = Offset(0f, lineY),
                        end = Offset(chartWidth + 10.dp.toPx(), lineY),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw benchmark label on right side (e.g. "15 min", "30 min", "45 min", "60 min")
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "$benchmark min",
                        topLeft = Offset(chartWidth + 14.dp.toPx(), lineY - 8.dp.toPx()),
                        style = TextStyle(
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                // Draw Baseline
                drawLine(
                    color = gridColor,
                    start = Offset(0f, chartBottom),
                    end = Offset(chartWidth + 10.dp.toPx(), chartBottom),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Draw Bars for each day
                stats.forEachIndexed { i, stat ->
                    val barCenterX = (i * barSlotWidth) + (barSlotWidth / 2f)
                    val barLeft = barCenterX - (barWidth / 2f)

                    val frac = (stat.minutesWatched.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                    val barHeight = (availableHeight * frac).coerceAtLeast(0f)
                    val barTop = chartBottom - barHeight

                    val isSelected = selectedIndex == i

                    if (stat.minutesWatched > 0) {
                        drawRoundRect(
                            color = if (isSelected) selectedBarColor else barColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    // Draw Day Label under bar (Wed, Thu, Fri, Sat, Sun, Mon, Today)
                    val dayText = stat.dayLabel
                    val measured = textMeasurer.measure(
                        text = dayText,
                        style = TextStyle(
                            color = if (isSelected || stat.isToday) onBackgroundColor else labelColor,
                            fontSize = 12.sp,
                            fontWeight = if (stat.isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(barCenterX - (measured.size.width / 2f), chartBottom + 8.dp.toPx())
                    )
                }
            }
        }

        // Active Bar Popover Inspection
        AnimatedVisibility(visible = selectedIndex != null && selectedIndex in weeklySummary.dailyStats.indices) {
            val stat = selectedIndex?.let { weeklySummary.dailyStats.getOrNull(it) }
            if (stat != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stat.dayLabel} (${stat.fullDateLabel})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${stat.minutesWatched} minutes watched",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3EA6FF)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Breakdown Rows (Matching YouTube Reference: Today / Last seven days)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SummaryMetricRow(
                label = "Today",
                value = TimeAndDataManager.formatDuration(weeklySummary.todayMinutes)
            )
            Spacer(modifier = Modifier.height(14.dp))
            SummaryMetricRow(
                label = "Last seven days",
                value = TimeAndDataManager.formatDuration(weeklySummary.totalSevenDaysMinutes)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Stats are based on your watch history across all sources (YouTube, Bilibili, Twitch, local & cloud media).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Monthly Tab with 4-week / monthly progression.
 */
@Composable
private fun MonthlyTabContent(
    monthlySummary: MonthlyTimeSummary,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Hero Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "${monthlySummary.totalHoursFormatted} total this month",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "~${monthlySummary.dailyAverageMinutes} min daily average across 30 days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4-Week Bar Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp)
        ) {
            val weeks = monthlySummary.weekStats
            val maxMins = max(120, (weeks.maxOfOrNull { it.totalMinutes } ?: 120) + 30)

            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val onBackgroundColor = MaterialTheme.colorScheme.onBackground
            val barColor = Color(0xFF00E5FF)
            val selectedBarColor = Color(0xFF80F0FF)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(weeks) {
                        detectTapGestures { offset ->
                            val chartWidth = size.width - 60.dp.toPx()
                            val slotWidth = chartWidth / max(1, weeks.size)
                            val tapped = (offset.x / slotWidth).toInt().coerceIn(0, weeks.size - 1)
                            onSelectIndex(if (selectedIndex == tapped) null else tapped)
                        }
                    }
            ) {
                val chartBottom = size.height - 35.dp.toPx()
                val chartTop = 15.dp.toPx()
                val availableHeight = chartBottom - chartTop
                val chartWidth = size.width - 60.dp.toPx()
                val slotWidth = chartWidth / max(1, weeks.size)
                val barWidth = slotWidth * 0.45f

                // Draw benchmark line at half and max
                val midVal = maxMins / 2
                val midY = chartBottom - (availableHeight * 0.5f)
                drawLine(gridColor, Offset(0f, midY), Offset(chartWidth + 10.dp.toPx(), midY), 1.dp.toPx())
                drawText(
                    textMeasurer = textMeasurer,
                    text = TimeAndDataManager.formatDuration(midVal),
                    topLeft = Offset(chartWidth + 14.dp.toPx(), midY - 8.dp.toPx()),
                    style = TextStyle(color = labelColor, fontSize = 11.sp)
                )

                drawLine(gridColor, Offset(0f, chartBottom), Offset(chartWidth + 10.dp.toPx(), chartBottom), 1.5.dp.toPx())

                weeks.forEachIndexed { i, stat ->
                    val barCenterX = (i * slotWidth) + (slotWidth / 2f)
                    val barLeft = barCenterX - (barWidth / 2f)
                    val frac = (stat.totalMinutes.toFloat() / maxMins.toFloat()).coerceIn(0f, 1f)
                    val barHeight = (availableHeight * frac).coerceAtLeast(0f)
                    val barTop = chartBottom - barHeight
                    val isSelected = selectedIndex == i

                    if (stat.totalMinutes > 0) {
                        drawRoundRect(
                            color = if (isSelected) selectedBarColor else barColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }

                    val textLayout = textMeasurer.measure(
                        text = stat.weekLabel,
                        style = TextStyle(
                            color = if (isSelected) onBackgroundColor else labelColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(barCenterX - (textLayout.size.width / 2f), chartBottom + 8.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monthly Metrics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SummaryMetricRow(
                label = "This month",
                value = monthlySummary.totalHoursFormatted
            )
            Spacer(modifier = Modifier.height(14.dp))
            SummaryMetricRow(
                label = "Most active day",
                value = monthlySummary.mostActiveDay
            )
            Spacer(modifier = Modifier.height(14.dp))
            SummaryMetricRow(
                label = "Daily average",
                value = "${monthlySummary.dailyAverageMinutes} min"
            )
        }
    }
}

/**
 * Data Usage Tab showing Internet Bandwidth Consumption per Source/Provider.
 */
@Composable
private fun DataUsageTabContent(
    dataUsageSummary: DataUsageSummary,
    dataSaverEnabled: Boolean,
    onToggleDataSaver: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Hero Total Data Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3EA6FF).copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Internet Data Consumed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dataUsageSummary.formattedTotal,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3EA6FF)
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = Color(0xFF3EA6FF),
                        modifier = Modifier.size(36.dp)
                    )
                }

                if (dataUsageSummary.topSource != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF3EA6FF).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏆 Top consumer: ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${dataUsageSummary.topSource.displayName} (${String.format("%.0f", dataUsageSummary.topSource.percentage)}% of all data)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF3EA6FF),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION HEADER: CONSUMPTION BY SOURCE
        Text(
            text = "Data consumption by source",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Sources List with Animated Progress Bars
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            dataUsageSummary.sources.forEach { source ->
                SourceDataUsageItem(source = source)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MEDIA TYPE BREAKDOWN CARD
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Media type breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MediaTypeStatPill(
                        label = "Video Streams",
                        sizeStr = dataUsageSummary.formattedVideoSize,
                        icon = Icons.Outlined.Videocam,
                        color = Color(0xFF3EA6FF)
                    )
                    MediaTypeStatPill(
                        label = "Audio Streams",
                        sizeStr = dataUsageSummary.formattedAudioSize,
                        icon = Icons.Outlined.Headphones,
                        color = Color(0xFFFF5500)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MediaTypeStatPill(
                        label = "Thumbnails / UI",
                        sizeStr = dataUsageSummary.formattedThumbnailSize,
                        icon = Icons.Outlined.Image,
                        color = Color(0xFFFFC107)
                    )
                    MediaTypeStatPill(
                        label = "Downloads",
                        sizeStr = dataUsageSummary.formattedDownloadSize,
                        icon = Icons.Outlined.Download,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceDataUsageItem(source: SourceDataUsage) {
    val brandColor = remember(source.colorHex) { Color(source.colorHex) }
    val animatedFraction by animateFloatAsState(
        targetValue = (source.percentage / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "progress"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(brandColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = source.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.formattedSize,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${String.format("%.1f", source.percentage)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(brandColor)
                )
            }
        }
    }
}

@Composable
private fun MediaTypeStatPill(
    label: String,
    sizeStr: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.widthIn(min = 140.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = sizeStr,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }
}

@Composable
private fun TabSegmentButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SummaryMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManagementSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
