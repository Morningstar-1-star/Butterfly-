package com.example.torrent.provider

import com.example.torrent.model.TorrentRelease

data class MediaIdentity(
    val title: String,
    val year: String? = null,
    val mediaType: String? = "movie", // "movie", "tv", "anime"
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null
)

interface TorrentProvider {
    val id: String
    val name: String
    val isEnabled: Boolean
    suspend fun search(query: String, identity: MediaIdentity): List<TorrentRelease>
}
