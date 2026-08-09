package com.example.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.PlaybackParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UniversalVideoPlayer(
    streamOption: PlayableStreamOption?,
    hlsUrl: String?,
    captionOption: CaptionOption?,
    embedUrl: String? = null,
    providerId: String? = null,
    isPlaying: Boolean = true,
    videoId: String? = null,
    initialPositionMs: Long = 0L,
    failedSourceLogs: List<com.example.model.FailedSourceLog> = emptyList(),
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val rawVideoUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl ?: embedUrl

    var playerError by remember { mutableStateOf<String?>(null) }
    var forceWebViewFallback by remember { mutableStateOf(false) }

    val playbackSourceType = remember(rawVideoUrl, streamOption, forceWebViewFallback) {
        if (forceWebViewFallback) com.example.model.PlaybackSourceType.EMBED_WEBVIEW
        else com.example.model.PlaybackDecisionResolver.determineSourceType(rawVideoUrl, streamOption?.format)
    }

    val isMagnetLink = playbackSourceType == com.example.model.PlaybackSourceType.MAGNET
    val isEmbedOrWebPage = playbackSourceType == com.example.model.PlaybackSourceType.EMBED_WEBVIEW

    // Playback Speed & Scaling Controls
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var resizeModeState by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Gesture Seek Notification Toast
    var seekNoticeText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seekNoticeText) {
        if (seekNoticeText != null) {
            delay(1200)
            seekNoticeText = null
        }
    }

    val exoPlayer = remember(context) { GlobalPlayerManager.getExoPlayer(context) }
    val seekSecs = remember(context) { com.example.util.DebridSettingsManager.getDoubleTapSeekSecs(context) }

    LaunchedEffect(playbackSpeed) {
        GlobalPlayerManager.setPlaybackSpeed(playbackSpeed)
    }

    // Continuous progress tracking loop
    val curPos by GlobalPlayerManager.currentPositionMs.collectAsState()
    val durMs by GlobalPlayerManager.durationMs.collectAsState()

    LaunchedEffect(curPos, durMs) {
        if (durMs > 0 && curPos >= 0) {
            onProgressUpdate(curPos, durMs)
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) GlobalPlayerManager.play() else GlobalPlayerManager.pause()
    }

    LaunchedEffect(streamOption, hlsUrl, captionOption, isEmbedOrWebPage, videoId) {
        GlobalPlayerManager.prepareAndPlay(
            context = context,
            streamData = null,
            streamOption = streamOption,
            hlsUrl = hlsUrl,
            captionOption = captionOption,
            embedUrl = embedUrl,
            initialPos = initialPositionMs
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .pointerInput(seekSecs) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val halfWidth = size.width / 2
                        val seekMs = seekSecs * 1000L
                        if (offset.x < halfWidth) {
                            seekNoticeText = "◄◄ ${seekSecs}s Rewind"
                            if (!isEmbedOrWebPage) {
                                GlobalPlayerManager.seekTo(exoPlayer.currentPosition - seekMs)
                            }
                        } else {
                            seekNoticeText = "${seekSecs}s Forward ►►"
                            if (!isEmbedOrWebPage) {
                                GlobalPlayerManager.seekTo(exoPlayer.currentPosition + seekMs)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isEmbedOrWebPage && !rawVideoUrl.isNullOrEmpty()) {
            val srcUrl = remember(rawVideoUrl, isMagnetLink) {
                var cleanUrl = (rawVideoUrl ?: "")
                    .replace("&#038;", "&")
                    .replace("&amp;", "&")
                    .replace("&#38;", "&")
                    .replace("&quot;", "")
                    .trim()

                if (isMagnetLink) {
                    val fullMagnet = com.example.utils.TorrentUtils.formatMagnetUrl(cleanUrl)
                    val encodedMagnet = try { URLEncoder.encode(fullMagnet, "UTF-8") } catch (e: Exception) { fullMagnet }
                    "https://webtor.io/show?magnet=$encodedMagnet"
                } else {
                    if (!cleanUrl.contains("autoplay")) {
                        cleanUrl += if (cleanUrl.contains("?")) "&autoplay=1" else "?autoplay=1"
                    }
                    cleanUrl
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

                            val cleanPlayerScript = """
                                (function() {
                                    try {
                                        var styleId = '__clean_player_style';
                                        if (!document.getElementById(styleId)) {
                                            var style = document.createElement('style');
                                            style.id = styleId;
                                            style.type = 'text/css';
                                            style.innerHTML = `
                                                header, nav, .navbar, .header, #header, .top-bar,
                                                footer, .footer, .sidebar, .comments, .related-posts,
                                                .site-header, .site-footer, .webtor-header, .download-box,
                                                .webtor-promo, .promo-banner, .unlock-banner, .ad-box,
                                                div[class*="promo"], div[class*="unlock"], div[class*="banner"],
                                                p[class*="promo"], span[class*="promo"], a[class*="promo"],
                                                .logo, .brand, div[class*="logo"], div[class*="brand"], a[class*="logo"],
                                                div[class*="top"], .mvapm-logo, .site-title, [class*="apijav"], [class*="APIJAV"],
                                                .servers, .server-list, .servers-list, #servers, .server-tabs, .server-btn,
                                                .server_list, .servers_list, .selector, .server-select, .nav-server, .header-server,
                                                .ep-servers, #server-list, .servers-tab, .server-box, .servers-box,
                                                ul[class*="server"], li[class*="server"], .nav-tabs, .nav-item, .nav-link,
                                                .server-item, .server-node, .server-group, .server-items, .server-buttons,
                                                div[class*="server-"], div[class*="-server"], div[class*="servers"],
                                                .switches, .select-server, ul[class*="tab"], div[class*="tab-"], .tab-content,
                                                div[class*="direct"], button[class*="direct"], span[class*="direct"], .direct-node,
                                                div[class*="node"], div[class*="server"], .server-switcher, #server-switcher,
                                                div[style*="position: absolute; top: 0"], div[style*="position:fixed; top:0"],
                                                .watermark, .logo-watermark, .player-logo, .jw-logo, .vjs-watermark {
                                                    display: none !important;
                                                    visibility: hidden !important;
                                                    height: 0 !important;
                                                    max-height: 0 !important;
                                                    opacity: 0 !important;
                                                    pointer-events: none !important;
                                                    margin: 0 !important;
                                                    padding: 0 !important;
                                                    overflow: hidden !important;
                                                }
                                                html, body, #player, .player, .video-player, .player-container, #player-container,
                                                .embed-responsive, iframe, video, object, embed, .jwplayer, .vjs-tech, .video-js,
                                                #video-player, #main-player, .dplayer, .plyr, #vjs_video_3, .video-content {
                                                    background-color: #000000 !important;
                                                    margin: 0 !important;
                                                    padding: 0 !important;
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    max-width: 100% !important;
                                                    max-height: 100% !important;
                                                    box-sizing: border-box !important;
                                                    border: none !important;
                                                    overflow: hidden !important;
                                                    object-fit: cover !important;
                                                }
                                                video {
                                                    object-fit: contain !important;
                                                    width: 100vw !important;
                                                    height: 100vh !important;
                                                }
                                            `;
                                            (document.head || document.documentElement).appendChild(style);
                                        }

                                        function cleanDomAndPlay() {
                                            try {
                                                var targets = document.querySelectorAll('div, ul, li, nav, header, span, a, p, button');
                                                targets.forEach(function(el) {
                                                    var txt = (el.textContent || '').trim().toUpperCase();
                                                    if ((txt.includes('PRO HD') || txt.includes('ALT 1') || txt.includes('AVDB') || txt.includes('APIJAV') || txt.includes('DIRECT')) &&
                                                        !el.querySelector('video') && !el.querySelector('iframe')) {
                                                        el.style.display = 'none';
                                                        el.style.visibility = 'hidden';
                                                        el.style.height = '0px';
                                                        el.style.pointerEvents = 'none';
                                                    }
                                                });
                                            } catch(e) {}

                                            try {
                                                var vids = document.querySelectorAll('video');
                                                vids.forEach(function(v) {
                                                    v.style.width = '100vw';
                                                    v.style.height = '100vh';
                                                    v.style.objectFit = 'contain';
                                                    if (v.parentElement && v.parentElement.tagName !== 'BODY') {
                                                        v.parentElement.style.width = '100vw';
                                                        v.parentElement.style.height = '100vh';
                                                        v.parentElement.style.margin = '0';
                                                        v.parentElement.style.padding = '0';
                                                    }
                                                });
                                            } catch(e) {}

                                            try {
                                                var playSelectors = [
                                                    'button.play', '.play-button', '.vjs-big-play-button', '.ytp-large-play-button',
                                                    '.play_btn', '[aria-label="Play"]', '.plyr__control--overlaid', '.jw-icon-display',
                                                    'div[class*="play"]', 'a[class*="play"]', '.p-button', '#play_button', '.play-overlay',
                                                    '.video-play-btn', '.overlay-play', '.click-to-play', '.play-icon', '.play_icon',
                                                    'div[onclick*="play"]', '.play-trigger', '#play'
                                                ];
                                                playSelectors.forEach(function(sel) {
                                                    var els = document.querySelectorAll(sel);
                                                    els.forEach(function(el) {
                                                        try {
                                                            el.click();
                                                            var ev = new MouseEvent('click', { view: window, bubbles: true, cancelable: true });
                                                            el.dispatchEvent(ev);
                                                        } catch(e){}
                                                    });
                                                });

                                                var centerX = window.innerWidth / 2;
                                                var centerY = window.innerHeight / 2;
                                                var centerEl = document.elementFromPoint(centerX, centerY);
                                                if (centerEl && !centerEl.tagName.match(/VIDEO/i) && !centerEl.tagName.match(/HTML/i) && !centerEl.tagName.match(/BODY/i)) {
                                                    try {
                                                        centerEl.click();
                                                        var ev = new MouseEvent('click', { view: window, bubbles: true, cancelable: true });
                                                        centerEl.dispatchEvent(ev);
                                                    } catch(e){}
                                                }

                                                var vids = document.querySelectorAll('video');
                                                vids.forEach(function(v) {
                                                    try {
                                                        v.muted = false;
                                                        var p = v.play();
                                                        if (p !== undefined) {
                                                            p.catch(function() {
                                                                v.muted = true;
                                                                v.play().catch(function(){});
                                                            });
                                                        }
                                                    } catch(e){}
                                                });
                                            } catch(e){}
                                        }

                                        cleanDomAndPlay();
                                        var loopCount = 0;
                                        var playInterval = setInterval(function() {
                                            cleanDomAndPlay();
                                            loopCount++;
                                            if (loopCount > 25) { clearInterval(playInterval); }
                                        }, 350);
                                    } catch(e){}
                                })();
                            """.trimIndent()

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    if (newProgress >= 40) {
                                        view?.evaluateJavascript(cleanPlayerScript, null)
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url?.toString() ?: ""
                                    if (reqUrl.startsWith("magnet:") || reqUrl.startsWith("intent:") || reqUrl.startsWith("torrent:") || reqUrl.startsWith("seedr:")) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            ctx.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "Opening magnet stream", Toast.LENGTH_SHORT).show()
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(cleanPlayerScript, null)
                                    GlobalPlayerManager.notifyFirstFrameRendered()
                                }
                            }

                            tag = rawVideoUrl
                            if (isMagnetLink) {
                                val htmlContent = buildWebTorHtml(rawVideoUrl ?: "")
                                loadDataWithBaseURL("https://webtor.io", htmlContent, "text/html", "UTF-8", null)
                            } else {
                                loadUrl(srcUrl)
                            }
                        }
                    },
                    update = { webView ->
                        val currentRawUrl = webView.tag as? String
                        if (currentRawUrl != rawVideoUrl) {
                            webView.tag = rawVideoUrl
                            if (isMagnetLink) {
                                val htmlContent = buildWebTorHtml(rawVideoUrl ?: "")
                                webView.loadDataWithBaseURL("https://webtor.io", htmlContent, "text/html", "UTF-8", null)
                            } else {
                                webView.loadUrl(srcUrl)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isMagnetLink) {
                    // Magnet Action Overlay Chips at top right
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawVideoUrl))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No external torrent app found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in Torrent App",
                                tint = Color.Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Magnet Link", rawVideoUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Magnet URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Magnet",
                                tint = Color.Yellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            val currentPlayerContext = LocalContext.current
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = resizeModeState
                        setFullscreenButtonClickListener { isFullscreen ->
                            toggleFullscreen(currentPlayerContext)
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = resizeModeState
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Floating Action Buttons Row (Bottom Right: Fullscreen & Picture-in-Picture)
        val playerScreenContext = LocalContext.current
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PiP Mode Button
            IconButton(
                onClick = { enterPipMode(playerScreenContext) },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription = "Picture in Picture Mode",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Fullscreen / Landscape Toggle Button
            IconButton(
                onClick = { toggleFullscreen(playerScreenContext) },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Toggle Fullscreen / Landscape",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Gesture Seek Notice Overlay
        AnimatedVisibility(
            visible = seekNoticeText != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = seekNoticeText ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun buildWebTorHtml(magnetUrl: String): String {
    val formattedMagnet = com.example.utils.TorrentUtils.formatMagnetUrl(magnetUrl)
    val escapedMagnet = formattedMagnet
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "")
        .replace("\r", "")
        .trim()
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body {
                    width: 100vw;
                    height: 100vh;
                    background-color: #000000;
                    color: #ffffff;
                    font-family: system-ui, -apple-system, sans-serif;
                    overflow: hidden;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                #player {
                    width: 100vw;
                    height: 100vh;
                }
                iframe {
                    border: none !important;
                    width: 100% !important;
                    height: 100% !important;
                }
                header, nav, .navbar, .header, #header, .top-bar,
                footer, .footer, .sidebar, .comments, .related-posts,
                .site-header, .site-footer, .webtor-header, .download-box,
                div[class*="promo"], div[class*="unlock"], div[class*="banner"] {
                    display: none !important;
                    visibility: hidden !important;
                }
            </style>
            <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/@webtor/embed-sdk-js/dist/index.min.js" charset="utf-8" async></script>
        </head>
        <body>
            <div id="player"></div>
            <script>
                window.webtor = window.webtor || [];
                window.webtor.push({
                    id: 'player',
                    magnet: '$escapedMagnet',
                    width: '100%',
                    height: '100%',
                    features: {
                        title: false,
                        download: true,
                        subtitles: true,
                        settings: true,
                        continue: true
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun toggleFullscreen(context: Context) {
    val activity = context.findActivity() ?: return
    val currentOrientation = activity.requestedOrientation
    val isLandscape = currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    if (isLandscape) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private fun enterPipMode(context: Context) {
    val activity = context.findActivity() ?: return
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        try {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(context, "PiP Mode not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
