package com.example

import com.example.extractor.TencentProvider
import com.example.util.SourceTagHelper
import org.junit.Assert.*
import org.junit.Test

class TencentProviderPipelineTest {

    @Test
    fun testIsTencentUrl() {
        assertTrue(TencentProvider.isTencentUrl("https://v.qq.com/x/cover/m44106pxfq4.html"))
        assertTrue(TencentProvider.isTencentUrl("https://video.qq.com/x/cover/zpyp9ejmmv1pshd.html"))
        assertTrue(TencentProvider.isTencentUrl("https://wetv.vip/play/zpyp9ejmmv1pshd/f4102l3zpcj"))
        assertTrue(TencentProvider.isTencentUrl("vqq:video:m44106pxfq4"))
        assertTrue(TencentProvider.isTencentUrl("vqq:series:m44106pxfq4"))
        assertTrue(TencentProvider.isTencentUrl("tencent:Soul Land"))
        assertFalse(TencentProvider.isTencentUrl("https://www.youtube.com/watch?v=12345678901"))
    }

    @Test
    fun testSanitizeTencentChannelName() {
        val anim = TencentProvider.sanitizeTencentChannelName("腾讯视频动漫 - Get the WeTV APP")
        assertTrue(anim.contains("Tencent Video Animation") || anim.contains("WeTV"))

        val drama = TencentProvider.sanitizeTencentChannelName("腾讯视频-电视剧")
        assertEquals("Tencent Video Drama", drama)

        val studio = TencentProvider.sanitizeTencentChannelName("玄机科技官方")
        assertEquals("Sparkly Key Animation", studio)
    }

    @Test
    fun testGetTencentAvatar() {
        val animAvatar = TencentProvider.getTencentAvatar("Tencent Video Animation", "Soul Land")
        assertNotNull(animAvatar)
        assertTrue(animAvatar.startsWith("https://"))

        val dramaAvatar = TencentProvider.getTencentAvatar("Tencent Video Drama", "The Untamed")
        assertNotNull(dramaAvatar)
        assertTrue(dramaAvatar.startsWith("https://"))
    }

    @Test
    fun testSourceTagHelperMatchesProvider() {
        assertTrue(SourceTagHelper.matchesProvider("tencent", "tencent"))
        assertTrue(SourceTagHelper.matchesProvider("wetv", "tencent"))
        assertTrue(SourceTagHelper.matchesProvider("tencent", "wetv"))
        assertTrue(SourceTagHelper.matchesProvider("vqq", "tencent"))
    }

    @Test
    fun testNormalizeTencentUrl() {
        val raw = "tencent:zpyp9ejmmv1pshd"
        val normalized = TencentProvider.normalizeTencentUrl(raw)
        assertTrue(normalized.startsWith("https://v.qq.com/x/cover/zpyp9ejmmv1pshd"))
    }
}
