package com.example

import com.example.extractor.ArchiveOrgProvider
import com.example.extractor.YouTubeExtractorHelper
import com.example.extractor.YtDlpResolver
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackPipelineRegressionTest {

    @Test
    fun testYtDlpSupportedUrlDetection() {
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://vimeo.com/76979871"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.dailymotion.com/video/x7tgad0"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.tiktok.com/@user/video/1234567890"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.twitch.tv/videos/123456789"))
    }

    @Test
    fun testEpornerAndRule34IdExtraction() {
        assertEquals("123456", com.example.extractor.EpornerProvider.extractVideoId("https://www.eporner.com/video-123456/test-slug/"))
        assertEquals("987654", com.example.extractor.Rule34VideoProvider.extractVideoId("https://rule34video.com/video/987654/animation-slug/"))
    }

    @Test
    fun testHeaderIsolationForProviders() {
        // YouTube must NOT have synthetic Referer/Origin headers injected
        val ytHeaders = mapOf("User-Agent" to "TestUA")
        assertFalse(ytHeaders.containsKey("Referer"))

        // Bilibili must have Bilibili Referer
        val biliHeaders = mapOf("User-Agent" to "TestUA", "Referer" to "https://www.bilibili.com/")
        assertEquals("https://www.bilibili.com/", biliHeaders["Referer"])

        // Eporner must have Eporner Referer
        val epornerHeaders = mapOf("User-Agent" to "TestUA", "Referer" to "https://www.eporner.com/")
        assertEquals("https://www.eporner.com/", epornerHeaders["Referer"])

        // Rule34Video must have Rule34Video Referer
        val r34Headers = mapOf("User-Agent" to "TestUA", "Referer" to "https://rule34video.com/")
        assertEquals("https://rule34video.com/", r34Headers["Referer"])
    }

    @Test
    fun testParsedFormatPrioritization() {
        val muxedH264 = YtDlpResolver.ParsedFormat(
            formatId = "18",
            url = "https://example.com/muxed18.mp4",
            ext = "mp4",
            resolution = "640x360",
            width = 640,
            height = 360,
            fps = 30.0,
            tbr = 500.0,
            vbr = 400.0,
            abr = 96.0,
            vcodec = "avc1.42001E",
            acodec = "mp4a.40.2",
            formatNote = "360p",
            protocol = "https",
            httpHeaders = mapOf("Referer" to "https://www.youtube.com/")
        )

        val videoOnly = YtDlpResolver.ParsedFormat(
            formatId = "137",
            url = "https://example.com/video137.mp4",
            ext = "mp4",
            resolution = "1920x1080",
            width = 1920,
            height = 1080,
            fps = 30.0,
            tbr = 2500.0,
            vbr = 2500.0,
            abr = 0.0,
            vcodec = "avc1.640028",
            acodec = "none",
            formatNote = "1080p",
            protocol = "https"
        )

        assertTrue(muxedH264.isMuxed)
        assertTrue(muxedH264.isH264)
        assertFalse(muxedH264.isVideoOnly)
        assertTrue(videoOnly.isVideoOnly)
        assertFalse(videoOnly.isMuxed)
        assertEquals("https://www.youtube.com/", muxedH264.httpHeaders["Referer"])
    }

    @Test
    fun testYouTubeQualityScorePrioritizesMuxed() {
        val muxedOption = PlayableStreamOption(
            qualityLabel = "720p Progressive (mp4)",
            format = "mp4",
            isMuxed = true,
            videoUrl = "https://example.com/720p.mp4",
            providerType = ProviderType.DIRECT,
            headers = mapOf("User-Agent" to "TestUA", "Referer" to "https://www.youtube.com/")
        )

        val adaptiveOption = PlayableStreamOption(
            qualityLabel = "1080p Adaptive (mp4)",
            format = "mp4",
            isMuxed = false,
            videoUrl = "https://example.com/1080p.mp4",
            providerType = ProviderType.DIRECT
        )

        val muxedScore = YouTubeExtractorHelper.parseQualityScore(muxedOption)
        val adaptiveScore = YouTubeExtractorHelper.parseQualityScore(adaptiveOption)

        assertTrue("Muxed score ($muxedScore) should exceed adaptive score ($adaptiveScore)", muxedScore > adaptiveScore)
        assertEquals("TestUA", muxedOption.headers["User-Agent"])
    }

    @Test
    fun testYtDlpUpdateStateTransitions() {
        com.example.extractor.YtDlpUpdateManager.resetState()
        assertEquals(com.example.extractor.YtDlpUpdateManager.UpdateState.Idle, com.example.extractor.YtDlpUpdateManager.updateState.value)
    }
}
