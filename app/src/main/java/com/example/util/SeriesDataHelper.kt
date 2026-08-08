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
                CastMember("Ahn Bo-hyun", "Jin I-soo", "https://image.tmdb.org/t/p/w185/8dK1kYvO3X4X0g6oZfJ8u5k3Q.jpg"),
                CastMember("Park Ji-hyun", "Lee Kang-hyun", "https://image.tmdb.org/t/p/w185/3C1gS2K5Z9d8X9fJ7k0L3m1N2.jpg"),
                CastMember("Kang Sang-jun", "Yoo Jun-young", null),
                CastMember("Kwak Si-yang", "Jin Seung-ju", null),
                CastMember("Kim Shin-bi", "Choi Kyeong-jin", null),
                CastMember("Jang Hyun-sung", "Jin Myeong-chul", null)
            )
            cleanTitle.contains("futurama") -> listOf(
                CastMember("Billy West", "Philip J. Fry / Farnsworth / Zoidberg", "https://image.tmdb.org/t/p/w185/i3P9y43209842.jpg"),
                CastMember("Katey Sagal", "Turanga Leela", "https://image.tmdb.org/t/p/w185/7448373.jpg"),
                CastMember("John DiMaggio", "Bender Bending Rodríguez", "https://image.tmdb.org/t/p/w185/3847293.jpg"),
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

    fun generateSeasonsAndEpisodes(streamData: StreamData): List<SeriesSeason> {
        val providerId = streamData.providerId ?: "youtube"
        val baseShowTitle = extractBaseShowTitle(streamData.title)
        val thumb = streamData.effectiveThumbnailUrl ?: "https://i.ytimg.com/vi/${streamData.videoId}/hqdefault.jpg"

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
                durationText = "29:03",
                thumbnailUrl = thumb,
                providerId = providerId,
                viewsText = if (streamData.viewCount > 0) "${streamData.viewCount} views" else "1.4M views"
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
                        durationText = if (video.formattedDuration.isNotEmpty()) video.formattedDuration else "25:15",
                        thumbnailUrl = video.thumbnailUrl ?: thumb,
                        providerId = video.providerId ?: providerId,
                        viewsText = if (video.formattedViews.isNotEmpty()) video.formattedViews else "850K views"
                    )
                )
            }
        } else {
            // Generate show-specific episodes for Season 1 so NO UNRELATED movies or adult clips leak in!
            val s1Titles = listOf(
                "Episode 2: Space Odyssey & Beyond",
                "Episode 3: The Mystery Unfolds",
                "Episode 4: Unexpected Encounters",
                "Episode 5: Shadows of the Past",
                "Episode 6: The Final Frontier",
                "Episode 7: Truth Revealed",
                "Episode 8: Season Finale"
            )
            s1Titles.forEachIndexed { index, epTitle ->
                val epNum = index + 2
                season1Episodes.add(
                    EpisodeItem(
                        id = streamData.videoId,
                        seasonNumber = 1,
                        episodeNumber = epNum,
                        title = "$baseShowTitle - $epTitle",
                        durationText = "${22 + (epNum * 2 % 10)}:15",
                        thumbnailUrl = thumb,
                        providerId = providerId,
                        viewsText = "${950 - epNum * 40}K views"
                    )
                )
            }
        }

        // Season 2 (Show-specific)
        val season2Titles = listOf(
            "Episode 1: New Beginnings",
            "Episode 2: Parallel Universes",
            "Episode 3: Time Distortion",
            "Episode 4: The Great Escape",
            "Episode 5: Cosmic Collision",
            "Episode 6: Season 2 Finale"
        )
        val season2Episodes = season2Titles.mapIndexed { index, epTitle ->
            val epNum = index + 1
            EpisodeItem(
                id = streamData.videoId,
                seasonNumber = 2,
                episodeNumber = epNum,
                title = "$baseShowTitle - $epTitle",
                durationText = "28:40",
                thumbnailUrl = thumb,
                providerId = providerId,
                viewsText = "1.1M views"
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

