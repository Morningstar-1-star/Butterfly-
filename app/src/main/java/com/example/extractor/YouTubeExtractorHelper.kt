package com.example.extractor

import android.content.Context
import android.util.Log
import java.net.URLEncoder
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

sealed class UrlParseResult {
    data class VideoId(val id: String) : UrlParseResult()
    data class ChannelId(val id: String) : UrlParseResult()
    data class PlaylistId(val id: String) : UrlParseResult()
    data class ShortId(val id: String) : UrlParseResult()
    object SearchQuery : UrlParseResult()
    data class Unknown(val url: String) : UrlParseResult()
    data class ParsedSearchResults(val items: List<VideoItem>) : UrlParseResult()
}

object YouTubeExtractorHelper {
    private const val TAG = "YouTubeExtractorHelper"

    interface CustomPoTokenProvider {
        fun getPoToken(visitorData: String?): String?
    }

    private var customPoTokenProvider: CustomPoTokenProvider? = null

    fun setPoTokenProvider(provider: CustomPoTokenProvider?) {
        customPoTokenProvider = provider
    }

    sealed class ExtractionResult {
        data class Success(val streamData: StreamData) : ExtractionResult()
        data class Error(val errorDetails: ExtractorErrorDetails) : ExtractionResult()
    }

    @Volatile
    private var isNewPipeInitialized = false
    private val initLock = Any()

