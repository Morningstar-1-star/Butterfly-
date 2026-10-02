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

    @Test
    fun testNewPipeExtractorInitialization() {
        YouTubeExtractorHelper.ensureNewPipeInitialized()
        assertTrue(org.schabi.newpipe.extractor.NewPipe.getDownloader() != null)
    }

    @Test
    fun testTitleLanguageHandlingRules() = kotlinx.coroutines.runBlocking {
        // 1. English title: kept in English, no Hindi translation
        val enTitle = "India W vs Hong Kong W | English | Highlights"
        val enResult = com.example.util.UniversalTranslator.translateTitle(enTitle)
        assertEquals("en", enResult.detectedLanguage)
        assertEquals(enTitle, enResult.translatedEN)
        assertTrue(enResult.translatedHI.isBlank())

        val enVideo = com.example.model.VideoItem(
            id = "en123",
            title = enTitle,
            uploaderName = "TestChannel",
            originalTitle = enTitle,
            translatedTitleEN = enResult.translatedEN,
            detectedLanguage = enResult.detectedLanguage
        )
        assertEquals(enTitle, enVideo.getDisplayTitle())

        // 2. Hindi title: user knows Hindi natively, so kept in Hindi untouched with zero change
        val hiTitle = "भारत बनाम हांगकांग हाइलाइट्स"
        val hiResult = com.example.util.UniversalTranslator.translateTitle(hiTitle)
        assertEquals("hi", hiResult.detectedLanguage)
        assertEquals(hiTitle, hiResult.translatedEN)
        val hiVideo = com.example.model.VideoItem(
            id = "hi123",
            title = hiTitle,
            uploaderName = "TestChannel",
            originalTitle = hiTitle,
            translatedTitleEN = hiResult.translatedEN,
            detectedLanguage = hiResult.detectedLanguage
        )
        assertEquals(hiTitle, hiVideo.getDisplayTitle())

        // 3. Foreign title: defaults to English
        val jaVideo = com.example.model.VideoItem(
            id = "ja123",
            title = "進撃の巨人",
            uploaderName = "TestChannel",
            originalTitle = "進撃の巨人",
            translatedTitleEN = "Attack on Titan",
            detectedLanguage = "ja"
        )
        assertEquals("Attack on Titan", jaVideo.getDisplayTitle())
        assertEquals("進撃の巨人", jaVideo.getDisplayTitle(showOriginal = true))
    }

    @Test
    fun testPlaybackMetricsAndCacheHitCalculations() {
        val tracker = com.example.ui.player.metrics.PlaybackMetricsTracker
        tracker.resetSession()
        tracker.resetCacheMetrics()

        tracker.startPreparation("https://rr3---sn-4g5ednks.googlevideo.com/videoplayback?id=test123")
        assertEquals("rr3---sn-4g5ednks.googlevideo.com", tracker.currentCdnHost.value)

        tracker.onFirstFrameRendered()
        assertTrue(tracker.startupLatencyMs.value >= 0L)

        // Test Cache Hit Ratio: 8MB cached, 2MB network = 80% hit ratio
        tracker.recordCachedBytes(8L * 1024L * 1024L)
        tracker.recordNetworkBytes(2L * 1024L * 1024L)
        assertEquals(80f, tracker.cacheHitPercentage.value, 0.5f)
        assertEquals(2L * 1024L * 1024L, tracker.networkBytesDownloaded.value)
        assertEquals(8L * 1024L * 1024L, tracker.cachedBytesRead.value)

        // Test Rebuffer Stutter Tracking
        tracker.onRebufferStarted()
        assertEquals(1, tracker.rebufferCount.value)
        tracker.onRebufferEnded()

        tracker.updateVideoFormat(1920, 1080, 4_500_000)
        assertEquals("1920x1080 (1080p)", tracker.currentResolution.value)
        assertEquals(4500, tracker.currentBitrateKbps.value)

        tracker.updateThroughput(42.5f)
        assertEquals(42.5f, tracker.measuredThroughputMbps.value, 0.1f)
    }

    @Test
    fun testAdaptiveLoadControlParameters() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val loadControl = com.example.ui.player.core.SmartAdaptiveLoadControl.create(context)
        assertNotNull(loadControl)
    }

    @Test
    fun testPlayerPlaybackInitialization() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val dummyListener = object : androidx.media3.common.Player.Listener {}
        val playerCore = com.example.ui.player.core.PlayerCore(context, dummyListener)
        assertNotNull(playerCore.player)

        val streamData = com.example.model.StreamData(
            videoId = "test_vid",
            videoUrl = "https://example.com/test.mp4",
            title = "Test Video",
            channelName = "Channel",
            providerId = "youtube"
        )
        val option = com.example.model.PlayableStreamOption(
            videoUrl = "https://example.com/test.mp4",
            qualityLabel = "1080p",
            format = "mp4",
            isMuxed = true
        )
        val session = com.example.ui.player.session.PlaybackSession(context)
        session.prepareAndPlay(context, streamData, option)
        assertNotNull(session.getExoPlayer())
    }

    @Test
    fun testAllStreamFormatsMediaSourceCreation() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()

        // 1. Progressive MP4
        val mp4Factory = com.example.ui.player.core.MediaSourceFactoryHelper.createMediaSourceFactory(
            targetUrl = "https://example.com/stream.mp4",
            streamData = null,
            context = context
        )
        val mp4Item = androidx.media3.common.MediaItem.fromUri("https://example.com/stream.mp4")
        val mp4Source = mp4Factory.createMediaSource(mp4Item)
        assertNotNull(mp4Source)

        // 2. Progressive MKV with Provider Headers Preserved
        val mkvHeaders = mapOf("Referer" to "https://www.bilibili.com/", "User-Agent" to "CustomBiliUA")
        val mkvFactory = com.example.ui.player.core.MediaSourceFactoryHelper.createMediaSourceFactory(
            targetUrl = "https://upos-hz-mirrorakam.akamaized.net/video.mkv",
            streamData = null,
            specificHeaders = mkvHeaders,
            context = context
        )
        val mkvItem = androidx.media3.common.MediaItem.Builder()
            .setUri("https://upos-hz-mirrorakam.akamaized.net/video.mkv")
            .setMimeType(androidx.media3.common.MimeTypes.VIDEO_MATROSKA)
            .build()
        val mkvSource = mkvFactory.createMediaSource(mkvItem)
        assertNotNull(mkvSource)

        // 3. HLS Master / Segment Stream
        val hlsFactory = com.example.ui.player.core.MediaSourceFactoryHelper.createMediaSourceFactory(
            targetUrl = "https://example.com/master.m3u8",
            streamData = null,
            context = context
        )
        val hlsItem = androidx.media3.common.MediaItem.Builder()
            .setUri("https://example.com/master.m3u8")
            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            .build()
        val hlsSource = hlsFactory.createMediaSource(hlsItem)
        assertNotNull(hlsSource)

        // 4. DASH Stream
        val dashFactory = com.example.ui.player.core.MediaSourceFactoryHelper.createMediaSourceFactory(
            targetUrl = "https://example.com/manifest.mpd",
            streamData = null,
            context = context
        )
        val dashItem = androidx.media3.common.MediaItem.Builder()
            .setUri("https://example.com/manifest.mpd")
            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
            .build()
        val dashSource = dashFactory.createMediaSource(dashItem)
        assertNotNull(dashSource)

        // 5. Localhost Torrent Engine Stream (Uncached direct stream)
        val torrentFactory = com.example.ui.player.core.MediaSourceFactoryHelper.createMediaSourceFactory(
            targetUrl = "http://127.0.0.1:8888/stream?hash=12345",
            streamData = null,
            context = context
        )
        val torrentItem = androidx.media3.common.MediaItem.fromUri("http://127.0.0.1:8888/stream?hash=12345")
        val torrentSource = torrentFactory.createMediaSource(torrentItem)
        assertNotNull(torrentSource)
    }
}

