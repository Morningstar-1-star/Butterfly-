package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.model.VideoItem

/**
 * SourceTagHelper provides centralized, ultra-accurate source platform identification
 * for video thumbnail tags and badges.
 *
 * Guarantees that thumbnail source tags ONLY show the genuine source platform name
 * (e.g. "YouTube", "Crunchyroll", "SonyLIV", "Dailymotion", etc.) with distinctive brand
 * color styling, and NEVER pollutes source tags with content categories (e.g. "Song", "Movie")
 * or generic placeholders ("Video").
 */
object SourceTagHelper {

    data class SourceBadge(
        val name: String,
        val backgroundColor: Color,
        val contentColor: Color = Color.White,
        val providerIdKey: String = "youtube"
    )

    fun isActualYouTubeVideo(
        providerId: String?,
        videoId: String?,
        thumbnailUrl: String? = null,
        uploaderUrl: String? = null,
        description: String? = null
    ): Boolean {
        val pid = (providerId ?: "").trim().lowercase()
        val vid = (videoId ?: "").trim()
        val vidLower = vid.lowercase()
        val thumb = (thumbnailUrl ?: "").trim().lowercase()
        val upUrl = (uploaderUrl ?: "").trim().lowercase()
        val desc = (description ?: "").trim().lowercase()

        // 1. Explicit YouTube provider
        if (pid == "youtube") return true

        // 2. Strict non-YouTube platform exclusions (protect native providers from false YouTube tags)
        if (pid == "tencent" || pid == "vqq" || pid == "qq" ||
            pid == "bilibili" || pid == "dailymotion" || pid == "vimeo" ||
            pid == "twitch" || pid == "bigo" || pid == "bigolive" ||
            pid == "bunkr" || pid == "mega" || pid == "telegram" ||
            pid == "yts" || pid == "eztv" || pid == "1337x" || pid == "nyaa" || pid == "torrent" || pid == "torrentio" ||
            pid == "archive_org" || pid == "archive" || pid == "tmdb" || pid == "anilist" || pid == "jikan" ||
            isAdultSource(pid)
        ) {
            return false
        }

        // Strict URL/ID prefix exclusions
        if (vid.startsWith("tencent:", ignoreCase = true) ||
            vid.startsWith("vqq:", ignoreCase = true) ||
            vid.contains("v.qq.com", ignoreCase = true) ||
            vid.contains("video.qq.com", ignoreCase = true) ||
            vid.startsWith("bili_", ignoreCase = true) ||
            vid.startsWith("bv", ignoreCase = true) ||
            vid.startsWith("av", ignoreCase = true) ||
            vid.startsWith("dm_", ignoreCase = true) ||
            vid.startsWith("vim_", ignoreCase = true) ||
            vid.startsWith("twitch_", ignoreCase = true) ||
            vid.startsWith("bunkr_", ignoreCase = true) ||
            vid.startsWith("mega_", ignoreCase = true) ||
            vid.startsWith("tg_", ignoreCase = true) ||
            vid.startsWith("torrent:", ignoreCase = true) ||
            vid.startsWith("magnet:", ignoreCase = true) ||
            vid.startsWith("yts_", ignoreCase = true) ||
            vid.startsWith("nyaa_", ignoreCase = true) ||
            vid.startsWith("eztv_", ignoreCase = true) ||
            vid.startsWith("1337x_", ignoreCase = true) ||
            vid.startsWith("eporner:", ignoreCase = true) ||
            vid.startsWith("ph_", ignoreCase = true) ||
            vid.startsWith("xv_", ignoreCase = true) ||
            vid.startsWith("xh_", ignoreCase = true) ||
            vid.startsWith("spankbang:", ignoreCase = true) ||
            vid.startsWith("tnaflix:", ignoreCase = true) ||
            vid.startsWith("motherless:", ignoreCase = true) ||
            vid.startsWith("playvid:", ignoreCase = true) ||
            vid.startsWith("thisvid:", ignoreCase = true) ||
            vid.startsWith("4tube:", ignoreCase = true) ||
            vid.startsWith("archive_", ignoreCase = true) ||
            vid.startsWith("tmdb_", ignoreCase = true) ||
            vid.startsWith("anilist_", ignoreCase = true) ||
            vid.startsWith("vega_", ignoreCase = true)
        ) {
            return false
        }

        if (vidLower.contains("bilibili.com") ||
            vidLower.contains("dailymotion.com") ||
            vidLower.contains("vimeo.com") ||
            vidLower.contains("twitch.tv") ||
            vidLower.contains("archive.org") ||
            vidLower.contains("pornhub.com") ||
            vidLower.contains("xvideos.com") ||
            vidLower.contains("eporner.com") ||
            vidLower.contains("spankbang.com") ||
            vidLower.contains("tnaflix.com") ||
            vidLower.contains("bunkr.") ||
            vidLower.contains("mega.nz") ||
            vidLower.contains("t.me")
        ) {
            return false
        }

        // Non-YouTube CDN thumbnails
        if (thumb.contains("gtimg.com") ||
            thumb.contains("qpic.cn") ||
            thumb.contains("hdslb.com") ||
            thumb.contains("dmcdn.net") ||
            thumb.contains("vimeocdn.com") ||
            thumb.contains("ttvnw.net") ||
            thumb.contains("phncdn.com") ||
            thumb.contains("xhcdn.com") ||
            thumb.contains("eporner.com") ||
            thumb.contains("sb-cd.com") ||
            thumb.contains("spankcdn") ||
            thumb.contains("motherless") ||
            thumb.contains("bunkr")
        ) {
            return false
        }

        // Positive YouTube signals
        // 3. YouTube URL in videoId or URL
        if (vidLower.contains("youtube.com") || vidLower.contains("youtu.be")) {
            return true
        }

        // 4. YouTube CDN thumbnails (i.ytimg.com, yt3.ggpht.com, etc.)
        if (thumb.contains("ytimg.com") || thumb.contains("ggpht.com") || thumb.contains("youtube.com")) {
            return true
        }

        // 5. Uploader URL pointing to YouTube
        if (upUrl.contains("youtube.com") || upUrl.contains("youtu.be")) {
            return true
        }

        // 6. Video description containing YouTube watch or youtu.be link
        if (desc.contains("youtu.be/") || desc.contains("youtube.com/watch")) {
            return true
        }

        // 7. Standard 11-character YouTube video ID: ONLY apply when provider is generic/unspecified or OTT wrapper
        val isGenericOrWrapper = pid.isBlank() || pid == "all" || pid == "video" || pid == "default" || pid == "custom" ||
            pid == "hotstar" || pid == "sonyliv" || pid == "crunchyroll" || pid == "amazonminitv" || pid == "minitv" || pid == "imdb"
        if (isGenericOrWrapper && vid.length == 11 && vid.matches(Regex("^[a-zA-Z0-9_-]{11}$")) && !vid.all { it.isDigit() }) {
            // For OTT wrappers, require either a YouTube thumb or not having native domains
            if (pid == "hotstar" || pid == "sonyliv" || pid == "crunchyroll" || pid == "amazonminitv" || pid == "minitv") {
                return thumb.contains("ytimg") || thumb.contains("ggpht") || desc.contains("youtu") || upUrl.contains("youtu")
            }
            return true
        }

        return false
    }

