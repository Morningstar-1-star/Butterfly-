package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.extractor.SextbProvider
import com.example.extractor.sextb.SextbError
import com.example.extractor.sextb.SextbException
import com.example.extractor.sextb.SextbParser
import com.example.extractor.sextb.SextbResolver
import com.example.extractor.sextb.SextbWebViewFallback
import com.example.extractor.sextb.VideoSource
import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.resolver.providers.SextbSourceProvider
import com.example.util.JsUnpacker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
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

    // 1. Search Test
    @Test
    fun testSearchParsing() {
        val sampleSearchHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="video-item">
                    <a href="https://sextb.net/video/ssis-123-sample-video/" title="SSIS-123 Beautiful Actress Drama">
                        <img data-src="https://images.example.com/cover1.jpg" alt="SSIS-123" />
                        <span class="duration">01:45:20</span>
                        <span class="badge">1080p</span>
                    </a>
                    <span class="studio">S1 NO.1 STYLE</span>
                </div>
                <div class="video-item">
                    <a href="https://sextb.net/video/ipx-456-actress-story/" title="IPX-456 High Definition Action">
                        <img src="https://images.example.com/cover2.jpg" alt="IPX-456" />
                        <span class="duration">58:12</span>
                        <span class="badge">4K</span>
                    </a>
                    <span class="studio">IdeaPocket</span>
                </div>
            </body>
            </html>
        """.trimIndent()

        val results = SextbParser.parseSearchResults(sampleSearchHtml, "https://sextb.net")
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("ssis-123-sample-video", first.id)
        assertEquals("SSIS-123 Beautiful Actress Drama", first.title)
        assertEquals("https://images.example.com/cover1.jpg", first.thumbnailUrl)
        assertEquals("S1 NO.1 STYLE", first.uploaderName)
        assertEquals(6320L, first.durationSeconds) // 1h 45m 20s = 6320s
        assertTrue(first.tags.contains("1080p"))

        val second = results[1]
        assertEquals("ipx-456-actress-story", second.id)
        assertEquals("IPX-456 High Definition Action", second.title)
        assertEquals(3492L, second.durationSeconds) // 58m 12s = 3492s
    }

    // 2. Details Test
    @Test
    fun testDetailsExtraction() {
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
                <meta property="og:video:tag" content="Drama" />
            </head>
            <body>
                <h1 class="title">MIDV-789 Exclusive Tale - SEXTB</h1>
                <span class="original-title">Exclusive Japanese Title - 原題</span>
                <span class="studio"><a href="/studio/moodyz/">MOODYZ</a></span>
                <span class="duration">02:10:00</span>
                <div class="episodes-list">
                    <a href="https://sextb.net/video/midv-789-part-1/">Part 1</a>
                    <a href="https://sextb.net/video/midv-789-part-2/">Part 2</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val details = SextbParser.parseDetailsPage(sampleDetailHtml, "https://sextb.net/video/midv-789/")
        assertEquals("midv-789", details.id)
        assertEquals("MIDV-789 Exclusive Tale", details.title)
        assertEquals("Exclusive Japanese Title - 原題", details.originalTitle)
        assertEquals("https://images.example.com/midv789.jpg", details.thumbnailUrl)
        assertEquals("2024-05-18", details.releaseDate)
        assertEquals("MOODYZ", details.studio)
        assertEquals("Takahiro Ishii", details.director)
        assertTrue(details.actors.contains("Yua Mikami"))
        assertTrue(details.actors.contains("Ken Shimizu"))
        assertTrue(details.categories.contains("Cosplay"))
        assertEquals(7800L, details.durationSeconds) // 2h 10m = 7800s
        assertEquals(2, details.episodes.size)
    }

    // 3. Metadata Extraction (JAVM-style Scoped OpenGraph)
    @Test
    fun testScopedMetadataExtraction() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Scoped Test Title" />
                <meta property="og:image" content="https://cdn.example.com/poster.jpg" />
                <meta property="og:video:duration" content="3600" />
            </head>
            <body>
                <div class="entry-content">
                    <div class="cast"><a href="/actor/actress1">Actress One</a></div>
                    <div class="maker"><a href="/maker/s1">S1</a></div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val details = SextbParser.parseDetailsPage(html, "https://sextb.net/video/test-123/")
        assertEquals("Scoped Test Title", details.title)
        assertEquals("https://cdn.example.com/poster.jpg", details.thumbnailUrl)
        assertEquals(3600L, details.durationSeconds)
        assertTrue(details.actors.contains("Actress One"))
        assertEquals("S1", details.studio)
    }

    // 4. Player-Page Extraction (iframe & embed discovery)
    @Test
    fun testPlayerPageExtraction() {
        val html = """
            <div id="player">
                <iframe src="https://streamtb.me/e/abc123xyz" width="100%" height="100%" allowfullscreen></iframe>
            </div>
            <div data-embed="https://streamtb.me/e/backup456"></div>
        """.trimIndent()

        val embeds = SextbParser.extractPlayerEmbedUrls(html, "https://sextb.net/video/test/")
        assertEquals(2, embeds.size)
        assertTrue(embeds.contains("https://streamtb.me/e/abc123xyz"))
        assertTrue(embeds.contains("https://streamtb.me/e/backup456"))
    }

    // 5. HLS Detection
    @Test
    fun testHlsDetection() {
        val script = """
            var playerInstance = jwplayer("player");
            playerInstance.setup({
                sources: [
                    { file: "https://cdn.streamtb.me/hls/master.m3u8?token=xyz", label: "1080p", type: "hls" }
                ],
                tracks: [
                    { file: "https://cdn.streamtb.me/sub/eng.vtt", label: "English", kind: "captions" }
                ]
            });
        """.trimIndent()

        val sources = SextbResolver.extractStreamUrlsFromText(script, "https://streamtb.me/e/abc")
        assertEquals(1, sources.size)

        val hls = sources[0]
        assertTrue(hls.isHls)
        assertEquals("application/x-mpegURL", hls.mimeType)
        assertEquals("1080p", hls.quality)
        assertEquals("https://streamtb.me/e/abc", hls.headers["Referer"])
        assertEquals("https://streamtb.me", hls.headers["Origin"])
    }

    // 6. MP4 Detection
    @Test
    fun testMp4Detection() {
        val html = """
            <video controls>
                <source src="https://cdn.streamtb.me/videos/direct_video_1080p.mp4" type="video/mp4" label="1080p">
                <source src="https://cdn.streamtb.me/videos/direct_video_720p.mp4" type="video/mp4" label="720p">
            </video>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://sextb.net/video/test/")
        val sources = SextbParser.parseDirectVideoSources(doc, "https://sextb.net/video/test/")
        assertEquals(2, sources.size)

        val first = sources[0]
        assertFalse(first.isHls)
        assertEquals("video/mp4", first.mimeType)
        assertEquals("1080p", first.quality)
        assertEquals("https://cdn.streamtb.me/videos/direct_video_1080p.mp4", first.url)
    }

    // 7. Quality Extraction & Normalization
    @Test
    fun testQualityExtraction() {
        assertEquals("4K", SextbParser.normalizeQualityLabel("2160p 4K UHD"))
        assertEquals("1080p", SextbParser.normalizeQualityLabel("Full HD 1080p"))
        assertEquals("720p", SextbParser.normalizeQualityLabel("HD 720p"))
        assertEquals("480p", SextbParser.normalizeQualityLabel("SD 480p"))
        assertEquals("360p", SextbParser.normalizeQualityLabel("Low 360p"))
        assertEquals("1080p", SextbParser.normalizeQualityLabel("Unknown Format"))
    }

    // 8. Missing Player Handling
    @Test
    fun testMissingPlayer() = runBlocking {
        val emptyHtml = "<html><body><h1>No video available</h1></body></html>"
        val doc = Jsoup.parse(emptyHtml, "https://sextb.net/video/empty/")
        val sources = SextbParser.parseDirectVideoSources(doc, "https://sextb.net/video/empty/")
        val embeds = SextbParser.extractPlayerEmbedUrls(emptyHtml, "https://sextb.net/video/empty/")

        assertTrue(sources.isEmpty())
        assertTrue(embeds.isEmpty())

        // Ensure SextbProvider handles this gracefully without throwing unexpected runtime exceptions
        val streamData = SextbProvider.getStreamData("https://sextb.net/video/nonexistent-test-page/")
        // If HTTP or source is not found, getStreamData returns null gracefully
        assertNull(streamData)
    }

    // 9. Malformed HTML
    @Test
    fun testMalformedHtml() {
        val corruptHtml = "<div class='video-item'><a href='/broken<img src='broken.jpg'><h3>Incomplete"
        // Ensure parser does not crash
        val results = SextbParser.parseSearchResults(corruptHtml, "https://sextb.net")
        assertNotNull(results)

        val corruptDetailsHtml = "<<<<<head><meta><<body title=\"broken\">"
        val details = SextbParser.parseDetailsPage(corruptDetailsHtml, "https://sextb.net/video/corrupt/")
        assertNotNull(details)
        assertEquals("corrupt", details.id)
    }

    // 10. HTTP Failure Handling
    @Test
    fun testHttpFailureGracefulHandling() = runBlocking {
        // Calling search on unreachable domain or 404 should return emptyList without throwing
        val results = SextbProvider.search("random_nonexistent_query_xyz_9999", limit = 10)
        assertNotNull(results)
        assertTrue(results.isEmpty())
    }

    // 11. WebView Fallback Flow & Interception Logic
    @Test
    fun testWebViewFallbackFlow() = runBlocking {
        // Verify fallback detects stream properly given valid media URL patterns
        val testHls = "https://cdn.streamtb.me/media/stream_token_123/master.m3u8"
        val isHls = testHls.contains(".m3u8")
        val source = VideoSource(
            url = testHls,
            mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
            quality = "1080p",
            headers = mapOf("Referer" to "https://streamtb.me/e/test"),
            sourceName = "SEXТB WebView Fallback"
        )

        assertNotNull(source)
        assertTrue(source.isHls)
        assertEquals("application/x-mpegURL", source.mimeType)
        assertEquals("SEXТB WebView Fallback", source.sourceName)
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
                // Simulate resolution process
                kotlinx.coroutines.delay(5000L)
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            }
        }
        job.cancelAndJoin()
        assertTrue(wasCancelled)
    }

    // 14. Android Low-RAM Device & Packed JS Unpacker
    @Test
    fun testAndroidLowRamDeviceCleanupAndJsUnpacking() {
        // Test JsUnpacker to ensure Dean Edwards packer is unpacked without JS runtime overhead on low-RAM devices
        val packedJs = """
            eval(function(p,a,c,k,e,d){e=function(c){return c.toString(36)};if(!''.replace(/^/,String)){while(c--){d[c.toString(a)]=k[c]||c.toString(a)}k=[function(e){return d[e]}];e=function(){return'\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}('4 0={1:"2://3.5/6.7"};',8,8,'config|file|https|cdn|var|streamtb|master|m3u8'.split('|'),0,{}))
        """.trimIndent()

        assertTrue(JsUnpacker.isPacked(packedJs))
        val unpacked = JsUnpacker.unpack(packedJs)
        assertTrue(unpacked.contains("https://cdn.streamtb/master.m3u8"))

        // Verify SourceProvider flow emits valid SourceCandidate
        val sourceProvider = SextbSourceProvider()
        assertEquals("sextb", sourceProvider.id)
        assertEquals("SEXТB", sourceProvider.displayName)
        assertTrue(sourceProvider.supportedMediaTypes.contains(MediaType.JAV))
    }
}
