package com.example

import com.example.extractor.YtDlpResolver
import com.example.model.PlaybackDecisionResolver
import com.example.model.PlaybackSourceType
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
    fun testPlaybackDecisionResolver() {
        val hlsType = PlaybackDecisionResolver.determineSourceType("https://example.com/live/playlist.m3u8", "hls")
        assertEquals(PlaybackSourceType.DIRECT_STREAM, hlsType)

        val dashType = PlaybackDecisionResolver.determineSourceType("https://example.com/manifest.mpd", "dash")
        assertEquals(PlaybackSourceType.DIRECT_STREAM, dashType)

        val mp4Type = PlaybackDecisionResolver.determineSourceType("https://example.com/video.mp4", "mp4")
        assertEquals(PlaybackSourceType.DIRECT_STREAM, mp4Type)
    }
}
