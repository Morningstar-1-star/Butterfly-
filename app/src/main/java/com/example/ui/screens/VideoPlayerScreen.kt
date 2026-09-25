package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.ErrorDiagnosticCard
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoDetailsSection
import com.example.ui.components.DownloadQualityBottomSheet
import com.example.ui.components.LandscapeRelatedDrawer
import com.example.ui.player.GlobalPlayerManager
import com.example.resolver.SourceStreamType
import com.example.ui.player.EmbedWebViewPlayer
import com.example.ui.player.UniversalVideoPlayer
import com.example.ui.ambient.AmbientPlayerGlow
import com.example.ui.ambient.rememberAmbientPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.model.EpisodeItem
import com.example.ui.player.NextEpisodeData

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeVideoId by viewModel.activeVideoId.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val selectedOption by viewModel.selectedStreamOption.collectAsState()
    val selectedCaption by viewModel.selectedCaptionOption.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val watchLaterList by viewModel.watchLaterList.collectAsState()
    val watchPositionMsMap by viewModel.watchPositionMsMap.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    val currentStreamData = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData
    val providerId = currentStreamData?.providerId
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentView = androidx.compose.ui.platform.LocalView.current

    // Keep Screen On automatically during active video playback
    val globalIsPlaying by GlobalPlayerManager.isPlaying.collectAsState()
    val globalIsBuffering by GlobalPlayerManager.isBuffering.collectAsState()
    val shouldKeepScreenOn = (isPlaying || globalIsPlaying || globalIsBuffering) && activeVideoId != null

    androidx.compose.runtime.DisposableEffect(shouldKeepScreenOn) {
        val window = (context as? android.app.Activity)?.window
            ?: (currentView.context as? android.app.Activity)?.window
        if (shouldKeepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            currentView.keepScreenOn = true
        } else if (!globalIsPlaying && !globalIsBuffering) {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            currentView.keepScreenOn = false
        }
        onDispose {
            if (!GlobalPlayerManager.isPlaying.value && !GlobalPlayerManager.isBuffering.value) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                currentView.keepScreenOn = false
            }
        }
    }

    val initialPositionMs = remember(activeVideoId) {
        activeVideoId?.let { id ->
            watchPositionMsMap[id]?.takeIf { it > 0L }
                ?: com.example.util.PlaybackResumeManager.getSavedPosition(context, id)
        } ?: 0L
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var showSaveToPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }

    val offlineDownloads by viewModel.offlineDownloads.collectAsState()
    val activeDownload = remember(activeVideoId, offlineDownloads) {
        offlineDownloads.firstOrNull { it.videoId == activeVideoId }
    }
    val isDownloaded = remember(activeDownload) {
        activeDownload?.status == "COMPLETED"
    }
    val isDownloading = remember(activeDownload) {
        activeDownload?.status == "DOWNLOADING"
    }
    val downloadProgressFraction = remember(activeDownload) {
        val dl = activeDownload
        if (dl != null && dl.totalBytes > 0L) {
            (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (dl?.status == "COMPLETED") {
            1f
        } else {
            0f
        }
    }

    val playbackEnded by GlobalPlayerManager.playbackEnded.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    var isUpNextActive by remember { mutableStateOf(false) }
    var autoPlayCountdown by remember { mutableStateOf(5) }

    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val activeVideoItem by viewModel.activeVideoItem.collectAsState()
    val failedSourceLogs by viewModel.failedSourceLogs.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val videoComments by viewModel.videoComments.collectAsState()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val videoReactions by viewModel.videoReactions.collectAsState()
    val isReactionsLoading by viewModel.isReactionsLoading.collectAsState()
    val tvSeasons by viewModel.tvSeasons.collectAsState()
    val isSeasonsLoading by viewModel.isSeasonsLoading.collectAsState()
    var selectedSeasonNumber by remember { mutableStateOf(1) }
    var selectedPillTab by remember { mutableStateOf("RELATED") } // "EPISODES", "RELATED", "REACTIONS", "COMMENTS"

    LaunchedEffect(tvSeasons) {
        if (tvSeasons.isNotEmpty()) {
            if (tvSeasons.none { it.seasonNumber == selectedSeasonNumber }) {
                selectedSeasonNumber = tvSeasons.first().seasonNumber
            }
            if (selectedPillTab == "RELATED") {
                selectedPillTab = "EPISODES"
            }
        } else if (selectedPillTab == "EPISODES") {
            selectedPillTab = "RELATED"
        }
    }

    val currentVideoItem = remember(currentStreamData, activeVideoId, activeVideoItem, trendingVideos, searchResults) {
        if (currentStreamData != null) {
            VideoItem(
                id = activeVideoId ?: "playing_video",
                title = currentStreamData.title,
                uploaderName = currentStreamData.channelName,
                thumbnailUrl = currentStreamData.thumbnailUrl,
                providerId = providerId ?: currentStreamData.providerId ?: "youtube"
            )
        } else if (activeVideoItem != null) {
            activeVideoItem
        } else {
            activeVideoId?.let { vid ->
                trendingVideos.firstOrNull { it.id == vid }
                    ?: searchResults.firstOrNull { it.id == vid }
                    ?: VideoItem(
                        id = vid,
                        title = if (vid.length == 11) "YouTube Video" else vid,
                        uploaderName = providerId?.replaceFirstChar { it.uppercase() } ?: "YouTube",
                        thumbnailUrl = if (vid.length == 11) "https://i.ytimg.com/vi/$vid/hqdefault.jpg" else null,
                        providerId = providerId ?: "youtube"
                    )
            }
        }
    }

    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val notInterestedChannels by viewModel.notInterestedChannels.collectAsState()
    val playerRecommendations by viewModel.playerRecommendations.collectAsState()
    val isLoadingPlayerRecs by viewModel.isLoadingPlayerRecs.collectAsState()

    val playbackPrefs = remember(context) { com.example.util.PlaybackPreferences.getInstance(context) }
    val isAmbientEnabled by playbackPrefs.ambientModeEnabled.collectAsState()
    val isPowerSaveActive by viewModel.isPowerSaveActive.collectAsState()
    val batterySaverDisableAmbient by viewModel.batterySaverDisableAmbient.collectAsState()
    val effectiveAmbient = isAmbientEnabled && (!isPowerSaveActive || !batterySaverDisableAmbient)
    val ambientPalette = rememberAmbientPalette(
        thumbnailUrl = currentStreamData?.thumbnailUrl ?: currentVideoItem?.thumbnailUrl
    )

    var relatedContent by remember(activeVideoId) { mutableStateOf<List<com.example.model.VideoItem>>(emptyList()) }
    LaunchedEffect(activeVideoId, currentStreamData, playerRecommendations.size, trendingVideos.size) {
        val streamRelated = currentStreamData?.relatedVideos?.filter { it.id != activeVideoId } ?: emptyList()
        val pId = providerId ?: currentStreamData?.providerId
        val isAdultCurrent = viewModel.isAdultProviderId(pId) ||
                (currentVideoItem != null && (viewModel.isAdultVideoItem(currentVideoItem) || viewModel.isAdultProviderId(currentVideoItem.providerId))) ||
                (currentStreamData != null && (viewModel.isAdultSearchQuery(currentStreamData.title) || viewModel.isAdultProviderId(currentStreamData.providerId)))

        val basePool = if (isAdultCurrent) {
            // In 18+ mode: strictly adult items only, never mix normal/YouTube videos!
            (playerRecommendations + streamRelated).filter {
                it.id != activeVideoId && (viewModel.isAdultVideoItem(it) || viewModel.isAdultProviderId(it.providerId)) && !viewModel.isNormalProvider(it.providerId)
            }
        } else {
            // In normal mode: strictly normal items only, never mix adult videos!
            (playerRecommendations + streamRelated + trendingVideos.filter { it.id != activeVideoId }).filter {
                !viewModel.isAdultVideoItem(it) && !viewModel.isAdultProviderId(it.providerId)
            }
        }
        val pool = basePool.distinctBy { (it.providerId ?: "") + "_" + it.id }.filterNot { viewModel.isBlockedVideo(it) }
        relatedContent = pool.take(20)
        withContext(Dispatchers.Default) {
            val activeItem = currentVideoItem ?: currentStreamData?.let {
                com.example.model.VideoItem(
                    id = it.videoId,
                    title = it.title,
                    uploaderName = it.channelName,
                    tags = it.tags,
                    providerId = it.providerId,
                    durationSeconds = currentVideoItem?.durationSeconds ?: 0
                )
            }
            val ranked = viewModel.rankFallbackRelated(pool, activeVideoId, activeItem)
            withContext(Dispatchers.Main) {
                relatedContent = ranked
            }
        }
    }

    val displayTitle = currentStreamData?.title ?: currentVideoItem?.title ?: ""
    val extractionError = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Error)?.errorDetails
    val isSavedInWatchLater = currentVideoItem != null && watchLaterList.any { it.id == currentVideoItem.id }

    val currentPositionMsForChapters by GlobalPlayerManager.currentPositionMs.collectAsState()
    val chaptersForDetail = remember(currentStreamData, extractionResult) {
        currentStreamData?.chapters?.takeIf { it.isNotEmpty() }
            ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.chapters
            ?: emptyList()
    }
    val activeChapterIndex = remember(currentPositionMsForChapters, chaptersForDetail) {
        chaptersForDetail.indexOfLast { currentPositionMsForChapters >= it.startTimeMs }
    }

    val listState = rememberLazyListState()
    var showLandscapeRelatedDrawer by remember { mutableStateOf(false) }
    var showServerSelectorSheet by remember { mutableStateOf(false) }

    val unifiedCandidates by viewModel.unifiedCandidates.collectAsState()
    val activeSourceCandidate by viewModel.activeSourceCandidate.collectAsState()
    val isResolvingUnifiedSources by viewModel.isResolvingUnifiedSources.collectAsState()
    val unifiedStatusMessage by viewModel.unifiedStatusMessage.collectAsState()

    val isOptionEmbed = remember(selectedOption) {
        val currOpt = selectedOption
        val url = currOpt?.videoUrl.orEmpty()
        currOpt?.format.equals("embed", ignoreCase = true) ||
        currOpt?.providerType == com.example.model.ProviderType.EMBED ||
        (url.contains("/embed/", ignoreCase = true) && !url.contains(".m3u8", ignoreCase = true) && !url.contains(".mp4", ignoreCase = true)) ||
        (url.contains("vidsrc.", ignoreCase = true) && !url.contains(".m3u8", ignoreCase = true) && !url.contains(".mp4", ignoreCase = true)) ||
        (url.contains("autoembed.", ignoreCase = true) && !url.contains(".m3u8", ignoreCase = true) && !url.contains(".mp4", ignoreCase = true)) ||
        (url.contains("vidlink.", ignoreCase = true) && !url.contains(".m3u8", ignoreCase = true) && !url.contains(".mp4", ignoreCase = true)) ||
        (url.contains("smashystream.", ignoreCase = true) && !url.contains(".m3u8", ignoreCase = true) && !url.contains(".mp4", ignoreCase = true)) ||
        (url.contains("2embed.", ignoreCase = true) && !url.contains(".m3u8", ignoreCase = true) && !url.contains(".mp4", ignoreCase = true)) ||
        (url.contains("multiembed.", ignoreCase = true) && !url.contains(".m3u8", ignoreCase = true) && !url.contains(".mp4", ignoreCase = true))
    }

    val effectiveEmbedCandidate = remember(activeSourceCandidate, isOptionEmbed, selectedOption, currentStreamData, currentVideoItem) {
        val currOpt = selectedOption
        activeSourceCandidate?.takeIf { it.type == SourceStreamType.EMBED_WEBVIEW }
            ?: if (isOptionEmbed && currOpt != null && !currOpt.videoUrl.isNullOrBlank()) {
                val vUrl = currOpt.videoUrl
                com.example.resolver.SourceCandidate(
                    id = "embed_${vUrl.hashCode()}",
                    providerId = currOpt.sourceName.ifBlank { "embed" },
                    providerName = currOpt.sourceName.ifBlank { "Embed Stream" },
                    serverName = currOpt.qualityLabel,
                    type = SourceStreamType.EMBED_WEBVIEW,
                    title = currentStreamData?.title ?: currentVideoItem?.title ?: "Video",
                    urlOrMagnet = vUrl,
                    quality = currOpt.qualityCategory.ifBlank { "1080p" },
                    qualityScore = 1080,
                    format = "embed",
                    headers = currOpt.headers
                )
            } else null
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val dragOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val maxDockDistancePx = with(density) { 380.dp.toPx() }
    val minimizeThresholdPx = with(density) { 70.dp.toPx() }

    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidthDp = configuration.screenWidthDp.dp
    val standardPlayerHeightDp = screenWidthDp * 9f / 16f
    val maxExpandPx = with(density) { (screenHeightDp - standardPlayerHeightDp).toPx().coerceAtLeast(100f) }

    val portraitExpandProgress = remember { Animatable(0f) }
    var isPortraitExpanded by rememberSaveable { mutableStateOf(false) }

    val currentDragY = dragOffsetY.value.coerceAtLeast(0f)
    val dragFraction = (currentDragY / maxDockDistancePx).coerceIn(0f, 1f)

    // Details sheet fades out smoothly during vertical drag
    val detailsAlpha = (1.0f - dragFraction * 2.2f).coerceIn(0f, 1f)
    val detailsTranslationY = currentDragY * 0.7f

    // 16:9 video player scales and translates down cleanly towards mini-player position
    val playerScale = 1.0f - (dragFraction * 0.45f)
    val playerTranslationX = dragFraction * (with(density) { 65.dp.toPx() })
    val playerTranslationY = currentDragY
    val playerCornerDp = (dragFraction * 14).dp
    val bgOverlayAlpha = (1.0f - dragFraction * 1.3f).coerceIn(0f, 1f)

    val minimizePlayerAction: () -> Unit = {
        onBackClick()
    }

    val collapsePortraitAction: () -> Unit = {
        coroutineScope.launch {
            isPortraitExpanded = false
            portraitExpandProgress.animateTo(
                0f,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    BackHandler(enabled = isPortraitExpanded || portraitExpandProgress.value > 0.05f) {
        collapsePortraitAction()
    }

    val expandProgress = portraitExpandProgress.value

    val nestedScrollConnection = remember(maxExpandPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (portraitExpandProgress.value > 0.05f && available.y < 0f) {
                    val deltaFraction = available.y / maxExpandPx
                    coroutineScope.launch {
                        portraitExpandProgress.snapTo(
                            (portraitExpandProgress.value + deltaFraction).coerceIn(0f, 1f)
                        )
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (portraitExpandProgress.value in 0.05f..0.95f) {
                    if (portraitExpandProgress.value < 0.65f || available.y < -300f) {
                        isPortraitExpanded = false
                        portraitExpandProgress.animateTo(
                            0f,
                            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        )
                    } else {
                        isPortraitExpanded = true
                        portraitExpandProgress.animateTo(
                            1f,
                            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(Unit) {
        dragOffsetY.snapTo(0f)
    }

    LaunchedEffect(activeVideoId) {
        dragOffsetY.snapTo(0f)
        portraitExpandProgress.snapTo(0f)
        isPortraitExpanded = false
    }

    val landscapeVideos = remember(relatedContent, activeVideoId) {
        relatedContent.filter { it.id != activeVideoId }
    }

    LaunchedEffect(initialPositionMs, activeVideoId) {
        if (initialPositionMs > 5000L) {
            val totalSecs = initialPositionMs / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            val formatted = String.format("%02d:%02d", mins, secs)
            snackbarHostState.showSnackbar("Resumed at $formatted")
        }
    }

    LaunchedEffect(currentStreamData?.videoId, activeVideoId) {
        viewModel.loadMorePlayerRecommendations(currentStreamData)
    }

    LaunchedEffect(selectedPillTab, activeVideoId, currentStreamData?.videoId) {
        val vid = activeVideoId
        if (selectedPillTab == "COMMENTS" && !vid.isNullOrBlank()) {
            val pId = providerId ?: currentStreamData?.providerId ?: "youtube"
            val title = displayTitle.takeIf { it.isNotBlank() && it != "Loading video..." }
            viewModel.loadVideoComments(
                videoId = vid,
                providerId = pId,
                videoTitle = title
            )
        } else if (selectedPillTab == "REACTIONS") {
            val title = displayTitle.takeIf { it.isNotBlank() && it != "Loading video..." }
            viewModel.loadVideoReactions(title, vid)
        }
    }

    LaunchedEffect(displayTitle, activeVideoId) {
        if (displayTitle.isNotBlank() && displayTitle != "Loading video...") {
            val cleanTitle = displayTitle.replace(Regex("""\s*\(\d{4}\).*"""), "").trim()
            viewModel.resolveUnifiedSourcesForMedia(
                com.example.model.MediaIdentity(
                    title = cleanTitle,
                    mediaType = if (cleanTitle.contains("season", true) || cleanTitle.contains("episode", true)) com.example.model.MediaType.TV else com.example.model.MediaType.MOVIE
                )
            )
        }
    }

    // High-precision resolution for NEXT EPISODE (Strictly for series / multi-episode content)
    val nextEpisodeData = remember(tvSeasons, currentStreamData, selectedOption, activeVideoId, activeVideoItem, playbackQueue) {
        // 1. Check TV Seasons & Episodes (TMDB / Archive / Crunchyroll / SonyLIV / Hotstar / Bilibili / Torrent)
        if (tvSeasons.isNotEmpty()) {
            val allEpisodes = tvSeasons.flatMap { it.episodes }
            val curOptionUrl = selectedOption?.videoUrl ?: currentStreamData?.selectedStreamOption?.videoUrl ?: ""
            val curTitle = currentStreamData?.title ?: activeVideoItem?.title ?: ""

            var currentIdx = allEpisodes.indexOfFirst { ep ->
                (activeVideoId != null && ep.id == activeVideoId) ||
                (curOptionUrl.isNotBlank() && ep.id == curOptionUrl)
            }

            if (currentIdx == -1 && curTitle.isNotBlank()) {
                val epRegex = Regex("(?i)(?:s(\\d{1,2})[._\\s-]*e(\\d{1,3}))|(?:(\\d{1,2})[xX](\\d{1,3}))|(?:episode[._\\s-]*(\\d{1,3}))|(?:ep[._\\s-]*(\\d{1,3}))")
                val match = epRegex.find(curTitle)
                if (match != null) {
                    val sNum = match.groupValues[1].toIntOrNull() ?: match.groupValues[3].toIntOrNull() ?: selectedSeasonNumber
                    val eNum = match.groupValues[2].toIntOrNull() ?: match.groupValues[4].toIntOrNull() ?: match.groupValues[5].toIntOrNull() ?: match.groupValues[6].toIntOrNull()
                    if (eNum != null) {
                        currentIdx = allEpisodes.indexOfFirst { it.seasonNumber == sNum && it.episodeNumber == eNum }
                    }
                }
            }

            if (currentIdx != -1 && currentIdx < allEpisodes.size - 1) {
                val nextEp = allEpisodes[currentIdx + 1]
                return@remember NextEpisodeData(
                    episodeId = nextEp.id,
                    title = nextEp.title.ifBlank { "Episode ${nextEp.episodeNumber}" },
                    subtitle = "Season ${nextEp.seasonNumber} • Episode ${nextEp.episodeNumber}",
                    thumbnailUrl = nextEp.thumbnailUrl ?: currentStreamData?.thumbnailUrl ?: activeVideoItem?.thumbnailUrl,
                    durationText = nextEp.durationText,
                    seasonNumber = nextEp.seasonNumber,
                    episodeNumber = nextEp.episodeNumber,
                    providerId = nextEp.providerId ?: providerId ?: currentStreamData?.providerId
                )
            }
        }

        // 2. Multi-episode stream options (Archive.org multi-part, Vega releases, etc.)
        val streamOptions = currentStreamData?.availableStreamOptions ?: emptyList()
        if (streamOptions.size > 1) {
            val isEpisodeOptions = streamOptions.any { opt ->
                val lbl = opt.qualityLabel.lowercase()
                lbl.contains("ep") || lbl.contains("s0") || lbl.contains("episode") || lbl.contains("x")
            }
            if (isEpisodeOptions) {
                val currentOptUrl = selectedOption?.videoUrl ?: currentStreamData?.selectedStreamOption?.videoUrl ?: ""
                val currentOptIdx = streamOptions.indexOfFirst { it.videoUrl == currentOptUrl }
                if (currentOptIdx != -1 && currentOptIdx < streamOptions.size - 1) {
                    val nextOpt = streamOptions[currentOptIdx + 1]
                    val nextOptId = nextOpt.videoUrl ?: nextOpt.videoStream?.url ?: ""
                    if (nextOptId.isNotBlank()) {
                        return@remember NextEpisodeData(
                            episodeId = nextOptId,
                            title = nextOpt.qualityLabel,
                            subtitle = currentStreamData?.title,
                            thumbnailUrl = currentStreamData?.thumbnailUrl ?: activeVideoItem?.thumbnailUrl,
                            providerId = providerId ?: currentStreamData?.providerId
                        )
                    }
                }
            }
        }

        // 3. Playback Queue if next queued item is an episode of the current show
        if (playbackQueue.isNotEmpty()) {
            val nextItem = playbackQueue.first()
            val isSeriesQueue = nextItem.uploaderName == activeVideoItem?.uploaderName ||
                    nextItem.title.contains("episode", true) ||
                    nextItem.title.contains("ep", true) ||
                    (activeVideoItem?.title?.contains("episode", true) == true)
            if (isSeriesQueue) {
                return@remember NextEpisodeData(
                    episodeId = nextItem.id,
                    title = nextItem.title,
                    subtitle = nextItem.uploaderName,
                    thumbnailUrl = nextItem.thumbnailUrl,
                    providerId = nextItem.providerId
                )
            }
        }

        null
    }

    val playNextEpisodeAction: () -> Unit = {
        if (nextEpisodeData != null) {
            val matchedEp = tvSeasons.flatMap { it.episodes }.firstOrNull { it.id == nextEpisodeData.episodeId }
            if (matchedEp != null) {
                viewModel.playEpisode(matchedEp, currentStreamData)
            } else {
                val matchingStreamOpt = currentStreamData?.availableStreamOptions?.firstOrNull { it.videoUrl == nextEpisodeData.episodeId }
                if (matchingStreamOpt != null) {
                    viewModel.selectStreamOption(matchingStreamOpt)
                } else if (playbackQueue.isNotEmpty() && playbackQueue.first().id == nextEpisodeData.episodeId) {
                    viewModel.playNextInQueue()
                } else {
                    viewModel.playVideo(nextEpisodeData.episodeId, nextEpisodeData.providerId ?: providerId ?: "torrent")
                }
            }
        } else {
            val currentQueue = viewModel.playbackQueue.value
            if (currentQueue.isNotEmpty()) {
                viewModel.playNextInQueue()
            }
        }
    }

    LaunchedEffect(playbackEnded) {
        if (playbackEnded) {
            if (nextEpisodeData != null) {
                playNextEpisodeAction()
            } else {
                val currentQueue = viewModel.playbackQueue.value
                if (currentQueue.isNotEmpty()) {
                    viewModel.playNextInQueue()
                }
            }
        }
    }

    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (effectiveEmbedCandidate != null) {
                EmbedWebViewPlayer(
                    candidate = effectiveEmbedCandidate,
                    onClose = onBackClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                UniversalVideoPlayer(
                streamOption = selectedOption,
                hlsUrl = currentStreamData?.hlsUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.hlsUrl,
                captionOption = selectedCaption,
                streamData = currentStreamData ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData,
                previewItem = currentVideoItem,
                providerId = providerId,
                isPlaying = isPlaying,
                videoId = activeVideoId,
                initialPositionMs = initialPositionMs,
                availableStreamOptions = currentStreamData?.availableStreamOptions ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.availableStreamOptions ?: emptyList(),
                onSelectStreamOption = { option -> viewModel.selectStreamOption(option) },
                failedSourceLogs = failedSourceLogs,
                onProgressUpdate = { pos, dur ->
                    activeVideoId?.let { id -> viewModel.recordWatchProgress(id, pos, dur) }
                },
                onBackClick = onBackClick,
                nextEpisodeData = nextEpisodeData,
                onPlayNextEpisode = playNextEpisodeAction,
                onNextClick = {
                    if (nextEpisodeData != null) {
                        playNextEpisodeAction()
                    } else {
                        val currentQueue = viewModel.playbackQueue.value
                        if (currentQueue.isNotEmpty()) {
                            viewModel.playNextInQueue()
                        } else if (landscapeVideos.isNotEmpty()) {
                            val nextVid = landscapeVideos.first()
                            viewModel.playVideo(nextVid.id, nextVid.providerId ?: "youtube")
                        } else {
                            val curMs = GlobalPlayerManager.currentPositionMs.value
                            GlobalPlayerManager.seekTo(curMs + 10000L)
                        }
                    }
                },
                onPreviousClick = {
                    val curMs = GlobalPlayerManager.currentPositionMs.value
                    if (curMs > 5000L) {
                        GlobalPlayerManager.seekTo(0L)
                    } else {
                        GlobalPlayerManager.seekTo((curMs - 10000L).coerceAtLeast(0L))
                    }
                },
                onOpenRelatedVideos = {
                    showLandscapeRelatedDrawer = true
                },
                modifier = Modifier.fillMaxSize()
            )
            }

            // Landscape Related Videos Drawer
            LandscapeRelatedDrawer(
                isVisible = showLandscapeRelatedDrawer,
                videos = landscapeVideos,
                currentVideoId = activeVideoId,
                currentChannelName = currentStreamData?.channelName ?: currentVideoItem?.uploaderName,
                onVideoClick = { video ->
                    viewModel.playVideo(video.id, video.providerId)
                },
                onDismiss = { showLandscapeRelatedDrawer = false }
            )
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            // Backdrop Overlay (Fades out cleanly to reveal underlying screen)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = bgOverlayAlpha))
            )

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // YouTube-style Dynamic Ambient Mode Lighting Effect
                AmbientPlayerGlow(
                    palette = ambientPalette,
                    isEnabled = effectiveAmbient && detailsAlpha > 0.1f && !isPortraitExpanded
                )

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val screenHeight = maxHeight
                    val standardPlayerHeight = maxWidth * 9f / 16f
                    val currentPlayerHeight = lerp(standardPlayerHeight, screenHeight, expandProgress)

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // PORTRAIT VIDEO PLAYER VIEW (Expands from 16:9 to Fullscreen Portrait)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(currentPlayerHeight)
                                .graphicsLayer {
                                    if (expandProgress < 0.05f) {
                                        translationY = playerTranslationY
                                        translationX = playerTranslationX
                                        scaleX = playerScale
                                        scaleY = playerScale
                                        transformOrigin = TransformOrigin(0.5f, 0.0f)
                                        shape = RoundedCornerShape(playerCornerDp)
                                        clip = true
                                        alpha = if (dragFraction > 0.95f) 1.0f - ((dragFraction - 0.95f) * 20f).coerceIn(0f, 1f) else 1.0f
                                    }
                                }
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (effectiveEmbedCandidate != null) {
                                EmbedWebViewPlayer(
                                    candidate = effectiveEmbedCandidate,
                                    onClose = minimizePlayerAction,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                UniversalVideoPlayer(
                                streamOption = selectedOption,
                                hlsUrl = currentStreamData?.hlsUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.hlsUrl,
                                captionOption = selectedCaption,
                                streamData = currentStreamData ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData,
                                previewItem = currentVideoItem,
                                chapters = currentStreamData?.chapters ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.chapters ?: emptyList(),
                                providerId = providerId,
                                isPlaying = isPlaying,
                                videoId = activeVideoId,
                                initialPositionMs = initialPositionMs,
                                availableStreamOptions = currentStreamData?.availableStreamOptions ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.availableStreamOptions ?: emptyList(),
                                onSelectStreamOption = { option -> viewModel.selectStreamOption(option) },
                                failedSourceLogs = failedSourceLogs,
                                onProgressUpdate = { pos, dur ->
                                    activeVideoId?.let { id -> viewModel.recordWatchProgress(id, pos, dur) }
                                },
                                isPortraitExpanded = isPortraitExpanded || expandProgress > 0.05f,
                                onTogglePortraitExpanded = {
                                    coroutineScope.launch {
                                        if (isPortraitExpanded || expandProgress > 0.5f) {
                                            isPortraitExpanded = false
                                            portraitExpandProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                        } else {
                                            isPortraitExpanded = true
                                            portraitExpandProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                },
                                onPortraitCollapseDrag = { deltaY ->
                                    coroutineScope.launch {
                                        val deltaFraction = deltaY / maxExpandPx
                                        portraitExpandProgress.snapTo(
                                            (portraitExpandProgress.value + deltaFraction).coerceIn(0f, 1f)
                                        )
                                    }
                                },
                                onPortraitCollapseEnd = { accumulatedDy ->
                                    coroutineScope.launch {
                                        val cur = portraitExpandProgress.value
                                        if (cur < 0.65f || accumulatedDy < -80f) {
                                            isPortraitExpanded = false
                                            portraitExpandProgress.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                            )
                                        } else {
                                            isPortraitExpanded = true
                                            portraitExpandProgress.animateTo(
                                                1f,
                                                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                                            )
                                        }
                                    }
                                },
                                onSwipeDownDrag = { deltaY ->
                                    if (!isPortraitExpanded && expandProgress < 0.05f) {
                                        coroutineScope.launch {
                                            dragOffsetY.snapTo((dragOffsetY.value + deltaY).coerceAtLeast(0f))
                                        }
                                    }
                                },
                                onSwipeDownEnd = { accumulatedDy ->
                                    if (!isPortraitExpanded && expandProgress < 0.05f) {
                                        coroutineScope.launch {
                                            if (dragOffsetY.value > minimizeThresholdPx || accumulatedDy > 40f) {
                                                minimizePlayerAction()
                                            } else {
                                                dragOffsetY.animateTo(0f, spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
                                            }
                                        }
                                    }
                                },
                                onBackClick = {
                                    if (isPortraitExpanded || expandProgress > 0.05f) {
                                        collapsePortraitAction()
                                    } else {
                                        minimizePlayerAction()
                                    }
                                },
                            nextEpisodeData = nextEpisodeData,
                            onPlayNextEpisode = playNextEpisodeAction,
                            onNextClick = {
                                if (nextEpisodeData != null) {
                                    playNextEpisodeAction()
                                } else {
                                    val currentQueue = viewModel.playbackQueue.value
                                    if (currentQueue.isNotEmpty()) {
                                        viewModel.playNextInQueue()
                                    } else if (landscapeVideos.isNotEmpty()) {
                                        val nextVid = landscapeVideos.first()
                                        viewModel.playVideo(nextVid.id, nextVid.providerId ?: "youtube")
                                    } else {
                                        val curMs = GlobalPlayerManager.currentPositionMs.value
                                        GlobalPlayerManager.seekTo(curMs + 10000L)
                                    }
                                }
                            },
                            onPreviousClick = {
                                val curMs = GlobalPlayerManager.currentPositionMs.value
                                if (curMs > 5000L) {
                                    GlobalPlayerManager.seekTo(0L)
                                } else {
                                    GlobalPlayerManager.seekTo((curMs - 10000L).coerceAtLeast(0L))
                                }
                            }
                        )
                        }
                    }

                    // SCROLLABLE CONTENT (DETAILS + RELATED VIDEOS) - SLIDES DOWN & VANISHES INSTANTLY UPON SWIPING DOWN OR EXPANDING!
                    if (expandProgress < 0.99f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = (detailsAlpha * (1.0f - expandProgress * 2.0f)).coerceIn(0f, 1f)
                                    translationY = detailsTranslationY
                                }
                                .nestedScroll(nestedScrollConnection)
                        ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Video Details Section
                        item {
                            VideoDetailsSection(
                                streamData = currentStreamData,
                                previewItem = currentVideoItem,
                                selectedOption = selectedOption,
                                selectedCaption = selectedCaption,
                                onSelectOption = { viewModel.selectStreamOption(it) },
                                onSelectCaption = { viewModel.selectCaptionOption(it) },
                                onTagClick = { tag ->
                                    viewModel.updateSearchQuery(tag)
                                    viewModel.performSearch(tag)
                                    onBackClick()
                                },
                                isLiked = isLiked,
                                isDisliked = isDisliked,
                                isSaved = isSavedInWatchLater,
                                onLikeClick = {
                                    isLiked = !isLiked
                                    if (isLiked) isDisliked = false
                                    activeVideoId?.let { id -> viewModel.toggleLikeVideo(id) }
                                },
                                onDislikeClick = {
                                    isDisliked = !isDisliked
                                    if (isDisliked) isLiked = false
                                    activeVideoId?.let { id -> viewModel.toggleDislikeVideo(id) }
                                },
                                onSaveClick = {
                                    currentVideoItem?.let { video ->
                                        if (isSavedInWatchLater) {
                                            viewModel.removeFromWatchLater(video)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Removed from Watch Later")
                                            }
                                        } else {
                                            viewModel.addToWatchLater(video)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Saved to Watch Later")
                                            }
                                        }
                                    }
                                },
                                onSaveLongClick = { showSaveToPlaylistSheet = true },
                                onShareClick = {
                                    com.example.util.VideoShareHelper.shareStream(
                                        context = context,
                                        streamData = currentStreamData,
                                        displayTitle = displayTitle,
                                        activeVideoId = activeVideoId
                                    )
                                },
                                onCommentsClick = { selectedPillTab = "COMMENTS" },
                                onChannelClick = { channelName ->
                                    viewModel.openChannel(channelName)
                                },
                                isSubscribed = viewModel.isSubscribed(currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: ""),
                                onSubscribeClick = {
                                    val chName = currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: ""
                                    if (chName.isNotBlank()) {
                                        val isNowSub = !viewModel.isSubscribed(chName)
                                        viewModel.toggleSubscription(chName, currentStreamData?.channelAvatarUrl ?: currentVideoItem?.thumbnailUrl)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (isNowSub) "Subscribed to $chName" else "Unsubscribed from $chName")
                                        }
                                    }
                                },
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgressFraction,
                                onDownloadClick = {
                                    if (isDownloaded) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Video is already downloaded for offline playback")
                                        }
                                    } else if (isDownloading) {
                                        coroutineScope.launch {
                                            val pct = (downloadProgressFraction * 100).toInt()
                                            snackbarHostState.showSnackbar("Downloading: $pct%")
                                        }
                                    } else {
                                        showDownloadQualitySheet = true
                                    }
                                },
                                onTitleDrag = { deltaY ->
                                    coroutineScope.launch {
                                        val deltaFraction = deltaY / maxExpandPx
                                        portraitExpandProgress.snapTo(
                                            (portraitExpandProgress.value + deltaFraction).coerceIn(0f, 1f)
                                        )
                                    }
                                },
                                onTitleDragEnd = { totalDy ->
                                    coroutineScope.launch {
                                        val cur = portraitExpandProgress.value
                                        if (cur > 0.38f || totalDy > 120f) {
                                            isPortraitExpanded = true
                                            portraitExpandProgress.animateTo(
                                                1f,
                                                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                            )
                                        } else {
                                            isPortraitExpanded = false
                                            portraitExpandProgress.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                                            )
                                        }
                                    }
                                },
                                onServersClick = {
                                    showServerSelectorSheet = true
                                    if (displayTitle.isNotBlank()) {
                                        val cleanTitle = displayTitle.replace(Regex("""\s*\(\d{4}\).*"""), "").trim()
                                        viewModel.resolveUnifiedSourcesForMedia(
                                            com.example.model.MediaIdentity(
                                                title = cleanTitle,
                                                mediaType = if (cleanTitle.contains("season", true) || cleanTitle.contains("episode", true)) com.example.model.MediaType.TV else com.example.model.MediaType.MOVIE
                                            ),
                                            force = true
                                        )
                                    }
                                }
                            )
                        }

                        // Interactive Timeline Preview Strip (SpankBang & Universal Storyboard Timeline)
                        item {
                            val curTimelinePosMs by GlobalPlayerManager.currentPositionMs.collectAsState()
                            val totalTimelineDurMs by GlobalPlayerManager.durationMs.collectAsState()
                            com.example.ui.components.InteractiveTimelinePreviewStrip(
                                currentPositionMs = curTimelinePosMs,
                                durationMs = totalTimelineDurMs,
                                streamData = currentStreamData,
                                previewItem = currentVideoItem,
                                onSeekTo = { targetMs ->
                                    GlobalPlayerManager.seekTo(targetMs)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        // YouTube Queue Section (Temporary Session Playlist)
                        if (playbackQueue.isNotEmpty()) {
                            item {
                                QueueSection(
                                    queue = playbackQueue,
                                    onPlayItem = { video -> viewModel.playFromQueue(video) },
                                    onRemoveItem = { video -> viewModel.removeFromQueue(video) },
                                    onClearQueue = { viewModel.clearQueue() }
                                )
                            }
                        }

                        // Modern Pill Tab Navigation Bar (Episodes vs Related Videos vs Comments)
                        item {
                            LazyRow(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (tvSeasons.isNotEmpty()) {
                                    item {
                                        val totalEpCount = tvSeasons.sumOf { it.episodes.size }
                                        FilterChip(
                                            selected = selectedPillTab == "EPISODES",
                                            onClick = { selectedPillTab = "EPISODES" },
                                            label = {
                                                Text(
                                                    text = if (totalEpCount > 0) "Episodes ($totalEpCount)" else "Episodes",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Tv,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                }

                                item {
                                    FilterChip(
                                        selected = selectedPillTab == "RELATED",
                                        onClick = { selectedPillTab = "RELATED" },
                                        label = {
                                            Text(
                                                text = "Related",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }

                                if (chaptersForDetail.isNotEmpty()) {
                                    item {
                                        FilterChip(
                                            selected = selectedPillTab == "CHAPTERS",
                                            onClick = { selectedPillTab = "CHAPTERS" },
                                            label = {
                                                Text(
                                                    text = "Chapters (${chaptersForDetail.size})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.ViewList,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                }

                                item {
                                    FilterChip(
                                        selected = selectedPillTab == "REACTIONS",
                                        onClick = {
                                            selectedPillTab = "REACTIONS"
                                            val title = displayTitle.takeIf { it.isNotBlank() && it != "Loading video..." }
                                            viewModel.loadVideoReactions(title, activeVideoId)
                                        },
                                        label = {
                                            Text(
                                                text = if (videoReactions.isNotEmpty()) "Reactions (${videoReactions.size})" else "Reactions",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.RateReview,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = selectedPillTab == "COMMENTS",
                                        onClick = {
                                            selectedPillTab = "COMMENTS"
                                            activeVideoId?.let { vid ->
                                                val pId = providerId ?: currentStreamData?.providerId ?: "youtube"
                                                val title = displayTitle.takeIf { it.isNotBlank() && it != "Loading video..." }
                                                viewModel.loadVideoComments(vid, pId, title)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "Comments (${videoComments.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Comment,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }
                            }
                        }

                        // Tab Content Section
                        if (selectedPillTab == "EPISODES" && (tvSeasons.isNotEmpty() || isSeasonsLoading)) {
                            if (isSeasonsLoading && tvSeasons.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else {
                                // Season Selector Row
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(tvSeasons, key = { "season_${it.seasonNumber}" }) { season ->
                                            val isSelected = season.seasonNumber == selectedSeasonNumber
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.clickable { selectedSeasonNumber = season.seasonNumber }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        text = season.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 13.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "(${season.episodes.size})",
                                                        fontSize = 11.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Episodes of selected season
                                val currentSeason = tvSeasons.firstOrNull { it.seasonNumber == selectedSeasonNumber } ?: tvSeasons.firstOrNull()
                                val episodesList = currentSeason?.episodes ?: emptyList()

                                items(episodesList, key = { "ep_${it.id}_s${it.seasonNumber}_e${it.episodeNumber}" }) { episode ->
                                    val isCurrentPlaying = (currentStreamData?.selectedStreamOption?.videoUrl == episode.id) ||
                                            (displayTitle.contains("E${episode.episodeNumber}", ignoreCase = true) && displayTitle.contains("S${episode.seasonNumber}", ignoreCase = true))

                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        border = if (isCurrentPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .clickable {
                                                viewModel.playEpisode(episode, currentStreamData)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Episode Still / Thumbnail
                                            Box(
                                                modifier = Modifier
                                                    .width(112.dp)
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                if (!episode.thumbnailUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = episode.thumbnailUrl,
                                                        contentDescription = episode.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayCircleOutline,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }

                                                // Episode number badge overlay
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.75f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(4.dp)
                                                ) {
                                                    Text(
                                                        text = "EP ${episode.episodeNumber}",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }

                                                // Play icon overlay if active
                                                if (isCurrentPlaying) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Playing",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Episode Metadata
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "Episode ${episode.episodeNumber}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                        fontWeight = FontWeight.SemiBold
                                                    )

                                                    if (episode.voteAverage != null && episode.voteAverage > 0f) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.Star,
                                                                contentDescription = null,
                                                                tint = Color(0xFFFFB800),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = String.format("%.1f", episode.voteAverage),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = episode.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (!episode.overview.isNullOrBlank()) {
                                                    Text(
                                                        text = episode.overview,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 15.sp,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }

                                                if (isCurrentPlaying) {
                                                    Text(
                                                        text = "▶ Now Playing",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(top = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (selectedPillTab == "CHAPTERS") {
                            itemsIndexed(chaptersForDetail, key = { idx, ch -> "detail_ch_${idx}_${ch.startTimeMs}" }) { idx, chapter ->
                                val isActive = idx == activeChapterIndex
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clickable {
                                            GlobalPlayerManager.seekTo(chapter.startTimeMs)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(96.dp)
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.BottomEnd
                                        ) {
                                            if (!currentStreamData?.thumbnailUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = currentStreamData?.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(if (isActive) Color(0x66FF0033) else Color(0x40000000))
                                            )
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .size(24.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Playing",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .padding(3.dp)
                                                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = com.example.extractor.chapters.YTCustomChapters.formatMs(chapter.startTimeMs),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = chapter.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = com.example.extractor.chapters.YTCustomChapters.formatMs(chapter.startTimeMs),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (selectedPillTab == "COMMENTS") {
                            item {
                                com.example.ui.components.VideoCommentsSection(
                                    comments = videoComments,
                                    isLoading = isCommentsLoading,
                                    onAddComment = { text ->
                                        activeVideoId?.let { vid -> viewModel.addComment(text, vid) }
                                    },
                                    onLikeComment = { commentId ->
                                        viewModel.toggleCommentLike(commentId)
                                    },
                                    onSeekToTimestamp = { ms ->
                                        GlobalPlayerManager.seekTo(ms)
                                    },
                                    onRefresh = {
                                        activeVideoId?.let { vid ->
                                            val pId = providerId ?: currentStreamData?.providerId ?: "youtube"
                                            val title = displayTitle.takeIf { it.isNotBlank() && it != "Loading video..." }
                                            viewModel.loadVideoComments(
                                                videoId = vid,
                                                providerId = pId,
                                                videoTitle = title
                                            )
                                        }
                                    }
                                )
                            }
                        } else if (selectedPillTab == "REACTIONS") {
                            item(key = "reactions_tab_content") {
                                com.example.ui.components.VideoReactionsSection(
                                    reactions = videoReactions,
                                    isLoading = isReactionsLoading,
                                    onReactionClick = { reactionVideo ->
                                        viewModel.playVideo(reactionVideo.id, reactionVideo.providerId)
                                    },
                                    onRefresh = {
                                        val title = displayTitle.takeIf { it.isNotBlank() && it != "Loading video..." }
                                        viewModel.loadVideoReactions(title, activeVideoId)
                                    }
                                )
                            }
                        } else {
                            // Related Videos List
                            val topFullVideo = relatedContent.firstOrNull {
                                val r = it.recommendationReason
                                r?.contains("Full Movie") == true ||
                                r?.contains("Full Match") == true ||
                                r?.contains("Full Episode") == true ||
                                r?.contains("Next Part") == true ||
                                r?.contains("Uncut Reaction") == true ||
                                r?.contains("Full Video") == true
                            }

                            if (topFullVideo != null) {
                                val reason = topFullVideo.recommendationReason ?: ""
                                val isMovie = reason.contains("Full Movie")
                                val isMatch = reason.contains("Full Match")
                                val isEpisode = reason.contains("Full Episode")
                                val isNextPart = reason.contains("Next Part")
                                val isUncut = reason.contains("Uncut Reaction")

                                val bannerIcon = when {
                                    isMovie -> Icons.Default.Movie
                                    isMatch -> Icons.Default.SportsSoccer
                                    isEpisode -> Icons.Default.Tv
                                    isNextPart -> Icons.Default.SkipNext
                                    isUncut -> Icons.Default.RateReview
                                    else -> Icons.Default.PlayCircle
                                }

                                val bannerSubtitle = when {
                                    isMovie -> "Watching a clip? Tap to switch to the complete full movie"
                                    isMatch -> "Watching highlights? Tap to watch the complete full match replay"
                                    isEpisode -> "Watching an excerpt? Tap to watch the full episode"
                                    isNextPart -> "Finished this part? Continue watching the next reaction part"
                                    isUncut -> "Enjoying this reaction? Tap to watch the full uncut version"
                                    else -> "Watching a clip? Tap to switch to the complete full video"
                                }

                                val bannerButtonText = when {
                                    isMovie -> "Full Movie"
                                    isMatch -> "Full Match"
                                    isEpisode -> "Full Episode"
                                    isNextPart -> "Next Part"
                                    isUncut -> "Watch Uncut"
                                    else -> "Watch Full"
                                }

                                item(key = "spotlight_full_video") {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .clickable {
                                                viewModel.playVideo(topFullVideo.id, topFullVideo.providerId)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = bannerIcon,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = topFullVideo.recommendationReason ?: "Full Version Available",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = topFullVideo.title,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = bannerSubtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            FilledTonalButton(
                                                onClick = { viewModel.playVideo(topFullVideo.id, topFullVideo.providerId) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(bannerButtonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (relatedContent.isNotEmpty()) {
                                items(
                                    items = relatedContent,
                                    key = { "rel_${it.providerId ?: ""}_${it.id}" },
                                    contentType = { "video_card" }
                                ) { video ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 0.dp)
                                    ) {
                                        VideoCard(
                                            video = video,
                                            showProviderBadge = showThumbnailTags,
                                            onClick = {
                                                viewModel.playVideo(video.id, video.providerId)
                                            },
                                            onChannelClick = { channelName -> viewModel.openChannel(channelName) },
                                            onNotInterested = { v -> viewModel.markNotInterested(v) },
                                            onPlayNextInQueue = { v -> viewModel.playNextInQueue(v) },
                                            onAddToQueue = { v -> viewModel.addToQueue(v) },
                                            onSaveToWatchLater = { v -> viewModel.addToWatchLater(v) },
                                            onDownload = { v -> viewModel.showDownloadSheet(v) }
                                        )
                                    }
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No related videos available.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (isLoadingMore || isLoadingPlayerRecs) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}

    // MODAL BOTTOM SHEET: DOWNLOAD QUALITY PICKER
    if (showDownloadQualitySheet) {
        DownloadQualityBottomSheet(
            videoTitle = currentStreamData?.title ?: currentVideoItem?.title ?: "Video",
            channelName = currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: "Channel",
            thumbnailUrl = currentStreamData?.thumbnailUrl ?: currentVideoItem?.thumbnailUrl,
            durationText = currentVideoItem?.formattedDuration,
            availableOptions = currentStreamData?.availableStreamOptions ?: emptyList(),
            onConfirmDownload = { qualityLabel, chosenStreamOption ->
                showDownloadQualitySheet = false
                val targetVideoId = activeVideoId ?: currentStreamData?.videoId ?: currentVideoItem?.id
                if (!targetVideoId.isNullOrBlank()) {
                    viewModel.startDownload(
                        videoId = targetVideoId,
                        title = currentStreamData?.title ?: currentVideoItem?.title ?: "Video",
                        channelName = currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: "Channel",
                        thumbnailUrl = currentStreamData?.thumbnailUrl ?: currentVideoItem?.thumbnailUrl,
                        qualityLabel = qualityLabel,
                        streamOption = chosenStreamOption ?: selectedOption
                    )
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Download started in $qualityLabel")
                    }
                }
            },
            onDismissRequest = { showDownloadQualitySheet = false }
        )
    }

    // UNIFIED SERVERS & SOURCES SELECTOR SHEET (Vega Direct Streams + BitTorrent Swarms)
    if (showServerSelectorSheet) {
        com.example.ui.player.UnifiedServerSelectorSheet(
            candidates = unifiedCandidates,
            activeCandidate = activeSourceCandidate,
            isResolving = isResolvingUnifiedSources,
            statusMessage = unifiedStatusMessage,
            onSelectCandidate = { candidate ->
                showServerSelectorSheet = false
                viewModel.switchUnifiedSource(candidate)
            },
            onDismiss = { showServerSelectorSheet = false }
        )
    }

    // SAVE TO PLAYLIST SHEET
    if (showSaveToPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveToPlaylistSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save video to...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            showSaveToPlaylistSheet = false
                            showCreatePlaylistDialog = true
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Playlist")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Watch Later item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentVideoItem?.let { video ->
                                if (isSavedInWatchLater) viewModel.removeFromWatchLater(video)
                                else viewModel.addToWatchLater(video)
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSavedInWatchLater,
                        onCheckedChange = { checked ->
                            currentVideoItem?.let { video ->
                                if (checked) viewModel.addToWatchLater(video)
                                else viewModel.removeFromWatchLater(video)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Watch later",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Custom User Playlists
                userPlaylists.forEach { playlist ->
                    val isVideoInPlaylist = currentVideoItem != null && playlist.videos.any { it.id == currentVideoItem.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentVideoItem?.let { video ->
                                    if (isVideoInPlaylist) {
                                        viewModel.removeFromPlaylist(playlist.id, video)
                                    } else {
                                        viewModel.addToPlaylist(playlist.id, video)
                                    }
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isVideoInPlaylist,
                            onCheckedChange = { checked ->
                                currentVideoItem?.let { video ->
                                    if (checked) viewModel.addToPlaylist(playlist.id, video)
                                    else viewModel.removeFromPlaylist(playlist.id, video)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = playlist.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showSaveToPlaylistSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistTitle.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistTitle)
                            val created = viewModel.userPlaylists.value.lastOrNull()
                            if (created != null && currentVideoItem != null) {
                                viewModel.addToPlaylist(created.id, currentVideoItem)
                            }
                            newPlaylistTitle = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QueueSection(
    queue: List<VideoItem>,
    onPlayItem: (VideoItem) -> Unit,
    onRemoveItem: (VideoItem) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Queue • ${queue.size} video${if (queue.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Temporary",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                TextButton(
                    onClick = onClearQueue,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Queue",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Queued items in a horizontal scrollable row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(queue, key = { index, item -> "queue_${item.id}_$index" }) { index, item ->
                    Card(
                        onClick = { onPlayItem(item) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.width(180.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = item.thumbnailUrl ?: "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg",
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (index == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "Playing next",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveItem(item) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                if (item.formattedDuration.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = item.formattedDuration,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = item.uploaderName,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
