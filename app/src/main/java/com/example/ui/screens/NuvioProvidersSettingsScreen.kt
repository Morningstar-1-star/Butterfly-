package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extractor.nuvio.InstalledNuvioProvider
import com.example.extractor.nuvio.NuvioScraperManifestItem
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuvioProvidersSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isMasterEnabled by viewModel.isNuvioMasterEnabled.collectAsState()
    val installedProviders by viewModel.installedNuvioProviders.collectAsState()
    val availableProviders = viewModel.availableNuvioProviders
    val healthMap by viewModel.nuvioHealthMap.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var testingProviderId by remember { mutableStateOf<String?>(null) }
    var testResultDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filteredInstalled = remember(installedProviders, searchQuery) {
        if (searchQuery.isBlank()) {
            installedProviders.sortedByDescending { it.priority }
        } else {
            val q = searchQuery.trim().lowercase()
            installedProviders.filter {
                it.name.lowercase().contains(q) || it.id.lowercase().contains(q) || it.description.lowercase().contains(q)
            }.sortedByDescending { it.priority }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nuvio Providers", fontWeight = FontWeight.Bold)
                        Text(
                            "29+ Multi-Source Dynamic Scrapers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSyncing = true
                            viewModel.syncNuvioManifest { count ->
                                isSyncing = false
                                Toast.makeText(context, "Manifest synced: $count providers updated!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync Manifest")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Switch Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setNuvioMasterEnabled(!isMasterEnabled) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text(
                                    text = "Enable Nuvio Engine",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isMasterEnabled) "Active • UHDMovies, MoviesMod & 29+ Scrapers" else "Disabled • All Nuvio scrapers inactive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isMasterEnabled,
                            onCheckedChange = { viewModel.setNuvioMasterEnabled(it) }
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Nuvio providers (e.g. UHDMovies, MoviesMod)...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Section Header with Stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INSTALLED PROVIDERS (${filteredInstalled.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = {
                            isSyncing = true
                            viewModel.syncNuvioManifest { count ->
                                isSyncing = false
                                Toast.makeText(context, "Synced $count providers from upstream", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Update All", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Providers List
            items(filteredInstalled, key = { it.id }) { provider ->
                NuvioProviderItemCard(
                    provider = provider,
                    isTesting = testingProviderId == provider.id,
                    healthStatus = healthMap[provider.id] ?: provider.status,
                    onToggleEnabled = { enabled ->
                        viewModel.toggleNuvioProvider(provider.id, enabled)
                    },
                    onTest = {
                        testingProviderId = provider.id
                        viewModel.testNuvioProvider(provider.id) { result ->
                            testingProviderId = null
                            testResultDialog = Pair(provider.name, result)
                        }
                    },
                    onRemove = {
                        viewModel.removeNuvioProvider(provider.id)
                        Toast.makeText(context, "Removed ${provider.name}", Toast.LENGTH_SHORT).show()
                    },
                    onUpdate = {
                        val def = availableProviders.find { it.id == provider.id }
                        if (def != null) {
                            viewModel.installNuvioProvider(def)
                            Toast.makeText(context, "Updated ${provider.name} to v${def.version}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // Test Results Dialog
    if (testResultDialog != null) {
        val (pName, resultText) = testResultDialog!!
        AlertDialog(
            onDismissRequest = { testResultDialog = null },
            icon = {
                Icon(
                    imageVector = if (resultText.startsWith("Active")) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = if (resultText.startsWith("Active")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(text = "Provider Probe: $pName", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Live extraction test (TMDB ID 27205 - Inception):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { testResultDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun NuvioProviderItemCard(
    provider: InstalledNuvioProvider,
    isTesting: Boolean,
    healthStatus: String,
    onToggleEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOlaMovies = provider.id.equals("olamovies", ignoreCase = true)
    val isUnavailable = healthStatus.contains("Unavailable", ignoreCase = true) || isOlaMovies

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isUnavailable) Color.Gray.copy(alpha = 0.2f)
                                else if (provider.priority >= 95) Color(0xFFFF9800).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUnavailable) Icons.Outlined.Block else Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = if (isUnavailable) Color.Gray else if (provider.priority >= 95) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "v${provider.version}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = provider.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = provider.isEnabled && !isUnavailable,
                    onCheckedChange = { onToggleEnabled(it) },
                    enabled = !isUnavailable
                )
            }

            // Badges row (Formats, Types, Status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Badge
                if (provider.priority >= 95) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFF9800).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "PRIORITY ⭐",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF9800),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isUnavailable) Color.Red.copy(alpha = 0.15f)
                    else if (healthStatus.contains("Online", ignoreCase = true) || healthStatus.contains("Active", ignoreCase = true)) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = healthStatus,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = if (isUnavailable) Color.Red else if (healthStatus.contains("Online", ignoreCase = true) || healthStatus.contains("Active", ignoreCase = true)) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Supported Formats
                provider.formats.forEach { fmt ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = fmt.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Actions row: Test, Update, Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !isTesting && !isUnavailable,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Testing...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Stream", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = onUpdate,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update", style = MaterialTheme.typography.labelSmall)
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
