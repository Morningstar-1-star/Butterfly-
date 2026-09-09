package com.example.ui.player.core

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
            .build()
    }

    val extractorsFactory: DefaultExtractorsFactory by lazy {
        DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA)
            .setFragmentedMp4ExtractorFlags(FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK)
    }

    val errorHandlingPolicy: LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy(1) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val rootCause = loadErrorInfo.exception
            if (rootCause is HttpDataSource.InvalidResponseCodeException) {
                if (rootCause.responseCode in 400..599) {
                    return C.TIME_UNSET
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
                lowerTarget.contains("mirrorbos") || lowerTarget.contains("mirror08c") || lowerTarget.contains("bstar") ||
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
            reqHeaders["Referer"] = "https://www.bilibili.com/"
            customUserAgent = NetworkManager.DEFAULT_USER_AGENT
            reqHeaders["Accept"] = "*/*"
            reqHeaders["Accept-Language"] = "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7"
            reqHeaders["Sec-Fetch-Mode"] = "no-cors"
            reqHeaders["Sec-Fetch-Site"] = "cross-site"
            reqHeaders.remove("Origin")
        } else {
            val hasReferer = reqHeaders.keys.any { it.equals("Referer", ignoreCase = true) }
            if (!hasReferer) {
                when {
                    lowerTarget.contains("dailymotion.com") || lowerTarget.contains("dmcdn.net") || lowerTarget.contains("dai.ly") -> {
                        reqHeaders["Referer"] = "https://www.dailymotion.com/"
                        if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) reqHeaders["Origin"] = "https://www.dailymotion.com"
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
                    lowerTarget.contains("vimeo.com") || (streamData?.providerId == "vimeo" && !isBilibiliStream) -> {
                        reqHeaders["Referer"] = "https://vimeo.com/"
                        reqHeaders["Origin"] = "https://vimeo.com"
                    }
                    (lowerTarget.contains("hotstar.com") || streamData?.providerId == "hotstar") && !isGoogleStorageOrPublic -> {
                        reqHeaders["Referer"] = "https://www.hotstar.com/"
                        reqHeaders["Origin"] = "https://www.hotstar.com"
                    }
                }
            }
        }

        return Pair(customUserAgent, reqHeaders)
    }

    /**
     * Builds an OkHttpDataSource.Factory configured with custom User-Agent and per-request headers.
     */
    fun createDataSourceFactory(targetUrl: String, streamData: StreamData?, specificHeaders: Map<String, String> = emptyMap()): OkHttpDataSource.Factory {
        val (userAgent, reqHeaders) = resolveRequestHeaders(targetUrl, streamData, specificHeaders)
        val dsFactory = OkHttpDataSource.Factory(okHttpClient)
        userAgent?.let { dsFactory.setUserAgent(it) }
        if (reqHeaders.isNotEmpty()) {
            dsFactory.setDefaultRequestProperties(reqHeaders)
        }
        return dsFactory
    }

    /**
     * Creates a DefaultMediaSourceFactory.
     */
    fun createMediaSourceFactory(targetUrl: String, streamData: StreamData?, specificHeaders: Map<String, String> = emptyMap()): DefaultMediaSourceFactory {
        val dsFactory = createDataSourceFactory(targetUrl, streamData, specificHeaders)
        return DefaultMediaSourceFactory(dsFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)
    }
}
