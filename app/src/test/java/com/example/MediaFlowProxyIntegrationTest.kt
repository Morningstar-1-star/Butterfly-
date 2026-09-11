package com.example

import com.example.remote.MediaFlowProxyHelper
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceStreamType
import com.example.util.AppConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaFlowProxyIntegrationTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        AppConfig.init(context)
        AppConfig.setMediaFlowEnabled(context, true)
        AppConfig.setMediaFlowServerUrl(context, "http://192.168.1.100:8888")
        AppConfig.setMediaFlowApiPassword(context, "my_secret_token_123")
        AppConfig.setMediaFlowLightMode(context, true)
        AppConfig.setMediaFlowFallbackToDirect(context, true)
        AppConfig.setMediaFlowProxyAllStreams(context, false)
    }

    @Test
    fun testBuildProxiedUrlForDirectStream() {
        val original = "https://cdn.example.com/video.mp4?token=abc123xyz&exp=999999"
        val headers = mapOf("Referer" to "https://origin.example.com/", "User-Agent" to "CustomUA/2.0")

        val proxied = MediaFlowProxyHelper.buildProxiedUrl(
            originalUrl = original,
            headers = headers,
            isHls = false,
            isDash = false
        )

        assertTrue(proxied.startsWith("http://192.168.1.100:8888/proxy/stream?d="))
        assertTrue(proxied.contains("api_password=my_secret_token_123"))
        assertTrue(proxied.contains("mode=light"))
        assertTrue(proxied.contains("h_Referer="))
        assertTrue(proxied.contains("h_User-Agent="))
        assertTrue(proxied.contains("&headers="))
        assertTrue(proxied.contains("&request_headers="))

        // Verify decoded target URL preserves query parameters exactly
        val dParam = proxied.substringAfter("d=").substringBefore("&")
        val decodedTarget = URLDecoder.decode(dParam, StandardCharsets.UTF_8.name())
        assertEquals(original, decodedTarget)
    }

    @Test
    fun testBuildProxiedUrlForHls() {
        val originalHls = "https://stream.server.org/live/master.m3u8?auth=token99"
        val headers = mapOf("Origin" to "https://server.org", "Cookie" to "session=active")

        val proxied = MediaFlowProxyHelper.buildProxiedUrl(
            originalUrl = originalHls,
            headers = headers,
            isHls = true
        )

        assertTrue(proxied.startsWith("http://192.168.1.100:8888/proxy/hls/manifest.m3u8?d="))
        assertTrue(proxied.contains("h_Origin="))
        assertTrue(proxied.contains("h_Cookie="))

        val dParam = proxied.substringAfter("d=").substringBefore("&")
        val decoded = URLDecoder.decode(dParam, StandardCharsets.UTF_8.name())
        assertEquals(originalHls, decoded)
    }

    @Test
    fun testBuildProxiedUrlForDash() {
        val originalDash = "https://dash.server.org/manifest.mpd"

        val proxied = MediaFlowProxyHelper.buildProxiedUrl(
            originalUrl = originalDash,
            isDash = true
        )

        assertTrue(proxied.startsWith("http://192.168.1.100:8888/proxy/mpd/manifest.mpd?d="))
    }

    @Test
    fun testBuildForwardProxyUrl() {
        val apiUrl = "https://api.external.com/v1/metadata?id=12345"
        val headers = mapOf("Authorization" to "Bearer test_auth_token")

        val proxied = MediaFlowProxyHelper.buildForwardProxyUrl(
            targetApiUrl = apiUrl,
            headers = headers
        )

        assertTrue(proxied.startsWith("http://192.168.1.100:8888/proxy/forward?d="))
        assertTrue(proxied.contains("api_password=my_secret_token_123"))

        val dParam = proxied.substringAfter("d=").substringBefore("&")
        val decoded = URLDecoder.decode(dParam, StandardCharsets.UTF_8.name())
        assertEquals(apiUrl, decoded)
    }

    @Test
    fun testShouldProxyDecisionEngine() {
        val candidateWithHeaders = SourceCandidate(
            id = "test_1",
            providerId = "bilibili",
            providerName = "Bilibili",
            serverName = "Bilibili Server 1",
            type = SourceStreamType.DIRECT,
            title = "Test Stream",
            urlOrMagnet = "https://upos.bilibili.com/video/stream.mp4",
            headers = mapOf("Referer" to "https://www.bilibili.com/")
        )
        assertTrue(MediaFlowProxyHelper.shouldProxy(candidateWithHeaders))

        val torrentCandidate = SourceCandidate(
            id = "test_2",
            providerId = "torrentio",
            providerName = "Torrentio",
            serverName = "P2P Swarm",
            type = SourceStreamType.TORRENT,
            title = "Test Torrent",
            urlOrMagnet = "magnet:?xt=urn:btih:0123456789abcdef"
        )
        assertFalse(MediaFlowProxyHelper.shouldProxy(torrentCandidate))

        val embedCandidate = SourceCandidate(
            id = "test_3",
            providerId = "embed",
            providerName = "Embed",
            serverName = "VidSrc Player",
            type = SourceStreamType.EMBED_WEBVIEW,
            title = "Test Embed",
            urlOrMagnet = "https://player.vidsrc.me/embed/movie/1234"
        )
        assertFalse(MediaFlowProxyHelper.shouldProxy(embedCandidate))

        val localCandidate = SourceCandidate(
            id = "test_4",
            providerId = "local",
            providerName = "Local",
            serverName = "Local Storage",
            type = SourceStreamType.LOCAL,
            title = "Local File",
            urlOrMagnet = "file:///storage/emulated/0/Download/movie.mp4"
        )
        assertFalse(MediaFlowProxyHelper.shouldProxy(localCandidate))
    }

    @Test
    fun testProxyAllStreamsToggle() {
        val plainCandidate = SourceCandidate(
            id = "test_plain",
            providerId = "direct",
            providerName = "Direct",
            serverName = "Public CDN",
            type = SourceStreamType.DIRECT,
            title = "Plain Direct Stream",
            urlOrMagnet = "https://public.example.com/sample.mp4",
            headers = emptyMap()
        )

        AppConfig.setMediaFlowProxyAllStreams(context, false)
        assertFalse(MediaFlowProxyHelper.shouldProxy(plainCandidate))

        AppConfig.setMediaFlowProxyAllStreams(context, true)
        assertTrue(MediaFlowProxyHelper.shouldProxy(plainCandidate))
    }

    @Test
    fun testDisabledMediaFlowReturnsOriginalUrl() {
        AppConfig.setMediaFlowEnabled(context, false)
        val original = "https://cdn.example.com/video.mp4"
        val proxied = MediaFlowProxyHelper.buildProxiedUrl(original, mapOf("Referer" to "https://example.com/"))
        assertEquals(original, proxied)
    }

    @Test
    fun testSanitizeUrlForLogging() {
        val rawUrl = "http://192.168.1.100:8888/proxy/stream?d=https%3A%2F%2Fcdn.com%2Fv.mp4&api_password=super_secret_pass&token=secret_token_abc&Cookie=session_id_123"
        val sanitized = MediaFlowProxyHelper.sanitizeUrlForLogging(rawUrl)

        assertFalse(sanitized.contains("super_secret_pass"))
        assertFalse(sanitized.contains("secret_token_abc"))
        assertFalse(sanitized.contains("session_id_123"))
        assertTrue(sanitized.contains("api_password=***"))
    }
}
