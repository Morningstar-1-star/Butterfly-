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

    val extractorsFactory: DefaultExtractorsFactory by lazy {
        DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA)
            .setFragmentedMp4ExtractorFlags(FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK)
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
        val isGoogleStorageOrPublic = lowerTarget.contains("googlevideo.com") || lowerTarget.contains("youtube.com") ||
                lowerTarget.contains("youtu.be") || lowerTarget.contains("ytimg.com") ||
                lowerTarget.contains("googleapis.com") || lowerTarget.contains("storage.googleapis") ||
                lowerTarget.contains("commondatastorage") || lowerTarget.contains("w3schools") ||
                lowerTarget.contains("githubusercontent") || lowerTarget.contains("cloudflarestream")

        val isBilibiliStream = lowerTarget.contains("bilibili") || lowerTarget.contains("bilivideo") ||
                lowerTarget.contains("biliapi") || lowerTarget.contains("hdslb") || lowerTarget.contains("szbdyd") ||
                lowerTarget.contains("mcdn") || lowerTarget.contains("acgvideo") || lowerTarget.contains("upgcxcode") ||
                lowerTarget.contains("upos") || lowerTarget.contains("akamaized") || lowerTarget.contains("bcache") ||
                lowerTarget.contains("mirrorali") || lowerTarget.contains("mirrorcos") || lowerTarget.contains("mirrorhw") ||
                lowerTarget.contains("mirrorbos") || lowerTarget.contains("mirror08c") || lowerTarget.contains("mirrorakam") ||
                lowerTarget.contains("bstar") || lowerTarget.contains("biliintl") ||
                streamData?.providerId == "bilibili"

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
        } else if (isBilibiliStream) {
            // Preserve extractor-supplied Bilibili headers. The CDN may bind the
            // signed media URL to the Referer/UA used during extraction.
            if (reqHeaders.keys.none { it.equals("Referer", ignoreCase = true) }) {
                reqHeaders["Referer"] = "https://www.bilibili.com/"
            }
            if (customUserAgent == null) {
                customUserAgent = NetworkManager.DEFAULT_USER_AGENT
            }
            reqHeaders["Accept"] = "*/*"
            reqHeaders["Accept-Language"] = "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7"
            reqHeaders.remove("Sec-Fetch-Mode")
            reqHeaders.remove("Sec-Fetch-Site")
            reqHeaders.remove("Origin")
            val cookie = com.example.extractor.BilibiliProvider.getBilibiliCookie()
            if (cookie.isNotBlank() && !reqHeaders.containsKey("Cookie")) {
                reqHeaders["Cookie"] = cookie
            }
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
                    lowerTarget.contains("hqporner") || lowerTarget.contains("hqplayer") || streamData?.providerId == "hqporner" || streamData?.providerId == "hqplayer" -> {
                        reqHeaders["Referer"] = "https://hqporner.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://hqporner.com"
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "age_verified=1; country=US; consent=1"
                    }
                    lowerTarget.contains("spankbang") || lowerTarget.contains("sb-cd.com") || lowerTarget.contains("spankcdn") || streamData?.providerId == "spankbang" -> {
                        reqHeaders["Referer"] = "https://spankbang.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://spankbang.com"
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1"
                    }
                    (lowerTarget.contains("motherless.com") || lowerTarget.contains("motherlessmedia") || lowerTarget.contains("cdn.motherless") || streamData?.providerId == "motherless") -> {
                        reqHeaders["Referer"] = "https://motherless.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://motherless.com"
                        if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) reqHeaders["Cookie"] = "content_filter=0; member=1; age_verified=1; country=US; consent=1"
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
        val dsFactory = OkHttpDataSource.Factory(okHttpClient)
        userAgent?.let { dsFactory.setUserAgent(it) }
        if (reqHeaders.isNotEmpty()) {
            dsFactory.setDefaultRequestProperties(reqHeaders)
        }
        return dsFactory
    }

    /**
     * Builds a DataSource.Factory supporting HTTP(S), local file://, content://, and asset:// schemes.
     */
    fun createDataSourceFactory(
        targetUrl: String,
        streamData: StreamData?,
        specificHeaders: Map<String, String> = emptyMap(),
        context: android.content.Context? = null
    ): DataSource.Factory {
        val httpDsFactory = createHttpDataSourceFactory(targetUrl, streamData, specificHeaders)
        val ctx = context ?: com.example.MainApplication.appContext
        return DefaultDataSource.Factory(ctx, httpDsFactory)
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
        val dsFactory = createDataSourceFactory(targetUrl, streamData, specificHeaders, context)
        return DefaultMediaSourceFactory(dsFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)
    }
}