    fun getSourceBadge(video: VideoItem): SourceBadge {
        return getSourceBadge(
            providerId = video.providerId,
            videoId = video.id,
            title = video.title,
            uploaderName = video.uploaderName,
            thumbnailUrl = video.thumbnailUrl,
            uploaderUrl = video.uploaderUrl,
            description = video.description
        )
    }

    fun getSourceBadge(
        providerId: String?,
        videoId: String? = null,
        title: String? = null,
        uploaderName: String? = null,
        thumbnailUrl: String? = null,
        uploaderUrl: String? = null,
        description: String? = null
    ): SourceBadge {
        val pid = (providerId ?: "").trim().lowercase()
        val vid = (videoId ?: "").trim().lowercase()
        val uploader = (uploaderName ?: "").trim().lowercase()
        val thumb = (thumbnailUrl ?: "").trim().lowercase()

        // 0. Primary Native Provider Accuracy Checks (NEVER mistakenly tag these as YouTube!)
        // Tencent Video (v.qq.com)
        if (pid == "tencent" || pid == "vqq" || pid == "qq" ||
            vid.contains("v.qq.com") || vid.contains("video.qq.com") ||
            vid.startsWith("tencent:") || vid.startsWith("vqq:") ||
            thumb.contains("gtimg.com") || thumb.contains("qpic.cn") ||
            uploader.contains("tencent") || uploader.contains("腾讯") || uploader.contains("wetv")
        ) {
            val (bgColor, contentColor) = getBrandColors("Tencent Video")
            return SourceBadge(
                name = "Tencent Video",
                backgroundColor = bgColor,
                contentColor = contentColor,
                providerIdKey = "tencent"
            )
        }

        // Bilibili
        if (pid == "bilibili" || vid.contains("bilibili.com") || vid.startsWith("bili_") || vid.startsWith("bv") || vid.startsWith("av") || thumb.contains("hdslb.com")) {
            val (bgColor, contentColor) = getBrandColors("Bilibili")
            return SourceBadge(
                name = "Bilibili",
                backgroundColor = bgColor,
                contentColor = contentColor,
                providerIdKey = "bilibili"
            )
        }

        // Dailymotion
        if (pid == "dailymotion" || vid.contains("dailymotion.com") || vid.startsWith("dm_") || thumb.contains("dmcdn.net")) {
            val (bgColor, contentColor) = getBrandColors("Dailymotion")
            return SourceBadge(
                name = "Dailymotion",
                backgroundColor = bgColor,
                contentColor = contentColor,
                providerIdKey = "dailymotion"
            )
        }

        // Twitch
        if (pid == "twitch" || vid.contains("twitch.tv") || vid.startsWith("twitch_") || thumb.contains("ttvnw.net")) {
            val (bgColor, contentColor) = getBrandColors("Twitch")
            return SourceBadge(
                name = "Twitch",
                backgroundColor = bgColor,
                contentColor = contentColor,
                providerIdKey = "twitch"
            )
        }

        // Vimeo
        if (pid == "vimeo" || vid.contains("vimeo.com") || vid.startsWith("vim_") || thumb.contains("vimeocdn.com")) {
            val (bgColor, contentColor) = getBrandColors("Vimeo")
            return SourceBadge(
                name = "Vimeo",
                backgroundColor = bgColor,
                contentColor = contentColor,
                providerIdKey = "vimeo"
            )
        }

        // 1. High-priority verification: If any source (Hotstar, SonyLIV, Crunchyroll, etc.)
        // is actually serving a YouTube video (via ytimg thumbnail or youtu.be link),
        // accurately badge it as "YouTube"!
        if (isActualYouTubeVideo(pid, videoId, thumbnailUrl, uploaderUrl, description)) {
            val (bgColor, contentColor) = getBrandColors("YouTube")
            return SourceBadge(
                name = "YouTube",
                backgroundColor = bgColor,
                contentColor = contentColor,
                providerIdKey = "youtube"
            )
        }

        // 1. Direct and unambiguous match by provider ID
        val name: String = when {
            // Mainstream Streaming & OTT
            pid == "crunchyroll" -> "Crunchyroll"
            pid == "sonyliv" -> "SonyLIV"
            pid == "youtube" -> "YouTube"
            pid == "dailymotion" -> "Dailymotion"
            pid == "twitch" -> "Twitch"
            pid == "bigo" || pid == "bigolive" -> "Bigo Live"
            pid == "bilibili" -> "Bilibili"
            pid == "tencent" || pid == "vqq" || pid == "qq" -> "Tencent Video"
            pid == "vimeo" -> "Vimeo"
            pid == "hotstar" || pid == "jiohotstar" -> "Hotstar"
            pid == "amazonminitv" || pid == "minitv" -> "MiniTV"
            pid == "discoveryplus" || pid == "discovery" -> "Discovery+"
            pid == "disney" || pid == "disneyplus" -> "Disney+"
            pid == "hbo" || pid == "hbomax" || pid == "max" -> "HBO Max"
            pid == "curiositystream" || pid == "curiosity" -> "CuriosityStream"
            pid == "googledrive" || pid == "gdrive" || pid == "google_drive" -> "Google Drive"
            pid == "imdb" -> "IMDb"
            pid == "mxplayer" -> "MX Player"
            pid == "popcorntv" || pid == "popcorn" -> "PopcornTV"
            pid == "decryptor" -> "Decryptor"
            pid == "vidsrc" -> "VidSrc"

            // Cloud Social (Bunkr, Telegram, MEGA)
            pid == "bunkr" -> "Bunkr"
            pid == "mega" -> "MEGA"
            pid == "telegram" -> "Telegram"
            pid == "bun-tel-meg" || pid == "cloud_social" -> {
                when {
                    vid.contains("bunkr") || uploader.contains("bunkr") || thumb.contains("bunkr") -> "Bunkr"
                    vid.contains("mega") || uploader.contains("mega") || thumb.contains("mega") -> "MEGA"
                    vid.contains("tg") || vid.contains("telegram") || uploader.contains("telegram") -> "Telegram"
                    else -> "Cloud Social"
                }
            }

            // P2P / Torrents
            pid == "yts" -> "YTS"
            pid == "eztv" -> "EZTV"
            pid == "1337x" -> "1337x"
            pid == "nyaa" -> "Nyaa"
            pid == "torrentgalaxy" -> "TorrentGalaxy"
            pid == "torrent" || pid == "torrentio" -> "Torrent"

            // Catalogs & Archives
            pid == "archive_org" || pid == "archive" -> "Archive"
            pid.startsWith("tmdb_") && pid != "tmdb_embed" -> {
                val sub = pid.removePrefix("tmdb_")
                val src = com.example.extractor.tmdbembed.TMDBEmbedSource.fromId(sub)
                src?.displayName ?: sub.replaceFirstChar { it.uppercase() }
            }
            pid == "tmdb_embed" || pid == "tmdb" || pid == "tmdb_movies" -> {
                val matching = com.example.extractor.tmdbembed.TMDBEmbedSource.allSources.firstOrNull { s ->
                    uploader.contains(s.displayName, ignoreCase = true) ||
                    uploader.contains(s.id, ignoreCase = true) ||
                    vid.startsWith("tmdb_${s.id}:")
                }
                matching?.displayName ?: com.example.extractor.tmdbembed.TMDBEmbedConfig.cachedDefaultSourceName
            }
            pid == "anilist" -> "AniList"
            pid == "jikan" || pid == "jikan_anime" -> "Jikan"

            // Adult / 18+ Sources (Identify actual source accurately)
            pid == "pornhub" -> "Pornhub"
            pid == "xvideos" -> "XVideos"
            pid == "xnxx" -> "XNXX"
            pid == "hellporno" -> "HellPorno"
            pid == "stripchat" -> "Stripchat Live"
            pid == "xhamster" -> "xHamster"
            pid == "redtube" -> "RedTube"
            pid == "youporn" -> "YouPorn"
            pid == "eporner" -> "Eporner"
            pid == "spankbang" -> "SpankBang"
            pid == "motherless" -> "Motherless"
            pid == "playvid" -> "Playvid"
            pid == "tnaflix" -> "TNAFlix"
            pid == "txxx" -> "Txxx"
            pid == "thisvid" -> "ThisVid"
            pid == "noodlemagazine" || pid == "noodlemag" -> "NoodleMag"
            pid == "cam4" -> "CAM4 Live"
            pid == "cammodels" -> "CamModels Live"
            pid == "chaturbate" -> "Chaturbate Live"
            pid == "hanime1" || pid == "hanime" -> "Hanime"
            pid == "hqporner" || pid == "hqplayer" -> "HQPorner"
            pid == "beeg" -> "Beeg"
            pid == "4tube" -> "4Tube"
            pid == "rule34video" -> "Rule34"
            pid == "123av" || pid == "javplayer" -> "123AV"
            pid == "javtiful" -> "Javtiful"
            pid == "jav_all" || pid == "all_jav" || pid == "javbus" || pid == "javapi" || pid == "javdex" -> "JAV"
            pid == "sextb" -> "SEXТB"

            // Vega Providers
            pid.startsWith("vega_") -> {
                val clean = pid.removePrefix("vega_").replace("_", " ").trim()
                if (clean.isNotEmpty()) {
                    "Vega: " + clean.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                } else {
                    "Vega"
                }
            }

            // 2. Intelligent inference when providerId is generic ("all", "custom", "video", "default") or blank
            // Check specific video ID patterns / URLs
            vid.startsWith("crunchyroll:") || vid.contains("crunchyroll.com") -> "Crunchyroll"
            vid.startsWith("sonyliv:") || vid.contains("sonyliv.com") -> "SonyLIV"
            vid.startsWith("hotstar:") || vid.contains("hotstar.com") -> "Hotstar"
            vid.startsWith("bunkr_") || vid.contains("bunkr.") -> "Bunkr"
            vid.startsWith("mega_") || vid.contains("mega.nz") -> "MEGA"
            vid.startsWith("tg_") || vid.contains("t.me/") -> "Telegram"
            vid.startsWith("bili_") || vid.startsWith("bv") || vid.contains("bilibili.com") -> "Bilibili"
            vid.startsWith("tencent:") || vid.startsWith("vqq:") || vid.contains("v.qq.com") || vid.contains("video.qq.com") -> "Tencent Video"
            vid.startsWith("dm_") || vid.contains("dailymotion.com") -> "Dailymotion"
            vid.startsWith("vim_") || vid.contains("vimeo.com") -> "Vimeo"
            vid.startsWith("twitch_") || vid.contains("twitch.tv") -> "Twitch"
            vid.startsWith("yts_") -> "YTS"
            vid.startsWith("eztv_") -> "EZTV"
            vid.startsWith("nyaa_") -> "Nyaa"
            vid.startsWith("1337x_") -> "1337x"
            vid.startsWith("torrent:") || vid.startsWith("magnet:") -> "Torrent"
            vid.startsWith("archive_") || vid.contains("archive.org") -> "Archive"
            vid.startsWith("tmdb_") -> {
                val matching = com.example.extractor.tmdbembed.TMDBEmbedSource.allSources.firstOrNull { s ->
                    vid.startsWith("tmdb_${s.id}:")
                }
                matching?.displayName ?: com.example.extractor.tmdbembed.TMDBEmbedConfig.cachedDefaultSourceName
            }
            vid.startsWith("anilist_") -> "AniList"
            vid.startsWith("eporner:") || vid.contains("eporner.com") -> "Eporner"
            vid.startsWith("spankbang:") || vid.contains("spankbang.com") -> "SpankBang"
            vid.startsWith("motherless:") || vid.contains("motherless.com") -> "Motherless"
            vid.startsWith("tnaflix:") || vid.contains("tnaflix.com") -> "TNAFlix"
            vid.startsWith("playvid:") || vid.contains("playvid.com") -> "Playvid"
            vid.startsWith("thisvid:") || vid.contains("thisvid.com") -> "ThisVid"
            vid.startsWith("4tube:") || vid.contains("4tube.com") -> "4Tube"
            vid.startsWith("ph_") || vid.contains("pornhub.com") -> "Pornhub"
            vid.startsWith("xv_") || vid.contains("xvideos.com") -> "XVideos"
            vid.startsWith("xh_") || vid.contains("xhamster.com") -> "xHamster"
            vid.startsWith("vega_") -> "Vega"

            // Check Thumbnail CDN URLs
            thumb.contains("crunchyroll") -> "Crunchyroll"
            thumb.contains("sonyliv") -> "SonyLIV"
            thumb.contains("hotstar") -> "Hotstar"
            thumb.contains("ytimg.com") || thumb.contains("ggpht.com") -> "YouTube"
            thumb.contains("dmcdn.net") || thumb.contains("dailymotion.com") -> "Dailymotion"
            thumb.contains("vimeocdn.com") -> "Vimeo"
            thumb.contains("hdslb.com") || thumb.contains("biliapi.net") -> "Bilibili"
            thumb.contains("gtimg.com") || thumb.contains("qpic.cn") || thumb.contains("v.qq.com") -> "Tencent Video"
            thumb.contains("ttvnw.net") -> "Twitch"
            thumb.contains("archive.org") -> "Archive"
            thumb.contains("eporner.com") -> "Eporner"
            thumb.contains("spankbang") || thumb.contains("sb-cd.com") || thumb.contains("spankcdn") -> "SpankBang"
            thumb.contains("motherless") || thumb.contains("motherlessmedia") || thumb.contains("cdn.motherless") -> "Motherless"
            thumb.contains("tnaflix.com") -> "TNAFlix"
            thumb.contains("phncdn.com") -> "Pornhub"
            thumb.contains("xvideos-cdn") || thumb.contains("xnxx-cdn") -> "XVideos"
            thumb.contains("xhcdn.com") -> "xHamster"
            thumb.contains("bunkr") -> "Bunkr"

            // Check Uploader branding
            uploader.contains("crunchyroll") -> "Crunchyroll"
            uploader.contains("sonyliv") || uploader.contains("set india") || uploader.contains("sony sab") || uploader.contains("sony pal") -> "SonyLIV"
            uploader.contains("hotstar") -> "Hotstar"
            uploader.contains("bunkr") -> "Bunkr"
            uploader.contains("mega") -> "MEGA"
            uploader.contains("telegram") -> "Telegram"
            uploader.contains("dailymotion") -> "Dailymotion"
            uploader.contains("bilibili") -> "Bilibili"
            uploader.contains("tencent") || uploader.contains("v.qq") -> "Tencent Video"
            uploader.contains("vimeo") -> "Vimeo"
            uploader.contains("twitch") -> "Twitch"

            // Check if standard 11-character YouTube video ID (only for generic/unspecified providers)
            vid.length == 11 && vid.matches(Regex("^[a-zA-Z0-9_-]{11}$")) && (pid.isBlank() || pid == "all" || pid == "video" || pid == "custom" || pid == "default" || pid == "youtube") -> "YouTube"

            // If a custom non-empty provider ID was given, format it nicely
            pid.isNotEmpty() && pid != "all" && pid != "custom" && pid != "video" && pid != "default" -> {
                pid.split("_", "-", " ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }

            // Default safe source: The app is primarily a YouTube client
            else -> "YouTube"
        }

        val (bgColor, contentColor) = getBrandColors(name)
        val providerKey = getProviderIdKey(name, pid)
        return SourceBadge(
            name = name,
            backgroundColor = bgColor,
            contentColor = contentColor,
            providerIdKey = providerKey
        )
    }

    fun getProviderIdKey(sourceName: String, originalProviderId: String): String {
        val s = sourceName.lowercase()
        return when {
            s.contains("youtube") -> "youtube"
            s.contains("crunchyroll") -> "crunchyroll"
            s.contains("sonyliv") -> "sonyliv"
            s.contains("hotstar") -> "hotstar"
            s.contains("dailymotion") -> "dailymotion"
            s.contains("twitch") -> "twitch"
            s.contains("bilibili") -> "bilibili"
            s.contains("tencent") -> "tencent"
            s.contains("vimeo") -> "vimeo"
            s.contains("minitv") -> "amazonminitv"
            s.contains("discovery") -> "discoveryplus"
            s.contains("disney") -> "disney"
            s.contains("hbo") -> "hbo"
            s.contains("curiosity") -> "curiositystream"
            s.contains("mx player") -> "mxplayer"
            s.contains("popcorn") -> "popcorntv"
            s.contains("imdb") -> "imdb"
            s.contains("bunkr") -> "bunkr"
            s.contains("mega") -> "mega"
            s.contains("telegram") -> "telegram"
            s.contains("torrent") || s.contains("yts") || s.contains("nyaa") -> "torrent"
            s.contains("archive") -> "archive"
            s.contains("vixsrc") -> "tmdb_vixsrc"
            s.contains("netmirror") -> "tmdb_netmirror"
            s.contains("videasy") -> "tmdb_videasy"
            s.contains("vidlink") -> "tmdb_vidlink"
            s.contains("castletv") -> "tmdb_castletv"
            s.contains("4khdhub") -> "tmdb_4khdhub"
            s.contains("showbox") -> "tmdb_showbox"
            s.contains("vaplayer") -> "tmdb_vaplayer"
            s.contains("dahmermovies") -> "tmdb_dahmermovies"
            s.contains("streamflix") -> "tmdb_streamflix"
            s.contains("hdghartv") -> "tmdb_hdghartv"
            s.contains("onetouchtv") -> "tmdb_onetouchtv"
            s.contains("zxcstreams") -> "tmdb_zxcstreams"
            s.contains("tmdb") -> "tmdb_embed"
            s.contains("pornhub") -> "pornhub"
            s.contains("xvideos") -> "xvideos"
            s.contains("xnxx") -> "xnxx"
            s.contains("eporner") -> "eporner"
            s.contains("spankbang") -> "spankbang"
            s.contains("xhamster") -> "xhamster"
            s.contains("redtube") -> "redtube"
            s.contains("youporn") -> "youporn"
            s.contains("stripchat") -> "stripchat"
            s.contains("chaturbate") -> "chaturbate"
            s.contains("cam4") -> "cam4"
            s.contains("hanime") -> "hanime1"
            s.contains("rule34") -> "rule34video"
            originalProviderId.isNotBlank() -> originalProviderId.lowercase()
            else -> "youtube"
        }
    }

    private fun getBrandColors(sourceName: String): Pair<Color, Color> {
        val s = sourceName.lowercase()
        return when {
            s.contains("youtube") -> Pair(Color(0xFFFF0000), Color.White)
            s.contains("crunchyroll") -> Pair(Color(0xFFF47521), Color.White) // Crunchyroll Brand Orange
            s.contains("sonyliv") -> Pair(Color(0xFF4A148C), Color.White)     // SonyLIV Brand Royal Purple
            s.contains("dailymotion") -> Pair(Color(0xFF0066DC), Color.White)
            s.contains("twitch") -> Pair(Color(0xFF9146FF), Color.White)
            s.contains("bigo") -> Pair(Color(0xFF00B0FF), Color.White)
            s.contains("bilibili") -> Pair(Color(0xFF00AEEC), Color.White)
            s.contains("tencent") || s.contains("vqq") -> Pair(Color(0xFF0052D9), Color.White)
            s.contains("vimeo") -> Pair(Color(0xFF1AB7EA), Color.White)
            s.contains("hotstar") -> Pair(Color(0xFF0D47A1), Color.White)
            s.contains("minitv") -> Pair(Color(0xFFFF9900), Color.White)
            s.contains("discovery") -> Pair(Color(0xFF00838F), Color.White)
            s.contains("disney") -> Pair(Color(0xFF113CCF), Color.White)
            s.contains("hbo") || s.contains("max") -> Pair(Color(0xFF5822B4), Color.White)
            s.contains("curiosity") -> Pair(Color(0xFFEAA800), Color.White)
            s.contains("google drive") -> Pair(Color(0xFF0F9D58), Color.White)
            s.contains("imdb") -> Pair(Color(0xFFF5C518), Color.Black)
            s.contains("mx player") -> Pair(Color(0xFF1565C0), Color.White)
            s.contains("popcorntv") -> Pair(Color(0xFFD32F2F), Color.White)
            s.contains("decryptor") -> Pair(Color(0xFF00E5FF), Color.Black)
            s.contains("vidsrc") -> Pair(Color(0xFFFF9100), Color.White)
            s.contains("telegram") -> Pair(Color(0xFF2AABEE), Color.White)
            s.contains("mega") -> Pair(Color(0xFFD9272E), Color.White)
            s.contains("bunkr") -> Pair(Color(0xFF880E4F), Color.White)
            s.contains("cloud social") -> Pair(Color(0xFF5C6BC0), Color.White)
            s.contains("archive") -> Pair(Color(0xFF5D4037), Color.White)
            s.contains("torrent") || s.contains("yts") || s.contains("eztv") || s.contains("1337x") || s.contains("nyaa") -> Pair(Color(0xFF2E7D32), Color.White)
            
            // TMDB 13 Sources Distinct Branding
            s.contains("vixsrc") -> Pair(Color(0xFFE50914), Color.White)       // VixSrc Vibrant Red
            s.contains("netmirror") -> Pair(Color(0xFFB71C1C), Color.White)    // NetMirror Deep Ruby
            s.contains("videasy") -> Pair(Color(0xFF7C4DFF), Color.White)      // Videasy Electric Violet
            s.contains("vidlink") -> Pair(Color(0xFF00B4D8), Color.Black)      // Vidlink Bright Cyan
            s.contains("castletv") -> Pair(Color(0xFFFF5722), Color.White)     // CastleTV Coral
            s.contains("4khdhub") -> Pair(Color(0xFF0288D1), Color.White)      // 4KHDHub Ocean Blue
            s.contains("showbox") -> Pair(Color(0xFFFFB300), Color.Black)      // Showbox Amber Gold
            s.contains("vaplayer") -> Pair(Color(0xFF00C853), Color.White)     // VaPlayer Mint Green
            s.contains("dahmermovies") -> Pair(Color(0xFFC62828), Color.White) // DahmerMovies Crimson
            s.contains("streamflix") -> Pair(Color(0xFF8E24AA), Color.White)   // StreamFlix Purple
            s.contains("hdghartv") -> Pair(Color(0xFFEF6C00), Color.White)     // HDGharTV Flame Orange
            s.contains("onetouchtv") -> Pair(Color(0xFFE91E63), Color.White)   // OneTouchTV Hot Pink
            s.contains("zxcstreams") -> Pair(Color(0xFF5E35B1), Color.White)   // ZXCStreams Deep Indigo
            s.contains("tmdb") -> Pair(Color(0xFF01B4E4), Color.White)         // TMDB Teal
            s.contains("anilist") -> Pair(Color(0xFF02A9FF), Color.White)
            s.contains("jikan") -> Pair(Color(0xFF2E51A2), Color.White)
            s.contains("vega") -> Pair(Color(0xFF6200EE), Color.White)

            // Adult / 18+ Platforms
            s.contains("pornhub") -> Pair(Color(0xFFFF9900), Color.Black)
            s.contains("xvideos") -> Pair(Color(0xFFD32F2F), Color.White)
            s.contains("xnxx") -> Pair(Color(0xFF0288D1), Color.White)
            s.contains("hellporno") -> Pair(Color(0xFFD50000), Color.White)
            s.contains("stripchat") -> Pair(Color(0xFFE91E63), Color.White)
            s.contains("xhamster") -> Pair(Color(0xFFF7941D), Color.White)
            s.contains("redtube") -> Pair(Color(0xFFE50914), Color.White)
            s.contains("youporn") -> Pair(Color(0xFFF22B69), Color.White)
            s.contains("eporner") -> Pair(Color(0xFFC2185B), Color.White)
            s.contains("spankbang") -> Pair(Color(0xFFE53935), Color.White)
            s.contains("motherless") -> Pair(Color(0xFF880E4F), Color.White)
            s.contains("playvid") -> Pair(Color(0xFF00897B), Color.White)
            s.contains("tnaflix") -> Pair(Color(0xFFD84315), Color.White)
            s.contains("txxx") -> Pair(Color(0xFFE53935), Color.White)
            s.contains("thisvid") -> Pair(Color(0xFFAD1457), Color.White)
            s.contains("noodlemag") -> Pair(Color(0xFF880E4F), Color.White)
            s.contains("cam4") -> Pair(Color(0xFFFF5722), Color.White)
            s.contains("cammodels") -> Pair(Color(0xFFD81B60), Color.White)
            s.contains("chaturbate") -> Pair(Color(0xFFE65100), Color.White)
            s.contains("hanime") -> Pair(Color(0xFFFF4081), Color.White)
            s.contains("hqporner") -> Pair(Color(0xFF00ACC1), Color.White)
            s.contains("beeg") -> Pair(Color(0xFFFB8C00), Color.White)
            s.contains("4tube") -> Pair(Color(0xFF00B0FF), Color.White)
            s.contains("rule34") -> Pair(Color(0xFF43A047), Color.White)
            s.contains("123av") -> Pair(Color(0xFF9C27B0), Color.White)
            s.contains("javtiful") -> Pair(Color(0xFF673AB7), Color.White)
            s.contains("jav") -> Pair(Color(0xFF7B1FA2), Color.White)
            s.contains("sextb") -> Pair(Color(0xFFE91E63), Color.White)

            else -> Pair(Color(0xFF37474F), Color.White)
        }
    }

    fun matchesProvider(itemProviderId: String?, targetProviderId: String): Boolean {
        if (targetProviderId.isBlank() || targetProviderId.equals("ALL", ignoreCase = true)) return true
        val pId = (itemProviderId ?: "").lowercase(java.util.Locale.ROOT).trim()
        val target = targetProviderId.lowercase(java.util.Locale.ROOT).trim()
        if (pId == target) return true
        if (pId.contains(target) || target.contains(pId)) return true
        if ((target == "bun-tel-meg" || target == "bunkr" || target == "telegram" || target == "mega" || target == "cloud_social") &&
            (pId == "bun-tel-meg" || pId == "bunkr" || pId == "telegram" || pId == "mega" || pId == "cloud_social")) {
            return true
        }
        if ((target == "jav_all" || target == "all_jav") &&
            (pId == "jav_all" || pId == "all_jav" || pId == "supjav" || pId == "123av" || pId == "javtiful" || pId == "sextb" || pId == "javplayer" || pId.contains("jav"))) {
            return true
        }
        if ((target == "jikan_anime" || target == "anime") &&
            (pId == "jikan_anime" || pId == "anime" || pId == "torrent" || pId == "crunchyroll" || pId == "hanime1")) {
            return true
        }
        if (target.startsWith("vega_") && pId.startsWith("vega_")) {
            return target == pId || target.removePrefix("vega_") == pId.removePrefix("vega_")
        }
        if (target == "tmdb_embed" || target == "tmdb") {
            if (pId.startsWith("tmdb_") || pId == "tmdb" || pId == "tmdb_embed" || pId == "tmdb_movies") return true
        }
        if (target.startsWith("tmdb_")) {
            val sub = target.removePrefix("tmdb_")
            if (pId == target || pId == sub || pId.contains(sub)) return true
        }
        return false
    }

    fun isAdultSource(providerId: String?): Boolean {
        val pid = (providerId ?: "").trim().lowercase(java.util.Locale.ROOT)
        return pid in setOf(
            "pornhub", "xvideos", "xnxx", "hellporno", "stripchat", "xhamster",
            "redtube", "youporn", "eporner", "spankbang", "motherless", "playvid",
            "tnaflix", "txxx", "thisvid", "noodlemagazine", "noodlemag", "cam4",
            "cammodels", "chaturbate", "hanime1", "hanime", "hqporner", "hqplayer",
            "beeg", "4tube", "rule34video", "123av", "javplayer", "javtiful",
            "jav_all", "all_jav", "javbus", "javapi", "javdex", "sextb", "supjav"
        )
    }
}
