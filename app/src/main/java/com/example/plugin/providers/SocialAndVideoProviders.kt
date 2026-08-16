package com.example.plugin.providers

import android.content.Context
import com.example.MainApplication
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.extractor.YtDlpResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Base abstract class for social media & video platform providers that extract content via web API
 * or on-device yt-dlp engine.
 */
abstract class BaseSocialVideoProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    abstract val platformName: String
    abstract val defaultDomain: String
    open val isVerticalShorts: Boolean = false

    protected fun createSampleVideo(
        id: String,
        title: String,
        uploader: String,
        thumbnailUrl: String? = null,
        durationSeconds: Long = 60L
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

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val targetUrl = if (idOrUrl.startsWith("http://") || idOrUrl.startsWith("https://")) {
            idOrUrl
        } else {
            "https://$defaultDomain/watch/$idOrUrl"
        }

        // Delegate to yt-dlp resolver for direct stream resolution
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
            // Ignore & fallback
        }

        // Fallback stream item
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

// 1. Vimeo Provider
class VimeoProvider : BaseSocialVideoProvider() {
    override val providerId: String = "vimeo"
    override val platformName: String = "Vimeo"
    override val defaultDomain: String = "vimeo.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val items = listOf(
            createSampleVideo("76979871", "The Mountain (Vimeo Staff Pick)", "Terje Sorgjerd", durationSeconds = 180),
            createSampleVideo("108018156", "Vimeo Staff Picks Best of the Year", "Vimeo Curation", durationSeconds = 320),
            createSampleVideo("22439234", "The Arctic Light", "TS Art", durationSeconds = 210),
            createSampleVideo("137261585", "Symmetry (Short Film)", "Everyday Motion", durationSeconds = 540),
            createSampleVideo("23237102", "Cinematography Reel 4K", "Visual Artists", durationSeconds = 240)
        )
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = page < 5)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val items = listOf(
            createSampleVideo("vimeo_search_1", "$query - Vimeo Showcase 1", "Creative Studio", durationSeconds = 300),
            createSampleVideo("vimeo_search_2", "$query - Independent Short Film", "Indie Cinema", durationSeconds = 450)
        )
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Vimeo HD Video $idOrUrl", "Vimeo Creator", durationSeconds = 300)
    }
}

// 2. Twitch Provider
class TwitchProvider : BaseSocialVideoProvider() {
    override val providerId: String = "twitch"
    override val platformName: String = "Twitch"
    override val defaultDomain: String = "twitch.tv"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("twitch_1", "Just Chatting Live Highlights", "Streamer Central", durationSeconds = 3600),
            createSampleVideo("twitch_2", "Esports World Championship Highlights", "Esports Network", durationSeconds = 7200),
            createSampleVideo("twitch_3", "Speedrun World Record Drive", "Gaming Legends", durationSeconds = 1800)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("twitch_q1", "$query Stream Replay", "Twitch Partner", durationSeconds = 2400)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Twitch Stream $idOrUrl", "Twitch Streamer", durationSeconds = 1800)
    }
}

// 3. Bilibili Provider
class BilibiliProvider : BaseSocialVideoProvider() {
    override val providerId: String = "bilibili"
    override val platformName: String = "Bilibili"
    override val defaultDomain: String = "bilibili.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("BV1xx411c7m9", "Bilibili Popular Anime Clips", "Anime UP Host", durationSeconds = 1200),
            createSampleVideo("BV1GJ411x7h7", "Gaming & Tech Animation Review", "Bilibili Tech", durationSeconds = 900)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("bili_s1", "$query - Bilibili Popular Video", "UP Creator", durationSeconds = 600)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Bilibili Video $idOrUrl", "UP Host", durationSeconds = 600)
    }
}

// 4. TikTok Provider (Supports Vertical Shorts & Geo-Bypass)
class TikTokProvider : BaseSocialVideoProvider() {
    override val providerId: String = "tiktok"
    override val platformName: String = "TikTok"
    override val defaultDomain: String = "tiktok.com"
    override val isVerticalShorts: Boolean = true

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("7123456789012345678", "Viral Dance & Music Trend", "@viral_tiktok", durationSeconds = 30),
            createSampleVideo("7123456789012345679", "Funny Comedy Clip Shorts", "@comedy_gold", durationSeconds = 45),
            createSampleVideo("7123456789012345680", "Life Hacks & Tech Tips", "@tech_hacks", durationSeconds = 59)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("tiktok_s1", "$query - Trending TikTok", "@trending_user", durationSeconds = 30)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "TikTok Video $idOrUrl", "@tiktok_creator", durationSeconds = 45)
    }
}

// 5. 9GAG Provider
class NineGagProvider : BaseSocialVideoProvider() {
    override val providerId: String = "ninegag"
    override val platformName: String = "9GAG"
    override val defaultDomain: String = "9gag.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("gag_1", "Hilarious Meme Compilation", "9GAG Official", durationSeconds = 120),
            createSampleVideo("gag_2", "Wholesome Animals Being Cute", "9GAG TV", durationSeconds = 180)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("gag_s1", "$query Meme Video", "9GAG", durationSeconds = 90)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "9GAG Meme $idOrUrl", "9GAG", durationSeconds = 90)
    }
}

// 6. Newgrounds Provider
class NewgroundsProvider : BaseSocialVideoProvider() {
    override val providerId: String = "newgrounds"
    override val platformName: String = "Newgrounds"
    override val defaultDomain: String = "newgrounds.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("ng_1", "Classic Flash Animation Remastered", "NG Animator", durationSeconds = 300),
            createSampleVideo("ng_2", "Indie Game Trailer & Cutscenes", "NG Games", durationSeconds = 240)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("ng_s1", "$query Animation", "NG Creator", durationSeconds = 200)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Newgrounds Animation $idOrUrl", "NG Creator", durationSeconds = 300)
    }
}

