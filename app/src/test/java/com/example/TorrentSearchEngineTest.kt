package com.example

import com.example.torrent.engine.TorrentSearchEngine
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import com.example.torrent.provider.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TorrentSearchEngineTest {

    @Test
    fun testMagnetParserValidHexAndBase32() {
        val hex40 = "urn:btih:4139824422e1cdb8eb4ecaa0c2420953c8db3c7b"
        val magnetUrl = "magnet:?xt=$hex40&dn=Test+Movie+2024&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
        val parsed = MagnetParser.parse(magnetUrl)

        assertNotNull(parsed)
        assertEquals("4139824422e1cdb8eb4ecaa0c2420953c8db3c7b", parsed?.infoHashHex)
        assertEquals("Test Movie 2024", parsed?.displayName)
        assertTrue(parsed?.trackers?.contains("udp://tracker.opentrackr.org:1337/announce") == true)
    }

    @Test
    fun testMalformedMagnetHandling() {
        assertNull(MagnetParser.parse(""))
        assertNull(MagnetParser.parse("   "))
        assertNull(MagnetParser.parse("magnet:?xt=invalid_hash_content"))
        assertNull(MagnetParser.parse("not_a_magnet_at_all"))

        // Engine filtering test
        val engine = TorrentSearchEngine()
        val corruptItem = TorrentResult(
            title = "Corrupt Torrent",
            magnet = "magnet:?xt=invalid_hash",
            infoHash = "corrupted_non_hex_hash",
            source = "Test"
        )
        val validItem = TorrentResult(
            title = "Valid Torrent 1080p",
            magnet = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111&dn=Valid+1080p",
            infoHash = "1111111111111111111111111111111111111111",
            seeders = 25,
            source = "Test"
        )

        val processed = engine.processAndDeduplicate(listOf(corruptItem, validItem))
        assertEquals(1, processed.size)
        assertEquals("1111111111111111111111111111111111111111", processed[0].infoHash)
    }

    @Test
    fun testDuplicateResultRemovalAndMerging() {
        val engine = TorrentSearchEngine()
        val hash = "aabbccddeeff00112233445566778899aabbccdd"

        val itemFromYts = TorrentResult(
            title = "Inception (2010) [1080p] [YTS]",
            magnet = "magnet:?xt=urn:btih:$hash&dn=Inception",
            infoHash = hash,
            size = 2_000_000_000L,
            formattedSize = "1.86 GB",
            seeders = 50,
            leechers = 10,
            source = "YTS",
            category = "Movies"
        )

        val itemFromTGx = TorrentResult(
            title = "Inception.2010.1080p.BluRay.x264",
            magnet = "magnet:?xt=urn:btih:$hash&dn=Inception",
            infoHash = hash,
            size = 2_000_000_000L,
            formattedSize = "1.86 GB",
            seeders = 120, // higher seeds
            leechers = 15,
            source = "TorrentGalaxy",
            category = "Movies"
        )

        val processed = engine.processAndDeduplicate(listOf(itemFromYts, itemFromTGx))
        assertEquals(1, processed.size)
        val merged = processed[0]
        assertEquals(120, merged.seeders)
        assertEquals(15, merged.leechers)
        assertTrue(merged.source.contains("YTS"))
        assertTrue(merged.source.contains("TorrentGalaxy"))
    }

    @Test
    fun testProviderFailureIsolationAndTimeout() = runBlocking {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .build()

        val engine = TorrentSearchEngine(client)

        // Register a failing provider
        val failingProvider = object : TorrentProvider {
            override val id: String = "failing_mock"
            override val name: String = "Failing Provider"
            override val isEnabled: Boolean = true
            override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> {
                throw RuntimeException("Simulated indexer 500 error / connection reset")
            }
        }

        // Register a hanging/timeout provider
        val timeoutProvider = object : TorrentProvider {
            override val id: String = "timeout_mock"
            override val name: String = "Timeout Provider"
            override val isEnabled: Boolean = true
            override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> {
                delay(30_000L) // Exceeds PROVIDER_TIMEOUT_MS
                return listOf()
            }
        }

        // Register a working provider
        val workingProvider = object : TorrentProvider {
            override val id: String = "working_mock"
            override val name: String = "Working Provider"
            override val isEnabled: Boolean = true
            override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> {
                return listOf(
                    TorrentResult(
                        title = "Working Torrent 2024 1080p",
                        magnet = "magnet:?xt=urn:btih:3333333333333333333333333333333333333333&dn=Working",
                        infoHash = "3333333333333333333333333333333333333333",
                        seeders = 42,
                        source = "Working Provider"
                    )
                )
            }
        }

        engine.registerProvider(failingProvider)
        engine.registerProvider(timeoutProvider)
        engine.registerProvider(workingProvider)

        val results = engine.search("test query", MediaIdentity(title = "test query"))

        // Must still succeed and return results from the healthy provider
        assertTrue(results.isNotEmpty())
        val found = results.find { it.infoHash == "3333333333333333333333333333333333333333" }
        assertNotNull(found)
        assertEquals(42, found?.seeders)
    }

    @Test
    fun testLiveAnimeToshoSearch() = runBlocking {
        val nyaa = NyaaProvider()
        val identity = MediaIdentity(
            title = "Attack on Titan",
            mediaType = "anime",
            season = 1,
            episode = 1
        )
        val results = nyaa.search("Attack on Titan 01", identity)
        // Verify response schema and hash validity if network reachable
        if (results.isNotEmpty()) {
            val first = results.first()
            assertFalse(first.title.isBlank())
            assertEquals(40, first.infoHash.length)
            assertTrue(first.magnet.startsWith("magnet:?"))
            assertEquals("Anime", first.category)
            assertEquals("Nyaa", first.source)
        }
    }

    @Test
    fun testLiveYtsSearch() = runBlocking {
        val yts = YtsProvider()
        val identity = MediaIdentity(
            title = "Inception",
            imdbId = "tt1375666",
            mediaType = "movie"
        )
        val results = yts.search("Inception", identity)
        if (results.isNotEmpty()) {
            val first = results.first()
            assertTrue(first.title.contains("Inception", ignoreCase = true))
            assertEquals(40, first.infoHash.length)
            assertEquals("Movies", first.category)
            assertEquals("YTS", first.source)
        }
    }
}
