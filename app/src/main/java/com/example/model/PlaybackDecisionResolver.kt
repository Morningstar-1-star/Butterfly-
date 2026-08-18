package com.example.model

enum class PlaybackSourceType {
    DIRECT_STREAM, // Direct playable video stream (.m3u8, .mp4, .mkv, HLS, DASH, local HTTP range stream) for ExoPlayer
    EMBED_WEBVIEW  // Web page/embed iframe URL for WebView player
}

object PlaybackDecisionResolver {

    fun determineSourceType(rawUrl: String?, format: String? = null): PlaybackSourceType {
        if (rawUrl.isNullOrEmpty()) return PlaybackSourceType.DIRECT_STREAM

        val urlLower = rawUrl.lowercase().trim()
        val fmtLower = format?.lowercase()?.trim() ?: ""

        // 1. Direct video streams (.m3u8, .mp4, .mkv, .webm, hls, local 127.0.0.1 stream) take absolute priority for native ExoPlayer
        if (fmtLower == "hls" || fmtLower == "mp4" || fmtLower == "mkv" || fmtLower == "webm" ||
            urlLower.startsWith("http://127.0.0.1") || urlLower.startsWith("http://localhost") ||
            urlLower.endsWith(".m3u8") || urlLower.contains(".m3u8") || urlLower.contains("m3u8") ||
            urlLower.endsWith(".mp4") || urlLower.contains(".mp4") ||
            urlLower.endsWith(".mkv") || urlLower.contains(".mkv") ||
            urlLower.endsWith(".webm") || urlLower.contains(".webm") ||
            urlLower.contains("googlevideo.com") ||
            urlLower.contains("phncdn.com") ||
            urlLower.contains("dmcdn.net") ||
            urlLower.contains("cdndirector.dailymotion.com") ||
            urlLower.contains("eporner.com/dload")
        ) {
            return PlaybackSourceType.DIRECT_STREAM
        }

        // 2. Explicit Embed formats or known embed domain patterns
        if (fmtLower == "embed" || fmtLower == "iframe" ||
            urlLower.contains("/embed/") ||
            urlLower.contains("vidsrc") ||
            urlLower.contains("nvembed") ||
            urlLower.contains("mvembed")
        ) {
            return PlaybackSourceType.EMBED_WEBVIEW
        }

        // Fallback for HTTP/HTTPS URLs: default to direct stream
        return PlaybackSourceType.DIRECT_STREAM
    }
}



