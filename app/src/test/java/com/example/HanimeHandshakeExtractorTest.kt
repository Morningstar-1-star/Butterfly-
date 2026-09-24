package com.example

import com.example.extractor.Hanime1Provider
import com.example.extractor.hanime.HanimeHandshakeExtractor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HanimeHandshakeExtractorTest {

    @Test
    fun testGenerateCredentials() {
        val (signature, ts) = HanimeHandshakeExtractor.generateCredentials()
        assertNotNull(signature)
        assertEquals(64, signature.length) // SHA-256 hex length
        assertTrue("Timestamp should be recent Unix epoch", ts > 1700000000L)
    }

    @Test
    fun testAesGcmDigestAndParseTokenRoundTrip() {
        val payload = JSONObject().apply {
            put("timestamp_unix", 1727170000L)
            put("directive", "htv_player_handshake")
            put("slug", "rance-01-the-animation-1")
            put("test_key", "butterfly_hanime_test_12345")
        }

        val encryptedToken = HanimeHandshakeExtractor.digestToken(payload)
        assertNotNull(encryptedToken)
        assertTrue(encryptedToken.isNotBlank())

        val decryptedPayload = HanimeHandshakeExtractor.parseToken(encryptedToken)
        assertNotNull(decryptedPayload)
        assertEquals(1727170000L, decryptedPayload.getLong("timestamp_unix"))
        assertEquals("htv_player_handshake", decryptedPayload.getString("directive"))
        assertEquals("rance-01-the-animation-1", decryptedPayload.getString("slug"))
        assertEquals("butterfly_hanime_test_12345", decryptedPayload.getString("test_key"))
    }

    @Test
    fun testSlugResolution() {
        assertEquals("rance-01-the-animation-1", HanimeHandshakeExtractor.resolveSlug("39207"))
        assertEquals("isekai-harem-monogatari-1", HanimeHandshakeExtractor.resolveSlug("39201"))
        assertEquals("overflow-1", HanimeHandshakeExtractor.resolveSlug("4430"))
        assertEquals("rance-01-the-animation-1", HanimeHandshakeExtractor.resolveSlug("hanimetv:rance-01-the-animation-1"))
        assertEquals("rance-01-the-animation-1", HanimeHandshakeExtractor.resolveSlug("https://hanime.tv/videos/hentai/rance-01-the-animation-1"))
        assertEquals("rance-01-the-animation-1", HanimeHandshakeExtractor.resolveSlug("https://hanime1.me/watch?v=39207"))
    }

    @Test
    fun testLiveExtractStream() = runBlocking {
        val slug = "rance-01-the-animation-1"
        val (signature, ts) = HanimeHandshakeExtractor.generateCredentials()
        val payload = JSONObject().apply {
            put("timestamp_unix", ts)
            put("directive", "htv_player_handshake")
            put("slug", slug)
        }
        val digestedToken = HanimeHandshakeExtractor.digestToken(payload)
        val reqBody = JSONObject().apply {
            put("token", digestedToken)
        }

        val client = okhttp3.OkHttpClient()
        val reqBodyStr = reqBody.toString()
        val request = okhttp3.Request.Builder()
            .url("https://auth.hanime.tv/api/v11/handshake")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Origin", "https://hanime.tv")
            .header("Referer", "https://hanime.tv/")
            .header("X-Csrf-Token", "null")
            .header("X-Signature", signature)
            .header("X-Time", ts.toString())
            .header("X-Signature-Version", "web2")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .post(okhttp3.RequestBody.create(null, reqBodyStr))
            .build()

        val response = client.newCall(request).execute()
        println("HANDSHAKE_DEBUG_CODE: ${response.code}")
        println("HANDSHAKE_DEBUG_HEADERS: ${response.headers}")
        println("HANDSHAKE_DEBUG_BODY: ${response.body?.string()}")
    }

    @Test
    fun testCoverResolution() {
        val cover = HanimeHandshakeExtractor.getCoverForSlug("rance-01-the-animation-1")
        assertTrue("Cover must be non-blank", cover.isNotBlank())
        assertTrue("Cover must not be broken vdownload/hembed", !cover.contains("vdownload") && !cover.contains("hembed.com"))
        assertTrue("Cover must point to AniList CDN", cover.contains("anilist.co"))
    }

    @Test
    fun testNoEmbedFallbackAllowed() = runBlocking {
        // A non-existent/invalid video ID must return null, NOT a fake embed stream!
        val invalidResult = Hanime1Provider.getStreamData("non_existent_fake_video_99999999999", null)
        assertNull("Extraction failure must return null instead of a fake embed stream", invalidResult)
    }
}
