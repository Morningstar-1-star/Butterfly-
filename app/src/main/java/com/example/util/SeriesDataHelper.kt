package com.example.util

import com.example.model.CastMember
import com.example.model.EpisodeItem
import com.example.model.SeriesSeason
import com.example.model.StreamData
import com.example.model.VideoComment
import com.example.model.VideoItem

object SeriesDataHelper {

    fun generateCast(streamData: StreamData): List<CastMember> {
        val cleanTitle = TMDBHelper.cleanTitleForSearch(streamData.title).lowercase()
        return when {
            cleanTitle.contains("flex x cop") || cleanTitle.contains("flex") -> listOf(
                CastMember("Ahn Bo-hyun", "Jin I-soo", null),
                CastMember("Park Ji-hyun", "Lee Kang-hyun", null),
                CastMember("Kang Sang-jun", "Yoo Jun-young", null),
                CastMember("Kwak Si-yang", "Jin Seung-ju", null),
                CastMember("Kim Shin-bi", "Choi Kyeong-jin", null),
                CastMember("Jang Hyun-sung", "Jin Myeong-chul", null)
            )
            cleanTitle.contains("futurama") -> listOf(
                CastMember("Billy West", "Philip J. Fry / Farnsworth / Zoidberg", null),
                CastMember("Katey Sagal", "Turanga Leela", null),
                CastMember("John DiMaggio", "Bender Bending Rodríguez", null),
                CastMember("Tress MacNeille", "Mom / Ndnd", null),
                CastMember("Phil LaMarr", "Hermes Conrad", null),
                CastMember("Lauren Tom", "Amy Wong", null)
            )
            cleanTitle.contains("spider-man") || cleanTitle.contains("spiderman") -> listOf(
                CastMember("Tom Holland", "Peter Parker / Spider-Man", null),
                CastMember("Zendaya", "MJ", null),
                CastMember("Jacob Batalon", "Ned Leeds", null),
                CastMember("Benedict Cumberbatch", "Doctor Strange", null)
            )
            cleanTitle.contains("simpsons") -> listOf(
                CastMember("Dan Castellaneta", "Homer Simpson", null),
                CastMember("Julie Kavner", "Marge Simpson", null),
                CastMember("Nancy Cartwright", "Bart Simpson", null),
                CastMember("Yeardley Smith", "Lisa Simpson", null)
            )
            else -> emptyList()
        }
    }

    fun getSeriesPillText(title: String?, uploadDate: String? = null, providerId: String? = null, videoId: String? = null): String? {
        if (TMDBHelper.isJavOrAdultProvider(providerId, title)) {
            return null
        }
        val pid = (providerId ?: "").lowercase()
        val clean = (title ?: "").lowercase()
        val uploadClean = (uploadDate ?: "").lowercase()
        val vid = (videoId ?: "").lowercase()

        // Explicit movie check: never show season/episode badge on movies
        if (vid.startsWith("movie_") || pid == "yts" || pid == "tmdb_movies" || clean.contains("yify")) {
            return null
        }

        val hasSeasonOrEpisodePattern = clean.contains(Regex("""\bs\d{1,2}(e\d{1,2})?\b""")) ||
                clean.contains(Regex("""\bseason\s*\d+\b""")) ||
                clean.contains(Regex("""\bep(isode)?\s*\d+\b""")) ||
                vid.startsWith("tv_") ||
                pid.contains("eztv")

        if (uploadClean.contains("s") && uploadClean.contains("ep") && !uploadClean.contains("movie")) {
            return uploadDate
        }

        if (!hasSeasonOrEpisodePattern && !pid.contains("eztv") && !pid.contains("jikan") && !pid.contains("nyaa")) {
            return null
        }

        return when {
            clean.contains("house of the dragon") -> "S2 · 18 ep"
            clean.contains("game of thrones") -> "S8 · 73 ep"
            clean.contains("gintama") -> "S4 · 367 ep"
            clean.contains("futurama") -> "S8 · 150 ep"
            clean.contains("breaking bad") -> "S5 · 62 ep"
            clean.contains("last of us") -> "S2 · 16 ep"
            clean.contains("flex x cop") || clean.contains("flex") -> "S1 · 16 ep"
            clean.contains("stranger things") -> "S4 · 34 ep"
            clean.contains("mandalorian") -> "S3 · 24 ep"
            clean.contains("loki") -> "S2 · 12 ep"
            clean.contains("the boys") -> "S4 · 32 ep"
            clean.contains("arcane") -> "S2 · 18 ep"
            clean.contains("attack on titan") || clean.contains("shingeki") -> "S4 · 89 ep"
            clean.contains("jujutsu") -> "S2 · 47 ep"
            clean.contains("re:zero") || clean.contains("rezero") -> "S3 · 50 ep"
            clean.contains("naruto") -> "S5 · 220 ep"
            clean.contains("one piece") -> "S21 · 1100 ep"
            clean.contains("s01") || clean.contains("season 1") || clean.contains("s1:") -> "S1 · 10 ep"
            clean.contains("s02") || clean.contains("season 2") -> "S2 · 12 ep"
            clean.contains("s03") || clean.contains("season 3") -> "S3 · 12 ep"
            clean.contains("s04") || clean.contains("season 4") -> "S4 · 12 ep"
            clean.contains("s05") || clean.contains("season 5") -> "S5 · 12 ep"
            vid.startsWith("tv_") || pid.contains("eztv") -> "Series"
            else -> null
        }
    }

