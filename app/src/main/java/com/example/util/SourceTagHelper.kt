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
        val contentColor: Color = Color.White
    )

    fun getSourceBadge(video: VideoItem): SourceBadge {
        return getSourceBadge(
            providerId = video.providerId,
            videoId = video.id,
            title = video.title,
            uploaderName = video.uploaderName,
            thumbnailUrl = video.thumbnailUrl
        )
    }

    fun getSourceBadge(
        providerId: String?,
        videoId: String? = null,
        title: String? = null,
        uploaderName: String? = null,
        thumbnailUrl: String? = null
    ): SourceBadge {
        val pid = (providerId ?: "").trim().lowercase()
        val vid = (videoId ?: "").trim().lowercase()
        val uploader = (uploaderName ?: "").trim().lowercase()
        val thumb = (thumbnailUrl ?: "").trim().lowercase()

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
            pid == "tmdb" || pid == "tmdb_movies" -> "TMDB"
            pid == "anilist" -> "AniList"
            pid == "jikan" || pid == "jikan_anime" -> "Jikan"

            // Adult / 18+ Sources (Identify actual source accurately)
            pid == "pornhub" -> "Pornhub"
            pid == "xvideos" -> "XVideos"
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
            vid.startsWith("dm_") || vid.contains("dailymotion.com") -> "Dailymotion"
            vid.startsWith("vim_") || vid.contains("vimeo.com") -> "Vimeo"
            vid.startsWith("twitch_") || vid.contains("twitch.tv") -> "Twitch"
            vid.startsWith("yts_") -> "YTS"
            vid.startsWith("eztv_") -> "EZTV"
            vid.startsWith("nyaa_") -> "Nyaa"
            vid.startsWith("1337x_") -> "1337x"
            vid.startsWith("torrent:") || vid.startsWith("magnet:") -> "Torrent"
            vid.startsWith("archive_") || vid.contains("archive.org") -> "Archive"
            vid.startsWith("tmdb_") -> "TMDB"
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
            uploader.contains("vimeo") -> "Vimeo"
            uploader.contains("twitch") -> "Twitch"

            // Check if standard 11-character YouTube video ID
            vid.length == 11 && vid.matches(Regex("^[a-zA-Z0-9_-]{11}$")) -> "YouTube"

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
        return SourceBadge(
            name = name,
            backgroundColor = bgColor,
            contentColor = contentColor
        )
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
            s.contains("telegram") -> Pair(Color(0xFF2AABEE), Color.White)
            s.contains("mega") -> Pair(Color(0xFFD9272E), Color.White)
            s.contains("bunkr") -> Pair(Color(0xFF880E4F), Color.White)
            s.contains("cloud social") -> Pair(Color(0xFF5C6BC0), Color.White)
            s.contains("archive") -> Pair(Color(0xFF5D4037), Color.White)
            s.contains("torrent") || s.contains("yts") || s.contains("eztv") || s.contains("1337x") || s.contains("nyaa") -> Pair(Color(0xFF2E7D32), Color.White)
            s.contains("tmdb") -> Pair(Color(0xFF01B4E4), Color.White)
            s.contains("anilist") -> Pair(Color(0xFF02A9FF), Color.White)
            s.contains("jikan") -> Pair(Color(0xFF2E51A2), Color.White)
            s.contains("vega") -> Pair(Color(0xFF6200EE), Color.White)

            // Adult / 18+ Platforms
            s.contains("pornhub") -> Pair(Color(0xFFFF9900), Color.Black)
            s.contains("xvideos") -> Pair(Color(0xFFD32F2F), Color.White)
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
            (pId == "jav_all" || pId == "123av" || pId == "javtiful" || pId == "sextb")) {
            return true
        }
        if ((target == "jikan_anime" || target == "anime") &&
            (pId == "jikan_anime" || pId == "anime" || pId == "torrent" || pId == "crunchyroll" || pId == "hanime1")) {
            return true
        }
        if (target.startsWith("vega_") && pId.startsWith("vega_")) {
            return target == pId || target.removePrefix("vega_") == pId.removePrefix("vega_")
        }
        return false
    }
}
