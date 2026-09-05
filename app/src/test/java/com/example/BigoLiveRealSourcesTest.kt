package com.example

import com.example.extractor.BigoProvider
import com.example.extractor.YtDlpResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BigoLiveRealSourcesTest {

    @Test
    fun testBigoUrlExtractionAndSupportedPatterns() {
        assertEquals("221338632", BigoProvider.extractBigoId("https://www.bigo.tv/221338632"))
        assertEquals("1003582519", BigoProvider.extractBigoId("bigo:1003582519"))
        assertEquals("Thuyhang86", BigoProvider.extractBigoId("https://bigo.tv/show/Thuyhang86"))
        assertEquals("testuser", BigoProvider.extractBigoId("https://www.bigo.tv/s/testuser?lang=en#top"))

        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bigo.tv/1003582519"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("bigo:1003582519"))
    }

    @Test
    fun testRealBigoHomeFeedFetchesRealBroadcasters() = runBlocking {
        val items = BigoProvider.getHome(limit = 10, page = 1)
        assertNotNull(items)
        assertTrue("Expected at least some real live rooms from Bigo", items.isNotEmpty())

        val first = items.first()
        assertTrue("Item ID should be a bigo.tv URL", first.id.contains("bigo.tv/"))
        assertTrue("Title should indicate live broadcast", first.title.contains("LIVE"))
        assertTrue("Uploader should indicate Bigo creator", first.uploaderName.contains("(Bigo)"))

        // Ensure fake demo broadcasters from prior buggy implementation are completely eliminated
        val fakeNames = setOf("lisa_singing_queen", "alex_mlbb_king", "dj_marcus_club", "maya_kpop_dance")
        for (item in items) {
            val idPart = BigoProvider.extractBigoId(item.id)
            assertFalse("Feed must not contain demo broadcaster: $idPart", fakeNames.contains(idPart))
            assertFalse("Feed must not contain picsum placeholder", item.thumbnailUrl?.contains("picsum.photos") == true)
            assertFalse("Feed must not contain unsplash placeholder", item.thumbnailUrl?.contains("unsplash.com") == true)
        }
    }

    @Test
    fun testRealBigoStreamResolutionProvidesActiveHls() = runBlocking {
        val homeItems = BigoProvider.getHome(limit = 5, page = 1)
        if (homeItems.isNotEmpty()) {
            val target = homeItems.first()
            val streamData = BigoProvider.getStreamData(target.id)
            assertNotNull("StreamData should be resolved for live room", streamData)
            if (streamData != null) {
                assertFalse("StreamData must not play BigBuckBunny demo video", streamData.videoUrl.contains("BigBuckBunny"))
                assertFalse("StreamData must not play ElephantsDream demo video", streamData.videoUrl.contains("ElephantsDream"))
                assertTrue("Stream must provide working videoUrl", streamData.videoUrl.isNotBlank())
                assertTrue("Stream option should be available", streamData.availableStreamOptions.isNotEmpty())
            }
        }
    }
}
