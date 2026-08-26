package com.example.util

/**
 * Centralized application configuration and external API keys.
 * 
 * Note for production deployment:
 * Rotate all exposed client API keys in the Secrets panel / BuildConfig.
 */
object AppConfig {
    /**
     * TMDB Public API Key for movie/TV metadata enrichment.
     * Centralized here to avoid scattering across multiple files.
     */
    const val TMDB_API_KEY = ""

    const val TMDB_IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_BASE_W342 = "https://image.tmdb.org/t/p/w342"
    const val TMDB_IMAGE_BASE_W185 = "https://image.tmdb.org/t/p/w185"
    const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
}
