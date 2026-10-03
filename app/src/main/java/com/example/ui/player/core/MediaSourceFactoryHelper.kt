package com.example.ui.player.core

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.util.NetworkManager
import okhttp3.OkHttpClient

/**
 * Encapsulates construction of ExoPlayer MediaSources (Progressive, HLS, DASH, Merged Audio+Video)
 * with domain-specific network header injection and resilient error retry handling.
 */
object MediaSourceFactoryHelper {

    val okHttpClient: OkHttpClient by lazy {
        NetworkManager.mediaClient.newBuilder()
            .addInterceptor(MediaHeaderHelper.mediaHeaderInterceptor)
            .addNetworkInterceptor(MediaHeaderHelper.networkHeaderInterceptor)
            .build()
    }

    val bilibiliMediaClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(com.example.util.SecureDnsManager.bilibiliDns)
            .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor(MediaHeaderHelper.mediaHeaderInterceptor)
            .addNetworkInterceptor(MediaHeaderHelper.networkHeaderInterceptor)
            .build()
    }

    val tencentMediaClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(okhttp3.Dns.SYSTEM)
            .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor(MediaHeaderHelper.mediaHeaderInterceptor)
            .addNetworkInterceptor(MediaHeaderHelper.networkHeaderInterceptor)
            .build()
    }

    val extractorsFactory: DefaultExtractorsFactory by lazy {
        DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
    }

    val errorHandlingPolicy: LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy(3) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val rootCause = loadErrorInfo.exception
            if (rootCause is HttpDataSource.InvalidResponseCodeException) {
                val code = rootCause.responseCode
                if (code == 401 || code == 403 || code == 404) {
                    return C.TIME_UNSET // Permanent client side authorization errors
                }
                if (code in 500..599 || code == 429) {
                    return 1000L // Retry transient server or rate limit errors
                }
            }
            return super.getRetryDelayMsFor(loadErrorInfo)
        }
    }

    /**
     * Resolves all HTTP headers needed for a given target media URL, merging global streamData headers,
     * streamOption specific headers, and standard domain referers/cookies.
     */
    fun resolveRequestHeaders(
        targetUrl: String,
        streamData: StreamData?,
        specificHeaders: Map<String, String> = emptyMap()
    ): Pair<String?, Map<String, String>> {
        val headersMap = mutableMapOf<String, String>()
        streamData?.headers?.let { headersMap.putAll(it) }
        headersMap.putAll(specificHeaders)

        var customUserAgent: String? = null
        val reqHeaders = mutableMapOf<String, String>()
        headersMap.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) {
                customUserAgent = v
            } else {
                reqHeaders[k] = v
            }
        }
        if (customUserAgent == null) {
            customUserAgent = NetworkManager.DEFAULT_USER_AGENT
        }

        val lowerTarget = targetUrl.lowercase()
        val isNoodleMagazineStream = streamData?.providerId == "noodlemagazine" ||
                lowerTarget.contains("pvvstream.pro") ||
                lowerTarget.contains("noodlemagazine.com")
        if (isNoodleMagazineStream) {
            // Requirement: When yt-dlp returns a format for NoodleMagazine, pass BOTH url and its
            // http_headers untouched to Media3. Do not add/replace Referer, Origin, cookies, UA, or CDN URL.
            val untouchedHeaders = mutableMapOf<String, String>()
            streamData?.headers?.forEach { (k, v) ->
                if (k.equals("User-Agent", ignoreCase = true)) {
                    customUserAgent = v
                } else {
                    untouchedHeaders[k] = v
                }
            }
            specificHeaders.forEach { (k, v) ->
                if (k.equals("User-Agent", ignoreCase = true)) {
                    customUserAgent = v
                } else {
                    untouchedHeaders[k] = v
                }
            }
            return Pair(customUserAgent, untouchedHeaders)
        }

        val isBilibiliStream = lowerTarget.contains("bilibili") || lowerTarget.contains("bilivideo") ||
                lowerTarget.contains("biliapi") || lowerTarget.contains("hdslb") || lowerTarget.contains("szbdyd") ||
                lowerTarget.contains("mcdn") || lowerTarget.contains("acgvideo") || lowerTarget.contains("upgcxcode") ||
                lowerTarget.contains("upos") || lowerTarget.contains("akamaized") || lowerTarget.contains("bcache") ||
                lowerTarget.contains("mirrorali") || lowerTarget.contains("mirrorcos") || lowerTarget.contains("mirrorhw") ||
                lowerTarget.contains("mirrorbos") || lowerTarget.contains("mirror08c") || lowerTarget.contains("mirrorakam") ||
                lowerTarget.contains("bstar") || lowerTarget.contains("biliintl") ||
                streamData?.providerId == "bilibili"
        if (isBilibiliStream) {
            // Bilibili CDN anti-hotlink authorization:
            // Strictly hand off all headers (Referer, User-Agent, Origin, Cookie, Accept, etc.) without stripping
            val biliCleanHeaders = mutableMapOf<String, String>()
            streamData?.headers?.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    biliCleanHeaders[k] = v
                }
            }
            specificHeaders.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    biliCleanHeaders[k] = v
                }
            }

            val existingRef = specificHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
                ?: streamData?.headers?.entries?.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
            val biliReferer = if (!existingRef.isNullOrBlank()) {
                existingRef
            } else {
                if (lowerTarget.contains("live") || lowerTarget.contains("gotcha") || lowerTarget.contains("xlive")) "https://live.bilibili.com/" else "https://www.bilibili.com/"
            }
            biliCleanHeaders["Referer"] = biliReferer
            if (!biliCleanHeaders.containsKey("Accept")) {
                biliCleanHeaders["Accept"] = "*/*"
            }
            if (!biliCleanHeaders.containsKey("Accept-Language")) {
                biliCleanHeaders["Accept-Language"] = "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7"
            }

            val ua = specificHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: streamData?.headers?.entries?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: NetworkManager.DEFAULT_USER_AGENT
            return Pair(ua, biliCleanHeaders)
        }

        val isGoogleStorageOrPublic = lowerTarget.contains("googlevideo.com") || lowerTarget.contains("youtube.com") ||
                lowerTarget.contains("youtu.be") || lowerTarget.contains("ytimg.com") ||
                lowerTarget.contains("googleapis.com") || lowerTarget.contains("storage.googleapis") ||
                lowerTarget.contains("commondatastorage") || lowerTarget.contains("w3schools") ||
                lowerTarget.contains("githubusercontent") || lowerTarget.contains("cloudflarestream")

        val isVkStream = lowerTarget.contains("vk.com") || lowerTarget.contains("vkvideo") ||
                lowerTarget.contains("vkuser") || lowerTarget.contains("mycdn") || lowerTarget.contains("vk-cdn") ||
                lowerTarget.contains("userapi") || lowerTarget.contains("ok.ru") || lowerTarget.contains("odnoklassniki")

        if (isGoogleStorageOrPublic) {
            reqHeaders.remove("Referer")
            reqHeaders.remove("referer")
            reqHeaders.remove("Origin")
            reqHeaders.remove("origin")
            reqHeaders.remove("Cookie")
            reqHeaders.remove("cookie")
        } else if (isVkStream) {
            reqHeaders["Referer"] = "https://vk.com/"
            customUserAgent = NetworkManager.DEFAULT_USER_AGENT
            reqHeaders.remove("Origin")
            reqHeaders.remove("Cookie")
        } else {
            val hasReferer = reqHeaders.keys.any { it.equals("Referer", ignoreCase = true) }
            if (!hasReferer) {
                when {
                    lowerTarget.contains("dailymotion.com") || lowerTarget.contains("dmcdn.net") || lowerTarget.contains("dai.ly") -> {
                        reqHeaders["Referer"] = "https://www.dailymotion.com/"
                        reqHeaders.remove("Origin")
                        reqHeaders.remove("origin")
                        if (lowerTarget.contains("dmcdn.net")) {
                            reqHeaders.remove("Cookie")
                            reqHeaders.remove("cookie")
                        } else {
                            val dmCookie = com.example.extractor.DailymotionProvider.lastDmCookies
                            if (dmCookie.isNotBlank() && !reqHeaders.containsKey("Cookie")) {
                                reqHeaders["Cookie"] = dmCookie
                            }
                        }
                    }
                    lowerTarget.contains("archive.org") || streamData?.providerId == "archive_org" -> {
                        reqHeaders["Referer"] = "https://archive.org/"
                    }
                    lowerTarget.contains("pornhub.com") || streamData?.providerId == "pornhub" -> {
                        reqHeaders["Referer"] = "https://www.pornhub.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://www.pornhub.com"
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "age_verified=1; platform=pc; ip_country=US; has_consent=1"
                    }
                    lowerTarget.contains("eporner") || streamData?.providerId == "eporner" -> {
                        reqHeaders["Referer"] = "https://www.eporner.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://www.eporner.com"
                    }
                    lowerTarget.contains("hanime") || lowerTarget.contains("hanime1") || lowerTarget.contains("hanime.tv") || streamData?.providerId == "hanime1" || streamData?.providerId == "hanime" -> {
                        val ref = if (lowerTarget.contains("hanime1")) "https://hanime1.me/" else "https://hanime.tv/"
                        reqHeaders["Referer"] = ref
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = ref.trimEnd('/')
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "age_verified=1; country=US; language=en; ft_mature=1; consent=1"
                    }
                    (lowerTarget.contains("playvid") || lowerTarget.contains("playvids") || streamData?.providerId == "playvid") -> {
                        reqHeaders["Referer"] = "https://www.playvids.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://www.playvids.com"
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1"
                    }
                    (lowerTarget.contains("spankbang") || lowerTarget.contains("sb-cd.com") || lowerTarget.contains("spankcdn")) -> {
                        reqHeaders["Referer"] = "https://spankbang.com/"
                        reqHeaders.remove("Origin")
                        reqHeaders.remove("origin")
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1; sb_consent=1"
                    }
                    lowerTarget.contains("stripchat") || lowerTarget.contains("doppiocdn") || lowerTarget.contains("strpst") || lowerTarget.contains("b-hls") || lowerTarget.contains("edge-hls") || streamData?.providerId == "stripchat" -> {
                        reqHeaders["Referer"] = "https://stripchat.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://stripchat.com"
                        if (!reqHeaders.keys.any { it.equals("Accept", ignoreCase = true) }) reqHeaders["Accept"] = "*/*"
                        reqHeaders["User-Agent"] = customUserAgent ?: NetworkManager.DEFAULT_USER_AGENT
                    }
                    (lowerTarget.contains("txxx") || lowerTarget.contains("txxx.com") || lowerTarget.contains("txxx.tube") || lowerTarget.contains("tubecdn.com") || lowerTarget.contains("ahcdn.com") || streamData?.providerId == "txxx") -> {
                        reqHeaders["Referer"] = "https://txxx.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://txxx.com"
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "age_verified=1; platform=pc; country=US; ft_mature=1; consent=1"
                    }
                    lowerTarget.contains("vimeo.com") || (streamData?.providerId == "vimeo" && !isBilibiliStream) -> {
                        reqHeaders["Referer"] = "https://vimeo.com/"
                        reqHeaders["Origin"] = "https://vimeo.com"
                    }
                    (lowerTarget.contains("hotstar.com") || streamData?.providerId == "hotstar") && !isGoogleStorageOrPublic -> {
                        reqHeaders["Referer"] = "https://www.hotstar.com/"
                        reqHeaders["Origin"] = "https://www.hotstar.com"
                    }
                    (lowerTarget.contains("tubitv") || lowerTarget.contains("tubi.tv") || streamData?.providerId == "tubitv") -> {
                        if (!reqHeaders.containsKey("Referer") && !reqHeaders.containsKey("referer")) {
                            reqHeaders["Referer"] = "https://tubitv.com/"
                        }
                        if (!reqHeaders.containsKey("Origin") && !reqHeaders.containsKey("origin")) {
                            reqHeaders["Origin"] = "https://tubitv.com"
                        }
                    }
                    (lowerTarget.contains("thisvid") || lowerTarget.contains("thisvid.com") || lowerTarget.contains("tvid") || streamData?.providerId == "thisvid") -> {
                        reqHeaders["Referer"] = "https://thisvid.com/"
                        reqHeaders["Origin"] = "https://thisvid.com"
                        reqHeaders["Cookie"] = "age_verified=1; platform=pc; has_consent=1; kt_ips=1; kt_is_visited=1"
                        reqHeaders["Accept"] = "*/*"
                        if (customUserAgent == null) customUserAgent = NetworkManager.DEFAULT_USER_AGENT
                    }
                    (lowerTarget.contains("tnaflix") || lowerTarget.contains("tnaflix.com") || streamData?.providerId == "tnaflix") -> {
                        reqHeaders["Referer"] = "https://www.tnaflix.com/"
                        reqHeaders["Origin"] = "https://www.tnaflix.com"
                        reqHeaders["Cookie"] = "age_verified=1; platform=pc; ft_mature=1; consent=1; has_consent=1"
                        reqHeaders["Accept"] = "*/*"
                    }
                    (lowerTarget.contains("hellporno") || lowerTarget.contains("hellporno.com") || lowerTarget.contains("hellporno.net") || lowerTarget.contains("hellporno.tv") || streamData?.providerId == "hellporno") -> {
                        reqHeaders["Referer"] = "https://hellporno.com/"
                        reqHeaders["Origin"] = "https://hellporno.com"
                        reqHeaders["Cookie"] = "age_verified=1; has_consent=1; country=US"
                        reqHeaders["Accept"] = "*/*"
                    }
                    lowerTarget.contains("supjav") || lowerTarget.contains("tvlogy") || lowerTarget.contains("supplayer") ||
                    lowerTarget.contains("streamwish") || lowerTarget.contains("wishembed") || lowerTarget.contains("awish") ||
                    lowerTarget.contains("dwish") || lowerTarget.contains("strwish") || lowerTarget.contains("cdnwish") ||
                    lowerTarget.contains("embedwish") || lowerTarget.contains("sfastwish") || lowerTarget.contains("filelions") ||
                    lowerTarget.contains("voe") || lowerTarget.contains("audaciousdefaulthouse") || lowerTarget.contains("dood") ||
                    lowerTarget.contains("ds2play") || lowerTarget.contains("streamtape") || lowerTarget.contains("tapecontent") ||
                    lowerTarget.contains("stbturbo") || lowerTarget.contains("streamtb") || streamData?.providerId == "supjav" -> {
                        val ref = when {
                            lowerTarget.contains("tvlogy") || lowerTarget.contains("supplayer") -> "https://tvlogy.to/"
                            lowerTarget.contains("streamwish") || lowerTarget.contains("wishembed") || lowerTarget.contains("awish") || lowerTarget.contains("dwish") || lowerTarget.contains("strwish") || lowerTarget.contains("cdnwish") || lowerTarget.contains("embedwish") || lowerTarget.contains("sfastwish") || lowerTarget.contains("filelions") -> "https://streamwish.to/"
                            lowerTarget.contains("voe") || lowerTarget.contains("audaciousdefaulthouse") -> "https://voe.sx/"
                            lowerTarget.contains("dood") || lowerTarget.contains("ds2play") -> "https://dood.to/"
                            lowerTarget.contains("streamtape") || lowerTarget.contains("tapecontent") -> "https://streamtape.com/"
                            lowerTarget.contains("stbturbo") || lowerTarget.contains("streamtb") -> "https://stbturbo.xyz/"
                            else -> "https://supjav.com/"
                        }
                        reqHeaders["Referer"] = ref
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                            reqHeaders["Origin"] = ref.trimEnd('/')
                        }
                    }
                }
            }
        }

        // Strictly preserve all explicit specificHeaders (e.g. Decryptor Referer, Origin, Host, tokens)
        specificHeaders.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) {
                customUserAgent = v
            } else if (v.isNotBlank()) {
                reqHeaders[k] = v
            }
        }

        return Pair(customUserAgent, reqHeaders)
    }

    /**
     * Builds an OkHttpDataSource.Factory configured with custom User-Agent and per-request headers.
     */
    fun createHttpDataSourceFactory(targetUrl: String, streamData: StreamData?, specificHeaders: Map<String, String> = emptyMap()): OkHttpDataSource.Factory {
        val (userAgent, reqHeaders) = resolveRequestHeaders(targetUrl, streamData, specificHeaders)
        val lowerTarget = targetUrl.lowercase()
        val isBili = lowerTarget.contains("bilibili") || lowerTarget.contains("bilivideo") ||
                lowerTarget.contains("biliapi") || lowerTarget.contains("hdslb") ||
                lowerTarget.contains("szbdyd") || lowerTarget.contains("mcdn") ||
                lowerTarget.contains("upgcxcode") || lowerTarget.contains("upos") ||
                lowerTarget.contains("bcache") || lowerTarget.contains("mirrorakam") ||
                lowerTarget.contains("mirrorali") || lowerTarget.contains("mirrorcos") ||
                lowerTarget.contains("mirrorhw") || lowerTarget.contains("mirrorbos") ||
                lowerTarget.contains("mirror08c") || lowerTarget.contains("akamaized") ||
                lowerTarget.contains("bstar") || lowerTarget.contains("biliintl") ||
                streamData?.providerId == "bilibili"
        val isTencent = lowerTarget.contains("qq.com") || lowerTarget.contains("tc.qq.com") ||
                lowerTarget.contains("v.qq.com") || lowerTarget.contains("myqcloud.com") ||
                lowerTarget.contains("wetv.vip") || lowerTarget.contains("qpic.cn") ||
                lowerTarget.contains("gtimg.com") ||
                streamData?.providerId == "tencent" || streamData?.providerId == "vqq"
        val client = when {
            isBili -> bilibiliMediaClient
            isTencent -> tencentMediaClient
            else -> okHttpClient
        }
        val dsFactory = OkHttpDataSource.Factory(client)
        userAgent?.let { dsFactory.setUserAgent(it) }
        if (reqHeaders.isNotEmpty()) {
            dsFactory.setDefaultRequestProperties(reqHeaders)
        }
        return dsFactory
    }

    /**
     * Builds a DataSource.Factory supporting HTTP(S), local file://, content://, and asset:// schemes,
     * fully backed by a persistent LRU SimpleCache (512MB) to prevent duplicate downloads and enable instant seeking.
     */
    fun createDataSourceFactory(
        targetUrl: String,
        streamData: StreamData?,
        specificHeaders: Map<String, String> = emptyMap(),
        context: android.content.Context? = null
    ): DataSource.Factory {
        val httpDsFactory = createHttpDataSourceFactory(targetUrl, streamData, specificHeaders)
        val ctx = context ?: com.example.MainApplication.appContext
        val upstreamFactory = DefaultDataSource.Factory(ctx, httpDsFactory)

        // Don't cache local localhost / 127.0.0.1 torrent engine streams (already stored locally)
        val isLocalHost = targetUrl.contains("127.0.0.1") || targetUrl.contains("localhost")
        if (isLocalHost) {
            return upstreamFactory
        }

        return try {
            com.example.ui.player.cache.MediaCacheManager.createCacheDataSourceFactory(ctx, upstreamFactory)
        } catch (_: Throwable) {
            upstreamFactory
        }
    }

    /**
     * Creates a DefaultMediaSourceFactory supporting HTTP, HTTPS, local file://, content://, and HLS/DASH/MP4.
     */
    fun createMediaSourceFactory(
        targetUrl: String,
        streamData: StreamData?,
        specificHeaders: Map<String, String> = emptyMap(),
        context: android.content.Context? = null
    ): DefaultMediaSourceFactory {
        val ctx = context ?: com.example.MainApplication.appContext
        val dsFactory = createDataSourceFactory(targetUrl, streamData, specificHeaders, ctx)
        return DefaultMediaSourceFactory(ctx, extractorsFactory)
            .setDataSourceFactory(dsFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)
    }

    /**
     * Sanitizes and normalizes media URLs for ExoPlayer.
     */
    fun sanitizeMediaUrl(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") &&
            !trimmed.startsWith("file://") && !trimmed.startsWith("content://") &&
            !trimmed.startsWith("asset://") && !trimmed.startsWith("rtmp://") &&
            !trimmed.startsWith("rtsp://") && !trimmed.startsWith("udp://")
        ) {
            if (trimmed.startsWith("/")) {
                trimmed = "file://$trimmed"
            } else if (trimmed.contains(".")) {
                trimmed = "https://$trimmed"
            } else {
                return null
            }
        }
        return try {
            val sanitized = if (trimmed.startsWith("file://") || trimmed.startsWith("content://") || trimmed.startsWith("asset://")) {
                trimmed
            } else {
                trimmed
                    .replace("\n", "").replace("\r", "").replace("\t", "")
                    .replace(" ", "%20").replace("\"", "%22").replace("<", "%3C")
                    .replace(">", "%3E").replace("\\", "/")
            }
            val parsed = Uri.parse(sanitized)
            if (parsed.scheme.isNullOrEmpty()) null else sanitized
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Builds a Media3 MediaItem with MIME detection, subtitle configurations, and optional tag.
     */
    fun buildMediaItem(
        inputUrl: String,
        format: String? = null,
        subtitles: List<MediaItem.SubtitleConfiguration> = emptyList(),
        tag: Any? = null
    ): MediaItem? {
        val cleanUrl = sanitizeMediaUrl(inputUrl) ?: return null
        val uri = Uri.parse(cleanUrl)
        val lowerUrl = cleanUrl.lowercase()
        val lowerFormat = format?.lowercase()
        val builder = MediaItem.Builder().setUri(uri)

        val isExplicitHls = lowerFormat == "hls" || lowerFormat == "m3u8" || lowerUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8?") || lowerUrl.contains("/hls/")
        val isExplicitMpd = lowerFormat == "mpd" || lowerUrl.endsWith(".mpd") || lowerUrl.contains(".mpd?")
        val isExplicitMkv = lowerFormat == "mkv" || lowerUrl.endsWith(".mkv")
        val isExplicitAudioWebm = lowerFormat == "audio_webm" || lowerUrl.contains("mime=audio%2fwebm") || lowerUrl.contains("mime=audio/webm")
        val isExplicitVideoWebm = lowerFormat == "webm" || lowerUrl.contains("mime=video%2fwebm") || lowerUrl.contains("mime=video/webm") || lowerUrl.endsWith(".webm")
        val isExplicitAudioMp4 = lowerFormat == "audio_mp4" || lowerFormat == "m4a" || lowerUrl.contains("mime=audio%2fmp4") || lowerUrl.contains("mime=audio/mp4")
        val isExplicitVideoMp4 = lowerFormat == "video_mp4" || lowerFormat == "mp4" || lowerUrl.contains(".mp4") || lowerUrl.contains(".m4s") || lowerUrl.contains("mime=video%2fmp4") || lowerUrl.contains("mime=video/mp4")

        if (isExplicitHls) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        else if (isExplicitMpd) builder.setMimeType(MimeTypes.APPLICATION_MPD)
        else if (isExplicitMkv) builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
        else if (isExplicitAudioWebm) builder.setMimeType(MimeTypes.AUDIO_WEBM)
        else if (isExplicitVideoWebm) builder.setMimeType(MimeTypes.VIDEO_WEBM)
        else if (isExplicitAudioMp4) builder.setMimeType(MimeTypes.AUDIO_MP4)
        else if (isExplicitVideoMp4) builder.setMimeType(MimeTypes.VIDEO_MP4)

        if (subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(subtitles)
        }
        if (tag != null) {
            builder.setTag(tag)
        }
        return builder.build()
    }

    /**
     * Builds a complete Media3 MediaSource (Progressive, HLS, DASH, or Merging audio+video)
     * for any given StreamData / PlayableStreamOption.
     */
    fun buildMediaSource(
        context: android.content.Context?,
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        captionOption: com.example.model.CaptionOption? = null,
        hlsUrl: String? = null,
        tag: Any? = null
    ): MediaSource? {
        val rawUrl = streamOption?.videoUrl
            ?: streamOption?.videoStream?.url
            ?: hlsUrl
            ?: streamData?.hlsUrl

        if (rawUrl.isNullOrBlank()) return null

        val isEmbedWebUrl = streamOption?.format.equals("embed", true) ||
                (rawUrl.contains("/embed/", ignoreCase = true) && !rawUrl.contains(".mp4") && !rawUrl.contains(".m3u8"))
        if (isEmbedWebUrl) return null

        val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
        if (captionOption != null && !captionOption.url.isNullOrEmpty()) {
            val cleanCapUrl = sanitizeMediaUrl(captionOption.url)
            if (cleanCapUrl != null) {
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(cleanCapUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(captionOption.languageCode)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                subtitleConfigs.add(subtitleConfig)
            }
        }

        if (streamOption?.subtitles?.isNotEmpty() == true) {
            streamOption.subtitles.forEach { sub ->
                val cleanSubUrl = sanitizeMediaUrl(sub.url)
                if (cleanSubUrl != null) {
                    val isVtt = sub.format.equals("vtt", ignoreCase = true) || cleanSubUrl.contains(".vtt", ignoreCase = true)
                    val mimeType = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                    val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(cleanSubUrl))
                        .setMimeType(mimeType)
                        .setLanguage(sub.languageCode)
                        .setLabel(sub.languageName)
                        .setSelectionFlags(if (sub.languageCode.startsWith("en", ignoreCase = true)) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                    subtitleConfigs.add(config)
                }
            }
        }

        val vUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url
        val aUrl = streamOption?.audioUrl ?: streamOption?.audioStream?.url

        if (streamOption != null) {
            if (streamOption.isMuxed && !vUrl.isNullOrEmpty()) {
                val mediaSourceFactory = createMediaSourceFactory(vUrl, streamData, streamOption.headers, context)
                val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs, tag)
                if (item != null) {
                    return mediaSourceFactory.createMediaSource(item)
                }
            } else if (!streamOption.isMuxed && !vUrl.isNullOrEmpty() && !aUrl.isNullOrEmpty()) {
                val videoSourceFactory = createMediaSourceFactory(vUrl, streamData, streamOption.headers, context)
                val audioHeaders = if (streamOption.audioHeaders.isNotEmpty()) streamOption.audioHeaders else streamOption.headers
                val audioSourceFactory = createMediaSourceFactory(aUrl, streamData, audioHeaders, context)

                val videoItem = buildMediaItem(vUrl, streamOption.format.ifEmpty { "video_mp4" }, subtitleConfigs, tag)
                val audioItem = buildMediaItem(aUrl, if (aUrl.contains("webm")) "audio_webm" else "audio_mp4", emptyList(), tag)
                if (videoItem != null && audioItem != null) {
                    val videoSource = videoSourceFactory.createMediaSource(videoItem)
                    val audioSource = audioSourceFactory.createMediaSource(audioItem)
                    return try {
                        MergingMediaSource(true, true, videoSource, audioSource)
                    } catch (_: Exception) {
                        videoSource
                    }
                } else if (videoItem != null) {
                    return videoSourceFactory.createMediaSource(videoItem)
                }
            } else if (!vUrl.isNullOrEmpty()) {
                val mediaSourceFactory = createMediaSourceFactory(vUrl, streamData, streamOption.headers, context)
                val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs, tag)
                if (item != null) {
                    return mediaSourceFactory.createMediaSource(item)
                }
            }
        }

        if (!hlsUrl.isNullOrEmpty()) {
            val cleanHls = sanitizeMediaUrl(hlsUrl)
            if (cleanHls != null) {
                val item = buildMediaItem(cleanHls, "hls", subtitleConfigs, tag)
                if (item != null) {
                    val mediaSourceFactory = createMediaSourceFactory(cleanHls, streamData, emptyMap(), context)
                    return mediaSourceFactory.createMediaSource(item)
                }
            }
        }

        if (!rawUrl.isNullOrEmpty()) {
            val cleanRaw = sanitizeMediaUrl(rawUrl)
            if (cleanRaw != null) {
                val item = buildMediaItem(cleanRaw, streamOption?.format, subtitleConfigs, tag)
                if (item != null) {
                    val mediaSourceFactory = createMediaSourceFactory(cleanRaw, streamData, streamOption?.headers ?: emptyMap(), context)
                    return mediaSourceFactory.createMediaSource(item)
                }
            }
        }

        return null
    }
}
