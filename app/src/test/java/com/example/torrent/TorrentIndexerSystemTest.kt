package com.example.torrent

import com.example.torrent.indexer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TorrentIndexerSystemTest {

    @Test
    fun testPirateBayIndexerRealSearch() = runBlocking {
        val indexer = PirateBayIndexer()
        val results = indexer.search("Sintel")
        println("PirateBay returned ${results.size} items")
        results.take(3).forEach { item ->
            assertTrue("Title should not be blank", item.title.isNotBlank())
            assertTrue("InfoHash should be valid 32+ char hex", item.infoHash.length >= 32)
            assertTrue("Magnet URL should contain btih", item.magnetUrl.contains("btih:"))
            println(" -> [TPB] ${item.title} | Seeds: ${item.seeders} | Size: ${item.sizeFormatted}")
        }
    }

    @Test
    fun testYtsIndexerRealSearch() = runBlocking {
        val indexer = YtsIndexer()
        val results = indexer.search("Oppenheimer")
        println("YTS returned ${results.size} items")
        results.take(3).forEach { item ->
            assertTrue("Title should not be blank", item.title.isNotBlank())
            assertTrue("InfoHash should be valid hex", item.infoHash.length >= 32)
            assertTrue("Magnet URL should contain btih", item.magnetUrl.contains("btih:"))
            println(" -> [YTS] ${item.title} | Seeds: ${item.seeders} | Size: ${item.sizeFormatted}")
        }
    }

    @Test
    fun testNyaaIndexerRealSearch() = runBlocking {
        val indexer = NyaaIndexer()
        val results = indexer.search("Cyberpunk")
        println("Nyaa returned ${results.size} items")
        results.take(3).forEach { item ->
            assertTrue("Title should not be blank", item.title.isNotBlank())
            assertTrue("InfoHash should be valid hex", item.infoHash.length >= 32)
            println(" -> [Nyaa] ${item.title} | Seeds: ${item.seeders} | Size: ${item.sizeFormatted}")
        }
    }

    @Test
    fun testTorrentIndexerEngineAggregationAndDeduplication() = runBlocking {
        val aggregatedResults = TorrentIndexerEngine.searchAll("Sintel", timeoutMs = 8000L)
        println("Aggregated total deduplicated results: ${aggregatedResults.size}")

        // Ensure infoHashes are unique
        val hashSet = aggregatedResults.map { it.infoHash.lowercase() }.toSet()
        assertEquals("Results must be strictly deduplicated by InfoHash", aggregatedResults.size, hashSet.size)

        val auditTable = TorrentIndexerEngine.getFullAuditTable()
        println("\n--- TORRENT INDEXER AUDIT TABLE ---")
        println("Indexer | Source Repository | Integrated | Android-Compatible | Test Result | Failure Reason")
        auditTable.forEach { row ->
            println("${row.indexerName} | ${row.sourceRepository} | ${row.isIntegrated} | ${row.isAndroidCompatible} | ${row.testResult} | ${row.failureReason ?: "None"}")
        }
    }
}
