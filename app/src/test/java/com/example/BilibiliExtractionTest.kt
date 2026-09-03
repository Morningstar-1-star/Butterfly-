package com.example

import com.example.extractor.BilibiliProvider
import com.example.extractor.YtDlpResolver
import com.example.util.SmartSearchSanitizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BilibiliExtractionTest {

    @Test
    fun testYtDlpSupportedUrlDetectionForBilibili() {
        // Standard video URLs & IDs
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://b23.tv/BV1xx411c7mD"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("BV1xx411c7mD"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("av170001"))

        // Bangumi (ep / ss / md)
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bilibili.com/bangumi/play/ep123456"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bilibili.com/bangumi/play/ss34567"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bilibili.com/bangumi/media/md89012"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("ep123456"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("ss34567"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("md89012"))

        // Bilisearch prefix
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("bilisearch:eva"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("bilisearch10:naruto"))
    }

    @Test
    fun testSmartSearchSanitizerPreservesBilisearch() {
        val res1 = SmartSearchSanitizer.sanitizeQuery("bilisearch:genshin impact")
        assertEquals("bilisearch:genshin impact", res1.cleanQuery)

        val res2 = SmartSearchSanitizer.sanitizeQuery("bilisearch20:anime music")
        assertEquals("bilisearch20:anime music", res2.cleanQuery)
    }

    @Test
    fun testCategoryMapContainsExpectedCategories() {
        assertTrue(BilibiliProvider.CATEGORY_RID_MAP.containsKey("anime"))
        assertTrue(BilibiliProvider.CATEGORY_RID_MAP.containsKey("music"))
        assertTrue(BilibiliProvider.CATEGORY_RID_MAP.containsKey("gaming"))
        assertTrue(BilibiliProvider.CATEGORY_RID_MAP.containsKey("dance"))
        assertTrue(BilibiliProvider.CATEGORY_RID_MAP.containsKey("technology"))
        assertTrue(BilibiliProvider.CATEGORY_RID_MAP.containsKey("life"))
    }

    @Test
    fun testDurationParsing() {
        assertEquals(272L, BilibiliProvider.parseDurationString("04:32"))
        assertEquals(3723L, BilibiliProvider.parseDurationString("01:02:03"))
        assertEquals(-1L, BilibiliProvider.parseDurationString(""))
    }
}
