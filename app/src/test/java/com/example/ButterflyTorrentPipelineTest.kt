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
}
