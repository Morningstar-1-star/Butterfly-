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

    @Test
    fun testCleanBilibiliStreamUrlP2pAvoidanceAndAkamaiPreference() {
        val backup = org.json.JSONArray().apply {
            put("http://d1.mcdn.bilivideo.cn:4483/upgcxcode/36/45/12345/12345.m4s")
            put("http://upos-sz-mirrorcos.bilivideo.com/upgcxcode/36/45/12345/12345.m4s")
            put("http://upos-sz-mirrorakam.bilivideo.com/upgcxcode/36/45/12345/12345.m4s")
        }
        val rawP2p = "http://d1.mcdn.bilivideo.cn:4483/upgcxcode/36/45/12345/12345.m4s"

        // Should pick Akamai mirror first, upgraded to https
        val cleaned = BilibiliProvider.cleanBilibiliStreamUrl(rawP2p, backup)
        assertEquals("https://upos-sz-mirrorakam.bilivideo.com/upgcxcode/36/45/12345/12345.m4s", cleaned)
    }

    @Test
    fun testCleanBilibiliStreamUrlPreservesCustomPortWithoutHttpsUpgrade() {
        // If ONLY P2P/custom port host exists, do NOT upgrade to HTTPS (would break plaintext port)
        val rawP2p = "http://112.29.13.45:4483/upgcxcode/sample.m4s"
        val cleaned = BilibiliProvider.cleanBilibiliStreamUrl(rawP2p, null)
        assertEquals("http://112.29.13.45:4483/upgcxcode/sample.m4s", cleaned)
    }

    @Test
    fun testRankAndSelectBilibiliStreamOptionsPrefersH264OverHevcAndAv1() {
        val options = listOf(
            com.example.model.PlayableStreamOption(
                qualityLabel = "1080p 60fps Adaptive (AV1)",
                format = "video_mp4",
                isMuxed = false,
                videoUrl = "https://cdn.example.com/av1_1080p.m4s"
            ),
            com.example.model.PlayableStreamOption(
                qualityLabel = "1080p 60fps Adaptive (HEVC)",
                format = "video_mp4",
                isMuxed = false,
                videoUrl = "https://cdn.example.com/hevc_1080p.m4s"
            ),
            com.example.model.PlayableStreamOption(
                qualityLabel = "1080p Adaptive (H.264)",
                format = "video_mp4",
                isMuxed = false,
                videoUrl = "https://cdn.example.com/h264_1080p.m4s"
            ),
            com.example.model.PlayableStreamOption(
                qualityLabel = "720p Adaptive (H.264)",
                format = "video_mp4",
                isMuxed = false,
                videoUrl = "https://cdn.example.com/h264_720p.m4s"
            )
        )

        val (ranked, selected) = BilibiliProvider.rankAndSelectBilibiliStreamOptions(options)
        assertNotNull(selected)
        assertEquals("1080p Adaptive (H.264)", selected?.qualityLabel)
        // First in ranked list should be H.264
        assertTrue(ranked.first().qualityLabel.contains("H.264"))
    }

    @Test
    fun testSecureDnsPrioritizesIpv4() {
        val v6 = java.net.Inet6Address.getByName("240e:97c:2f:1::50")
        val v4 = java.net.Inet4Address.getByName("119.29.29.29")
        val mixed = listOf(v6, v4)

        val sorted = com.example.util.SecureDnsManager.prioritizeIpv4(mixed)
        assertTrue(sorted[0] is java.net.Inet4Address)
        assertTrue(sorted[1] is java.net.Inet6Address)
    }
}
