package com.example.plugin.sdk.api

import com.example.plugin.sdk.model.*

/**
 * Provider API v1 Contract.
 * Butterfly owns UI, playback (Media3), download manager, history, and persistence.
 * Plugins implement this interface to supply streaming content to Butterfly.
 */
interface ContentProviderApi {

    /**
     * Unique identifier matching manifest.id
     */
    val providerId: String

    /**
     * Provider Capabilities descriptor
     */
    val capabilities: ProviderCapabilities get() = ProviderCapabilities()

    /**
     * Fetch homepage feed / recommendations
     */
    suspend fun home(pageToken: String? = null): PagedResult<PluginVideoItem>

    /**
     * Search for videos or content matching query
     */
    suspend fun search(query: String, pageToken: String? = null): PagedResult<PluginVideoItem>

    /**
     * Retrieve full video metadata for details screen
     */
    suspend fun getVideo(idOrUrl: String): PluginVideoItem

    /**
     * Fetch playable audio/video streams for Media3
     */
    suspend fun getStreams(idOrUrl: String): PluginStreamInfo

    /**
     * Fetch comments for a video
     */
    suspend fun getComments(idOrUrl: String, pageToken: String? = null): PagedResult<PluginComment> = PagedResult(emptyList())

    /**
     * Fetch available subtitles/captions
     */
    suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = emptyList()

    /**
     * Fetch channel profile & video uploads
     */
    suspend fun getChannel(channelIdOrUrl: String): PluginChannel = PluginChannel(id = channelIdOrUrl, name = "Channel")

    /**
     * Fetch playlist content
     */
    suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = PluginPlaylist(id = playlistIdOrUrl, title = "Playlist", uploaderName = "Provider")

    /**
     * Fetch related/recommended videos for a given video
     */
    suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = emptyList()
}
