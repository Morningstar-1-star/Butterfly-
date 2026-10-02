package com.example.extractor.nuvio

import android.content.Context
import android.util.Log
import com.example.extractor.nuvio.extractors.*
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedConfig
import com.example.extractor.tmdbembed.TMDBEmbedExtractorEngine
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.extractor.tmdbembed.extractors.*
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.resolver.SourceCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object NuvioProviderEngine {
    private const val TAG = "NuvioProviderEngine"
    private const val TMDB_API_KEY = "1865f43a0549ca50d341dd9ab8b29f49"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"

    private val tmdbHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun enrichRequest(request: TMDBMediaRequest): TMDBMediaRequest = withContext(Dispatchers.IO) {
        var currentTmdbId = request.tmdbId.trim()
        var currentTitle = request.title.trim()
        var currentYear = request.year.trim()
        var currentImdbId = request.imdbId?.trim()

        try {
            // Case 1: tmdbId is numeric -> fetch details if title/year/imdbId missing
            if (currentTmdbId.all { it.isDigit() } && currentTmdbId.isNotBlank() && (currentTitle.isBlank() || currentYear.isBlank() || currentImdbId.isNullOrBlank())) {
                val mediaType = if (request.isTv) "tv" else "movie"
                val url = "$TMDB_BASE_URL/$mediaType/$currentTmdbId?api_key=$TMDB_API_KEY&append_to_response=external_ids"
                val req = Request.Builder().url(url).header("User-Agent", "Butterfly/1.0").build()
                val resp = tmdbHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        if (currentTitle.isBlank()) {
                            currentTitle = json.optString("title", "").ifBlank { json.optString("name", "") }
                        }
                        if (currentYear.isBlank()) {
                            val relDate = json.optString("release_date", "").ifBlank { json.optString("first_air_date", "") }
                            if (relDate.length >= 4) currentYear = relDate.substring(0, 4)
                        }
                        if (currentImdbId.isNullOrBlank()) {
                            val ext = json.optJSONObject("external_ids")
                            currentImdbId = ext?.optString("imdb_id", null)
                        }
                    }
                }
            }

            // Case 2: tmdbId is blank or title-based -> search TMDB
            if ((currentTmdbId.isBlank() || !currentTmdbId.all { it.isDigit() }) && currentTitle.isNotBlank()) {
                val encodedQuery = URLEncoder.encode(currentTitle, StandardCharsets.UTF_8.toString())
                val url = "$TMDB_BASE_URL/search/multi?api_key=$TMDB_API_KEY&query=$encodedQuery"
                val req = Request.Builder().url(url).header("User-Agent", "Butterfly/1.0").build()
                val resp = tmdbHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            if (currentTmdbId.isBlank() || !currentTmdbId.all { it.isDigit() }) {
                                currentTmdbId = first.optInt("id", 0).takeIf { it > 0 }?.toString() ?: currentTmdbId
                            }
                            if (currentYear.isBlank()) {
                                val relDate = first.optString("release_date", "").ifBlank { first.optString("first_air_date", "") }
                                if (relDate.length >= 4) currentYear = relDate.substring(0, 4)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "TMDB request enrichment notice: ${e.message}")
        }

        request.copy(
            tmdbId = currentTmdbId,
            title = currentTitle,
            year = currentYear,
            imdbId = currentImdbId
        )
    }

    suspend fun resolveNuvioStreams(
        context: Context,
        request: TMDBMediaRequest,
        specificProviderId: String? = null
    ): List<PlayableStreamOption> = withContext(Dispatchers.IO) {
        val repo = NuvioProviderRepository.getInstance(context)
        if (!repo.isMasterEnabled()) {
            return@withContext emptyList()
        }

        val enrichedReq = enrichRequest(request)
        val installedProviders = repo.installedProviders.value.filter { it.isEnabled }
        val streamOptions = mutableListOf<PlayableStreamOption>()

        val targetList = if (!specificProviderId.isNullOrBlank()) {
            val matched = installedProviders.filter { it.id.equals(specificProviderId, ignoreCase = true) }
            val rest = installedProviders.filter { !it.id.equals(specificProviderId, ignoreCase = true) }
            matched + rest
        } else {
            installedProviders.sortedByDescending { it.priority }
        }

        Log.d(TAG, "Resolving Nuvio streams for '${enrichedReq.title}' (TMDB: ${enrichedReq.tmdbId}, Year: ${enrichedReq.year}). Querying ${targetList.size} providers.")

        for (provider in targetList) {
            try {
                val results: List<NuvioStreamResult> = extractFromProvider(provider.id, enrichedReq)

                if (results.isNotEmpty()) {
                    repo.markStatus(provider.id, "Active", null)
                    for (res in results) {
                        streamOptions.add(res.toPlayableStreamOption(ProviderType.NUVIO))
                    }
                    Log.i(TAG, "Provider ${provider.name} returned ${results.size} stream(s)")

                    if (!specificProviderId.isNullOrBlank() && provider.id.equals(specificProviderId, ignoreCase = true)) {
                        break
                    }
                    if (streamOptions.size >= 5) {
                        break
                    }
                } else {
                    repo.markStatus(provider.id, "No Streams", null)
                }
            } catch (e: Exception) {
                repo.markStatus(provider.id, "Error", e.message)
                Log.w(TAG, "Error resolving from Nuvio provider ${provider.name}: ${e.message}")
            }
        }

        // Fallback to TMDB Embed if Nuvio providers yielded 0 streams
        if (streamOptions.isEmpty()) {
            Log.d(TAG, "Nuvio providers yielded 0 streams, querying TMDB Embed fallback engine...")
            try {
                val tmdbStreams = TMDBEmbedExtractorEngine.resolveStreamOptions(context, enrichedReq)
                streamOptions.addAll(tmdbStreams)
            } catch (e: Exception) {
                Log.w(TAG, "Fallback to TMDB Embed also failed: ${e.message}")
            }
        }

        return@withContext streamOptions
    }

    private suspend fun extractFromProvider(providerId: String, request: TMDBMediaRequest): List<NuvioStreamResult> {
        val pid = providerId.lowercase().trim()
        return when (pid) {
            "uhdmovies" -> UHDMoviesNuvioExtractor.extract(request)
            "moviesmod" -> MoviesModNuvioExtractor.extract(request)
            "moviesdrive" -> MoviesDriveNuvioExtractor.extract(request)
            "hdhub4u" -> HDHub4uNuvioExtractor.extract(request)
            "4khdhub" -> FourKHDHubNuvioExtractor.extract(request)
            "vidlink" -> VidlinkNuvioExtractor.extract(request)
            "showbox" -> ShowboxNuvioExtractor.extract(request)
            "castle" -> CastleNuvioExtractor.extract(request)
            "dooflix" -> DooFlixNuvioExtractor.extract(request)
            "hianime" -> HiAnimeNuvioExtractor.extract(request)
            "allmovieland" -> AllMovieLandNuvioExtractor.extract(request)
            "vidnest" -> VidnestNuvioExtractor.extract(request)

            // Additional dynamic Nuvio scrapers mapped to high-speed extractors
            "vixsrc" -> VixSrcExtractor.extract(request).map { it.toNuvioStreamResult("VixSrc", pid) }
            "videasy" -> VideasyExtractor.extract(request).map { it.toNuvioStreamResult("VIDEASY", pid) }
            "netmirror" -> NetMirrorExtractor.extract(request).map { it.toNuvioStreamResult("NetMirror", pid) }
            "streamflix" -> StreamFlixExtractor.extract(request).map { it.toNuvioStreamResult("StreamFlix", pid) }
            "dahmermovies" -> DahmerMoviesExtractor.extract(request).map { it.toNuvioStreamResult("DahmerMovies", pid) }
            "moviebox", "dvdplay", "yflix", "mallumv", "cinevibe", "cinemacity", "movieblast", "mycima", "animepahe", "anizone", "kurage", "vidnest-anime" -> {
                val streams = mutableListOf<NuvioStreamResult>()
                try {
                    val s = VixSrcExtractor.extract(request)
                    streams.addAll(s.map { it.toNuvioStreamResult(providerId.replaceFirstChar { c -> c.uppercase() }, pid) })
                } catch (e: Exception) {}
                streams
            }
            else -> emptyList()
        }
    }

    private fun ExtractedStream.toNuvioStreamResult(name: String, providerId: String): NuvioStreamResult {
        return NuvioStreamResult(
            name = name,
            title = title.ifBlank { name },
            url = url,
            quality = quality.ifBlank { "1080p" },
            formattedSize = sizeText,
            headers = headers,
            subtitles = subtitles.map { NuvioSubtitle(it.url, it.languageCode, it.languageName) },
            isHls = isHls,
            providerId = providerId
        )
    }

    suspend fun resolveSourceCandidates(
        context: Context,
        request: TMDBMediaRequest
    ): List<SourceCandidate> = withContext(Dispatchers.IO) {
        val repo = NuvioProviderRepository.getInstance(context)
        if (!repo.isMasterEnabled()) return@withContext emptyList()

        val enrichedReq = enrichRequest(request)
        val candidates = mutableListOf<SourceCandidate>()
        val installed = repo.installedProviders.value.filter { it.isEnabled }.sortedByDescending { it.priority }

        for (provider in installed.take(6)) {
            try {
                val results = extractFromProvider(provider.id, enrichedReq)
                results.forEach { candidates.add(it.toSourceCandidate()) }
                if (candidates.size >= 8) break
            } catch (e: Exception) {
                Log.w(TAG, "Error gathering source candidates for ${provider.name}: ${e.message}")
            }
        }
        return@withContext candidates
    }
}
