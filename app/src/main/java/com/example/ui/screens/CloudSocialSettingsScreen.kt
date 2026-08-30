package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudsocial.db.CloudSocialSourceEntity
import com.example.cloudsocial.repository.CloudSocialRepository
import com.example.cloudsocial.telegram.TelegramAuthState
import com.example.cloudsocial.telegram.TelegramSessionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSocialSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val repository = remember { CloudSocialRepository.getInstance(context) }
    val tgSessionManager = remember { TelegramSessionManager.getInstance(context) }

    val sources by repository.allSources.collectAsState(initial = emptyList())
    val isSyncing by repository.isSyncing.collectAsState()
    val tgAuthState by tgSessionManager.authState.collectAsState()
    val tgUserSession by tgSessionManager.userSession.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    // Telegram Auth Dialog state
    var showTgAuthDialog by remember { mutableStateOf(false) }
    var tgPhoneInput by remember { mutableStateOf("") }
    var tgCodeInput by remember { mutableStateOf("") }
    var tg2faInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud & Social Sources") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.syncAllSources()
                                Toast.makeText(context, "Sync triggered", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Refresh All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Source") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECTION 1: TELEGRAM ACCOUNT & SOURCES ---
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Telegram Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (tgAuthState == TelegramAuthState.AUTHENTICATED) {
                                OutlinedButton(onClick = { tgSessionManager.logout() }) {
                                    Text("Disconnect")
                                }
                            } else {
                                Button(onClick = { showTgAuthDialog = true }) {
                                    Text("Connect Account")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        if (tgAuthState == TelegramAuthState.AUTHENTICATED) {
                            Text(
                                "Connected: ${tgUserSession.username.ifBlank { tgUserSession.phoneNumber }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Connect your Telegram account to access restricted/private channels & chats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Telegram Channels List
            val tgSources = sources.filter { it.type == "TELEGRAM" }
            item {
                Text(
                    text = "Telegram Channels & Groups (${tgSources.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (tgSources.isEmpty()) {
                item {
                    Text(
                        text = "No Telegram channels imported yet. Tap 'Add Source' below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(tgSources) { source ->
                    SourceItemRow(source = source, onDelete = {
                        coroutineScope.launch { repository.deleteSource(source.id) }
                    })
                }
            }

            // --- SECTION 2: MEGA FILES & FOLDERS ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MEGA Cloud Folders & Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            val megaSources = sources.filter { it.type == "MEGA" }
            if (megaSources.isEmpty()) {
                item {
                    Text(
                        text = "No MEGA folders or links added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(megaSources) { source ->
                    SourceItemRow(source = source, onDelete = {
                        coroutineScope.launch { repository.deleteSource(source.id) }
                    })
                }
            }

            // --- SECTION 3: BUNKR ALBUMS & FILES ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bunkr Albums & Direct Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            val bunkrSources = sources.filter { it.type == "BUNKR" }
            if (bunkrSources.isEmpty()) {
                item {
                    Text(
                        text = "No Bunkr albums added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(bunkrSources) { source ->
                    SourceItemRow(source = source, onDelete = {
                        coroutineScope.launch { repository.deleteSource(source.id) }
                    })
                }
            }
        }
    }

    if (showAddDialog) {
        AddCloudSocialSourceDialog(
            onDismiss = { showAddDialog = false },
            onSourceAdded = {
                Toast.makeText(context, "Source added & indexed", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showTgAuthDialog) {
        AlertDialog(
            onDismissRequest = { showTgAuthDialog = false },
            title = { Text("Connect Telegram Account") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (tgAuthState) {
                        TelegramAuthState.DISCONNECTED, TelegramAuthState.WAITING_PHONE -> {
                            Text("Enter your phone number with country code:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = tgPhoneInput,
                                onValueChange = { tgPhoneInput = it },
                                label = { Text("Phone Number") },
                                placeholder = { Text("+1234567890") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        TelegramAuthState.WAITING_CODE -> {
                            Text("Enter the 5-digit verification code sent to your Telegram app:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = tgCodeInput,
                                onValueChange = { tgCodeInput = it },
                                label = { Text("Verification Code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        TelegramAuthState.WAITING_PASSWORD -> {
                            Text("Enter your 2FA Two-Step Verification Password:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = tg2faInput,
                                onValueChange = { tg2faInput = it },
                                label = { Text("2FA Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        else -> {
                            Text("Account is authenticated!")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        when (tgAuthState) {
                            TelegramAuthState.DISCONNECTED, TelegramAuthState.WAITING_PHONE -> {
                                val res = tgSessionManager.requestPhoneCode(tgPhoneInput)
                                if (res.isFailure) {
                                    Toast.makeText(context, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                                }
                            }

                            TelegramAuthState.WAITING_CODE -> {
                                val res = tgSessionManager.verifyCode(tgCodeInput)
                                if (res.isSuccess) {
                                    showTgAuthDialog = false
                                    Toast.makeText(context, "Telegram Connected!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                                }
                            }

                            TelegramAuthState.WAITING_PASSWORD -> {
                                val res = tgSessionManager.verifyCode(tgCodeInput, tg2faInput)
                                if (res.isSuccess) {
                                    showTgAuthDialog = false
                                    Toast.makeText(context, "Telegram Connected!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                                }
                            }

                            else -> showTgAuthDialog = false
                        }
                    }
                }) {
                    Text(if (tgAuthState == TelegramAuthState.WAITING_CODE) "Verify" else "Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTgAuthDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SourceItemRow(
    source: CloudSocialSourceEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(source.sourceUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Source", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
