package com.example.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.resolver.SourceCandidate

private const val TAG = "EmbedWebViewPlayer"

/**
 * Isolated, high-performance WebView player for [SourceStreamType.EMBED_WEBVIEW] sources.
 *
 * Features:
 * - On-demand creation with deterministic lifecycle disposal.
 * - Strict popup/redirect isolation rejecting non-streaming navigation & ad schemes.
 * - Fullscreen HTML5 video container integration.
 * - Renderer crash recovery and network error states.
 */
@Composable
fun EmbedWebViewPlayer(
    candidate: SourceCandidate,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember(candidate.urlOrMagnet) { mutableStateOf(true) }
    var errorMessage by remember(candidate.urlOrMagnet) { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customViewRef by remember { mutableStateOf<View?>(null) }

    val initialUrl = candidate.urlOrMagnet
    val providerName = candidate.providerName.ifBlank { "Embed Provider" }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main WebView Layer
        AndroidView(
            factory = { ctx ->
                createConfiguredWebView(
                    context = ctx,
                    initialUrl = initialUrl,
                    providerName = providerName,
                    onLoadingChanged = { loading -> isLoading = loading },
                    onError = { err -> errorMessage = err },
                    onCustomViewShow = { view -> customViewRef = view },
                    onCustomViewHide = { customViewRef = null }
                ).also { webViewRef = it }
            },
            update = { view ->
                if (view.url != initialUrl && errorMessage == null) {
                    view.loadUrl(initialUrl)
                }
            },
            onRelease = { view ->
                safelyDisposeWebView(view)
            },
            modifier = Modifier.fillMaxSize()
        )

        // HTML5 Video Fullscreen Overlay Container if active
        customViewRef?.let { customView ->
            AndroidView(
                factory = {
                    FrameLayout(it).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        addView(customView)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // Top Control Overlay Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Embed Player",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = providerName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            IconButton(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    webViewRef?.reload()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reload Embed Player",
                    tint = Color.White
                )
            }
        }

        // Loading Overlay
        AnimatedVisibility(
            visible = isLoading && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading $providerName...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // Error Banner Overlay
        errorMessage?.let { errorText ->
            Surface(
                color = Color(0xFF1E1010).copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Embed Player Error",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            webViewRef?.loadUrl(initialUrl)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Loading")
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    initialUrl: String,
    providerName: String,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onCustomViewShow: (View) -> Unit,
    onCustomViewHide: () -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        setBackgroundColor(android.graphics.Color.BLACK)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            databaseEnabled = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // Reject popup window creation attempts
                Log.d(TAG, "Blocked popup window request from embed player")
                return false
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null) {
                    onCustomViewShow(view)
                }
            }

            override fun onHideCustomView() {
                onCustomViewHide()
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""
                val host = uri.host?.lowercase() ?: ""

                // Reject non-http/https custom schemes
                if (scheme != "http" && scheme != "https") {
                    Log.w(TAG, "Blocked non-web scheme navigation: $uri")
                    return true
                }

                // Allow allowed embed hosts and video playback subdomains
                val isAllowedHost = isAuthorizedEmbedDomain(host)
                if (!isAllowedHost) {
                    Log.w(TAG, "Blocked external redirect to unauthorized host: $host")
                    return true // Block external redirects
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoadingChanged(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingChanged(false)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val desc = error?.description?.toString() ?: "Failed to load embed stream"
                    Log.e(TAG, "WebView main frame error: $desc")
                    onLoadingChanged(false)
                    onError("Connection error: $desc")
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                Log.e(TAG, "WebView render process crashed or gone")
                onLoadingChanged(false)
                onError("Embed render process terminated. Tap retry to reload.")
                return true // Handled safely without process termination
            }
        }

        loadUrl(initialUrl)
    }
}

private fun isAuthorizedEmbedDomain(host: String): Boolean {
    val allowedDomains = listOf(
        "vidlink.pro",
        "vidsrc.to",
        "2embed.cc",
        "vidsrc.me",
        "vidsrcme.ru",
        "secstream.pro",
        "autoembed.cc",
        "embed.su",
        "smashy.stream",
        "cloudnest.pro",
        "vidsrc.cc",
        "vidsrc.xyz",
        "vidsrc.in",
        "vidsrc.pm",
        "vidsrc.net"
    )
    return allowedDomains.any { domain ->
        host == domain || host.endsWith(".$domain")
    }
}

private fun safelyDisposeWebView(webView: WebView) {
    try {
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            onPause()
            removeAllViews()
            destroy()
        }
        Log.d(TAG, "WebView safely disposed")
    } catch (e: Exception) {
        Log.w(TAG, "Error disposing WebView: ${e.message}")
    }
}
