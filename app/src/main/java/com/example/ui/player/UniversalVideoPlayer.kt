package com.example.ui.player

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val rawVideoUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl ?: embedUrl

    var playerError by remember { mutableStateOf<String?>(null) }
    var forceWebViewFallback by remember { mutableStateOf(false) }

    val isMagnetLink = remember(rawVideoUrl) {
        val url = rawVideoUrl?.lowercase() ?: ""
        url.startsWith("magnet:") || url.contains("magnet:?xt=")
    }

    val isEmbedOrWebPage = remember(rawVideoUrl, streamOption, forceWebViewFallback, isMagnetLink) {
        if (isMagnetLink) return@remember true
        if (forceWebViewFallback) return@remember true
        val url = rawVideoUrl?.lowercase() ?: ""
        val fmt = streamOption?.format?.lowercase() ?: ""
        fmt == "embed" ||
                url.contains("embed") ||
                url.contains("eporner.com") ||
                url.contains("apijav") ||
                url.contains("dailymotion.com") ||
                url.contains("vimeo.com") ||
                url.contains("peertube") ||
                url.contains("nvembed") ||
                url.contains("mvembed") ||
                (url.startsWith("http") && !url.contains(".mp4") && !url.contains(".m3u8") && !url.contains("googlevideo.com"))
    }

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

    val okHttpClient = remember { OkHttpClient.Builder().build() }
    val dataSourceFactory = remember {
        OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage ?: "Playback error occurred"
                forceWebViewFallback = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(streamOption, hlsUrl, captionOption, isEmbedOrWebPage) {
        if (!isEmbedOrWebPage && !rawVideoUrl.isNullOrEmpty()) {
            playerError = null
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            try {
                if (streamOption != null) {
                    val vUrl = streamOption.videoUrl ?: streamOption.videoStream?.url
                    val aUrl = streamOption.audioUrl ?: streamOption.audioStream?.url

                    if (streamOption.isMuxed && vUrl != null) {
                        val builder = MediaItem.Builder().setUri(Uri.parse(vUrl))
                        if (captionOption != null) {
                            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(captionOption.url))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(captionOption.languageCode)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                            builder.setSubtitleConfigurations(listOf(subtitleConfig))
                        }
                        exoPlayer.setMediaItem(builder.build())
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    } else if (!streamOption.isMuxed && vUrl != null && aUrl != null) {
                        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(Uri.parse(vUrl)))
                        val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(Uri.parse(aUrl)))

                        val mergedSource = MergingMediaSource(videoSource, audioSource)
                        exoPlayer.setMediaSource(mergedSource)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                } else if (!hlsUrl.isNullOrEmpty()) {
                    val mediaItem = MediaItem.fromUri(Uri.parse(hlsUrl))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            } catch (e: Exception) {
                playerError = e.localizedMessage
                forceWebViewFallback = true
            }
        } else {
            exoPlayer.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val halfWidth = size.width / 2
                        if (offset.x < halfWidth) {
                            seekNoticeText = "◄◄ 10s Rewind"
                            if (!isEmbedOrWebPage) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            }
                        } else {
                            seekNoticeText = "10s Forward ►►"
                            if (!isEmbedOrWebPage) {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
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
                                                p[class*="promo"], span[class*="promo"], a[class*="promo"] {
                                                    display: none !important;
                                                    visibility: hidden !important;
                                                    height: 0 !important;
                                                    opacity: 0 !important;
                                                    pointer-events: none !important;
                                                }
                                                html, body {
                                                    background-color: #000000 !important;
                                                    margin: 0 !important;
                                                    padding: 0 !important;
                                                    overflow: hidden !important;
                                                }
                                            `;
                                            (document.head || document.documentElement).appendChild(style);
                                        }

                                        function forcePlayAll() {
                                            var playSelectors = [
                                                'button.play', '.play-button', '.vjs-big-play-button', '.ytp-large-play-button',
                                                '.play_btn', '[aria-label="Play"]', '.plyr__control--overlaid', '.jw-icon-display',
                                                'div[class*="play"]', 'a[class*="play"]', '.p-button', '#play_button', '.play-overlay',
                                                '.video-play-btn', '.overlay-play'
                                            ];
                                            playSelectors.forEach(function(sel) {
                                                var els = document.querySelectorAll(sel);
                                                els.forEach(function(el) {
                                                    try { el.click(); } catch(e){}
                                                });
                                            });

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
                                        }

                                        forcePlayAll();
                                        setTimeout(forcePlayAll, 500);
                                        setTimeout(forcePlayAll, 1200);
                                    } catch(e){}
                                })();
                            """.trimIndent()

                            var scriptInjected = false
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    if (newProgress >= 70 && !scriptInjected) {
                                        scriptInjected = true
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
                        
                        val playPauseScript = if (isPlaying) {
                            "(function(){ var vids = document.querySelectorAll('video'); vids.forEach(function(v){ try { v.play(); } catch(e){} }); })();"
                        } else {
                            "(function(){ var vids = document.querySelectorAll('video'); vids.forEach(function(v){ try { v.pause(); } catch(e){} }); })();"
                        }
                        webView.evaluateJavascript(playPauseScript, null)
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
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = resizeModeState
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

        // Top Overlay Bar: Speed, Aspect Ratio, & Source Toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Speed Toggle Button
            TextButton(
                onClick = {
                    playbackSpeed = when (playbackSpeed) {
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2.0f
                        2.0f -> 0.75f
                        else -> 1.0f
                    }
                    seekNoticeText = "Speed: ${playbackSpeed}x"
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "${playbackSpeed}x",
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Aspect Ratio / Fit Toggle Button
            IconButton(
                onClick = {
                    resizeModeState = when (resizeModeState) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    val label = when (resizeModeState) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom Mode"
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch Mode"
                        else -> "Fit Mode"
                    }
                    seekNoticeText = "Display: $label"
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Aspect Ratio",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Web/Native Switch Toggle
            IconButton(
                onClick = {
                    forceWebViewFallback = !forceWebViewFallback
                    seekNoticeText = if (forceWebViewFallback) "Switched to Web Embed Engine" else "Switched to Native ExoPlayer"
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isEmbedOrWebPage) Icons.Default.Public else Icons.Default.OndemandVideo,
                    contentDescription = "Toggle Player Engine",
                    tint = if (forceWebViewFallback) Color.Cyan else Color.LightGray,
                    modifier = Modifier.size(16.dp)
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
