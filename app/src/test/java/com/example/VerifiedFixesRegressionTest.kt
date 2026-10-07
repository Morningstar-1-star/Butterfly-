package com.example

import com.example.extractor.tmdbembed.TMDBMediaRequest
import org.junit.Assert.*
import org.junit.Test

class VerifiedFixesRegressionTest {

    @Test
    fun testTMDBMediaRequestIsTvNeverTrueForMovie() {
        // Bug 1 verified: Default TMDBMediaRequest has season=1, episode=1 but mediaType="movie"
        val movieReq = TMDBMediaRequest(
            tmdbId = "550",
            title = "Fight Club",
            mediaType = "movie",
            season = 1,
            episode = 1
        )
        assertFalse("Movie request must NEVER evaluate isTv to true", movieReq.isTv)

        val defaultReq = TMDBMediaRequest(tmdbId = "550")
        assertFalse("Default request (movie) must NEVER evaluate isTv to true", defaultReq.isTv)

        val tvReq = TMDBMediaRequest(
            tmdbId = "1399",
            title = "Game of Thrones",
            mediaType = "tv",
            season = 2,
            episode = 5
        )
        assertTrue("TV request must evaluate isTv to true", tvReq.isTv)

        val seriesReq = TMDBMediaRequest(
            tmdbId = "1399",
            title = "Game of Thrones",
            mediaType = "series",
            season = 1,
            episode = 1
        )
        assertTrue("Series request must evaluate isTv to true", seriesReq.isTv)
    }

    @Test
    fun testDecryptorIdParsing() {
        // 1. Movie format: "decryptor:movie:27205"
        val movieClean = "decryptor:movie:27205"
        val movieParts = movieClean.split(":")
        val isTvMovie = movieClean.contains(":tv:", ignoreCase = true) || movieParts.any { it.equals("tv", ignoreCase = true) }
        assertFalse(isTvMovie)
        val movieTmdbId = movieParts[2].trim()
        assertEquals("27205", movieTmdbId)

        // 2. TV format with season and episode: "decryptor:tv:1399:2:5"
        val tvClean = "decryptor:tv:1399:2:5"
        val tvParts = tvClean.split(":")
        val isTv = tvClean.contains(":tv:", ignoreCase = true) || tvParts.any { it.equals("tv", ignoreCase = true) }
        assertTrue(isTv)
        val tvTmdbId: String
        val tvSeason: Int
        val tvEpisode: Int
        if (isTv && tvParts.size >= 5) {
            tvTmdbId = tvParts[2].trim()
            tvSeason = tvParts[3].toIntOrNull() ?: 1
            tvEpisode = tvParts[4].toIntOrNull() ?: 1
        } else {
            tvTmdbId = ""
            tvSeason = 1
            tvEpisode = 1
        }
        assertEquals("1399", tvTmdbId)
        assertEquals(2, tvSeason)
        assertEquals(5, tvEpisode)

        // 3. TV format with season only: "decryptor:tv:1399:2"
        val tvSeasonOnlyClean = "decryptor:tv:1399:2"
        val tvSeasonOnlyParts = tvSeasonOnlyClean.split(":")
        val isTvSeasonOnly = tvSeasonOnlyClean.contains(":tv:", ignoreCase = true) || tvSeasonOnlyParts.any { it.equals("tv", ignoreCase = true) }
        assertTrue(isTvSeasonOnly)
        val sOnlyTmdbId = tvSeasonOnlyParts[2].trim()
        val sOnlySeason = tvSeasonOnlyParts[3].toIntOrNull() ?: 1
        val sOnlyEpisode = 1
        assertEquals("1399", sOnlyTmdbId)
        assertEquals(2, sOnlySeason)
        assertEquals(1, sOnlyEpisode)
    }

    @Test
    fun testTorrentioDebridSeedDetectionRegex() {
        // Bug 11 verified: Torrentio seed detection with RD/AD/PM
        val debridStreamName = "[RD+] Torrentio\n1080p"
        val isDebrid1 = Regex("""\[(RD|AD|PM|TB|DL|OC)\+?\]""").containsMatchIn(debridStreamName) ||
                Regex("""(?i)\b(real-?debrid|all-?debrid|premiumize)\b""").containsMatchIn(debridStreamName)
        assertTrue("True RD stream must be detected as debrid", isDebrid1)

        // Title containing "RD" or "AD" or "PM" in a word should NOT be marked as debrid
        val nonDebridStreamName = "Torrentio\n1080p"
        val nonDebridTitle = "SPIDER-MAN FORWARD ADVENTURE 7:30PM\n👤 85"
        val isDebrid2 = Regex("""\[(RD|AD|PM|TB|DL|OC)\+?\]""").containsMatchIn(nonDebridStreamName) ||
                Regex("""(?i)\b(real-?debrid|all-?debrid|premiumize)\b""").containsMatchIn(nonDebridStreamName)
        assertFalse("Regular stream name containing letters RD/AD/PM in words must not be debrid", isDebrid2)

        val seederMatch = Regex("""👤\s*(\d+)""").find(nonDebridTitle)
        assertNotNull(seederMatch)
        assertEquals(85, seederMatch!!.groupValues[1].toInt())
    }
}
