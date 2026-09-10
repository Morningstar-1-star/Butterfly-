package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.auth.SourceAccountManager
import com.example.auth.SourcePlatform
import kotlinx.coroutines.launch

/**
 * Isolated in-app Web Sandbox for logging into individual streaming platforms (Grayjay style).
 * Intercepts authentication session cookies upon user login and saves them to [SourceAccountManager].
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceLoginSandboxDialog(
    platform: SourcePlatform,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(platform.defaultLoginUrl) }
    var pageTitle by remember { mutableStateOf(platform.displayName) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var hasDetectedSession by remember { mutableStateOf(false) }
    var detectedCookiesCount by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = {
            webViewRef?.destroy()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Top Navigation Bar
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                webViewRef?.destroy()
                                onDismiss()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Sandbox")
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(platform.brandColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = platform.icon,
                                    contentDescription = null,
                                    tint = platform.brandColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sign in: ${platform.displayName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Secure",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = platform.primaryDomain,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Save Session / Done button
                            Button(
                                onClick = {
                                    val cookieManager = CookieManager.getInstance()
                                    val cookies = cookieManager.getCookie(currentUrl) ?: cookieManager.getCookie(platform.primaryDomain)
                                    if (!cookies.isNullOrBlank()) {
                                        SourceAccountManager.onLoginSuccess(
                                            platformId = platform.id,
                                            cookies = cookies,
                                            username = "Account (${platform.displayName})",
                                            isPremium = true,
                                            planType = "Premium Active"
                                        )

                                        if (platform == SourcePlatform.CRUNCHYROLL) {
                                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    com.example.extractor.CrunchyrollApiClient.getAuthInfo(forceRefresh = true)
                                                } catch (_: Exception) {}
                                            }
                                        }

                                        Toast.makeText(context, "${platform.displayName} session saved successfully!", Toast.LENGTH_SHORT).show()
                                        webViewRef?.destroy()
                                        onLoginSuccess()
                                    } else {
                                        Toast.makeText(context, "No active session detected yet. Please log in first.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasDetectedSession) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (hasDetectedSession) Icons.Default.Check else Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (hasDetectedSession) "Save Login" else "Save Session")
                            }
                        }

                        // Web Loading Progress indicator
                        if (pageProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                                color = platform.brandColor
                            )
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                // Sandbox Information Pill
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Isolated Sandbox: Credentials & cookies are kept on your device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (detectedCookiesCount > 0) {
                            Text(
                                text = "$detectedCookiesCount cookies",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }

                // WebView Container
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    setSupportZoom(true)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    cacheMode = WebSettings.LOAD_DEFAULT

                                    // Modern Chrome Android User-Agent to ensure all streaming sites render properly
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            pageTitle = title
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        url?.let { currentUrl = it }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        url?.let { finishedUrl ->
                                            currentUrl = finishedUrl
                                            val cookieManager = CookieManager.getInstance()
                                            val cookies = cookieManager.getCookie(finishedUrl)
                                            if (!cookies.isNullOrBlank()) {
                                                val count = cookies.split(";").size
                                                detectedCookiesCount = count

                                                // Check for indicators of an authenticated user session
                                                val lower = cookies.lowercase()
                                                val isAuthed = lower.contains("session") ||
                                                        lower.contains("token") ||
                                                        lower.contains("auth") ||
                                                        lower.contains("user_id") ||
                                                        lower.contains("cr_") ||
                                                        lower.contains("login") ||
                                                        !finishedUrl.contains("login") && !finishedUrl.contains("signin")

                                                if (isAuthed && count >= 2) {
                                                    hasDetectedSession = true
                                                }
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        return false // Keep all navigation within this sandbox
                                    }
                                }

                                loadUrl(platform.defaultLoginUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
