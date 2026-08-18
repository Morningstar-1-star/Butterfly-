package com.example.util

import com.example.model.EpisodeItem
import com.example.model.SeriesSeason
import com.example.model.StreamData

object SeriesDataHelper {
    fun isLikelyTvSeries(streamData: StreamData): Boolean {
        val title = streamData.title.lowercase()
        return title.contains("s0") || title.contains("season") || title.contains("episode") || title.contains("e0")
    }

    fun generateSeasonsAndEpisodes(streamData: StreamData): List<SeriesSeason> {
        val ep = EpisodeItem(
            id = streamData.videoId,
            seasonNumber = 1,
            episodeNumber = 1,
            title = streamData.title,
            thumbnailUrl = streamData.thumbnailUrl,
            providerId = streamData.providerId
        )
        return listOf(
            SeriesSeason(
                seasonNumber = 1,
                seasonName = "Season 1",
                episodes = listOf(ep)
            )
        )
    }
}
