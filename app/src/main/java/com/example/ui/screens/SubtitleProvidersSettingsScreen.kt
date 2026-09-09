package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subtitles.SubtitleManager
import com.example.subtitles.plugin.SubtitlePluginInfo
import com.example.subtitles.plugin.SubtitlePluginRegistry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleProvidersSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateSignal by SubtitlePluginRegistry.pluginUpdateSignal.collectAsState()

    var plugins by remember(updateSignal) {
        mutableStateOf(SubtitlePluginRegistry.getAllPluginDescriptors(context))
    }

    var showResetDialog by remember { mutableStateOf(false) }
    val preferHi by SubtitleManager.preferHearingImpaired.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Subtitle Providers",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lightweight plugin architecture & Bazarr sources",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("subtitle_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.testTag("subtitle_settings_reset")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Reset Defaults"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Card
            item {
                ArchitectureOverviewCard(
                    activeCount = plugins.count { it.isEnabled && it.isInstalled },
                    totalCount = plugins.count { it.isInstalled }
                )
            }

            // Global Preferences Section
            item {
                Text(
                    text = "Preferences & Ranking",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
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
                                    text = "Prefer Hearing Impaired (HI) Tracks",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Ranks SDH and descriptive sound subtitles higher",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferHi,
                                onCheckedChange = {
                                    SubtitleManager.setPreferHearingImpaired(it)
                                },
                                modifier = Modifier.testTag("toggle_prefer_hi")
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Parallel Concurrency & Fallback",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Queries all active plugins concurrently with 8s per-plugin timeout",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Installed & Available Plugins Header
            item {
                Text(
                    text = "Provider Plugins (${plugins.count { it.isInstalled }})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            // Plugin cards
            items(plugins, key = { it.id }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    onToggleEnabled = { enabled ->
                        SubtitlePluginRegistry.setPluginEnabled(context, plugin.id, enabled)
                    },
                    onToggleInstalled = { installed ->
                        SubtitlePluginRegistry.setPluginInstalled(context, plugin.id, installed)
                    },
                    onSaveApiKey = { newKey ->
                        SubtitlePluginRegistry.setPluginApiKey(context, plugin.id, newKey)
                        Toast.makeText(context, "${plugin.name} API key updated", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Restore Default Providers?") },
            text = { Text("This will re-enable SubDL, OpenSubtitles, SubtitleCat, and Gestdown, and reset custom API keys.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SubtitlePluginRegistry.resetToDefaults(context)
                        showResetDialog = false
                        Toast.makeText(context, "Subtitle providers restored to defaults", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ArchitectureOverviewCard(
    activeCount: Int,
    totalCount: Int
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ClosedCaption,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Butterfly Subtitle Core",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$activeCount of $totalCount providers active",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Unified Pipeline",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pipeline: Core → Subtitle Manager → Provider Plugins → Unified Results → Deduplicate / Rank → Download",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: SubtitlePluginInfo,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleInstalled: (Boolean) -> Unit,
    onSaveApiKey: (String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expandedApiKey by remember { mutableStateOf(false) }
    var apiKeyInput by remember(plugin.id) {
        mutableStateOf(SubtitlePluginRegistry.getPluginApiKey(context, plugin.id) ?: "")
    }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (plugin.isInstalled && plugin.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (plugin.isEnabled && plugin.isInstalled) 2.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plugin_card_${plugin.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (plugin.isEnabled && plugin.isInstalled)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (plugin.id) {
                                "subdl" -> Icons.Outlined.CloudDownload
                                "opensubtitles" -> Icons.Outlined.Translate
                                "subtitlecat" -> Icons.Outlined.Language
                                "gestdown" -> Icons.Outlined.Tv
                                else -> Icons.Outlined.Extension
                            },
                            contentDescription = null,
                            tint = if (plugin.isEnabled && plugin.isInstalled)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = plugin.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "v${plugin.version}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = plugin.author,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (plugin.isInstalled) {
                    Switch(
                        checked = plugin.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("toggle_plugin_${plugin.id}")
                    )
                } else {
                    Button(
                        onClick = { onToggleInstalled(true) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("install_plugin_${plugin.id}")
                    ) {
                        Text("Install", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = plugin.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plugin.supportsFps) {
                    PluginBadge("FPS Sync")
                }
                if (plugin.supportsHi) {
                    PluginBadge("HI / SDH")
                }
                if (plugin.id == "subdl" || plugin.id == "opensubtitles") {
                    PluginBadge("API Key (Optional)")
                } else {
                    PluginBadge("Zero-Config")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row: Test Connection, API Key, Uninstall
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Test connection button
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testStatus = null
                            coroutineScope.launch {
                                val result = SubtitlePluginRegistry.testPlugin(context, plugin.id)
                                isTesting = false
                                testStatus = if (result.isSuccess) {
                                    result.getOrNull() ?: "OK"
                                } else {
                                    "Failed: ${result.exceptionOrNull()?.message?.take(25)}"
                                }
                            }
                        },
                        enabled = !isTesting && plugin.isInstalled,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("test_btn_${plugin.id}")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...", fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Ping", fontSize = 12.sp)
                        }
                    }

                    // API Key configurator for compatible providers
                    if (plugin.id == "subdl" || plugin.id == "opensubtitles") {
                        OutlinedButton(
                            onClick = { expandedApiKey = !expandedApiKey },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("api_key_btn_${plugin.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("API Key", fontSize = 12.sp)
                        }
                    }
                }

                if (plugin.isInstalled) {
                    IconButton(
                        onClick = { onToggleInstalled(false) },
                        modifier = Modifier.size(32.dp).testTag("uninstall_btn_${plugin.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Uninstall Plugin",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Connection test status badge
            if (testStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isSuccess = testStatus!!.startsWith("Connected") || testStatus == "OK"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSuccess) Color(0xFF1B5E20).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = testStatus!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Expandable API key entry
            AnimatedVisibility(visible = expandedApiKey) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("${plugin.name} API Key") },
                        placeholder = { Text("Optional (uses default key if blank)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_key_${plugin.id}")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                apiKeyInput = ""
                                onSaveApiKey(null)
                                expandedApiKey = false
                            }
                        ) {
                            Text("Clear")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSaveApiKey(apiKeyInput.ifBlank { null })
                                expandedApiKey = false
                            }
                        ) {
                            Text("Save Key")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
