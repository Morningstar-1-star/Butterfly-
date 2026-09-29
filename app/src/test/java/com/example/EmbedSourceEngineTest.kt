package com.example

import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceRankingEngine
import com.example.resolver.SourceStreamType
import com.example.resolver.embed.EmbedProviderHealth
import com.example.resolver.embed.TwoEmbedProvider
import com.example.resolver.embed.VidlinkEmbedProvider
import com.example.resolver.embed.VidsrcMeEmbedProvider
import com.example.resolver.embed.VidsrcToEmbedProvider
import com.example.resolver.providers.EmbedSourceProviderAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EmbedSourceEngineTest {

    @Test
    fun testSourceStreamTypeEmbedWebview() {
        val type = SourceStreamType.EMBED_WEBVIEW
        assertEquals("EMBED_WEBVIEW", type.name)

        val candidate = SourceCandidate(
            id = "test_embed",
            providerId = "vidlink_pro",
            providerName = "Vidlink Pro",
            serverName = "Vidlink Pro (Embed)",
            type = SourceStreamType.EMBED_WEBVIEW,
            title = "Fight Club",
            urlOrMagnet = "https://vidlink.pro/movie/550"
        )

        assertEquals("Embed", candidate.badgeLabel)
        assertFalse(candidate.isTorrent)
    }

    @Test
    fun testVidlinkEmbedProviderUrlGeneration() {
        val provider = VidlinkEmbedProvider()

        val movieUrl = provider.buildMovieUrl(tmdbId = "550", imdbId = "tt0137523", title = "Fight Club", year = 1999)
        assertEquals("https://vidlink.pro/movie/550", movieUrl)

        val tvUrl = provider.buildEpisodeUrl(tmdbId = "1399", imdbId = "tt0944947", season = 1, episode = 1, title = "Game of Thrones")
        assertEquals("https://vidlink.pro/tv/1399/1/1", tvUrl)

        assertNull(provider.buildMovieUrl(tmdbId = null, imdbId = null, title = null, year = null))
        assertNull(provider.buildEpisodeUrl(tmdbId = "1399", imdbId = null, season = 0, episode = 1, title = "Game of Thrones"))
    }

    @Test
    fun testVidsrcToEmbedProviderUrlGeneration() {
        val provider = VidsrcToEmbedProvider()

        val movieUrl = provider.buildMovieUrl(tmdbId = "550", imdbId = null, title = "Fight Club", year = 1999)
        assertEquals("https://vidsrc.to/embed/movie/550", movieUrl)

        val tvUrl = provider.buildEpisodeUrl(tmdbId = "1399", imdbId = null, season = 1, episode = 1, title = "Game of Thrones")
        assertEquals("https://vidsrc.to/embed/tv/1399/1/1", tvUrl)

        assertNull(provider.buildMovieUrl(tmdbId = "", imdbId = "invalid", title = "Test", year = 2020))
    }

    @Test
    fun testTwoEmbedProviderUrlGeneration() {
        val provider = TwoEmbedProvider()

        val movieUrl = provider.buildMovieUrl(tmdbId = "550", imdbId = null, title = "Fight Club", year = 1999)
        assertEquals("https://www.2embed.cc/embed/550", movieUrl)

        val tvUrl = provider.buildEpisodeUrl(tmdbId = "1399", imdbId = null, season = 1, episode = 1, title = "Game of Thrones")
        assertEquals("https://www.2embed.cc/embedtv/1399&s=1&e=1", tvUrl)
    }

    @Test
    fun testVidsrcMeEmbedProviderUrlGeneration() {
        val provider = VidsrcMeEmbedProvider()

        val movieUrl = provider.buildMovieUrl(tmdbId = "550", imdbId = null, title = "Fight Club", year = 1999)
        assertEquals("https://vidsrc.me/embed/movie?tmdb=550", movieUrl)

        val tvUrl = provider.buildEpisodeUrl(tmdbId = "1399", imdbId = null, season = 1, episode = 1, title = "Game of Thrones")
        assertEquals("https://vidsrc.me/embed/tv?tmdb=1399&season=1&episode=1", tvUrl)
    }

    @Test
    fun testEmbedSourceProviderAdapterFlow() = runBlocking {
        val rawProvider = VidlinkEmbedProvider()
        val adapter = EmbedSourceProviderAdapter(rawProvider)

        val identity = MediaIdentity(
            tmdbId = "550",
            title = "Fight Club",
            mediaType = MediaType.MOVIE
        )

        val candidates = adapter.searchSources(identity).first()
        assertEquals(1, candidates.size)

        val candidate = candidates.first()
        assertEquals("vidlink_pro", candidate.providerId)
        assertEquals(SourceStreamType.EMBED_WEBVIEW, candidate.type)
        assertEquals("https://vidlink.pro/movie/550", candidate.urlOrMagnet)
    }

    @Test
    fun testEmbedSourceProviderAdapterDisabledState() = runBlocking {
        val rawProvider = VidlinkEmbedProvider().apply {
            healthStatus = EmbedProviderHealth.UNAVAILABLE
        }
        val adapter = EmbedSourceProviderAdapter(rawProvider)

        val identity = MediaIdentity(
            tmdbId = "550",
            title = "Fight Club",
            mediaType = MediaType.MOVIE
        )

        val candidates = adapter.searchSources(identity).first()
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun testSourceRankingEngineScoresEmbed() {
        val candidate = SourceCandidate(
            id = "test_embed",
            providerId = "vidlink_pro",
            providerName = "Vidlink Pro",
            serverName = "Vidlink Pro (Embed)",
            type = SourceStreamType.EMBED_WEBVIEW,
            title = "Fight Club",
            urlOrMagnet = "https://vidlink.pro/movie/550"
        )

        val score = SourceRankingEngine.calculateCompositeScore(candidate)
        assertTrue("Composite score for embed candidate should be > 1000", score > 1000)
    }
}