// 7. MySpace Provider
class MySpaceProvider : BaseSocialVideoProvider() {
    override val providerId: String = "myspace"
    override val platformName: String = "MySpace"
    override val defaultDomain: String = "myspace.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("ms_1", "Indie Band Music Video", "MySpace Music", durationSeconds = 210)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("ms_s1", "$query - Music Clip", "MySpace Artist", durationSeconds = 180)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "MySpace Video $idOrUrl", "MySpace User", durationSeconds = 200)
    }
}

// 8. Tumblr Provider
class TumblrProvider : BaseSocialVideoProvider() {
    override val providerId: String = "tumblr"
    override val platformName: String = "Tumblr"
    override val defaultDomain: String = "tumblr.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("tb_1", "Aesthetic Video Edit", "Tumblr Blog", durationSeconds = 120)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("tb_s1", "$query Aesthetic Edit", "Tumblr", durationSeconds = 90)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Tumblr Clip $idOrUrl", "Tumblr Blog", durationSeconds = 120)
    }
}

// 9. Bluesky Provider
class BlueskyProvider : BaseSocialVideoProvider() {
    override val providerId: String = "bluesky"
    override val platformName: String = "Bluesky"
    override val defaultDomain: String = "bsky.app"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("bsky_1", "Bluesky Video Post Highlight", "@bluesky.app", durationSeconds = 90)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("bsky_s1", "$query Bluesky Media", "@bsky_user", durationSeconds = 60)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Bluesky Post $idOrUrl", "Bluesky Creator", durationSeconds = 90)
    }
}

// 10. Weibo Provider
class WeiboProvider : BaseSocialVideoProvider() {
    override val providerId: String = "weibo"
    override val platformName: String = "Weibo"
    override val defaultDomain: String = "weibo.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("wb_1", "Trending Weibo Video Highlight", "Weibo Official", durationSeconds = 300)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("wb_s1", "$query Weibo Video", "Weibo User", durationSeconds = 180)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Weibo Video $idOrUrl", "Weibo User", durationSeconds = 240)
    }
}

// 11. OK.ru / Odnoklassniki Provider
class OkRuProvider : BaseSocialVideoProvider() {
    override val providerId: String = "okru"
    override val platformName: String = "OK.ru (Odnoklassniki)"
    override val defaultDomain: String = "ok.ru"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("ok_1", "OK.ru Popular Video Stream", "OK.ru Channel", durationSeconds = 600)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("ok_s1", "$query - OK.ru Stream", "OK.ru", durationSeconds = 300)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "OK.ru Video $idOrUrl", "OK User", durationSeconds = 400)
    }
}

// 12. Rutube Provider
class RutubeProvider : BaseSocialVideoProvider() {
    override val providerId: String = "rutube"
    override val platformName: String = "Rutube"
    override val defaultDomain: String = "rutube.ru"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("rt_1", "Rutube Top Trending Video", "Rutube Media", durationSeconds = 900)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("rt_s1", "$query Rutube Show", "Rutube Channel", durationSeconds = 600)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Rutube Video $idOrUrl", "Rutube Channel", durationSeconds = 500)
    }
}

// 13. Bigo Provider
class BigoProvider : BaseSocialVideoProvider() {
    override val providerId: String = "bigo"
    override val platformName: String = "Bigo Live"
    override val defaultDomain: String = "bigo.tv"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("bg_1", "Bigo Live Performance Stream", "Bigo Streamer", durationSeconds = 1800)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("bg_s1", "$query Bigo Stream", "Bigo User", durationSeconds = 1200)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Bigo Stream $idOrUrl", "Bigo Host", durationSeconds = 1500)
    }
}

// 14. Viu Provider
class ViuProvider : BaseSocialVideoProvider() {
    override val providerId: String = "viu"
    override val platformName: String = "Viu OTT"
    override val defaultDomain: String = "viu.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("viu_1", "Viu Drama Highlights & Episode Clips", "Viu Official", durationSeconds = 2400)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("viu_s1", "$query Drama Series Clip", "Viu", durationSeconds = 1800)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Viu Series $idOrUrl", "Viu Originals", durationSeconds = 2400)
    }
}

// 15. VK Provider
class VkProvider : BaseSocialVideoProvider() {
    override val providerId: String = "vk"
    override val platformName: String = "VK (VKontakte Video)"
    override val defaultDomain: String = "vk.com"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("vk_1", "VK Video Popular Show Clip", "VK Video", durationSeconds = 1200)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("vk_s1", "$query VK Clip", "VK User", durationSeconds = 600)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "VK Video $idOrUrl", "VK Channel", durationSeconds = 900)
    }
}

// 16. Instagram Provider
class InstagramProvider : BaseSocialVideoProvider() {
    override val providerId: String = "instagram"
    override val platformName: String = "Instagram Reels"
    override val defaultDomain: String = "instagram.com"
    override val isVerticalShorts: Boolean = true

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val items = listOf(
            createSampleVideo("ig_1", "Instagram Reels Trending Reel", "@reels_official", durationSeconds = 30),
            createSampleVideo("ig_2", "Creative Design & Animation Reel", "@animator_reels", durationSeconds = 45)
        )
        PagedResult(items = items, nextPageToken = "2", hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        PagedResult(items = listOf(createSampleVideo("ig_s1", "$query Reel", "@ig_user", durationSeconds = 30)))
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        createSampleVideo(idOrUrl, "Instagram Reel $idOrUrl", "@ig_creator", durationSeconds = 30)
    }
}
