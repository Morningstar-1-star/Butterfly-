package com.example.torrent.provider

import com.example.torrent.model.TorrentRelease
import com.example.torrent.model.TorrentResult

data class MediaIdentity(
    val title: String,
    val year: String? = null,
    val mediaType: String? = "movie", // "movie", "tv", "anime", "jav"
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val rawQuery: String? = null
)

interface TorrentProvider {
    val id: String
    val name: String
    val isEnabled: Boolean
    suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult>
}
