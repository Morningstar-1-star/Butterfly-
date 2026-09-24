package com.example

import com.example.model.VideoItem
import com.example.util.SearchRelevanceScorer
import com.example.util.SmartSearchSanitizer
import com.example.util.SourceTagHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchSanitizerTest {

    @Test
    fun testTypoCorrectionForInterstellar() {
        val result1 = SmartSearchSanitizer.sanitizeQuery("inter stell")
        assertEquals("Interstellar", result1.cleanQuery)
        assertEquals("Interstellar", result1.didYouMean)

        val result2 = SmartSearchSanitizer.sanitizeQuery("intersteelarg")
        assertEquals("Interstellar", result2.cleanQuery)
        assertEquals("Interstellar", result2.didYouMean)
    }

    @Test
    fun testTypoCorrectionForOppenheimer() {
        val match = SmartSearchSanitizer.findFuzzyMatch("openhaymer")
        assertEquals("Oppenheimer", match)
    }

    @Test
    fun testRelevanceScorerPlacesRelevantItemsFirst() {
        val targetQuery = "Interstellar"
        val video1 = VideoItem(
            id = "1",
            title = "Random Vlog in Hawaii",
            uploaderName = "Traveler",
            thumbnailUrl = "https://thumb.jpg",
            providerId = "youtube"
        )
        val video2 = VideoItem(
            id = "2",
            title = "Interstellar Official Soundtrack OST",
            uploaderName = "Hans Zimmer",
            thumbnailUrl = "https://thumb.jpg",
            providerId = "youtube"
        )
        val video3 = VideoItem(
            id = "3",
            title = "Interstellar (2014) Full Movie Review",
            uploaderName = "Movie Guy",
            thumbnailUrl = "https://thumb.jpg",
            providerId = "youtube"
        )

        val ranked = SearchRelevanceScorer.rankSearchResults(
            items = listOf(video1, video2, video3),
            query = "intersteelarg",
            correctedQuery = targetQuery
        )

        // The Interstellar videos must be ranked before the random vlog
        assertTrue(ranked[0].title.contains("Interstellar"))
        assertTrue(ranked[1].title.contains("Interstellar"))
        assertEquals("Random Vlog in Hawaii", ranked[2].title)
    }

    @Test
    fun testSourceProviderMatching() {
        assertTrue(SourceTagHelper.matchesProvider("dailymotion", "dailymotion"))
        assertTrue(SourceTagHelper.matchesProvider("youtube", "ALL"))
        assertTrue(SourceTagHelper.matchesProvider("bun-tel-meg", "telegram"))
        assertTrue(SourceTagHelper.matchesProvider("bun-tel-meg", "mega"))
        assertTrue(SourceTagHelper.matchesProvider("bun-tel-meg", "bunkr"))
        assertTrue(SourceTagHelper.matchesProvider("jikan_anime", "anime"))
        assertTrue(SourceTagHelper.matchesProvider("archive_org", "archive"))
    }

    @Test
    fun testAccurateYouTubeSourceBadgeAndShareOrigin() {
        // 1. Hotstar wrapper showing YouTube video (e.g. JioHotstar YouTube channel)
        val hotstarYtBadge = SourceTagHelper.getSourceBadge(
            providerId = "hotstar",
            videoId = "7mw_d4rQslY",
            title = "Hotstar Specials: Thukra Ke Mera Pyaar",
            uploaderName = "JioHotstar",
            thumbnailUrl = "https://i.ytimg.com/vi/7mw_d4rQslY/hqdefault.jpg"
        )
        assertEquals("YouTube", hotstarYtBadge.name)
        assertEquals("youtube", hotstarYtBadge.providerIdKey)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF0000), hotstarYtBadge.backgroundColor)

        // 2. SonyLIV wrapper showing YouTube video
        val sonyLivYtBadge = SourceTagHelper.getSourceBadge(
            providerId = "sonyliv",
            videoId = "dQw4w9WgXcQ",
            title = "SonyLIV Special",
            uploaderName = "Sony LIV"
        )
        assertEquals("YouTube", sonyLivYtBadge.name)
        assertEquals("youtube", sonyLivYtBadge.providerIdKey)

        // 3. Crunchyroll wrapper showing YouTube video
        val crunchyYtBadge = SourceTagHelper.getSourceBadge(
            providerId = "crunchyroll",
            videoId = "dQw4w9WgXcQ",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        )
        assertEquals("YouTube", crunchyYtBadge.name)
        assertEquals("youtube", crunchyYtBadge.providerIdKey)

        // 4. Native Hotstar content (NOT YouTube)
        val nativeHotstarBadge = SourceTagHelper.getSourceBadge(
            providerId = "hotstar",
            videoId = "https://www.hotstar.com/in/movies/shang-chi/1260072682",
            thumbnailUrl = "https://img10.hotstar.com/image/upload/f_auto/sources/r1/cms/prod/1260072682"
        )
        assertEquals("Hotstar", nativeHotstarBadge.name)
        assertEquals("hotstar", nativeHotstarBadge.providerIdKey)

        // 5. Share helper returns YouTube platform and link
        val shareInfo = com.example.util.VideoShareHelper.resolveOriginInfo(
            streamData = null,
            fallbackVideoId = "7mw_d4rQslY",
            fallbackTitle = "Hotstar Specials"
        )
        assertEquals("YouTube", shareInfo.platformName)
        assertEquals("youtube.com", shareInfo.domain)
        assertEquals("https://youtu.be/7mw_d4rQslY", shareInfo.webUrl)
    }
}
