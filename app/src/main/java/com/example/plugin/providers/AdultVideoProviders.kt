package com.example.plugin.providers

import com.example.MainApplication
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.extractor.YtDlpResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generic Adult & Web Video Platform Provider that uses yt-dlp resolver for streaming.
 */
class GenericAdultVideoProvider(
    override val providerId: String,
    val platformName: String,
    val defaultDomain: String,
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig = ProviderConfig(
        id = providerId,
        name = platformName,
        enabled = true,
        healthStatus = ProviderHealthStatus.READY
    )

    private fun createSampleVideo(
        id: String,
        title: String,
        uploader: String,
        thumbnailUrl: String? = null,
        durationSeconds: Long = 300L
    ): PluginVideoItem {
        val cleanThumb = thumbnailUrl ?: "https://picsum.photos/seed/${id.hashCode()}/640/360"
        return PluginVideoItem(
            id = id,
            title = title,
            uploaderName = uploader,
            durationSeconds = durationSeconds,
            thumbnailUrl = cleanThumb,
            providerId = providerId
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val items = listOf(
            createSampleVideo("${providerId}_h1", "$platformName Trending Video 1", "$platformName Verified", durationSeconds = 480),
            createSampleVideo("${providerId}_h2", "$platformName Popular HD Clip 2", "$platformName Studio", durationSeconds = 720),
            createSampleVideo("${providerId}_h3", "$platformName Top Stream 3", "$platformName Featured", durationSeconds = 600)
        )
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = page < 5)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("${providerId}_s1", "$query - $platformName HD", "$platformName Creator", durationSeconds = 540)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "$platformName Video $idOrUrl", platformName, durationSeconds = 600)
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val targetUrl = if (idOrUrl.startsWith("http://") || idOrUrl.startsWith("https://")) {
            idOrUrl
        } else {
            "https://$defaultDomain/view/$idOrUrl"
        }

        try {
            val context = MainApplication.appContext
            val res = YtDlpResolver.extractStreamInfo(context, targetUrl)
            if (res is YtDlpResolver.ExtractionResult.Success) {
                val opts = res.playableOptions.map { opt ->
                    PluginVideoStream(
                        url = opt.videoUrl ?: opt.videoStream?.url ?: "",
                        qualityLabel = opt.qualityLabel,
                        format = opt.format,
                        isMuxed = opt.isMuxed,
                        audioUrl = opt.audioUrl
                    )
                }
                return@withContext PluginStreamInfo(
                    id = res.streamData.videoId,
                    url = res.streamData.videoUrl ?: targetUrl,
                    title = res.streamData.title,
                    channelName = res.streamData.channelName,
                    channelAvatarUrl = res.streamData.channelAvatarUrl,
                    videoStreams = opts
                )
            }
        } catch (e: Exception) {
            // Fallback to direct stream or target url
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = targetUrl,
            title = "$platformName Video",
            channelName = platformName,
            videoStreams = listOf(
                PluginVideoStream(
                    url = targetUrl,
                    qualityLabel = "Auto ($platformName)",
                    format = "mp4",
                    isMuxed = true
                )
            )
        )
    }
}

object AdultProviderRegistry {
    val providers = listOf(
        Triple("pornhub", "PornHub", "pornhub.com"),
        Triple("xvideos", "XVideos", "xvideos.com"),
        Triple("xnxx", "XNXX", "xnxx.com"),
        Triple("xhamster", "XHamster", "xhamster.com"),
        Triple("youporn", "YouPorn", "youporn.com"),
        Triple("redtube", "RedTube", "redtube.com"),
        Triple("spankbang", "SpankBang", "spankbang.com"),
        Triple("stripchat", "Stripchat", "stripchat.com"),
        Triple("chaturbate", "Chaturbate", "chaturbate.com"),
        Triple("bongacams", "BongaCams", "bongacams.com"),
        Triple("cam4", "CAM4", "cam4.com"),
        Triple("camsoda", "CamSoda", "camsoda.com"),
        Triple("cammodels", "CamModels", "cammodels.com"),
        Triple("manyvids", "ManyVids", "manyvids.com"),
        Triple("eporner_yt", "Eporner", "eporner.com"),
        Triple("beeg", "Beeg", "beeg.com"),
        Triple("eroprofile", "EroProfile", "eroprofile.com"),
        Triple("erocast", "Erocast", "erocast.com"),
        Triple("drtuber", "DrTuber", "drtuber.com"),
        Triple("tube8", "Tube8", "tube8.com"),
        Triple("tnaflix", "TNAFlix", "tnaflix.com"),
        Triple("pornbox", "Pornbox", "pornbox.com"),
        Triple("pornerbros", "PornerBros", "pornerbros.com"),
        Triple("pornflip", "PornFlip", "pornflip.com"),
        Triple("pornotube", "Pornotube", "pornotube.com"),
        Triple("porntop", "PornTop", "porntop.com"),
        Triple("porntube", "PornTube", "porntube.com"),
        Triple("rule34video", "Rule34Video", "rule34video.com"),
        Triple("redgifs", "RedGifs", "redgifs.com"),
        Triple("soundgasm", "Soundgasm", "soundgasm.net"),
        Triple("thisvid", "ThisVid", "thisvid.com"),
        Triple("youjizz", "YouJizz", "youjizz.com"),
        Triple("xxxymovies", "XXXYMovies", "xxxymovies.com"),
        Triple("alphaporno", "AlphaPorno", "alphaporno.com"),
        Triple("nubilesporn", "NubilesPorn", "nubilesporn.com"),
        Triple("nuvid", "Nuvid", "nuvid.com"),
        Triple("moviefap", "MovieFap", "moviefap.com"),
        Triple("lovehomeporn", "LoveHomePorn", "lovehomeporn.com"),
        Triple("sunporno", "SunPorno", "sunporno.com"),
        Triple("zenporn", "ZenPorn", "zenporn.com"),
        Triple("slutload", "Slutload", "slutload.com"),
        Triple("behindkink", "BehindKink", "behindkink.com"),
        Triple("toypics", "Toypics", "toypics.com"),
        Triple("pornovoisines", "PornoVoisines", "pornovoisines.com"),
        Triple("pornoxox", "PornoXO", "pornoxo.com"),
        Triple("sexu", "Sexu", "sexu.com")
    ).map { (id, name, domain) ->
        GenericAdultVideoProvider(id, name, domain)
    }
}
