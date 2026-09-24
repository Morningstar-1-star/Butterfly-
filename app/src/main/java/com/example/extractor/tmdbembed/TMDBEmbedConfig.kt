package com.example.extractor.tmdbembed

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

data class TMDBSourceHealth(
    val source: TMDBEmbedSource,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastError: String? = null,
    val lastSuccessTimeMs: Long = 0L,
    val lastFailureTimeMs: Long = 0L
) {
    val statusText: String
        get() = when {
            failureCount > 0 && successCount == 0 -> "Failing"
            failureCount > 3 && lastFailureTimeMs > lastSuccessTimeMs -> "Degraded"
            successCount > 0 -> "Online"
            else -> "Ready"
        }
}

object TMDBEmbedConfig {

    private const val PREFS_NAME = "butterfly_tmdb_embed_prefs"
    private const val KEY_MASTER_ENABLED = "tmdb_embed_master_enabled"
    private const val KEY_DEFAULT_SOURCE = "tmdb_embed_default_source"
    private const val KEY_FALLBACK_ENABLED = "tmdb_embed_fallback_enabled"
    private const val PREFIX_SOURCE_ENABLED = "tmdb_embed_source_enabled_"

    private val healthMap = ConcurrentHashMap<TMDBEmbedSource, TMDBSourceHealth>()

    @Volatile
    var cachedDefaultSourceName: String = "VixSrc"

    private fun getPrefs(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val def = prefs.getString(KEY_DEFAULT_SOURCE, "vixsrc")
        cachedDefaultSourceName = TMDBEmbedSource.fromId(def ?: "vixsrc")?.displayName ?: "VixSrc"
        return prefs
    }

    fun isMasterEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MASTER_ENABLED, true)
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun isSourceEnabled(context: Context, source: TMDBEmbedSource): Boolean {
        return getPrefs(context).getBoolean(PREFIX_SOURCE_ENABLED + source.id, true)
    }

    fun setSourceEnabled(context: Context, source: TMDBEmbedSource, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREFIX_SOURCE_ENABLED + source.id, enabled).apply()
    }

    fun getDefaultSource(context: Context): TMDBEmbedSource {
        val stored = getPrefs(context).getString(KEY_DEFAULT_SOURCE, null)
        val src = stored?.let { TMDBEmbedSource.fromId(it) } ?: TMDBEmbedSource.VIXSRC
        cachedDefaultSourceName = src.displayName
        return src
    }

    fun setDefaultSource(context: Context, source: TMDBEmbedSource) {
        cachedDefaultSourceName = source.displayName
        getPrefs(context).edit()
            .putString(KEY_DEFAULT_SOURCE, source.id)
            .putBoolean(PREFIX_SOURCE_ENABLED + source.id, true)
            .apply()
    }

    fun isFallbackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FALLBACK_ENABLED, true)
    }

    fun setFallbackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FALLBACK_ENABLED, enabled).apply()
    }

    /**
     * Returns ordered list of active sources for a query:
     * 1. The user's default source (if enabled).
     * 2. All other enabled sources in priority order.
     */
    fun getPrioritizedSources(context: Context): List<TMDBEmbedSource> {
        val defaultSource = getDefaultSource(context)
        val enabledSources = TMDBEmbedSource.allSources.filter { isSourceEnabled(context, it) }

        val result = mutableListOf<TMDBEmbedSource>()
        if (enabledSources.contains(defaultSource)) {
            result.add(defaultSource)
        }
        for (source in enabledSources) {
            if (source != defaultSource && !result.contains(source)) {
                result.add(source)
            }
        }
        return result
    }

    fun markSuccess(source: TMDBEmbedSource) {
        val current = healthMap[source] ?: TMDBSourceHealth(source)
        healthMap[source] = current.copy(
            successCount = current.successCount + 1,
            lastSuccessTimeMs = System.currentTimeMillis()
        )
    }

    fun markFailure(source: TMDBEmbedSource, error: String) {
        val current = healthMap[source] ?: TMDBSourceHealth(source)
        healthMap[source] = current.copy(
            failureCount = current.failureCount + 1,
            lastError = error,
            lastFailureTimeMs = System.currentTimeMillis()
        )
    }

    fun getSourceHealth(source: TMDBEmbedSource): TMDBSourceHealth {
        return healthMap[source] ?: TMDBSourceHealth(source)
    }

    fun resetHealthStats() {
        healthMap.clear()
    }
}