    private fun extractBaseShowTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("(?i)s\\d+e\\d+.*"), "")
            .replace(Regex("(?i)\\d{4}-s\\d+.*"), "")
            .replace(Regex("(?i)season\\s+\\d+.*"), "")
            .replace(Regex("(?i)ep\\d+.*"), "")
            .replace(Regex("(?i)episode\\s+\\d+.*"), "")
            .replace(Regex("(?i)720p|1080p|4k|hdr|hd"), "")
            .trim()
            .ifEmpty { rawTitle }
    }

    fun isLikelyTvSeries(streamData: StreamData): Boolean {
        if (TMDBHelper.isJavOrAdultProvider(streamData.providerId, streamData.title)) return false
        val vid = streamData.videoId.lowercase()
        val title = streamData.title.lowercase()
        val providerId = (streamData.providerId ?: "").lowercase()

        // Explicit movie indicators -> NOT a TV series
        if (vid.startsWith("movie_") || providerId == "yts" || title.contains("yify")) {
            if (!title.matches(Regex(".*s\\d{1,2}e\\d{1,2}.*")) && !title.contains("season") && !vid.startsWith("tv_")) {
                return false
            }
        }

        if (vid.startsWith("tv_") || providerId.contains("eztv") || title.matches(Regex(".*s\\d{1,2}e\\d{1,2}.*")) || title.contains("season ") || title.contains("episode ")) {
            return true
        }

        // Popular TV series check
        val tvShowNames = listOf(
            "house of the dragon", "game of thrones", "breaking bad", "better call saul",
            "stranger things", "the boys", "last of us", "mandalorian", "loki", "arcane",
            "attack on titan", "shingeki", "jujutsu", "demon slayer", "re:zero", "naruto",
            "one piece", "bleach", "death note", "futurama", "rick and morty", "simpsons",
            "flex x cop", "squid game", "wednesday", "witcher", "vincenzo", "solo leveling",
            "chainsaw man", "spy x family", "vinland saga", "gintama"
        )
        return tvShowNames.any { title.contains(it) }
    }

    fun generateSeasonsAndEpisodes(streamData: StreamData): List<SeriesSeason> {
        if (TMDBHelper.isJavOrAdultProvider(streamData.providerId, streamData.title)) {
            return emptyList()
        }
        val providerId = (streamData.providerId ?: "youtube").lowercase()
        val baseShowTitle = extractBaseShowTitle(streamData.title)
        val thumb = streamData.effectiveThumbnailUrl ?: "https://i.ytimg.com/vi/${streamData.videoId}/hqdefault.jpg"
        val isTorrent = streamData.isTorrent || providerId.contains("eztv") || providerId.contains("torrent") || providerId.contains("yts") || providerId.contains("unified")
        val isArchive = providerId == "archive_org" || streamData.availableStreamOptions.any { it.videoUrl?.contains("archive.org") == true }

        // Archive.org handling: only if multiple episode streams exist
        if (isArchive) {
            if (streamData.availableStreamOptions.size > 1) {
                val optionEpisodes = streamData.availableStreamOptions.mapIndexed { index, option ->
                    val epNum = index + 1
                    val cleanTitle = option.qualityLabel.ifBlank { "Episode $epNum" }
                    EpisodeItem(
                        id = option.videoUrl ?: "${streamData.videoId}_ep_$epNum",
                        seasonNumber = 1,
                        episodeNumber = epNum,
                        title = cleanTitle,
                        durationText = "",
                        thumbnailUrl = thumb,
                        providerId = providerId,
                        viewsText = "Direct MP4"
                    )
                }
                return listOf(SeriesSeason(1, "All Episodes (${optionEpisodes.size})", optionEpisodes))
            } else {
                return emptyList()
            }
        }

        // Return empty for all non-torrent providers (YouTube, Dailymotion, adult tube sources, etc.)
        if (!isTorrent) {
            return emptyList()
        }

        // Return empty for torrent movies (e.g. YTS movies or standalone films)
        if (!isLikelyTvSeries(streamData)) {
            return emptyList()
        }

        // If provider has multiple stream options for torrents/TV shows, map each stream option directly to an episode!
        if (streamData.availableStreamOptions.size > 1 && (providerId.contains("eztv") || providerId.contains("torrent") || providerId.contains("tv"))) {
            val optionEpisodes = streamData.availableStreamOptions.mapIndexed { index, option ->
                val epNum = index + 1
                val cleanTitle = option.qualityLabel.ifBlank { "Episode $epNum" }
                EpisodeItem(
                    id = option.videoUrl ?: "${streamData.videoId}_ep_$epNum",
                    seasonNumber = 1,
                    episodeNumber = epNum,
                    title = cleanTitle,
                    durationText = "",
                    thumbnailUrl = thumb,
                    providerId = providerId,
                    viewsText = "Direct Stream"
                )
            }
            return listOf(SeriesSeason(1, "All Episodes (${optionEpisodes.size})", optionEpisodes))
        }

        // Filter related videos to ONLY include those matching this show title
        val matchingRelated = streamData.relatedVideos.filter { video ->
            val vTitle = video.title.lowercase()
            val showLower = baseShowTitle.lowercase()
            vTitle.contains(showLower) || (showLower.length > 3 && vTitle.contains(showLower.take(4)))
        }

        // Season 1
        val season1Episodes = mutableListOf<EpisodeItem>()

        // Episode 1 (Current Episode - NOW PLAYING)
        season1Episodes.add(
            EpisodeItem(
                id = streamData.videoId,
                seasonNumber = 1,
                episodeNumber = 1,
                title = streamData.title,
                durationText = "",
                thumbnailUrl = thumb,
                providerId = providerId,
                viewsText = if (streamData.viewCount > 0) "${streamData.viewCount} views" else ""
            )
        )

        if (matchingRelated.isNotEmpty()) {
            matchingRelated.forEachIndexed { index, video ->
                val epNum = index + 2
                season1Episodes.add(
                    EpisodeItem(
                        id = video.id,
                        seasonNumber = 1,
                        episodeNumber = epNum,
                        title = video.title,
                        durationText = if (video.formattedDuration.isNotEmpty()) video.formattedDuration else "",
                        thumbnailUrl = video.thumbnailUrl ?: thumb,
                        providerId = video.providerId ?: providerId,
                        viewsText = if (video.formattedViews.isNotEmpty()) video.formattedViews else ""
                    )
                )
            }
        } else {
            // Generate clean show-specific episodes for Season 1
            for (epNum in 2..8) {
                season1Episodes.add(
                    EpisodeItem(
                        id = "${streamData.videoId}_s1_e$epNum",
                        seasonNumber = 1,
                        episodeNumber = epNum,
                        title = "Episode $epNum",
                        durationText = "",
                        thumbnailUrl = thumb,
                        providerId = providerId,
                        viewsText = ""
                    )
                )
            }
        }

        // Season 2
        val season2Episodes = (1..6).map { epNum ->
            EpisodeItem(
                id = "${streamData.videoId}_s2_e$epNum",
                seasonNumber = 2,
                episodeNumber = epNum,
                title = "Episode $epNum",
                durationText = "",
                thumbnailUrl = thumb,
                providerId = providerId,
                viewsText = ""
            )
        }

        return listOf(
            SeriesSeason(1, "Season 1", season1Episodes),
            SeriesSeason(2, "Season 2", season2Episodes)
        )
    }

    fun getRelatedContent(streamData: StreamData, feedVideos: List<VideoItem>): List<VideoItem> {
        val baseShowTitle = extractBaseShowTitle(streamData.title).lowercase()
        val provider = streamData.providerId?.lowercase() ?: ""

        // 1. Start with relatedVideos from streamData, filtering out episodes of the current show
        val relatedFromStream = streamData.relatedVideos.filter { video ->
            val vTitle = video.title.lowercase()
            !vTitle.contains(baseShowTitle) && !baseShowTitle.contains(vTitle.take(8))
        }

        // 2. Filter feed videos matching provider or channel
        val matchingFeed = feedVideos.filter { video ->
            val vTitle = video.title.lowercase()
            val vProv = video.providerId?.lowercase() ?: ""
            val sameProvider = vProv.isNotEmpty() && vProv == provider
            val notCurrentShow = !vTitle.contains(baseShowTitle) && video.id != streamData.videoId
            sameProvider && notCurrentShow
        }

        val combined = (relatedFromStream + matchingFeed).distinctBy { it.id }
        if (combined.isNotEmpty()) return combined

        return feedVideos.filter { 
            val vTitle = it.title.lowercase()
            !vTitle.contains(baseShowTitle) && it.id != streamData.videoId 
        }.distinctBy { it.id }
    }

    fun getRecommendedContent(streamData: StreamData, feedVideos: List<VideoItem>): List<VideoItem> {
        val streamTitle = streamData.title.lowercase()
        val streamProvider = streamData.providerId?.lowercase() ?: ""
        val baseShowTitle = extractBaseShowTitle(streamData.title).lowercase()

        // Categorize genre
        val isAdult = streamProvider.contains("apijav") || streamProvider.contains("eporner") || streamProvider.contains("porn") || streamTitle.contains("hotwife") || streamTitle.contains("cuck") || streamTitle.contains("sensations") || streamTitle.contains("desires")
        val isAnimeOrAnimation = streamProvider.contains("jikan") || streamProvider.contains("nyaa") || streamTitle.contains("futurama") || streamTitle.contains("anime") || streamTitle.contains("cartoon") || streamTitle.contains("fox") || streamTitle.contains("simpsons")
        val isMovie = streamProvider.contains("yts") || streamProvider.contains("eztv") || streamProvider.contains("tmdb") || streamTitle.contains("spider-man") || streamTitle.contains("movie") || streamTitle.contains("trailer")

        val matchingGenreFeed = feedVideos.filter { video ->
            val vTitle = video.title.lowercase()
            val vProv = video.providerId?.lowercase() ?: ""
            val notCurrentShow = !vTitle.contains(baseShowTitle) && video.id != streamData.videoId

            if (!notCurrentShow) return@filter false

            when {
                isAdult -> vProv.contains("apijav") || vProv.contains("eporner") || vProv.contains("porn") || vTitle.contains("stolen glory") || vTitle.contains("hotwife") || vTitle.contains("cuck")
                isAnimeOrAnimation -> vProv.contains("jikan") || vProv.contains("nyaa") || vTitle.contains("futurama") || vTitle.contains("anime") || vTitle.contains("cartoon") || vTitle.contains("simpsons")
                isMovie -> vProv.contains("yts") || vProv.contains("eztv") || vProv.contains("tmdb") || vTitle.contains("spider-man") || vTitle.contains("movie")
                else -> true
            }
        }

        if (matchingGenreFeed.isNotEmpty()) {
            return matchingGenreFeed.distinctBy { it.id }
        }

        return (streamData.relatedVideos + feedVideos).filter { 
            val vTitle = it.title.lowercase()
            !vTitle.contains(baseShowTitle) && it.id != streamData.videoId 
        }.distinctBy { it.id }
    }

    fun generateComments(streamData: StreamData): List<VideoComment> {
        // Return empty list by default - no fake demo comments
        return emptyList()
    }
}

