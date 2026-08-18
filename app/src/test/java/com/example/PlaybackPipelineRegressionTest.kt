package com.example

import com.example.extractor.YouTubeExtractorHelper
import com.example.extractor.YtDlpResolver
import com.example.model.PlaybackDecisionResolver
import com.example.model.PlaybackSourceType
import com.example.model.StreamFailureReason
import com.example.model.StreamType
import com.example.plugin.manager.StreamValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackPipelineRegressionTest {

    @Test
    fun testYouTubeInputParsing() {
        val standardUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val parsed1 = YouTubeExtractorHelper.parseYouTubeInput(standardUrl)
        assertTrue(parsed1 is YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (parsed1 as YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        val shortUrl = "https://youtu.be/dQw4w9WgXcQ?t=42"
        val parsed2 = YouTubeExtractorHelper.parseYouTubeInput(shortUrl)
        assertTrue(parsed2 is YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (parsed2 as YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        val shortsUrl = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        val parsed3 = YouTubeExtractorHelper.parseYouTubeInput(shortsUrl)
        assertTrue(parsed3 is YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (parsed3 as YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        val rawId = "dQw4w9WgXcQ"
        val parsed4 = YouTubeExtractorHelper.parseYouTubeInput(rawId)
        assertTrue(parsed4 is YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (parsed4 as YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)
    }

    @Test
    fun testYtDlpSupportedUrlDetection() {
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://vimeo.com/76979871"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.dailymotion.com/video/x7tgad0"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.tiktok.com/@user/video/1234567890"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.twitch.tv/videos/123456789"))
        // Archive.org and Eporner are routed to their own high-speed native extractors
        assertFalse(YtDlpResolver.isYtDlpSupportedUrl("https://archive.org/details/night_of_the_living_dead"))
        assertFalse(YtDlpResolver.isYtDlpSupportedUrl("https://www.eporner.com/video-12345/"))
    }

    @Test
    fun testPlaybackDecisionResolver() {
        val hlsType = PlaybackDecisionResolver.determineSourceType("https://example.com/live/playlist.m3u8", "hls")
        assertEquals(PlaybackSourceType.DIRECT_STREAM, hlsType)

        val dashType = PlaybackDecisionResolver.determineSourceType("https://example.com/manifest.mpd", "dash")
        assertEquals(PlaybackSourceType.DIRECT_STREAM, dashType)

        val mp4Type = PlaybackDecisionResolver.determineSourceType("https://example.com/video.mp4", "mp4")
        assertEquals(PlaybackSourceType.DIRECT_STREAM, mp4Type)

        val magnetType = PlaybackDecisionResolver.determineSourceType("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=test", "torrent")
        assertEquals(PlaybackSourceType.MAGNET, magnetType)

        val embedType = PlaybackDecisionResolver.determineSourceType("https://vidsrc.to/embed/movie/tt1234567", "embed")
        assertEquals(PlaybackSourceType.EMBED_WEBVIEW, embedType)
    }

    @Test
    fun testStreamValidatorRejectionOfEmbedPagesAndBlankUrls() = runBlocking {
        val validator = StreamValidator()

        // Blank URL test
        val blankRes = validator.validateStream("")
        assertFalse(blankRes.isValid)
        assertEquals(StreamFailureReason.NETWORK_ERROR, blankRes.failureReason)

        // Embed page rejection
        val embedRes = validator.validateStream("https://vidsrc.me/embed/movie/tt1375666")
        assertFalse(embedRes.isValid)
        assertEquals(StreamType.EMBED_PAGE, embedRes.streamType)
        assertEquals(StreamFailureReason.INVALID_CONTENT_TYPE, embedRes.failureReason)

        // Invalid magnet link
        val invalidMagnetRes = validator.validateStream("magnet:?dn=MissingInfoHash")
        assertFalse(invalidMagnetRes.isValid)
        assertEquals(StreamFailureReason.INVALID_MAGNET, invalidMagnetRes.failureReason)
    }
}
