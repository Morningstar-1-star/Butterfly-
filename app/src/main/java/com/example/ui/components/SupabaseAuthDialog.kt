package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.supabase.SupabaseAuthManager
import com.example.supabase.SupabaseConfig
import com.example.supabase.SupabaseSyncManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SupabaseAuthDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isLoggedIn by SupabaseAuthManager.isLoggedIn.collectAsState()
    val currentUser by SupabaseAuthManager.currentUser.collectAsState()
    val authError by SupabaseAuthManager.authError.collectAsState()
    val syncState by SupabaseSyncManager.syncState.collectAsState()

    var isSignUpTab by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Custom Server Config
    var showConfigSection by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf(SupabaseConfig.getUrl(context)) }
    var customKeyInput by remember { mutableStateOf(SupabaseConfig.getAnonKey(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isLoggedIn) "Supabase Cloud Sync" else "Sign in to Supabase",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoggedIn) {
                    // Logged In Dashboard
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Connected Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentUser?.email ?: "Active Account",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val lastSyncStr = if (syncState.lastSyncTimestamp > 0) {
                                SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(syncState.lastSyncTimestamp))
                            } else {
                                "Never"
                            }
                            Text(
                                text = "Last Synced: $lastSyncStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (syncState.pendingQueueCount > 0) {
                                Text(
                                    text = "Pending Offline Changes: ${syncState.pendingQueueCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Status: ${syncState.syncMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Auto-sync Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Sync", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Sync changes continuously in background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = syncState.autoSyncEnabled,
                            onCheckedChange = { SupabaseSyncManager.setAutoSyncEnabled(it) }
                        )
                    }

                    // Sync Now Button
                    Button(
                        onClick = { SupabaseSyncManager.triggerSync(forceFull = true) },
                        enabled = !syncState.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (syncState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }
                } else {
                    // Not Logged In - Sign In / Sign Up Form
                    TabRow(
                        selectedTabIndex = if (isSignUpTab) 1 else 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = !isSignUpTab,
                            onClick = { isSignUpTab = false },
                            text = { Text("Sign In") }
                        )
                        Tab(
                            selected = isSignUpTab,
                            onClick = { isSignUpTab = true },
                            text = { Text("Create Account") }
                        )
                    }

                    Text(
                        text = "Sync your watch history, bookmarks, likes, playlists, Bunkr, CloudSocial and taste preferences across all your devices securely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    authError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                isLoading = true
                                coroutineScope.launch {
                                    if (isSignUpTab) {
                                        SupabaseAuthManager.signUp(emailInput, passwordInput)
                                    } else {
                                        SupabaseAuthManager.signIn(emailInput, passwordInput)
                                    }
                                    isLoading = false
                                    if (SupabaseAuthManager.isLoggedIn.value) {
                                        SupabaseSyncManager.triggerSync(forceFull = true)
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (isSignUpTab) "Create Account" else "Sign In")
                        }
                    }
                }

                // Advanced Supabase Project Configuration Section
                TextButton(
                    onClick = { showConfigSection = !showConfigSection },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = if (showConfigSection) Icons.Default.ExpandLess else Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showConfigSection) "Hide Server Settings" else "Configure Custom Supabase Project",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                AnimatedVisibility(visible = showConfigSection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Supabase Project URL & Anon Key",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            label = { Text("Project URL (https://xxxx.supabase.co)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customKeyInput,
                            onValueChange = { customKeyInput = it },
                            label = { Text("Publishable Anon Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                SupabaseConfig.saveConfig(context, customUrlInput, customKeyInput)
                                showConfigSection = false
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Server Settings")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isLoggedIn) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            SupabaseAuthManager.signOut()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sign Out")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
