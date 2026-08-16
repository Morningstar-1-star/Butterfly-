package com.example.model

enum class PlaybackSourceType {
    MAGNET,        // Magnet link requiring resolution via TorrentStreamEngine / Debrid
    DIRECT_STREAM, // Direct playable video stream (.m3u8, .mp4, .mkv, HLS, DASH, local HTTP range stream) for ExoPlayer
    EMBED_WEBVIEW  // Web page/embed iframe URL for WebView player
}

object PlaybackDecisionResolver {

    fun determineSourceType(rawUrl: String?, format: String? = null): PlaybackSourceType {
        if (rawUrl.isNullOrEmpty()) return PlaybackSourceType.DIRECT_STREAM

        val urlLower = rawUrl.lowercase().trim()
        val fmtLower = format?.lowercase()?.trim() ?: ""

        // 1. Magnet links: Route through TorrentStreamEngine for HTTP stream to Media3 ExoPlayer
        if (urlLower.startsWith("magnet:") || urlLower.contains("magnet:?xt=") || fmtLower == "torrent" || fmtLower == "p2p") {
            return PlaybackSourceType.MAGNET
        }

        // 2. Direct video streams (.m3u8, .mp4, .mkv, .webm, hls, local 127.0.0.1 stream) take absolute priority for native ExoPlayer
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

        // 3. Explicit Embed formats or known embed domain patterns
        if (fmtLower == "embed" || fmtLower == "iframe" ||
            urlLower.contains("/embed/") ||
            urlLower.contains("vidsrc") ||
            urlLower.contains("nvembed") ||
            urlLower.contains("mvembed")
        ) {
            return PlaybackSourceType.EMBED_WEBVIEW
        }

        // Fallback for HTTP/HTTPS URLs: if not explicit video extension or stream, treat as embed
        return if (urlLower.startsWith("http://") || urlLower.startsWith("https://")) {
            PlaybackSourceType.DIRECT_STREAM
        } else {
            PlaybackSourceType.DIRECT_STREAM
        }
    }
}



