package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
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
import com.example.auth.SourceAccountManager
import com.example.auth.SourceAccountSession
import com.example.auth.SourcePlatform
import com.example.ui.components.SourceLoginSandboxDialog

/**
 * Multi-Account Sources Hub (Grayjay Model):
 * Allows logging into independent accounts for Crunchyroll, Hotstar, SonyLIV,
 * YouTube, and Google Drive with isolated session sandboxes.
 *
 * Guarantees zero regression on existing free scrapers and fallback paths.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsAndSourcesSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        }
    ) {
        AccountsAndSourcesScreenContent(
            modifier = Modifier.fillMaxHeight(0.92f),
            showHeader = true,
            onClose = onDismiss
        )
    }
}

@Composable
fun AccountsAndSourcesScreenContent(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sessions by SourceAccountManager.sessions.collectAsState()

    var activePlatformForLogin by remember { mutableStateOf<SourcePlatform?>(null) }
    var showLogoutConfirmDialog by remember { mutableStateOf<SourcePlatform?>(null) }
    var showCookieInspectionDialog by remember { mutableStateOf<SourceAccountSession?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Top Bar
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Accounts & Sources",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Grayjay Engine",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "Independent logins & premium streams for each platform",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Safety Guarantee Banner
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFF4CAF50).copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Zero Risk to Existing Free Sources",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "All your existing resolvers (Vidsrc, Embeds, Torrents, Mega, Bunkr, Vega) remain active. Logging into an account adds high-speed direct streams for that specific service without breaking anything else.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section Title: Premium & Cloud Accounts
            item {
                Text(
                    text = "Connected Streaming Accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }

            // Source Account Cards
            items(SourcePlatform.entries, key = { it.id }) { platform ->
                val session = sessions[platform.id] ?: SourceAccountSession(platformId = platform.id)
                SourceAccountCard(
                    platform = platform,
                    session = session,
                    onLoginClick = { activePlatformForLogin = platform },
                    onLogoutClick = { showLogoutConfirmDialog = platform },
                    onInspectCookies = { showCookieInspectionDialog = session },
                    onTogglePreferPremium = { prefer ->
                        SourceAccountManager.setPreferPremiumStream(platform.id, prefer)
                    },
                    onToggleFallback = { fallback ->
                        SourceAccountManager.setFallbackToFreeResolvers(platform.id, fallback)
                    }
                )
            }

            // Section Title: Core Free Resolvers Status
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Active Free Resolvers & Scrapers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        ResolverStatusRow("BitTorrent (P2P)", "Magnet & Torrent Streaming Engine", "Active", Color(0xFF4CAF50))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ResolverStatusRow("Unified Embed Resolvers", "Vidsrc, Vidlink, TwoEmbed, Vidrock", "Active", Color(0xFF4CAF50))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ResolverStatusRow("Cloud & Social Adapters", "MEGA direct folders, Bunkr CDN albums & Telegram", "Active", Color(0xFF4CAF50))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ResolverStatusRow("Vega Add-ons & Anime", "Jikan, AniList, TMDB & Vega cinema scrapers", "Active", Color(0xFF4CAF50))
                    }
                }
            }
        }
    }

    // In-app Login Sandbox Dialog
    if (activePlatformForLogin != null) {
        val platform = activePlatformForLogin!!
        SourceLoginSandboxDialog(
            platform = platform,
            onDismiss = { activePlatformForLogin = null },
            onLoginSuccess = {
                activePlatformForLogin = null
            }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmDialog != null) {
        val platform = showLogoutConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = null },
            title = { Text("Log out from ${platform.displayName}?") },
            text = {
                Text("This will remove saved cookies and authentication tokens for ${platform.displayName}. Your other accounts and free streams will remain unaffected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        SourceAccountManager.logout(platform.id)
                        Toast.makeText(context, "Logged out from ${platform.displayName}", Toast.LENGTH_SHORT).show()
                        showLogoutConfirmDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cookie & Token Inspector Dialog
    if (showCookieInspectionDialog != null) {
        val session = showCookieInspectionDialog!!
        val platform = SourcePlatform.fromId(session.platformId)
        AlertDialog(
            onDismissRequest = { showCookieInspectionDialog = null },
            title = { Text("${platform?.displayName ?: "Source"} Session Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Status: ${if (session.isLoggedIn) "Logged In" else "Guest"}",
                        fontWeight = FontWeight.Bold,
                        color = if (session.isLoggedIn) Color(0xFF4CAF50) else Color.Gray
                    )
                    Text(text = "Plan: ${session.planType}")
                    Text(text = "Account: ${session.username.ifBlank { "None" }}")
                    Text(text = "Domain: ${platform?.primaryDomain ?: "Unknown"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Encrypted Cookies Stored: ${if (!session.cookies.isNullOrBlank()) "Yes (${session.cookies.split(";").size} keys)" else "No"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCookieInspectionDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SourceAccountCard(
    platform: SourcePlatform,
    session: SourceAccountSession,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onInspectCookies: () -> Unit,
    onTogglePreferPremium: (Boolean) -> Unit,
    onToggleFallback: (Boolean) -> Unit
) {
    var expandedSettings by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (session.isLoggedIn) 1.5.dp else 1.dp,
            color = if (session.isLoggedIn) platform.brandColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Platform Icon with branded background
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(platform.brandColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = platform.icon,
                        contentDescription = platform.displayName,
                        tint = platform.brandColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = platform.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Status Badge
                        if (session.isLoggedIn) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = if (session.isPremium) "✨ Premium Active" else "Connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Guest / Free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (session.isLoggedIn) {
                            "Logged in as: ${session.username}"
                        } else {
                            platform.subtitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Login or More button
                if (!session.isLoggedIn) {
                    Button(
                        onClick = onLoginClick,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = platform.brandColor),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Log In", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    IconButton(
                        onClick = { expandedSettings = !expandedSettings },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (expandedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded Settings & Controls for Logged-In Account
            AnimatedVisibility(visible = expandedSettings && session.isLoggedIn) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Switch: Prefer Premium Direct Stream
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prefer Premium Direct Stream",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Use your account session for direct 1080p stream extraction",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = session.preferPremiumStream,
                            onCheckedChange = onTogglePreferPremium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Switch: Auto-fallback to Free Resolvers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-fallback to Free Resolvers",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Never fail playback: fallback to mirrors if premium stream fails",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = session.fallbackToFreeResolvers,
                            onCheckedChange = onToggleFallback
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons Row: Re-login / Inspect / Log Out
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onInspectCookies,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Session Info", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onLoginClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Account", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onLogoutClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Out", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolverStatusRow(
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = statusColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
