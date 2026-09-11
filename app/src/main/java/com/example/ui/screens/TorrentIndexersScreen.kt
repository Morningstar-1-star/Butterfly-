package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.torrent.cardigann.manager.CardigannManager
import com.example.torrent.cardigann.model.CardigannIndexerDefinition
import com.example.torrent.cardigann.model.IndexerHealthState
import com.example.torrent.cardigann.model.IndexerStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentIndexersScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { CardigannManager.getInstance(context) }

    val indexers by manager.indexersFlow.collectAsState()
    val statuses by manager.statusesFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showOnlyEnabled by remember { mutableStateOf(false) }

    var isUpdatingAll by remember { mutableStateOf(false) }
    var isTestingAll by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showMirrorDialog by remember { mutableStateOf<CardigannIndexerDefinition?>(null) }
    var showYamlDialog by remember { mutableStateOf<CardigannIndexerDefinition?>(null) }

    val filteredIndexers = remember(indexers, searchQuery, selectedCategoryFilter, showOnlyEnabled, statuses) {
        indexers.filter { def ->
            val matchesQuery = searchQuery.isBlank() ||
                    def.name.contains(searchQuery, ignoreCase = true) ||
                    def.id.contains(searchQuery, ignoreCase = true) ||
                    def.description.contains(searchQuery, ignoreCase = true)

            val matchesCat = when (selectedCategoryFilter) {
                "Movies" -> def.caps.categoryMappings.any { it.cat.contains("Movie", true) || it.id.startsWith("2") }
                "TV" -> def.caps.categoryMappings.any { it.cat.contains("TV", true) || it.id.startsWith("5") }
                "Anime" -> def.caps.categoryMappings.any { it.cat.contains("Anime", true) || it.cat == "5070" || it.id.contains("anime", true) }
                "Adult" -> def.caps.categoryMappings.any { it.cat.contains("XXX", true) || it.cat.contains("Adult", true) || it.id.startsWith("6") }
                else -> true
            }

            val isEnabled = manager.isIndexerEnabled(def.id)
            val matchesEnabled = !showOnlyEnabled || isEnabled

            matchesQuery && matchesCat && matchesEnabled
        }
    }

    val totalCount = indexers.size
    val enabledCount = indexers.count { manager.isIndexerEnabled(it.id) }
    val onlineCount = statuses.values.count { it.state == IndexerHealthState.ONLINE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Prowlarr & Cardigann Indexers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$enabledCount of $totalCount active • $onlineCount online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isUpdatingAll) {
                                isUpdatingAll = true
                                scope.launch {
                                    val res = manager.updateDefinitionsFromRemote()
                                    isUpdatingAll = false
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Updated ${res.getOrNull() ?: 0} definitions from Prowlarr", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Update check completed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        if (isUpdatingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.CloudSync, contentDescription = "Sync Definitions")
                        }
                    }

                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add Custom YAML")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Overview Stats & Control Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Universal Cardigann V11 Engine",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Interprets Prowlarr YAML definitions with fault isolation & deduplication",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (!isTestingAll) {
                                        isTestingAll = true
                                        scope.launch {
                                            manager.testAllEnabledIndexers()
                                            isTestingAll = false
                                            Toast.makeText(context, "Tested all active indexers", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isTestingAll,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingAll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Testing...")
                                } else {
                                    Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test All")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val lastSync = manager.getLastSyncTime()
                                    val dateStr = if (lastSync > 0) {
                                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastSync))
                                    } else "Never"
                                    Toast.makeText(context, "Last remote sync: $dateStr", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Info")
                            }
                        }
                    }
                }
            }

            // 2. Search & Filter Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search indexers by name, genre or ID...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Filter Chips Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All", "Movies", "TV", "Anime", "Adult").forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(cat) }
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        FilterChip(
                            selected = showOnlyEnabled,
                            onClick = { showOnlyEnabled = !showOnlyEnabled },
                            label = { Text("Active") },
                            leadingIcon = {
                                if (showOnlyEnabled) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        )
                    }
                }
            }

            // 3. List of Indexers
            if (filteredIndexers.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Text(
                            text = "No indexers found matching criteria",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredIndexers, key = { it.id }) { indexer ->
                    val isEnabled = manager.isIndexerEnabled(indexer.id)
                    val status = statuses[indexer.id] ?: IndexerStatus(indexer.id, isEnabled)

                    IndexerCard(
                        definition = indexer,
                        isEnabled = isEnabled,
                        status = status,
                        onToggleEnabled = { enabled ->
                            manager.setIndexerEnabled(indexer.id, enabled)
                        },
                        onTest = {
                            scope.launch {
                                val (ok, msg) = manager.testIndexer(indexer.id)
                                Toast.makeText(context, "${indexer.name}: $msg", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onConfigureMirror = {
                            showMirrorDialog = indexer
                        },
                        onViewYaml = {
                            showYamlDialog = indexer
                        },
                        onDeleteCustom = if (indexer.isCustom) {
                            {
                                scope.launch {
                                    manager.removeCustomDefinition(indexer.id)
                                    Toast.makeText(context, "Removed custom indexer ${indexer.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }

    // Add Custom YAML Dialog
    if (showAddDialog) {
        var yamlInput by remember { mutableStateOf(EXAMPLE_YAML_TEMPLATE) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Cardigann Indexer YAML") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste any Prowlarr V11 YAML definition below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = yamlInput,
                        onValueChange = {
                            yamlInput = it
                            errorMsg = null
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val res = manager.addCustomDefinition(yamlInput)
                            isSaving = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Added indexer ${res.getOrNull()?.name}", Toast.LENGTH_SHORT).show()
                                showAddDialog = false
                            } else {
                                errorMsg = res.exceptionOrNull()?.message ?: "Failed to parse YAML"
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text("Install Indexer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Mirror Dialog
    showMirrorDialog?.let { def ->
        var mirrorInput by remember { mutableStateOf(manager.getCustomMirror(def.id) ?: def.links.firstOrNull() ?: "") }

        AlertDialog(
            onDismissRequest = { showMirrorDialog = null },
            title = { Text("Configure Endpoint: ${def.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default mirrors:\n" + def.links.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = mirrorInput,
                        onValueChange = { mirrorInput = it },
                        label = { Text("Active Mirror URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        manager.setCustomMirror(def.id, mirrorInput.trim())
                        Toast.makeText(context, "Updated mirror for ${def.name}", Toast.LENGTH_SHORT).show()
                        showMirrorDialog = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMirrorDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // View YAML Dialog
    showYamlDialog?.let { def ->
        AlertDialog(
            onDismissRequest = { showYamlDialog = null },
            title = { Text("Definition: ${def.name}") },
            text = {
                OutlinedTextField(
                    value = def.sourceYaml,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            },
            confirmButton = {
                Button(onClick = { showYamlDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun IndexerCard(
    definition: CardigannIndexerDefinition,
    isEnabled: Boolean,
    status: IndexerStatus,
    onToggleEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onConfigureMirror: () -> Unit,
    onViewYaml: () -> Unit,
    onDeleteCustom: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Name, Badges & Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = definition.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        // Type Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (definition.type.equals("public", true)) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFE65100).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = definition.type.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (definition.type.equals("public", true)) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (definition.isCustom) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "CUSTOM",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (definition.description.isNotBlank()) {
                        Text(
                            text = definition.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Categories & Capabilities Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val cats = definition.caps.categoryMappings.map { it.cat }.distinct().take(4)
                for (cat in cats) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(cat, fontSize = 11.sp) },
                        modifier = Modifier.height(26.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Health status indicator
                StatusBadge(status = status, isEnabled = isEnabled)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action toolbar: Test button, Mirror config, View YAML, Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = isEnabled,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onConfigureMirror,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mirror", fontSize = 12.sp)
                }

                IconButton(
                    onClick = onViewYaml,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = "View YAML", modifier = Modifier.size(18.dp))
                }

                if (onDeleteCustom != null) {
                    IconButton(
                        onClick = onDeleteCustom,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: IndexerStatus, isEnabled: Boolean) {
    if (!isEnabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Disabled", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        return
    }

    when (status.state) {
        IndexerHealthState.ONLINE -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${status.latencyMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        IndexerHealthState.TESTING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Testing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        IndexerHealthState.DEGRADED, IndexerHealthState.OFFLINE -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFD32F2F)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Error", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD32F2F))
            }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF1976D2)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ready", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1976D2))
            }
        }
    }
}

private const val EXAMPLE_YAML_TEMPLATE = """---
id: custom_indexer
name: Custom Indexer
description: "Prowlarr V11 Indexer Definition"
language: en-US
type: public
encoding: UTF-8
links:
  - https://example-torrent-site.com/

caps:
  categorymappings:
    - { id: Movies, cat: Movies, desc: "Movies" }
    - { id: TV, cat: TV, desc: "TV" }

  modes:
    search: [q]
    tv-search: [q, season, ep]
    movie-search: [q]

search:
  paths:
    - path: "search?q={{ .Keywords }}"
  rows:
    selector: table tr:has(a[href^="magnet:?"])
  fields:
    title:
      selector: a.title
    magnet:
      selector: a[href^="magnet:?"]
      attribute: href
    size:
      selector: td.size
    seeders:
      selector: td.seeds
    leechers:
      selector: td.leeches
"""
