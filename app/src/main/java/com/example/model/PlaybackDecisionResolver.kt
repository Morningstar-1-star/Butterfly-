package com.example.model

enum class PlaybackSourceType {
    MAGNET,        // Magnet link requiring resolution via Debrid/Torrent pipeline before play
    DIRECT_STREAM, // Direct playable video stream (.m3u8, .mp4, .mkv, HLS, DASH) for ExoPlayer
    EMBED_WEBVIEW  // Web page/embed iframe URL for WebView player
}

object PlaybackDecisionResolver {

    fun determineSourceType(rawUrl: String?, format: String? = null): PlaybackSourceType {
        if (rawUrl.isNullOrEmpty()) return PlaybackSourceType.EMBED_WEBVIEW

        val urlLower = rawUrl.lowercase().trim()
        val fmtLower = format?.lowercase()?.trim() ?: ""

        // 1. Magnet links: NEVER send to WebView, NEVER attempt raw ExoPlayer play
        if (urlLower.startsWith("magnet:") || urlLower.contains("magnet:?xt=")) {
            return PlaybackSourceType.MAGNET
        }

        // 2. Explicit Embed formats or known embed domain patterns
        if (fmtLower == "embed" ||
            urlLower.contains("/embed/") ||
            urlLower.contains("vidsrc") ||
            urlLower.contains("eporner.com") ||
            urlLower.contains("apijav") ||
            urlLower.contains("dailymotion.com") ||
            urlLower.contains("vimeo.com") ||
            urlLower.contains("peertube") ||
            urlLower.contains("nvembed") ||
            urlLower.contains("mvembed")
        ) {
            return PlaybackSourceType.EMBED_WEBVIEW
        }

        // 3. Direct video streams
        if (fmtLower == "hls" || fmtLower == "mp4" ||
            urlLower.endsWith(".m3u8") || urlLower.contains(".m3u8") ||
            urlLower.endsWith(".mp4") || urlLower.contains(".mp4") ||
            urlLower.endsWith(".mkv") || urlLower.contains(".mkv") ||
            urlLower.contains("googlevideo.com") ||
            urlLower.contains("requestdl") // TorBox direct download link
        ) {
            return PlaybackSourceType.DIRECT_STREAM
        }

        // Fallback for HTTP/HTTPS URLs: if not explicit video extension, treat as embed
        return if (urlLower.startsWith("http://") || urlLower.startsWith("https://")) {
            PlaybackSourceType.EMBED_WEBVIEW
        } else {
            PlaybackSourceType.DIRECT_STREAM
        }
    }
}
