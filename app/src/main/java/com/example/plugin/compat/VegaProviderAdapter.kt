package com.example.plugin.compat

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.bridge.StorageBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vega Provider API Specifications & Adapter
 *
 * Defines Vega-style provider signatures and translates them into Butterfly ContentProviderApi contracts.
 */

data class VegaProviderContext(
    val baseUrl: String,
    val http: HttpBridge,
    val storage: StorageBridge,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap()
)

data class VegaPostItem(
    val id: String,
    val title: String,
    val link: String,
    val image: String? = null,
    val description: String? = null
)

data class VegaMetaItem(
    val id: String,
    val title: String,
    val type: String? = null,
    val poster: String? = null,
    val year: String? = null,
    val overview: String? = null
)

data class VegaStreamItem(
    val url: String,
    val quality: String? = null,
    val isHls: Boolean = false
)

interface VegaProviderSpec {
    suspend fun catalog(context: VegaProviderContext, page: Int = 1): List<VegaPostItem>
    suspend fun getPosts(context: VegaProviderContext, category: String, page: Int = 1): List<VegaPostItem>
    suspend fun getSearchPosts(context: VegaProviderContext, query: String, page: Int = 1): List<VegaPostItem>
    suspend fun getMeta(context: VegaProviderContext, id: String): VegaMetaItem
    suspend fun getStream(context: VegaProviderContext, id: String): List<VegaStreamItem>
    suspend fun getEpisodes(context: VegaProviderContext, id: String): List<VegaPostItem>
}

class VegaProviderAdapter(
    override val providerId: String,
    private val vegaSpec: VegaProviderSpec,
    private val vegaContext: VegaProviderContext
) : ContentProviderApi {

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val posts = vegaSpec.catalog(vegaContext, page)
        val items = posts.map { post ->
            PluginVideoItem(
                id = post.id,
                title = post.title,
                uploaderName = "Vega Source",
                thumbnailUrl = post.image,
                providerId = providerId
            )
        }
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = items.isNotEmpty())
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val posts = vegaSpec.getSearchPosts(vegaContext, query, page)
        val items = posts.map { post ->
            PluginVideoItem(
                id = post.id,
                title = post.title,
                uploaderName = "Vega Search",
                thumbnailUrl = post.image,
                providerId = providerId
            )
        }
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = items.isNotEmpty())
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val meta = vegaSpec.getMeta(vegaContext, idOrUrl)
        PluginVideoItem(
            id = meta.id,
            title = meta.title,
            uploaderName = meta.type ?: "Vega Meta",
            thumbnailUrl = meta.poster,
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val vegaStreams = vegaSpec.getStream(vegaContext, idOrUrl)
        val videoStreams = vegaStreams.map { s ->
            PluginVideoStream(
                url = s.url,
                qualityLabel = s.quality ?: "720p",
                format = if (s.isHls) "hls" else "mp4"
            )
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: idOrUrl,
            title = "Vega Stream",
            channelName = "Vega Provider",
            videoStreams = videoStreams,
            hlsUrl = vegaStreams.firstOrNull { it.isHls }?.url
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> =
        PagedResult(emptyList())

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> =
        emptyList()

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel =
        PluginChannel(id = channelIdOrUrl, name = "Vega Channel")

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist =
        PluginPlaylist(id = playlistIdOrUrl, title = "Vega Playlist", uploaderName = "Vega")

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> =
        emptyList()
}
