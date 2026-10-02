package com.example.extractor.nuvio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NuvioDomainManager {
    private const val TAG = "NuvioDomainManager"
    private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val domainCache = ConcurrentHashMap<String, String>()
    private var lastFetchTime = 0L
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    private val FALLBACK_DOMAINS = mapOf(
        "uhdmovies" to "https://uhdmovies.my",
        "moviesmod" to "https://moviesmod.ai.in",
        "moviesdrive" to "https://new5.moviesdrive.christmas",
        "hdhub4u" to "https://new6.hdhub4u.cl",
        "4khdhub" to "https://4khdhub.one",
        "showbox" to "https://id-mapping-api-showbox-proxy.hf.space",
        "vidlink" to "https://vidlink.pro",
        "castle" to "https://api.hlowb.com",
        "dooflix" to "https://panel.watchkaroabhi.com",
        "allmovieland" to "https://allmovieland.one",
        "mycima" to "https://mycima.red",
        "hianime" to "https://megacloud.bloggy.click",
        "kurage" to "https://kurage.live",
        "anizone" to "https://anizone.to",
        "vidnest" to "https://vidnest.fun",
        "vidnest-anime" to "https://backend.vidnest.fun",
        "dahmermovies" to "https://dahmermovies.top",
        "netmirror" to "https://netmirror.app",
        "streamflix" to "https://streamflix.one",
        "moviebox" to "https://moviebox.ph",
        "dvdplay" to "https://dvdplay.live",
        "yflix" to "https://yflix.to",
        "videasy" to "https://player.videasy.net",
        "mallumv" to "https://mallumv.lat",
        "cinevibe" to "https://cinevibe.asia",
        "cinemacity" to "https://cinema-city.pl",
        "animepahe" to "https://animepahe.ru",
        "vixsrc" to "https://vixsrc.to"
    )

    suspend fun getDomain(providerId: String): String = withContext(Dispatchers.IO) {
        val cleanKey = providerId.lowercase().trim()
        val cached = domainCache[cleanKey]
        if (cached != null && (System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS)) {
            return@withContext cached
        }

        refreshDomainsOnlineIfNeeded()
        return@withContext domainCache[cleanKey] ?: FALLBACK_DOMAINS[cleanKey] ?: "https://$cleanKey.com"
    }

    private fun refreshDomainsOnlineIfNeeded() {
        if (System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS && domainCache.isNotEmpty()) {
            return
        }
        try {
            val req = Request.Builder()
                .url(DOMAINS_URL)
                .header("User-Agent", "Butterfly-Nuvio/1.0")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = json.optString(key, "")
                        if (value.isNotBlank() && value.startsWith("http")) {
                            domainCache[key.lowercase()] = value
                        }
                    }
                    lastFetchTime = System.currentTimeMillis()
                    Log.d(TAG, "Refreshed ${domainCache.size} dynamic Nuvio domains from online config.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to fetch online domains config, using fallbacks: ${e.message}")
        }
    }
}
