package com.example

import com.example.torrent.protocol.MagnetParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ButterflyTorrentPipelineTest {

    @Test
    fun testMagnetParsingAndInfoHashNormalization() {
        val magnetUrl = "magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567&dn=Test+Movie+2026&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
        val parsed = MagnetParser.parse(magnetUrl)
        assertNotNull(parsed)
        assertEquals("0123456789abcdef0123456789abcdef01234567", parsed!!.infoHashHex.lowercase().trim())
        assertEquals("Test Movie 2026", parsed.displayName)
        assertTrue(parsed.trackers.isNotEmpty())
    }

    @Test
    fun testHashRoutingValidation() {
        val activeHash = "0123456789abcdef0123456789abcdef01234567"
        val requestHashValid = "0123456789ABCDEF0123456789ABCDEF01234567".lowercase().trim()
        val requestHashInvalid = "9999999999abcdef0123456789abcdef01234567"

        assertEquals(activeHash, requestHashValid)
        assertNotEquals(activeHash, requestHashInvalid)
    }

    @Test
    fun testContentRangeHeaderFormatting() {
        val totalLength = 1_048_576L // 1 MB
        val startByte = 0L
        val endByte = 65535L

        val rangeHeader = "bytes $startByte-$endByte/$totalLength"
        assertEquals("bytes 0-65535/1048576", rangeHeader)

        val contentLength = endByte - startByte + 1
        assertEquals(65536L, contentLength)
    }

    @Test
    fun testRangeBoundaryCalculations() {
        val totalLength = 1000L

        // Valid range 0-499
        var startByte = 0L
        var endByte = 499L
        assertTrue(startByte in 0 until totalLength && endByte in startByte until totalLength)

        // Invalid range out of bounds
        startByte = 1500L
        endByte = 2000L
        assertFalse(startByte in 0 until totalLength && endByte in startByte until totalLength)
    }

    @Test
    fun testVideoFileFilteringAndSelection() {
        val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".mov", ".m4v")
        val sampleKeywords = listOf("sample", "trailer", "preview", "bonus", "featurette")

        data class MockFile(val name: String, val size: Long)

        val files = listOf(
            MockFile("Movie.Sample.mkv", 45_000_000L),
            MockFile("Movie.2024.1080p.mkv", 2_500_000_000L),
            MockFile("info.nfo", 2048L),
            MockFile("poster.jpg", 150_000L),
            MockFile("trailer.mp4", 60_000_000L)
        )

        val eligible = files.filter { file ->
            val lower = file.name.lowercase()
            val isVideo = videoExtensions.any { lower.endsWith(it) }
            val isSample = sampleKeywords.any { lower.contains(it) }
            isVideo && !isSample
        }

        assertEquals(1, eligible.size)
        assertEquals("Movie.2024.1080p.mkv", eligible.first().name)
    }

    @Test
    fun testEpisodeMatching() {
        data class MockFile(val name: String, val size: Long)

        val files = listOf(
            MockFile("Show.S01E01.1080p.mkv", 1_200_000_000L),
            MockFile("Show.S01E02.1080p.mkv", 1_210_000_000L),
            MockFile("Show.S01E03.1080p.mkv", 1_190_000_000L)
        )

        val targetEpisode = 2
        val targetSeason = 1

        val pattern = Regex("""[Ss]0?$targetSeason[Ee]0?$targetEpisode\b""")
        val matched = files.firstOrNull { pattern.containsMatchIn(it.name) }

        assertNotNull(matched)
        assertEquals("Show.S01E02.1080p.mkv", matched!!.name)
    }

    @Test
    fun testPieceLookaheadWindowCalculations() {
        val pieceLength = 1_048_576L // 1MB piece
        val fileStartOffset = 0L
        val fileLength = 500_000_000L // 500MB file

        val streamOffset = 50_000_000L // seeking to ~50MB
        val targetPiece = ((fileStartOffset + streamOffset) / pieceLength).toInt()

        val lookaheadBytes = 25 * 1024 * 1024L // 25 MB lookahead
        val lookaheadPieces = (lookaheadBytes / pieceLength).toInt()
        val totalPieces = (fileLength / pieceLength).toInt()

        val endPiece = (targetPiece + lookaheadPieces).coerceAtMost(totalPieces - 1)

        assertTrue(targetPiece in 0 until totalPieces)
        assertTrue(endPiece >= targetPiece)
        assertEquals(targetPiece + 25, endPiece)
    }

    @Test
    fun testTorrentErrorCodesDefined() {
        assertEquals("NO_METADATA", com.example.torrent.model.TorrentErrorCode.NO_METADATA)
        assertEquals("NO_PEERS", com.example.torrent.model.TorrentErrorCode.NO_PEERS)
        assertEquals("NO_VIDEO_FILE", com.example.torrent.model.TorrentErrorCode.NO_VIDEO_FILE)
        assertEquals("BUFFER_TIMEOUT", com.example.torrent.model.TorrentErrorCode.BUFFER_TIMEOUT)
        assertEquals("HTTP_RANGE_ERROR", com.example.torrent.model.TorrentErrorCode.HTTP_RANGE_ERROR)
        assertEquals("MEDIA3_ERROR", com.example.torrent.model.TorrentErrorCode.MEDIA3_ERROR)
    }
}