    fun ensureNewPipeInitialized() {
        if (!isNewPipeInitialized) {
            synchronized(initLock) {
                if (!isNewPipeInitialized) {
                    try {
                        NewPipe.init(DownloaderImpl.getInstance())
                        isNewPipeInitialized = true
                        Log.i(TAG, "NewPipe initialized successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "NewPipe initialization note: ${e.message}")
                    }
                }
            }
        }
    }

    init {
        ensureNewPipeInitialized()
    }

    suspend fun fetchYouTubeTrending(context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val combinedTrending = mutableListOf<VideoItem>()

        // Step 1: NewPipe Trending Kiosk
        try {
            ensureNewPipeInitialized()
            val kioskInfo = org.schabi.newpipe.extractor.kiosk.KioskInfo.getInfo(
                ServiceList.YouTube,
                "Trending"
            )
            val items = kioskInfo.relatedItems?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                ?.mapNotNull { item ->
                    val vId = when {
                        item.url.contains("v=") -> item.url.substringAfter("v=").substringBefore("&").substringBefore("?")
                        item.url.contains("youtu.be/") -> item.url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                        item.url.length == 11 -> item.url
                        else -> item.url.substringAfterLast("/").takeIf { it.length == 11 }
                    }
                    if (vId.isNullOrBlank()) return@mapNotNull null
                    val rawThumb = item.thumbnails?.firstOrNull()?.url
                    val thumb = if (!rawThumb.isNullOrBlank()) rawThumb else "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
                    val uploaderAvatar = try {
                        item.uploaderAvatars?.firstOrNull()?.url
                    } catch (e: Exception) {
                        null
                    }
                    val uploaderUrl = try { item.uploaderUrl } catch (e: Exception) { null }
                    VideoItem(
                        id = vId,
                        title = item.name ?: "YouTube Video",
                        uploaderName = item.uploaderName ?: "YouTube",
                        uploaderUrl = uploaderUrl,
                        uploaderAvatarUrl = uploaderAvatar,
                        viewCount = item.viewCount,
                        durationSeconds = item.duration,
                        thumbnailUrl = thumb,
                        providerId = "youtube"
                    )
                } ?: emptyList()
            if (items.isNotEmpty()) {
                Log.i(TAG, "Fetched ${items.size} trending videos via NewPipe Kiosk")
                combinedTrending.addAll(items)
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe trending kiosk fetch failed: ${e.message}")
        }

        // Step 2: Dynamic Multi-Topic Expansion only if kiosk returned too few items (< 10)
        if (combinedTrending.size < 10) {
            val dynamicTrendingTopics = listOf(
                "latest viral trending videos",
                "official music videos 2026 top hits",
                "official movie trailers 4K",
                "trending anime episodes and scenes"
            ).shuffled().take(1)

            for (topic in dynamicTrendingTopics) {
                try {
                    val topicResults = searchYouTube(topic, context).take(10)
                    combinedTrending.addAll(topicResults)
                } catch (e: Exception) {
                    Log.w(TAG, "Dynamic topic search failed for '$topic': ${e.message}")
                }
            }
        }

        // Step 3: Additional topic search fallback if still low
        if (combinedTrending.size < 8) {
            val fallbackTopics = listOf("top news today", "viral videos", "music hits 2026")
            for (fTopic in fallbackTopics) {
                try {
                    val fResults = searchYouTube(fTopic, context).take(6)
                    combinedTrending.addAll(fResults)
                    if (combinedTrending.size >= 12) break
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback topic search failed for '$fTopic': ${e.message}")
                }
            }
        }

        combinedTrending.distinctBy { it.id }.shuffled()
    }

    suspend fun searchYouTube(query: String, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val cleanQuery = when {
            query.startsWith("ytsearch:", ignoreCase = true) -> query.substringAfter("ytsearch:").trim()
            query.startsWith("youtube:search:", ignoreCase = true) -> query.substringAfter("youtube:search:").trim()
            query.startsWith("youtube:search_url:", ignoreCase = true) -> query.substringAfter("youtube:search_url:").trim()
            query.startsWith("youtube:music:search_url:", ignoreCase = true) -> query.substringAfter("youtube:music:search_url:").trim()
            query.startsWith("youtube:music:", ignoreCase = true) -> "${query.substringAfter("youtube:music:").trim()} music"
            query.startsWith("ytuser:", ignoreCase = true) -> query.substringAfter("ytuser:").trim()
            query.startsWith("youtube:user:", ignoreCase = true) -> query.substringAfter("youtube:user:").trim()
            query.startsWith("youtube:playlist:", ignoreCase = true) -> query.substringAfter("youtube:playlist:").trim()
            query.startsWith("youtube:tab:", ignoreCase = true) -> query.substringAfter("youtube:tab:").trim()
            query.equals(":ytrec", ignoreCase = true) || query.equals("youtube:recommended", ignoreCase = true) -> "trending"
            query.equals(":ytfav", ignoreCase = true) || query.equals("youtube:favorites", ignoreCase = true) -> "top music favorites"
            query.equals(":ythis", ignoreCase = true) || query.equals("youtube:history", ignoreCase = true) -> "latest videos"
            query.equals(":ytnotif", ignoreCase = true) || query.equals("youtube:notif", ignoreCase = true) -> "news notifications"
            query.equals(":ytsubs", ignoreCase = true) || query.equals("youtube:subscriptions", ignoreCase = true) -> "popular channels"
            query.equals(":ytwatchlater", ignoreCase = true) || query.equals("youtube:watchlater", ignoreCase = true) -> "watch later mix"
            query.startsWith("youtube:", ignoreCase = true) -> query.substringAfter("youtube:").trim()
            else -> query.trim()
        }

        // Step 1: NewPipe Search
        try {
            ensureNewPipeInitialized()
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(cleanQuery)
            searchExtractor.fetchPage()
            val items = searchExtractor.initialPage?.items?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                ?.mapNotNull { item ->
                    val vId = when {
                        item.url.contains("v=") -> item.url.substringAfter("v=").substringBefore("&").substringBefore("?")
                        item.url.contains("youtu.be/") -> item.url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                        item.url.length == 11 -> item.url
                        else -> item.url.substringAfterLast("/").takeIf { it.length == 11 }
                    }
                    if (vId.isNullOrBlank()) return@mapNotNull null
                    val rawThumb = item.thumbnails?.firstOrNull()?.url
                    val thumb = if (!rawThumb.isNullOrBlank()) rawThumb else "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
                    val uploaderAvatar = try {
                        item.uploaderAvatars?.firstOrNull()?.url
                    } catch (e: Exception) {
                        null
                    }
                    val uploaderUrl = try { item.uploaderUrl } catch (e: Exception) { null }
                    VideoItem(
                        id = vId,
                        title = item.name ?: "YouTube Video",
                        uploaderName = item.uploaderName ?: "YouTube",
                        uploaderUrl = uploaderUrl,
                        uploaderAvatarUrl = uploaderAvatar,
                        viewCount = item.viewCount,
                        durationSeconds = item.duration,
                        thumbnailUrl = thumb,
                        providerId = "youtube"
                    )
                } ?: emptyList()
            if (items.isNotEmpty()) {
                Log.i(TAG, "Fetched ${items.size} search results for '$cleanQuery' via NewPipe")
                return@withContext items
            }
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe search failed for '$cleanQuery': ${e.message}")
        }

        emptyList()
    }

    suspend fun fetchChannelDetails(
        channelNameOrUrl: String,
        context: Context? = null,
        fallbackAvatar: String? = null
    ): ChannelDetails = withContext(Dispatchers.IO) {
        val trimmed = channelNameOrUrl.trim()
        val brand = com.example.util.ChannelLogoHelper.getBrandInfo(trimmed, fallbackAvatar)
        var cleanName = if (trimmed.startsWith("http") || trimmed.startsWith("@")) {
            brand.brandName
        } else {
            trimmed
        }
        if (cleanName.isBlank()) cleanName = "Creator Channel"

        val handle = if (cleanName.startsWith("@")) cleanName else "@${cleanName.replace("\\s+".toRegex(), "").lowercase()}"
        var channelAvatar = fallbackAvatar ?: brand.logoUrls.firstOrNull()
        val bannerUrl: String? = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1200&auto=format&fit=crop&q=80"
        val description = "Welcome to the official channel of $cleanName. Watch our latest videos, specials, and exclusive content."
        val subscriberCount = brand.subscriberCountText.ifBlank { "850K subscribers" }
        val videoList = mutableListOf<VideoItem>()

        try {
            val searchResults = searchYouTube(cleanName, context)
            val exactMatches = searchResults.filter { 
                it.uploaderName.equals(cleanName, ignoreCase = true) ||
                it.uploaderName.contains(cleanName, ignoreCase = true) ||
                cleanName.contains(it.uploaderName, ignoreCase = true)
            }

            if (exactMatches.isNotEmpty()) {
                videoList.addAll(exactMatches)
                if (channelAvatar == null) {
                    channelAvatar = exactMatches.firstOrNull { !it.uploaderAvatarUrl.isNullOrBlank() }?.uploaderAvatarUrl
                }
            } else if (searchResults.isNotEmpty()) {
                videoList.addAll(searchResults.take(15))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search fallback for channel '$cleanName' failed: ${e.message}")
        }

        // Secondary search if needed
        if (videoList.size < 4) {
            try {
                val more = searchYouTube("$cleanName official", context)
                val seen = videoList.map { it.id }.toSet()
                videoList.addAll(more.filterNot { seen.contains(it.id) })
            } catch (e: Exception) {
                // ignore
            }
        }

        val shorts = videoList.filter { it.durationSeconds in 1..65 }.take(12)
        val regularVideos = videoList.filterNot { it.durationSeconds in 1..65 }
        val finalVideos = if (regularVideos.isNotEmpty()) regularVideos else videoList

        val videoCountText = if (videoList.isNotEmpty()) "${videoList.size} videos" else "90+ videos"

        return@withContext ChannelDetails(
            channelId = cleanName.lowercase().replace("[^a-z0-9]".toRegex(), "_").take(30),
            name = cleanName,
            handle = handle,
            avatarUrl = channelAvatar,
            bannerUrl = bannerUrl,
            subscriberCount = subscriberCount,
            videoCount = videoCountText,
            description = description,
            totalViews = "450M views",
            joinedDate = "Joined 2020",
            isVerified = true,
            videos = finalVideos.distinctBy { it.id },
            shorts = shorts.distinctBy { it.id }
        )
    }

    suspend fun resolveStream(urlOrId: String, context: Context? = null, providerId: String? = null): ExtractionResult = withContext(Dispatchers.IO) {
        // Step 0: Direct Vault / M3U8 / Local file handling (zero transcoding/downloading)
        val isM3u8OrLocal = providerId == "m3u8" || providerId == "local" || providerId == "vault" || providerId == "gdrive" ||
                urlOrId.startsWith("content://") || urlOrId.startsWith("file://") ||
                urlOrId.contains(".m3u8", ignoreCase = true) || urlOrId.startsWith("m3u8_") || urlOrId.startsWith("local_") || urlOrId.startsWith("gdrive_")

        if (isM3u8OrLocal) {
            val isHls = urlOrId.contains(".m3u8", ignoreCase = true)
            val effectiveUrl = if (urlOrId.startsWith("content://") || urlOrId.startsWith("file://") || urlOrId.startsWith("http://") || urlOrId.startsWith("https://")) {
                urlOrId
            } else if (context != null) {
                try {
                    val repo = com.example.cloudsocial.repository.CloudSocialRepository.getInstance(context)
                    repo.resolveStreamUrlByUrlOrId(urlOrId).ifBlank { urlOrId }
                } catch (e: Exception) {
                    urlOrId
                }
            } else {
                urlOrId
            }

            val option = PlayableStreamOption(
                qualityLabel = if (isHls) "HLS Master Stream" else "Original Video",
                format = if (isHls) "hls" else "mp4",
                isMuxed = true,
                videoUrl = effectiveUrl,
                audioUrl = null,
                providerType = com.example.model.ProviderType.DIRECT,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            )
            val streamData = StreamData(
                videoId = urlOrId,
                videoUrl = effectiveUrl,
                title = if (isHls) "M3U8 Stream" else "Butterfly Vault Video",
                channelName = "Butterfly Vault",
                channelAvatarUrl = null,
                description = "Saved Stream Reference / Device Video",
                availableStreamOptions = listOf(option),
                selectedStreamOption = option,
                hlsUrl = if (isHls) effectiveUrl else null,
                providerId = if (isHls) "m3u8" else "local",
                providerType = com.example.model.ProviderType.DIRECT,
                headers = option.headers
            )
            Log.i(TAG, "Resolved stream via Butterfly Vault for $urlOrId -> $effectiveUrl")
            return@withContext ExtractionResult.Success(streamData)
        }

        // Step 0.5: Check registered specialized extractor plugins (HiAnime, AniWatch, Hanime, Coomer, PMVHaven)
        if (context != null) {
            val pluginStream = com.example.extractor.plugins.ExtractorPluginManager.tryExtractWithPlugin(context, urlOrId)
            if (pluginStream != null) {
                Log.i(TAG, "Resolved via specialized ExtractorPlugin for $urlOrId")
                return@withContext ExtractionResult.Success(pluginStream)
            }
        }

        val isCloudSocial = providerId == "bun-tel-meg" || providerId == "cloud_social" || providerId == "bunkr" || providerId == "telegram" || providerId == "mega" || urlOrId.contains("bunkr") || urlOrId.contains("mega.nz") || urlOrId.contains("mega.io") || urlOrId.contains("t.me/") || urlOrId.startsWith("tg_") || urlOrId.startsWith("mega_") || urlOrId.startsWith("bunkr_")
        if (isCloudSocial && context != null) {
            try {
                val repo = com.example.cloudsocial.repository.CloudSocialRepository.getInstance(context)
                val streamUrl = repo.resolveStreamUrlByUrlOrId(urlOrId)
                if (streamUrl.isNotBlank() && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) {
                    val streamHeaders = when {
                        streamUrl.contains("bunkr") || urlOrId.contains("bunkr") -> mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                            "Referer" to "https://bunkr.site/",
                            "Origin" to "https://bunkr.site"
                        )
                        streamUrl.contains("mega.nz") || streamUrl.contains("mega.co.nz") || streamUrl.contains("userstorage.mega") || urlOrId.contains("mega") -> mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            "Referer" to "https://mega.nz/"
                        )
                        streamUrl.contains("t.me") || streamUrl.contains("telesco.pe") || urlOrId.contains("tg_") -> mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            "Referer" to "https://t.me/"
                        )
                        else -> mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }
                    val option = PlayableStreamOption(
                        qualityLabel = "HD Direct Stream",
                        format = if (streamUrl.contains(".m3u8")) "hls" else "mp4",
                        isMuxed = true,
                        videoUrl = streamUrl,
                        audioUrl = null,
                        providerType = com.example.model.ProviderType.DIRECT,
                        headers = streamHeaders
                    )
                    val streamData = StreamData(
                        videoId = urlOrId,
                        title = "bun-tel-meg Stream",
                        channelName = "bun-tel-meg",
                        channelAvatarUrl = null,
                        description = "Direct stream resolved from bun-tel-meg Cloud & Social provider",
                        availableStreamOptions = listOf(option),
                        selectedStreamOption = option,
                        providerId = "bun-tel-meg",
                        providerType = com.example.model.ProviderType.DIRECT,
                        headers = option.headers
                    )
                    Log.i(TAG, "Resolved stream via CloudSocialRepository for $urlOrId -> $streamUrl")
                    return@withContext ExtractionResult.Success(streamData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "CloudSocial stream resolution failed: ${e.message}", e)
            }
        }

        val isArchive = providerId == "archive_org" || providerId == "archive" || urlOrId.contains("archive.org") || urlOrId.startsWith("archive_") || urlOrId.startsWith("archive:")
        if (isArchive) {
            val archiveData = ArchiveOrgProvider.getStreamData(urlOrId, context)
            if (archiveData != null) {
                Log.i(TAG, "Resolved via ArchiveOrgProvider for $urlOrId")
                return@withContext ExtractionResult.Success(archiveData)
            } else if (context != null) {
                val cleanId = ArchiveOrgProvider.extractId(urlOrId)
                val archiveUrl = if (urlOrId.startsWith("http")) urlOrId else "https://archive.org/details/$cleanId"
                Log.i(TAG, "Routing Archive.org to YtDlpResolver fallback for $archiveUrl")
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, archiveUrl)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ytdlResult
                }
            }
            return@withContext ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                    message = "Archive.org video could not be loaded",
                    rawExceptionName = "ArchiveExtractionException",
                    fullStackTrace = "",
                    urlOrId = urlOrId
                )
            )
        }

        val isEporner = providerId == "eporner" || urlOrId.contains("eporner.com")
        if (isEporner) {
            val epornerData = EpornerProvider.getStreamData(urlOrId, context)
            if (epornerData != null) {
                Log.i(TAG, "Resolved via EpornerProvider (yt-dlp) for $urlOrId")
                return@withContext ExtractionResult.Success(epornerData)
            } else if (context != null) {
                Log.i(TAG, "Routing Eporner to YtDlpResolver for $urlOrId")
                return@withContext YtDlpResolver.extractStreamInfo(context, urlOrId)
            }
        }

        val isPornhub = providerId == "pornhub" || urlOrId.contains("pornhub.com") || urlOrId.contains("phncdn.com")
        if (isPornhub) {
            val pornhubData = PornhubProvider.getStreamData(urlOrId, context)
            if (pornhubData != null) {
                Log.i(TAG, "Resolved via PornhubProvider for $urlOrId")
                return@withContext ExtractionResult.Success(pornhubData)
            } else if (context != null) {
                Log.i(TAG, "Routing Pornhub to YtDlpResolver for $urlOrId")
                val fullPhUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.pornhub.com/view_video.php?viewkey=$urlOrId"
                return@withContext YtDlpResolver.extractStreamInfo(context, fullPhUrl)
            }
        }

        val isRule34 = providerId == "rule34video" || urlOrId.contains("rule34video.com")
        if (isRule34) {
            val r34Data = Rule34VideoProvider.getStreamData(urlOrId, context)
            if (r34Data != null) {
                Log.i(TAG, "Resolved via Rule34VideoProvider for $urlOrId")
                return@withContext ExtractionResult.Success(r34Data)
            } else if (context != null) {
                Log.i(TAG, "Routing Rule34Video to YtDlpResolver for $urlOrId")
                return@withContext YtDlpResolver.extractStreamInfo(context, urlOrId)
            }
        }

        val isCam4 = providerId == "cam4" || urlOrId.contains("cam4.com") || urlOrId.startsWith("cam4:", ignoreCase = true)
        if (isCam4) {
            val cam4Data = CAM4Provider.getStreamData(urlOrId, context)
            if (cam4Data != null) {
                Log.i(TAG, "Resolved via CAM4Provider for $urlOrId")
                return@withContext ExtractionResult.Success(cam4Data)
            }
        }

        val isBigo = providerId == "bigo" || urlOrId.contains("bigo.tv") || urlOrId.contains("bigolive.tv") || urlOrId.startsWith("bigo:", ignoreCase = true)
        if (isBigo) {
            val bigoData = BigoProvider.getStreamData(urlOrId, context)
            if (bigoData != null) {
                Log.i(TAG, "Resolved via BigoProvider for $urlOrId")
                return@withContext ExtractionResult.Success(bigoData)
            } else if (context != null) {
                Log.i(TAG, "Routing Bigo to YtDlpResolver for $urlOrId")
                return@withContext YtDlpResolver.extractStreamInfo(context, urlOrId)
            }
        }

        val isCamModels = providerId == "cammodels" || urlOrId.contains("cammodels.com") || urlOrId.startsWith("cammodels:", ignoreCase = true)
        if (isCamModels) {
            val cmData = CamModelsProvider.getStreamData(urlOrId, context)
            if (cmData != null) {
                Log.i(TAG, "Resolved via CamModelsProvider for $urlOrId")
                return@withContext ExtractionResult.Success(cmData)
            }
        }

        val isChaturbate = providerId == "chaturbate" || urlOrId.contains("chaturbate.com") || urlOrId.startsWith("chaturbate:", ignoreCase = true)
        if (isChaturbate) {
            val cbData = ChaturbateProvider.getStreamData(urlOrId, context)
            if (cbData != null) {
                Log.i(TAG, "Resolved via ChaturbateProvider for $urlOrId")
                return@withContext ExtractionResult.Success(cbData)
            }
        }

        val isDailymotion = providerId == "dailymotion" || urlOrId.contains("dailymotion.com") || urlOrId.contains("dai.ly") ||
                urlOrId.startsWith("dailymotion:", ignoreCase = true)
        if (isDailymotion) {
            val dmData = DailymotionProvider.getStreamData(urlOrId, context)
            if (dmData != null) {
                Log.i(TAG, "Resolved via DailymotionProvider for $urlOrId")
                return@withContext ExtractionResult.Success(dmData)
            }
        }

        val isThisVid = providerId == "thisvid" || urlOrId.contains("thisvid.com") ||
                urlOrId.startsWith("thisvid:", ignoreCase = true)
        if (isThisVid) {
            val tvData = ThisVidProvider.getStreamData(urlOrId, context)
            if (tvData != null) {
                Log.i(TAG, "Resolved via ThisVidProvider for $urlOrId")
                return@withContext ExtractionResult.Success(tvData)
            }
        }

        val isTnaFlix = providerId == "tnaflix" || urlOrId.contains("tnaflix.com") ||
                urlOrId.startsWith("tnaflix:", ignoreCase = true)
        if (isTnaFlix) {
            val tnaData = TnaFlixProvider.getStreamData(urlOrId, context)
            if (tnaData != null) {
                Log.i(TAG, "Resolved via TnaFlixProvider for $urlOrId")
                return@withContext ExtractionResult.Success(tnaData)
            }
        }

        val isNoodleMagazine = providerId == "noodlemagazine" || providerId == "noodlemag" || urlOrId.contains("noodlemagazine.com") ||
                urlOrId.startsWith("noodlemagazine:", ignoreCase = true) || urlOrId.startsWith("noodlemag:", ignoreCase = true)
        if (isNoodleMagazine) {
            val nmData = NoodleMagazineProvider.getStreamData(urlOrId, context)
            if (nmData != null) {
                Log.i(TAG, "Resolved via NoodleMagazineProvider for $urlOrId")
                return@withContext ExtractionResult.Success(nmData)
            }
        }

        val isVimeo = providerId == "vimeo" || urlOrId.contains("vimeo.com")
        if (isVimeo) {
            val vimeoData = VimeoProvider.getStreamData(urlOrId, context)
            if (vimeoData != null) {
                Log.i(TAG, "Resolved via VimeoProvider for $urlOrId")
                return@withContext ExtractionResult.Success(vimeoData)
            }
        }

        val isHotstar = providerId == "hotstar" || providerId == "jiohotstar" || urlOrId.contains("hotstar.com") || urlOrId.contains("jiohotstar.com")
        if (isHotstar) {
            val hotstarData = HotstarProvider.getStreamData(urlOrId, context)
            if (hotstarData != null) {
                Log.i(TAG, "Resolved via HotstarProvider for $urlOrId")
                return@withContext ExtractionResult.Success(hotstarData)
            } else if (context != null) {
                Log.i(TAG, "Routing Hotstar to YtDlpResolver fallback for $urlOrId")
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, urlOrId)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ExtractionResult.Success(
                        ytdlResult.streamData.copy(providerId = HotstarProvider.PROVIDER_ID)
                    )
                }
            }
        }

        val isMiniTv = providerId == "amazonminitv" || providerId == "minitv" || urlOrId.contains("amazon.in/minitv") || urlOrId.contains("amazonminitv")
        if (isMiniTv) {
            val miniTvData = AmazonMiniTvProvider.getStreamData(urlOrId, context)
            if (miniTvData != null) {
                Log.i(TAG, "Resolved via AmazonMiniTvProvider for $urlOrId")
                return@withContext ExtractionResult.Success(miniTvData)
            }
        }

        val isDiscovery = providerId == "discoveryplus" || providerId == "discovery" || urlOrId.contains("discoveryplus")
        if (isDiscovery) {
            val discData = DiscoveryPlusProvider.getStreamData(urlOrId, context)
            if (discData != null) {
                Log.i(TAG, "Resolved via DiscoveryPlusProvider for $urlOrId")
                return@withContext ExtractionResult.Success(discData)
            }
        }

        val isDisney = providerId == "disney" || providerId == "disneyplus" || urlOrId.contains("disneyplus.com")
        if (isDisney) {
            val disneyData = DisneyProvider.getStreamData(urlOrId, context)
            if (disneyData != null) {
                Log.i(TAG, "Resolved via DisneyProvider for $urlOrId")
                return@withContext ExtractionResult.Success(disneyData)
            }
        }

        val isGoogleDrive = providerId == "googledrive" || providerId == "gdrive" || providerId == "google_drive" || urlOrId.contains("drive.google.com") || urlOrId.contains("docs.google.com")
        if (isGoogleDrive) {
            val gdriveData = GoogleDriveProvider.getStreamData(urlOrId, context)
            if (gdriveData != null) {
                Log.i(TAG, "Resolved via GoogleDriveProvider for $urlOrId")
                return@withContext ExtractionResult.Success(gdriveData)
            }
        }

        val isImdb = providerId == "imdb" || urlOrId.contains("imdb.com")
        if (isImdb) {
            val imdbData = ImdbProvider.getStreamData(urlOrId, context)
            if (imdbData != null) {
                Log.i(TAG, "Resolved via ImdbProvider for $urlOrId")
                return@withContext ExtractionResult.Success(imdbData)
            }
        }

        val isMxPlayer = providerId == "mxplayer" || urlOrId.contains("mxplayer.in")
        if (isMxPlayer) {
            val mxData = MxPlayerProvider.getStreamData(urlOrId, context)
            if (mxData != null) {
                Log.i(TAG, "Resolved via MxPlayerProvider for $urlOrId")
                return@withContext ExtractionResult.Success(mxData)
            }
        }

        val isPopcornTv = providerId == "popcorntv" || providerId == "popcorn" || urlOrId.contains("popcorntime")
        if (isPopcornTv) {
            val popcornData = PopcornTvProvider.getStreamData(urlOrId, context)
            if (popcornData != null) {
                Log.i(TAG, "Resolved via PopcornTvProvider for $urlOrId")
                return@withContext ExtractionResult.Success(popcornData)
            }
        }

        val isXHamster = providerId == "xhamster" || urlOrId.contains("xhamster.com") || urlOrId.contains("xhcdn.com")
        if (isXHamster) {
            val xhData = XHamsterProvider.getStreamData(urlOrId, context)
            if (xhData != null) {
                Log.i(TAG, "Resolved via XHamsterProvider for $urlOrId")
                return@withContext ExtractionResult.Success(xhData)
            } else if (context != null) {
                Log.i(TAG, "Routing xHamster to YtDlpResolver fallback for $urlOrId")
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, urlOrId)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ytdlResult
                }
            }
        }

        val isXVideos = providerId == "xvideos" || urlOrId.contains("xvideos.com")
        if (isXVideos) {
            val xvData = XVideosProvider.getStreamData(urlOrId, context)
            if (xvData != null) {
                Log.i(TAG, "Resolved via XVideosProvider for $urlOrId")
                return@withContext ExtractionResult.Success(xvData)
            }
        }

        val isYouPorn = providerId == "youporn" || urlOrId.contains("youporn.com")
        if (isYouPorn) {
            val ypData = YouPornProvider.getStreamData(urlOrId, context)
            if (ypData != null) {
                Log.i(TAG, "Resolved via YouPornProvider for $urlOrId")
                return@withContext ExtractionResult.Success(ypData)
            }
        }

        val isBeeg = providerId == "beeg" || urlOrId.contains("beeg.com")
        if (isBeeg) {
            val beegData = BeegProvider.getStreamData(urlOrId, context)
            if (beegData != null) {
                Log.i(TAG, "Resolved via BeegProvider for $urlOrId")
                return@withContext ExtractionResult.Success(beegData)
            }
            if (context != null) {
                val fullBeegUrl = if (urlOrId.startsWith("http")) urlOrId else "https://beeg.com/$urlOrId"
                Log.i(TAG, "Routing Beeg to YtDlpResolver fallback for $fullBeegUrl")
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullBeegUrl)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ExtractionResult.Success(
                        ytdlResult.streamData.copy(providerId = BeegProvider.PROVIDER_ID)
                    )
                }
            }
        }

        val isRedTube = providerId == "redtube" || urlOrId.contains("redtube.com")
        if (isRedTube) {
            val rtData = RedTubeProvider.getStreamData(urlOrId, context)
            if (rtData != null) {
                Log.i(TAG, "Resolved via RedTubeProvider for $urlOrId")
                return@withContext ExtractionResult.Success(rtData)
            } else if (context != null) {
                Log.i(TAG, "Routing RedTube to YtDlpResolver for $urlOrId")
                val fullRtUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.redtube.com/$urlOrId"
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullRtUrl)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ExtractionResult.Success(
                        ytdlResult.streamData.copy(providerId = RedTubeProvider.PROVIDER_ID)
                    )
                }
            }
        }

        val is4Tube = providerId == "4tube" || urlOrId.contains("4tube.com")
        if (is4Tube) {
            val ftData = FourTubeProvider.getStreamData(urlOrId, context)
            if (ftData != null) {
                Log.i(TAG, "Resolved via FourTubeProvider for $urlOrId")
                return@withContext ExtractionResult.Success(ftData)
            } else if (context != null) {
                Log.i(TAG, "Routing 4tube to YtDlpResolver for $urlOrId")
                val full4tUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.4tube.com/videos/$urlOrId"
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, full4tUrl)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ExtractionResult.Success(
                        ytdlResult.streamData.copy(providerId = FourTubeProvider.PROVIDER_ID)
                    )
                }
            }
        }

        val isSpankBang = providerId == "spankbang" || urlOrId.contains("spankbang.com") || urlOrId.contains("spankbang.")
        if (isSpankBang) {
            val sbData = SpankBangProvider.getStreamData(urlOrId, context)
            if (sbData != null) {
                Log.i(TAG, "Resolved via SpankBangProvider for $urlOrId")
                return@withContext ExtractionResult.Success(sbData)
            } else if (context != null) {
                val fullUrl = if (urlOrId.startsWith("http")) urlOrId else "https://spankbang.com/$urlOrId/video/"
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullUrl)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ExtractionResult.Success(ytdlResult.streamData.copy(providerId = "spankbang"))
                }
            }
        }

        val isHanime1 = providerId == "hanime1" || providerId == "hanime" || urlOrId.contains("hanime1") || urlOrId.contains("hanime.tv") ||
                urlOrId.startsWith("hanime1:", ignoreCase = true) || urlOrId.startsWith("hanime:", ignoreCase = true)
        if (isHanime1) {
            val h1Data = Hanime1Provider.getStreamData(urlOrId, context)
            if (h1Data != null) {
                Log.i(TAG, "Resolved via Hanime1Provider for $urlOrId")
                return@withContext ExtractionResult.Success(h1Data)
            } else if (context != null) {
                val videoId = Hanime1Provider.extractVideoId(urlOrId)
                val fullUrl = if (urlOrId.startsWith("http")) urlOrId else "https://hanime1.me/watch?v=$videoId"
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullUrl)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ExtractionResult.Success(ytdlResult.streamData.copy(providerId = "hanime1"))
                }
            }
        }

        val isHQPorner = providerId == "hqporner" || providerId == "hqplayer" || urlOrId.contains("hqporner") || urlOrId.contains("hqplayer") ||
                urlOrId.startsWith("hqporner:", ignoreCase = true) || urlOrId.startsWith("hqplayer:", ignoreCase = true)
        if (isHQPorner) {
            val hqpData = HQPornerProvider.getStreamData(urlOrId, context)
            if (hqpData != null) {
                Log.i(TAG, "Resolved via HQPornerProvider for $urlOrId")
                return@withContext ExtractionResult.Success(hqpData)
            } else if (context != null) {
                val videoSlug = HQPornerProvider.extractVideoId(urlOrId)
                val fullUrl = if (urlOrId.startsWith("http")) urlOrId else "https://hqporner.com/hdporn/$videoSlug.html"
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullUrl)
                if (ytdlResult is ExtractionResult.Success) {
                    return@withContext ExtractionResult.Success(ytdlResult.streamData.copy(providerId = "hqporner"))
                }
            }
        }

        val isTwitch = providerId == "twitch" || urlOrId.contains("twitch.tv")
        if (isTwitch) {
            val twitchData = TwitchProvider.getStreamData(urlOrId, context)
            if (twitchData != null) {
                Log.i(TAG, "Resolved via TwitchProvider for $urlOrId")
                return@withContext ExtractionResult.Success(twitchData)
            }
        }

        val isBilibili = providerId == "bilibili" ||
                urlOrId.contains("bilibili.com") ||
                urlOrId.contains("b23.tv") ||
                urlOrId.startsWith("BV", ignoreCase = true) ||
                urlOrId.startsWith("av", ignoreCase = true) ||
                urlOrId.startsWith("ep", ignoreCase = true) ||
                urlOrId.startsWith("ss", ignoreCase = true) ||
                urlOrId.startsWith("md", ignoreCase = true) ||
                urlOrId.startsWith("bilisearch", ignoreCase = true)
        if (isBilibili) {
            val biliData = BilibiliProvider.getStreamData(urlOrId, context)
            if (biliData != null && biliData.availableStreamOptions.isNotEmpty() && (!biliData.videoUrl.isNullOrBlank() || biliData.selectedStreamOption != null)) {
                Log.i(TAG, "Resolved via BilibiliProvider for $urlOrId")
                return@withContext ExtractionResult.Success(biliData)
            } else if (context != null) {
                Log.i(TAG, "Routing Bilibili to YtDlpResolver for $urlOrId")
                val fullBiliUrl = when {
                    urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId
                    urlOrId.startsWith("bilisearch", ignoreCase = true) -> urlOrId
                    urlOrId.startsWith("BV", ignoreCase = true) || urlOrId.startsWith("av", ignoreCase = true) -> "https://www.bilibili.com/video/$urlOrId"
                    urlOrId.startsWith("ep", ignoreCase = true) || urlOrId.startsWith("ss", ignoreCase = true) -> "https://www.bilibili.com/bangumi/play/$urlOrId"
                    urlOrId.startsWith("md", ignoreCase = true) -> "https://www.bilibili.com/bangumi/media/$urlOrId"
                    else -> "https://www.bilibili.com/video/$urlOrId"
                }
                return@withContext YtDlpResolver.extractStreamInfo(context, fullBiliUrl)
            }
        }



        val isYouTube = providerId == "youtube" ||
                urlOrId.contains("youtube.com") ||
                urlOrId.contains("youtu.be") ||
                urlOrId.startsWith("ytsearch:", ignoreCase = true) ||
                urlOrId.startsWith("youtube:", ignoreCase = true) ||
                urlOrId.startsWith("ytuser:", ignoreCase = true) ||
                urlOrId.startsWith(":yt", ignoreCase = true) ||
                (urlOrId.length == 11 && !urlOrId.startsWith("http"))

        if (isYouTube) {
            val cleanUrl = urlOrId.trim()
            val targetUrl = when {
                cleanUrl.startsWith("youtube:clip:", ignoreCase = true) -> "https://www.youtube.com/clip/${cleanUrl.substringAfter("youtube:clip:")}"
                cleanUrl.startsWith("youtube:shorts:pivot:audio:", ignoreCase = true) -> "https://www.youtube.com/source/${cleanUrl.substringAfter("youtube:shorts:pivot:audio:")}/shorts"
                cleanUrl.startsWith("youtube:playlist:", ignoreCase = true) -> "https://www.youtube.com/playlist?list=${cleanUrl.substringAfter("youtube:playlist:")}"
                cleanUrl.startsWith("youtube:user:", ignoreCase = true) -> "https://www.youtube.com/@${cleanUrl.substringAfter("youtube:user:")}"
                cleanUrl.startsWith("ytuser:", ignoreCase = true) -> "https://www.youtube.com/@${cleanUrl.substringAfter("ytuser:")}"
                cleanUrl.startsWith("youtube:search:", ignoreCase = true) -> "https://www.youtube.com/results?search_query=${URLEncoder.encode(cleanUrl.substringAfter("youtube:search:"), "UTF-8")}"
                cleanUrl.startsWith("ytsearch:", ignoreCase = true) -> "https://www.youtube.com/results?search_query=${URLEncoder.encode(cleanUrl.substringAfter("ytsearch:"), "UTF-8")}"
                cleanUrl.startsWith("youtube:music:", ignoreCase = true) -> "https://music.youtube.com/search?q=${URLEncoder.encode(cleanUrl.substringAfter("youtube:music:"), "UTF-8")}"
                cleanUrl.startsWith("youtube:tab:", ignoreCase = true) -> "https://www.youtube.com/${cleanUrl.substringAfter("youtube:tab:")}"
                cleanUrl.contains("v=") -> "https://www.youtube.com/watch?v=${cleanUrl.substringAfter("v=").substringBefore("&")}"
                cleanUrl.contains("youtu.be/") -> "https://www.youtube.com/watch?v=${cleanUrl.substringAfter("youtu.be/").substringBefore("?")}"
                cleanUrl.contains("youtube.com/embed/live_stream") -> cleanUrl
                cleanUrl.contains("youtube.com/live/") -> "https://www.youtube.com/watch?v=${cleanUrl.substringAfter("youtube.com/live/").substringBefore("?")}"
                cleanUrl.contains("youtube.com/shorts/") -> "https://www.youtube.com/watch?v=${cleanUrl.substringAfter("youtube.com/shorts/").substringBefore("?")}"
                cleanUrl.startsWith("http") -> cleanUrl
                cleanUrl.length == 11 -> "https://www.youtube.com/watch?v=$cleanUrl"
                else -> "https://www.youtube.com/watch?v=$cleanUrl"
            }

            val videoId = when {
                targetUrl.contains("v=") -> targetUrl.substringAfter("v=").substringBefore("&")
                targetUrl.contains("youtu.be/") -> targetUrl.substringAfter("youtu.be/").substringBefore("?")
                targetUrl.contains("/live/") -> targetUrl.substringAfter("/live/").substringBefore("?")
                targetUrl.contains("/shorts/") -> targetUrl.substringAfter("/shorts/").substringBefore("?")
                else -> cleanUrl
            }
            com.example.util.PlaybackPipelineTracker.logExtractionStart(videoId, targetUrl)
            Log.i(TAG, "Resolving YouTube Video ID: '$videoId', Target URL: '$targetUrl'")

            // Step 1: NewPipe Extractor (Primary Instant Fast Extractor)
            try {
                try {
                    NewPipe.init(DownloaderImpl.getInstance())
                } catch (ignored: Exception) {}

                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)
                val title = streamInfo.name ?: "YouTube Video"
                val uploader = streamInfo.uploaderName ?: "YouTube"
                val desc = streamInfo.description?.getContent() ?: ""
                val thumb = streamInfo.thumbnails?.firstOrNull()?.url ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val uploaderAvatar = try {
                    streamInfo.uploaderAvatars?.firstOrNull()?.url
                } catch (e: Exception) {
                    null
                }
                val subCountText = try {
                    val count = streamInfo.uploaderSubscriberCount
                    if (count > 0L) {
                        if (count >= 1_000_000L) "${String.format("%.1f", count / 1_000_000.0)}M subscribers"
                        else if (count >= 1_000L) "${count / 1_000L}K subscribers"
                        else "$count subscribers"
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }

                val progressiveStreams = streamInfo.videoStreams ?: emptyList()
                val videoOnlyStreams = streamInfo.videoOnlyStreams ?: emptyList()
                val audioStreams = streamInfo.audioStreams ?: emptyList()

                com.example.util.PlaybackPipelineTracker.logNewpipeResult(
                    progressiveCount = progressiveStreams.size,
                    adaptiveCount = videoOnlyStreams.size
                )

                val ytHeaders = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )

                val bestAudioStream = audioStreams.maxByOrNull { it.averageBitrate }
                val options = mutableListOf<PlayableStreamOption>()

                // 1. Progressive streams (Video + Audio muxed - H.264/MP4 preferred)
                progressiveStreams.forEach { vStream ->
                    val vUrl = vStream.content
                    val resolution = vStream.resolution ?: "720p"
                    val formatName = vStream.format?.name ?: "mp4"
                    if (!vUrl.isNullOrBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$resolution Progressive ($formatName)",
                                format = formatName,
                                isMuxed = true,
                                videoStream = vStream,
                                videoUrl = vUrl,
                                providerType = ProviderType.DIRECT,
                                headers = ytHeaders
                            )
                        )
                    }
                }

                // 2. Adaptive Video Streams (Paired with best audio stream - fallback only)
                videoOnlyStreams.forEach { vStream ->
                    val vUrl = vStream.content
                    val resolution = vStream.resolution ?: "1080p"
                    val formatName = vStream.format?.name ?: "mp4"
                    if (!vUrl.isNullOrBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$resolution Adaptive ($formatName)",
                                format = formatName,
                                isMuxed = false,
                                videoStream = vStream,
                                audioStream = bestAudioStream,
                                videoUrl = vUrl,
                                audioUrl = bestAudioStream?.content,
                                providerType = ProviderType.DIRECT,
                                headers = ytHeaders
                            )
                        )
                    }
                }

                // 3. HLS Master Playlist
                if (!streamInfo.hlsUrl.isNullOrBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "Adaptive HLS (m3u8)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = streamInfo.hlsUrl,
                            providerType = ProviderType.DIRECT,
                            headers = ytHeaders
                        )
                    )
                }

                if (options.isNotEmpty()) {
                    // Sort options: prefer progressive muxed streams, then high resolution
                    val sortedOptions = options.sortedWith(
                        compareByDescending<PlayableStreamOption> { parseQualityScore(it) }
                    ).distinctBy { it.qualityLabel }

                    // Priority: First prefer muxed MP4 progressive (720p, then 480p, then 360p, then any mp4 muxed)
                    val muxedMp4Streams = sortedOptions.filter { it.isMuxed && it.format.equals("mp4", ignoreCase = true) && !it.videoUrl.isNullOrBlank() }
                    val bestOption = muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("720p") }
                        ?: muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("1080p") }
                        ?: muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("480p") }
                        ?: muxedMp4Streams.firstOrNull { it.qualityLabel.startsWith("360p") }
                        ?: muxedMp4Streams.firstOrNull()
                        ?: sortedOptions.firstOrNull { it.isMuxed && !it.videoUrl.isNullOrBlank() }
                        ?: sortedOptions.first()

                    com.example.util.PlaybackPipelineTracker.logFormatSelected(
                        label = bestOption.qualityLabel,
                        isMuxed = bestOption.isMuxed,
                        format = bestOption.format,
                        urlSnippet = bestOption.videoUrl?.take(60) ?: "unknown"
                    )

                    val streamData = StreamData(
                        videoId = videoId,
                        videoUrl = bestOption.videoUrl ?: "",
                        title = title,
                        channelName = uploader,
                        channelAvatarUrl = uploaderAvatar,
                        subscriberCountText = subCountText,
                        description = desc,
                        thumbnailUrl = thumb,
                        progressiveStreams = progressiveStreams,
                        videoOnlyStreams = videoOnlyStreams,
                        audioStreams = audioStreams,
                        availableStreamOptions = sortedOptions,
                        selectedStreamOption = bestOption,
                        hlsUrl = streamInfo.hlsUrl,
                        providerId = "youtube",
                        providerType = ProviderType.DIRECT,
                        headers = ytHeaders
                    )
                    Log.i(TAG, "NewPipe extraction success: ${sortedOptions.size} formats available. Selected: ${bestOption.qualityLabel}")
                    return@withContext ExtractionResult.Success(streamData)
                }
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe extraction failed: ${e.message}")
            }

            // Step 2: yt-dlp Fallback for YouTube ONLY if NewPipe failed
            if (context != null) {
                Log.i(TAG, "YouTube Resolution Step 2 (yt-dlp fallback): $targetUrl")
                try {
                    val ytDlpResult = YtDlpResolver.extractStreamInfo(context, targetUrl)
                    if (ytDlpResult is ExtractionResult.Success && ytDlpResult.streamData.availableStreamOptions.isNotEmpty()) {
                        val primary = ytDlpResult.streamData.selectedStreamOption
                            ?: ytDlpResult.streamData.availableStreamOptions.firstOrNull()
                        if (primary != null) {
                            com.example.util.PlaybackPipelineTracker.logFormatSelected(
                                label = primary.qualityLabel,
                                isMuxed = primary.isMuxed,
                                format = primary.format,
                                urlSnippet = primary.videoUrl?.take(60) ?: "unknown"
                            )
                        }
                        return@withContext ytDlpResult
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "yt-dlp fallback YouTube extraction notice: ${e.message}")
                }
            }

            return@withContext ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                    message = "Unable to resolve playable stream for this video.",
                    rawExceptionName = "StreamResolutionException",
                    fullStackTrace = "",
                    urlOrId = targetUrl,
                    causeInfo = "Both NewPipe and yt-dlp extraction layers were attempted.",
                    technicalFixSuggestion = "Check network connection or try again later."
                )
            )
        } else {
            // Generic non-YouTube URL: direct yt-dlp resolution
            if (context != null) {
                Log.i(TAG, "Non-YouTube URL, resolving via yt-dlp: $urlOrId")
                return@withContext YtDlpResolver.extractStreamInfo(context, urlOrId)
            } else {
                return@withContext ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                        message = "Context required for generic stream resolution",
                        rawExceptionName = "NoContextException",
                        fullStackTrace = "",
                        urlOrId = urlOrId
                    )
                )
            }
        }
    }

    fun parseQualityScore(option: PlayableStreamOption): Long {
        val label = option.qualityLabel
        val regex = Regex("(\\d{3,4})p")
        val match = regex.find(label)
        val height = match?.groupValues?.get(1)?.toIntOrNull() ?: when {
            label.contains("2160", ignoreCase = true) || label.contains("4k", ignoreCase = true) -> 2160
            label.contains("1440", ignoreCase = true) || label.contains("2k", ignoreCase = true) -> 1440
            label.contains("1080", ignoreCase = true) -> 1080
            label.contains("720", ignoreCase = true) -> 720
            label.contains("480", ignoreCase = true) -> 480
            label.contains("360", ignoreCase = true) -> 360
            label.contains("240", ignoreCase = true) -> 240
            label.contains("144", ignoreCase = true) -> 144
            else -> 720
        }
        var score = height * 10_000L
        if (option.format.equals("mp4", ignoreCase = true) || label.contains("mp4", ignoreCase = true)) score += 2_000_000L
        if (option.isMuxed) score += 50_000_000L // Highly prioritize standalone playable streams over adaptive video-only
        return score
    }

    suspend fun fetchStreamData(urlOrId: String, context: Context? = null, providerId: String? = null): ExtractionResult {
        val result = resolveStream(urlOrId, context, providerId)
        if (result is ExtractionResult.Success) {
            val data = result.streamData
            if (data.tags.isEmpty()) {
                val enrichedTags = com.example.util.SmartTagExtractor.extractTagsFromMetadata(
                    title = data.title,
                    description = data.description,
                    uploader = data.channelName,
                    explicitTags = data.tags,
                    providerId = data.providerId ?: providerId
                )
                val primaryCat = com.example.util.SmartTagExtractor.extractTags(
                    com.example.model.VideoItem(
                        id = data.videoId,
                        title = data.title,
                        uploaderName = data.channelName,
                        description = data.description,
                        providerId = data.providerId ?: providerId,
                        tags = enrichedTags
                    ),
                    maxTags = 1
                ).firstOrNull()?.displayName

                return ExtractionResult.Success(
                    data.copy(
                        tags = enrichedTags,
                        category = data.category ?: primaryCat
                    )
                )
            }
        }
        return result
    }

    suspend fun fetchSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val rawTrimmed = query.trim()
        if (rawTrimmed.isBlank()) return@withContext emptyList()

        val sanitized = com.example.util.SmartSearchSanitizer.sanitizeQuery(rawTrimmed)
        val q = sanitized.cleanQuery
        val suggestions = mutableListOf<String>()

        if (sanitized.wasCleaned) {
            suggestions.add(sanitized.cleanQuery)
            if (sanitized.didYouMean != null && sanitized.didYouMean != sanitized.cleanQuery) {
                suggestions.add(sanitized.didYouMean)
            }
        }

        // 1. Fetch from Google / YouTube Suggest API using cleaned query
        try {
            val encoded = java.net.URLEncoder.encode(q, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val jsonStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val jsonArr = org.json.JSONArray(jsonStr)
                if (jsonArr.length() > 1) {
                    val suggestionArr = jsonArr.optJSONArray(1)
                    if (suggestionArr != null) {
                        for (i in 0 until suggestionArr.length()) {
                            val suggestion = suggestionArr.optString(i)
                            if (suggestion.isNotBlank()) {
                                suggestions.add(suggestion)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch suggestions from suggestqueries: ${e.message}")
        }

        // 2. Fallback to YouTube suggestqueries endpoint (client=youtube)
        if (suggestions.isEmpty()) {
            try {
                val encoded = java.net.URLEncoder.encode(q, "UTF-8")
                val url = "https://suggestqueries.google.com/complete/search?client=youtube&q=$encoded"
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val responseStr = client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!responseStr.isNullOrBlank()) {
                    val pattern = java.util.regex.Pattern.compile("\\[\"([^\"]+)\",\\s*0\\]")
                    val matcher = pattern.matcher(responseStr)
                    while (matcher.find()) {
                        val suggestion = matcher.group(1)
                        if (!suggestion.isNullOrBlank()) {
                            suggestions.add(suggestion)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Secondary YouTube suggestion fetch failed: ${e.message}")
            }
        }

        return@withContext suggestions.distinct()
    }

    suspend fun searchVideos(query: String, context: Context? = null): FeedResult = withContext(Dispatchers.IO) {
        try {
            val ytItems = searchYouTube(query, context)
            val archiveItems = ArchiveOrgProvider.search(query, 1)
            val combined = (ytItems + archiveItems).distinctBy { it.id }
            FeedResult.Success(combined)
        } catch (e: Exception) {
            FeedResult.Error(
                FeedErrorDetails(
                    rawExceptionName = e.javaClass.simpleName,
                    message = e.message ?: "Search failed",
                    fullStackTrace = e.stackTraceToString(),
                    urlOrQuery = query
                )
            )
        }
    }
}
