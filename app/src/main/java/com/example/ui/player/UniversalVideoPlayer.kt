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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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

@OptIn(ExperimentalMaterial3Api::class)
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
    availableStreamOptions: List<PlayableStreamOption> = emptyList(),
    onSelectStreamOption: (PlayableStreamOption) -> Unit = {},
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
    val playbackPrefs = remember(context) { com.example.util.PlaybackPreferences.getInstance(context) }
    val activeStreamData by GlobalPlayerManager.activeStreamData.collectAsState()

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSpeedSubMenu by remember { mutableStateOf(false) }
    var showQualitySubMenu by remember { mutableStateOf(false) }
    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f) }
    var customPlayerSpeedInput by remember { mutableStateOf("") }
    var isMusicTrackDetected by remember { mutableStateOf(false) }

    // Resolve Default Playback Speed according to settings & music detection
    LaunchedEffect(videoId, rawVideoUrl, activeStreamData) {
        val currentTitle = activeStreamData?.title
        val currentUploader = activeStreamData?.channelName
        val currentDesc = activeStreamData?.description
        val currentTags = activeStreamData?.relatedVideos?.flatMap { it.tags }
        val currentProvider = providerId ?: activeStreamData?.providerId

        val isMusic = com.example.util.PlaybackPreferences.isMusicMedia(
            title = currentTitle,
            uploaderName = currentUploader,
            description = currentDesc,
            tags = currentTags,
            providerId = currentProvider
        )
        isMusicTrackDetected = isMusic

        val targetSpeed = playbackPrefs.getEffectiveSpeed(isMusic)
        playbackSpeed = targetSpeed
        GlobalPlayerManager.setPlaybackSpeed(targetSpeed)
    }

    // Gesture Controls State
    var brightnessLevel by remember { mutableFloatStateOf(0.7f) }
    var volumeLevel by remember { mutableFloatStateOf(0.7f) }
    var gestureNoticeText by remember { mutableStateOf<String?>(null) }
    var gestureNoticeIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var seekNoticeText by remember { mutableStateOf<String?>(null) }

    val curPos by GlobalPlayerManager.currentPositionMs.collectAsState()
    val durMs by GlobalPlayerManager.durationMs.collectAsState()

    LaunchedEffect(seekNoticeText) {
        if (seekNoticeText != null) {
            delay(1200)
            seekNoticeText = null
        }
    }

    // SponsorBlock Auto-Skip Effect
    LaunchedEffect(videoId) {
        if (!videoId.isNullOrBlank()) {
            com.example.util.SponsorBlockHelper.fetchSegments(videoId)
        }
    }

    LaunchedEffect(curPos, videoId) {
        if (!videoId.isNullOrBlank() && curPos > 0) {
            val skipSegment = com.example.util.SponsorBlockHelper.getSkipTargetMs(videoId, curPos)
            if (skipSegment != null) {
                GlobalPlayerManager.seekTo(skipSegment.endMs)
                seekNoticeText = "Skipped ${skipSegment.category.replaceFirstChar { it.uppercase() }}"
            }
        }
    }

    val exoPlayer = remember(context) { GlobalPlayerManager.getExoPlayer(context) }
    val seekSecs = remember(context) { com.example.util.DebridSettingsManager.getDoubleTapSeekSecs(context) }

    LaunchedEffect(playbackSpeed) {
        GlobalPlayerManager.setPlaybackSpeed(playbackSpeed)
    }

    // Continuous progress tracking loop
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val areControlsVisible by GlobalPlayerManager.areControlsVisible.collectAsState()

    val playerContainerModifier = if (isLandscape) {
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    }

    Box(
        modifier = playerContainerModifier
            .pointerInput(seekSecs, isEmbedOrWebPage) {
                detectTapGestures(
                    onTap = {
                        if (!isEmbedOrWebPage) {
                            GlobalPlayerManager.toggleControlsVisibility()
                        }
                    },
                    onDoubleTap = { offset ->
                        val halfWidth = size.width / 2
                        val seekMs = seekSecs * 1000L
                        if (offset.x < halfWidth) {
                            gestureNoticeText = "◄◄ ${seekSecs}s Rewind"
                            if (!isEmbedOrWebPage) {
                                GlobalPlayerManager.seekTo(exoPlayer.currentPosition - seekMs)
                                GlobalPlayerManager.showControls()
                            }
                        } else {
                            gestureNoticeText = "${seekSecs}s Forward ►►"
                            if (!isEmbedOrWebPage) {
                                GlobalPlayerManager.seekTo(exoPlayer.currentPosition + seekMs)
                                GlobalPlayerManager.showControls()
                            }
                        }
                    }
                )
            }
            .pointerInput(isEmbedOrWebPage) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (!isEmbedOrWebPage) {
                            val dx = dragAmount.x
                            val dy = dragAmount.y
                            val absDx = kotlin.math.abs(dx)
                            val absDy = kotlin.math.abs(dy)
                            if (absDy > absDx) {
                                if (change.position.x < size.width / 2) {
                                    // Left side = Brightness
                                    val delta = -dy / size.height
                                    brightnessLevel = (brightnessLevel + delta).coerceIn(0.1f, 1.0f)
                                    val activity = context as? Activity
                                        ?: (context as? ContextWrapper)?.baseContext as? Activity
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = brightnessLevel
                                        act.window.attributes = lp
                                    }
                                    gestureNoticeText = "Brightness ${(brightnessLevel * 100).toInt()}%"
                                    gestureNoticeIcon = Icons.Default.BrightnessMedium
                                } else {
                                    // Right side = Volume
                                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                                    if (audioManager != null) {
                                        val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                        val delta = -dy / size.height
                                        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                        val targetVol = (volumeLevel * maxVol).toInt()
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                        gestureNoticeText = "Volume ${(volumeLevel * 100).toInt()}%"
                                        gestureNoticeIcon = if (targetVol == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                                    }
                                }
                            } else if (absDx > absDy) {
                                // Horizontal = Smooth Seek
                                val seekDeltaSecs = (dx / size.width) * 60f
                                val targetPos = (exoPlayer.currentPosition + (seekDeltaSecs * 1000).toLong()).coerceIn(0L, exoPlayer.duration.coerceAtLeast(1L))
                                GlobalPlayerManager.seekTo(targetPos)
                                val sign = if (seekDeltaSecs >= 0) "+" else ""
                                gestureNoticeText = "Seek ${sign}${seekDeltaSecs.toInt()}s"
                                gestureNoticeIcon = Icons.Default.FastForward
                            }
                        }
                    },
                    onDragEnd = {
                        // Gesture finished
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isEmbedOrWebPage && !rawVideoUrl.isNullOrEmpty()) {
            val embedContext = LocalContext.current
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

            androidx.compose.runtime.LaunchedEffect(srcUrl) {
                val webView = GlobalPlayerManager.getOrCreateWebView(embedContext)
                if (webView.tag != rawVideoUrl) {
                    webView.tag = rawVideoUrl
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

                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            if (newProgress >= 40) {
                                view?.evaluateJavascript(cleanPlayerScript, null)
                            }
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val reqUrl = request?.url?.toString() ?: ""
                            if (reqUrl.startsWith("magnet:") || reqUrl.startsWith("intent:") || reqUrl.startsWith("torrent:") || reqUrl.startsWith("seedr:")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    embedContext.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(embedContext, "Opening magnet stream", Toast.LENGTH_SHORT).show()
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

                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            if (view != null) {
                                val parent = view.parent as? android.view.ViewGroup
                                parent?.removeView(view)
                                view.destroy()
                            }
                            return true
                        }
                    }

                    if (isMagnetLink) {
                        val htmlContent = buildWebTorHtml(rawVideoUrl ?: "")
                        webView.loadDataWithBaseURL("https://webtor.io", htmlContent, "text/html", "UTF-8", null)
                    } else {
                        webView.loadUrl(srcUrl)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                PersistentPlayerHost(
                    useController = false,
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
            Box(modifier = Modifier.fillMaxSize()) {
                PersistentPlayerHost(
                    useController = true,
                    resizeMode = resizeModeState,
                    onFullscreenClick = {
                        toggleFullscreen(currentPlayerContext)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Header (Title + Actions Toolbar)
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
                ) {
                    val currentDisplayTitle = activeStreamData?.title?.takeIf { it.isNotBlank() }
                        ?: streamOption?.qualityLabel?.takeIf { it.isNotBlank() && !it.contains("http") }
                        ?: "Playing Video"
                    val currentUploader = activeStreamData?.channelName?.takeIf { it.isNotBlank() }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title + Channel Name Display
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = currentDisplayTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (!currentUploader.isNullOrBlank()) {
                                Text(
                                    text = currentUploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right-side actions (Speed, Settings, Fullscreen)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick Playback Speed Button / Badge
                            Surface(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    showSpeedSubMenu = true
                                    showSettingsSheet = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Playback Speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Settings Gear Icon
                            IconButton(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    showSpeedSubMenu = false
                                    showSettingsSheet = true
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Player Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Fullscreen Toggle Icon
                            IconButton(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    toggleFullscreen(currentPlayerContext)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // YouTube Style Settings Modal Bottom Sheet
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showSettingsSheet = false
                    showSpeedSubMenu = false
                    showQualitySubMenu = false
                },
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 32.dp, top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                showQualitySubMenu -> "Video Quality"
                                showSpeedSubMenu -> "Playback Speed"
                                else -> "Settings"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (showSpeedSubMenu || showQualitySubMenu) {
                            TextButton(onClick = {
                                showSpeedSubMenu = false
                                showQualitySubMenu = false
                            }) {
                                Text("Back", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    )

                    if (showQualitySubMenu) {
                        if (availableStreamOptions.isEmpty()) {
                            Text(
                                text = "Auto (Default quality stream)",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                            )
                        } else {
                            availableStreamOptions.forEach { option ->
                                val isSelected = (streamOption?.qualityLabel == option.qualityLabel || streamOption?.videoUrl == option.videoUrl)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectStreamOption(option)
                                            Toast.makeText(context, "Quality set to ${option.qualityLabel}", Toast.LENGTH_SHORT).show()
                                            showQualitySubMenu = false
                                            showSettingsSheet = false
                                        }
                                        .padding(vertical = 14.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = option.qualityLabel,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                        if (!option.format.isNullOrBlank()) {
                                            Text(
                                                text = option.format ?: "",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (showSpeedSubMenu) {
                        // Playback Speed Selector List & Custom Input
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMusicTrackDetected && playbackPrefs.disableSpeedForMusic.collectAsState().value) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🎵 Music video detected — Automatically playing at 1.0× speed",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            // Custom Speed Input Field
                            var inputVal by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inputVal,
                                    onValueChange = { inputVal = it },
                                    placeholder = { Text("Custom speed e.g. 1.35, 5, 8", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.Gray
                                    )
                                )
                                Button(
                                    onClick = {
                                        val parsed = inputVal.toFloatOrNull()
                                        if (parsed != null && parsed >= 0.1f && parsed <= 16.0f) {
                                            playbackSpeed = parsed
                                            GlobalPlayerManager.setPlaybackSpeed(parsed)
                                            Toast.makeText(context, "Speed set to ${parsed}x", Toast.LENGTH_SHORT).show()
                                            showSpeedSubMenu = false
                                            showSettingsSheet = false
                                        } else {
                                            Toast.makeText(context, "Enter speed between 0.1x and 16x", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                            // Presets
                            speedOptions.forEach { speed ->
                                val isSelected = (playbackSpeed == speed)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            playbackSpeed = speed
                                            GlobalPlayerManager.setPlaybackSpeed(speed)
                                            Toast.makeText(
                                                context,
                                                "Playback speed set to ${if (speed == 1.0f) "Normal" else "${speed}x"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            showSpeedSubMenu = false
                                            showSettingsSheet = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Main Settings Items
                        // 0. Quality Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showQualitySubMenu = true }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "Quality",
                                    tint = Color.White
                                )
                                Text(
                                    text = "Quality",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = streamOption?.qualityLabel ?: "Auto",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSpeedSubMenu = true }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = Color.White
                                )
                                Text(
                                    text = "Playback Speed",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = if (playbackSpeed == 1.0f) "Normal" else "${playbackSpeed}x",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. Aspect Ratio Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    resizeModeState = when (resizeModeState) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    Toast.makeText(
                                        context,
                                        "Aspect Ratio: ${when (resizeModeState) {
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop / Zoom"
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / Fill"
                                            else -> "Fit Screen"
                                        }}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White
                                )
                                Text(
                                    text = "Aspect Ratio",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = when (resizeModeState) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                                    else -> "Fit"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }

                        // 3. Format Preference (Fast H.264 vs. Original Quality)
                        var currentFormatPref by remember {
                            mutableStateOf(com.example.util.DebridSettingsManager.getArchiveFormatPreference(context))
                        }
                        // 4. Offline Download
                        val activeData by GlobalPlayerManager.activeStreamData.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val vid = videoId ?: activeData?.videoId ?: "vid_${System.currentTimeMillis()}"
                                    val title = activeData?.title ?: "Downloaded Video"
                                    val channel = activeData?.channelName ?: "Media Stream"
                                    val url = rawVideoUrl ?: ""
                                    if (url.isNotBlank()) {
                                        com.example.util.OfflineDownloadManager.downloadVideo(
                                            context = context,
                                            videoId = vid,
                                            title = title,
                                            channelName = channel,
                                            videoUrl = url,
                                            thumbnailUrl = activeData?.thumbnailUrl,
                                            qualityLabel = streamOption?.qualityLabel ?: "Auto"
                                        )
                                        Toast.makeText(context, "Downloading '$title' for offline viewing", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No downloadable stream URL found", Toast.LENGTH_SHORT).show()
                                    }
                                    showSettingsSheet = false
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Offline",
                                    tint = Color.White
                                )
                                Text(
                                    text = "Download Offline",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Save to Device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Gesture & Seek Overlay Notice
        val activeNoticeText = gestureNoticeText ?: seekNoticeText
        AnimatedVisibility(
            visible = activeNoticeText != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gestureNoticeIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = activeNoticeText ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
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
    val configOrientation = activity.resources.configuration.orientation
    val isLandscape = configOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    if (isLandscape) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
