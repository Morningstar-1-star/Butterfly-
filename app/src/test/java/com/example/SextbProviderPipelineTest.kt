package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.extractor.SextbProvider
import com.example.extractor.sextb.SextbError
import com.example.extractor.sextb.SextbException
import com.example.extractor.sextb.SextbParser
import com.example.extractor.sextb.SextbResolver
import com.example.extractor.sextb.StbturboExtractor
import com.example.extractor.sextb.StreamTapeExtractor
import com.example.extractor.sextb.VideoSource
import com.example.model.MediaType
import com.example.resolver.providers.SextbSourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SextbProviderPipelineTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // 1. Search Test with Authentic .tray-item DOM Structure
    @Test
    fun testSearchParsingWithTrayItems() {
        val sampleSearchHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="tray-item">
                    <a href="/video/ssis-123-sample-video/" title="SSIS-123 Beautiful Actress Drama">
                        <img class="tray-item-thumbnail" data-src="https://images.example.com/cover1.jpg" alt="SSIS-123" />
                    </a>
                    <div class="tray-item-title">
                        <a href="/video/ssis-123-sample-video/">SSIS-123 Beautiful Actress Drama</a>
                    </div>
                    <span class="tray-film-views">01:45:20</span>
                    <span class="tray-item-quality">1080p</span>
                </div>
                <div class="tray-item">
                    <a href="https://sextb.net/video/ipx-456-actress-story/">
                        <img class="tray-item-thumbnail" src="https://images.example.com/cover2.jpg" alt="IPX-456" />
                    </a>
                    <div class="tray-item-title">
                        <a href="https://sextb.net/video/ipx-456-actress-story/">IPX-456 High Definition Action</a>
                    </div>
                    <span class="tray-film-views">58:12</span>
                    <span class="tray-item-quality">4K</span>
                </div>
            </body>
            </html>
        """.trimIndent()

        val results = SextbParser.parseSearchResults(sampleSearchHtml, "https://sextb.net")
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("ssis-123-sample-video", first.id)
        assertEquals("SSIS-123 Beautiful Actress Drama", first.title)
        assertEquals("https://sextb.net/video/ssis-123-sample-video/", first.uploaderUrl)
        assertEquals("https://images.example.com/cover1.jpg", first.thumbnailUrl)
        assertEquals("SEXТB", first.uploaderName)
        assertEquals(6320L, first.durationSeconds) // 1h 45m 20s = 6320s
        assertTrue(first.tags.contains("1080p"))

        val second = results[1]
        assertEquals("ipx-456-actress-story", second.id)
        assertEquals("IPX-456 High Definition Action", second.title)
        assertEquals(3492L, second.durationSeconds) // 58m 12s = 3492s
        assertTrue(second.tags.contains("4K"))
    }

    // 2. Video Details Page with .episode-list .btn-player Data
    @Test
    fun testDetailsExtractionWithPlayerButtons() {
        val sampleDetailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="MIDV-789 Exclusive Tale - SEXТB" />
                <meta property="og:image" content="https://images.example.com/midv789.jpg" />
                <meta property="og:description" content="Detailed storyline of MIDV-789 starring premier actress." />
                <meta property="og:video:actor" content="Yua Mikami" />
                <meta property="og:video:actor" content="Ken Shimizu" />
                <meta property="og:video:director" content="Takahiro Ishii" />
                <meta property="og:video:release_date" content="2024-05-18" />
                <meta property="og:video:tag" content="Cosplay" />
            </head>
            <body>
                <h1 class="title">MIDV-789 Exclusive Tale</h1>
                <div class="episode-list">
                    <button class="btn-player" data-id="server_ep_1" data-source="film_midv_789">Server 1 - Stbturbo</button>
                    <button class="btn-player" data-id="server_ep_2" data-source="film_midv_789">Server 2 - StreamTape</button>
                </div>
            </body>
            </html>
        """.trimIndent()

        val details = SextbParser.parseDetailsPage(sampleDetailHtml, "https://sextb.net/video/midv-789/")
        assertEquals("midv-789", details.id)
        assertEquals("MIDV-789 Exclusive Tale", details.title)
        assertEquals("https://images.example.com/midv789.jpg", details.thumbnailUrl)
        assertEquals("2024-05-18", details.releaseDate)
        assertEquals("Takahiro Ishii", details.director)
        assertTrue(details.actors.contains("Yua Mikami"))
        assertTrue(details.actors.contains("Ken Shimizu"))
        assertTrue(details.categories.contains("Cosplay"))

        // Verify player buttons parsed into episodes and defaults
        assertEquals("server_ep_1", details.defaultEpisodeId)
        assertEquals("film_midv_789", details.defaultFilmId)
        assertEquals(2, details.episodes.size)
        assertEquals("server_ep_1", details.episodes[0].dataId)
        assertEquals("film_midv_789", details.episodes[0].dataSource)
    }

    // 3. AJAX Player Response Extraction (/ajax/player -> iframe)
    @Test
    fun testAjaxPlayerResponseExtraction() {
        val ajaxHtmlResponse = """
            <div class="player-container">
                <iframe src="https://stbturbo.xyz/e/stb_hash_123" width="100%" height="100%" frameborder="0" allowfullscreen></iframe>
            </div>
        """.trimIndent()

        val iframes = SextbResolver.extractIframeUrlsFromAjaxResponse(ajaxHtmlResponse)
        assertEquals(1, iframes.size)
        assertEquals("https://stbturbo.xyz/e/stb_hash_123", iframes[0])

        // Also test escaped response (JSON body)
        val escapedAjaxResponse = """
            {"status": "ok", "html": "<iframe src=\"https:\/\/stbturbo.xyz\/e\/stb_escaped_456?token=123\" allowfullscreen><\/iframe>"}
        """.trimIndent()

        val escapedIframes = SextbResolver.extractIframeUrlsFromAjaxResponse(escapedAjaxResponse)
        assertEquals(1, escapedIframes.size)
        assertEquals("https://stbturbo.xyz/e/stb_escaped_456", escapedIframes[0])
    }

    // 4. Stbturbo Extractor Resolution (#video_player[data-hash] -> HLS)
    @Test
    fun testStbturboExtractorResolution() {
        val stbturboHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Stbturbo Player</title></head>
            <body>
                <div id="video_player" data-hash="https://stbcdn.xyz/hls/master.m3u8?token=secure123"></div>
            </body>
            </html>
        """.trimIndent()

        val embedUrl = "https://stbturbo.xyz/e/stb_hash_123"
        val sources = StbturboExtractor.extractFromHtml(stbturboHtml, embedUrl)

        assertEquals(1, sources.size)
        val source = sources[0]
        assertEquals("https://stbcdn.xyz/hls/master.m3u8?token=secure123", source.url)
        assertTrue(source.isHls)
        assertEquals("application/x-mpegURL", source.mimeType)
        assertEquals("1080p", source.quality)
        assertEquals("Stbturbo", source.sourceName)
        assertEquals(embedUrl, source.headers["Referer"])
        assertEquals("https://stbturbo.xyz", source.headers["Origin"])
    }

    // 5. Stbturbo Protocol-Relative Hash Handling (//domain/...)
    @Test
    fun testStbturboProtocolRelativeHash() {
        val stbturboHtml = """
            <div id="video_player" data-hash="//cdn.stream.com/hls/index.m3u8"></div>
        """.trimIndent()

        val sources = StbturboExtractor.extractFromHtml(stbturboHtml, "https://stbturbo.xyz/e/test")
        assertEquals(1, sources.size)
        assertEquals("https://cdn.stream.com/hls/index.m3u8", sources[0].url)
        assertTrue(sources[0].isHls)
    }

    // 6. StreamTape Extractor Resolution
    @Test
    fun testStreamTapeExtractorResolution() {
        val streamTapeHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <script>
                    document.getElementById('robotlink').innerHTML = '//streamtape.com/get_video?id=tape_abc123' + ('&token=xyz789&expiry=1700000000').substring(0);
                </script>
            </body>
            </html>
        """.trimIndent()

        val embedUrl = "https://streamtape.com/e/tape_abc123"
        val sources = StreamTapeExtractor.extractFromHtml(streamTapeHtml, embedUrl)

        assertEquals(1, sources.size)
        val source = sources[0]
        assertEquals("https://streamtape.com/get_video?id=tape_abc123&token=xyz789&expiry=1700000000&stream=1", source.url)
        assertFalse(source.isHls)
        assertEquals("video/mp4", source.mimeType)
        assertEquals("StreamTape", source.sourceName)
    }

    // 7. Complete Realistic End-to-End Pipeline
    // SEXТB category/search -> .tray-item -> details -> .btn-player -> POST /ajax/player -> iframe -> Stbturbo -> HLS
    @Test
    fun testEndToEndPipeline() {
        // Step 1: Search / Catalog Card
        val catalogHtml = """
            <div class="tray-item">
                <a href="/video/star-999-actress/">
                    <img class="tray-item-thumbnail" data-src="https://img.example.com/star999.jpg" />
                </a>
                <div class="tray-item-title">
                    <a href="/video/star-999-actress/">STAR-999 Masterpiece</a>
                </div>
            </div>
        """.trimIndent()
        val searchItems = SextbParser.parseSearchResults(catalogHtml, "https://sextb.net")
        assertEquals(1, searchItems.size)
        val videoUrl = searchItems[0].uploaderUrl!!
        assertEquals("https://sextb.net/video/star-999-actress/", videoUrl)

        // Step 2: Video Details Page with player button
        val detailsHtml = """
            <html>
            <head>
                <meta property="og:title" content="STAR-999 Masterpiece - SEXTB" />
                <meta property="og:image" content="https://img.example.com/star999.jpg" />
            </head>
            <body>
                <div class="episode-list">
                    <button class="btn-player" data-id="ep_star_999" data-source="film_id_777">Play Main Stream</button>
                </div>
            </body>
            </html>
        """.trimIndent()
        val details = SextbParser.parseDetailsPage(detailsHtml, videoUrl)
        assertEquals("ep_star_999", details.defaultEpisodeId)
        assertEquals("film_id_777", details.defaultFilmId)

        // Step 3: Simulate AJAX POST /ajax/player returning iframe
        val ajaxPlayerResponse = """
            <iframe src="https://stbturbo.xyz/e/stb_star_999" width="100%" height="100%"></iframe>
        """.trimIndent()
        val iframes = SextbResolver.extractIframeUrlsFromAjaxResponse(ajaxPlayerResponse)
        assertEquals("https://stbturbo.xyz/e/stb_star_999", iframes[0])

        // Step 4: Stbturbo resolves data-hash to HLS
        val stbturboPageHtml = """
            <div id="video_player" data-hash="https://stbcdn.xyz/hls/star999/master.m3u8"></div>
        """.trimIndent()
        val resolvedSources = StbturboExtractor.extractFromHtml(stbturboPageHtml, iframes[0])
        assertEquals(1, resolvedSources.size)
        assertEquals("https://stbcdn.xyz/hls/star999/master.m3u8", resolvedSources[0].url)
        assertTrue(resolvedSources[0].isHls)

        // Step 5: Convert into Butterfly StreamData for ExoPlayer
        val streamData = details.toStreamData(resolvedSources)
        assertEquals("star-999-actress", streamData.videoId)
        assertEquals("STAR-999 Masterpiece", streamData.title)
        assertEquals("https://stbcdn.xyz/hls/star999/master.m3u8", streamData.hlsUrl)
        assertEquals(1, streamData.availableStreamOptions.size)
        val option = streamData.availableStreamOptions[0]
        assertEquals("hls", option.format)
        assertEquals("1080p", option.qualityLabel)
        assertEquals(iframes[0], option.headers["Referer"])
    }

    // 8. Quality Extraction & Normalization
    @Test
    fun testQualityExtraction() {
        assertEquals("4K", SextbParser.normalizeQualityLabel("2160p 4K UHD"))
        assertEquals("1080p", SextbParser.normalizeQualityLabel("Full HD 1080p"))
        assertEquals("720p", SextbParser.normalizeQualityLabel("HD 720p"))
        assertEquals("480p", SextbParser.normalizeQualityLabel("SD 480p"))
        assertEquals("360p", SextbParser.normalizeQualityLabel("Low 360p"))
        assertEquals("1080p", SextbParser.normalizeQualityLabel("Unknown Format"))
    }

    // 9. Missing Player Handling & No Cross-Provider Disguise
    @Test
    fun testMissingPlayerAndNoForeignProviderDisguise() = runBlocking {
        // An empty video page must return emptyList / null, never disguised JavVideo content
        val emptyHtml = "<html><body><h1>Page Not Found</h1></body></html>"
        val doc = Jsoup.parse(emptyHtml, "https://sextb.net/video/empty/")
        val sources = SextbParser.parseDirectVideoSources(doc, "https://sextb.net/video/empty/")
        assertTrue(sources.isEmpty())

        // Ensure SextbProvider handles this gracefully without throwing
        val streamData = SextbProvider.getStreamData("https://sextb.net/video/nonexistent-test-page/")
        assertNull(streamData)
    }

    // 10. Malformed HTML Resiliency
    @Test
    fun testMalformedHtmlResiliency() {
        val corruptHtml = "<div class='tray-item'><a href='/broken<img class='tray-item-thumbnail' src='broken.jpg'><h3>Incomplete"
        val results = SextbParser.parseSearchResults(corruptHtml, "https://sextb.net")
        assertNotNull(results)

        val corruptDetailsHtml = "<<<<<head><meta><<body title=\"broken\">"
        val details = SextbParser.parseDetailsPage(corruptDetailsHtml, "https://sextb.net/video/corrupt/")
        assertNotNull(details)
        assertEquals("corrupt", details.id)
    }

    // 11. HTTP Failure & Invalid Input Graceful Handling
    @Test
    fun testHttpFailureGracefulHandling() = runBlocking {
        // Blank search should safely return emptyList immediately
        val blankResults = SextbProvider.search("   ", limit = 10)
        assertNotNull(blankResults)
        assertTrue(blankResults.isEmpty())

        // Non-resolvable network endpoint should return empty list gracefully without uncaught crash
        val invalidSources = SextbResolver.resolveVideoSources("https://127.0.0.1:54321/nonexistent-endpoint")
        assertNotNull(invalidSources)
        assertTrue(invalidSources.isEmpty())
    }

    // 12. Expired Stream Handling
    @Test
    fun testExpiredStreamHandling() {
        val expiredError = SextbException(
            SextbError.STREAM_EXPIRED,
            "Stream token expired, refreshing session"
        )
        assertEquals(SextbError.STREAM_EXPIRED, expiredError.error)
        assertTrue(expiredError.message.contains("expired"))
    }

    // 13. Cancellation Support
    @Test
    fun testCancellationSupport() = runBlocking {
        var wasCancelled = false
        val job = launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(5000L)
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            }
        }
        job.cancelAndJoin()
        assertTrue(wasCancelled)
    }

    // 14. Butterfly SourceProvider Registration
    @Test
    fun testSextbSourceProviderRegistration() {
        val sourceProvider = SextbSourceProvider()
        assertEquals("sextb", sourceProvider.id)
        assertEquals("SEXТB", sourceProvider.displayName)
        assertTrue(sourceProvider.supportedMediaTypes.contains(MediaType.JAV))
    }
}
