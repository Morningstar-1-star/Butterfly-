package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized application configuration and external API keys.
 * Supports runtime customization via SharedPreferences / Settings.
 */
object AppConfig {

    private const val PREFS_NAME = "butterfly_api_configs"
    private const val KEY_TMDB_API_KEY = "tmdb_api_key"
    private const val KEY_SUBDL_API_KEY = "subdl_api_key"
    private const val KEY_OPENSUBTITLES_API_KEY = "opensubtitles_api_key"
    private const val KEY_TORRENTIO_BASE_URL = "torrentio_base_url"
    private const val KEY_POTOKEN_SERVER_URL = "potoken_server_url"
    private const val KEY_CUSTOM_POTOKEN = "custom_potoken"
    private const val KEY_VEGA_SERVER_URL = "vega_server_url"
    private const val KEY_DEBRID_API_KEY = "debrid_api_key"
    private const val KEY_TORRENT_PROXY_HOST = "torrent_proxy_host"
    private const val KEY_TORRENT_PROXY_PORT = "torrent_proxy_port"
    private const val KEY_TORRENT_PROXY_USER = "torrent_proxy_user"
    private const val KEY_TORRENT_PROXY_PASS = "torrent_proxy_pass"
    private const val KEY_TORRENT_PROXY_ENABLED = "torrent_proxy_enabled"
    private const val KEY_TORZNAB_BASE_URL = "torznab_base_url"
    private const val KEY_TORZNAB_API_KEY = "torznab_api_key"

    // Default working keys & mirrors
    const val DEFAULT_TMDB_API_KEY = "b4ef3b290130df4d8de63d410db2bdfc"
    const val DEFAULT_SUBDL_API_KEY = "subdl_Mp42hcrZJOddEWEGyUjzp1q2A1NsdWxkAd2pDD8PCwg"
    const val DEFAULT_OPENSUBTITLES_API_KEY = "p1Q8N8Z6eB0s6Z6A5t8Y4U1I3O9P2L5K"
    const val DEFAULT_TORRENTIO_BASE_URL = "https://torrentio.strem.fun"
    const val DEFAULT_VEGA_SERVER_URL = "https://vega.strem.fun"
    val DEFAULT_PO_TOKEN_SERVER_URL: String get() = com.example.BuildConfig.PO_TOKEN_SERVER_URL

    @Volatile
    private var cachedTorznabUrl: String? = null

    @Volatile
    private var cachedTorznabApiKey: String? = null

    @Volatile
    private var cachedTmdbKey: String? = null

    @Volatile
    private var cachedSubdlKey: String? = null

    @Volatile
    private var cachedOpenSubKey: String? = null

    @Volatile
    private var cachedTorrentioUrl: String? = null

    @Volatile
    private var cachedPoTokenServerUrl: String? = null

    @Volatile
    private var cachedCustomPoToken: String? = null

    @Volatile
    private var cachedVegaUrl: String? = null

    @Volatile
    private var cachedDebridKey: String? = null

    @Volatile
    private var cachedProxyHost: String? = null

    @Volatile
    private var cachedProxyPort: Int = 1080

    @Volatile
    private var cachedProxyUser: String? = null

    @Volatile
    private var cachedProxyPass: String? = null

    @Volatile
    private var cachedProxyEnabled: Boolean = false

    val TMDB_API_KEY: String
        get() = cachedTmdbKey ?: DEFAULT_TMDB_API_KEY

    const val TMDB_IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_BASE_W342 = "https://image.tmdb.org/t/p/w342"
    const val TMDB_IMAGE_BASE_W185 = "https://image.tmdb.org/t/p/w185"
    const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedTmdbKey = prefs.getString(KEY_TMDB_API_KEY, null)?.ifBlank { null } ?: DEFAULT_TMDB_API_KEY
        cachedSubdlKey = prefs.getString(KEY_SUBDL_API_KEY, null)?.ifBlank { null } ?: DEFAULT_SUBDL_API_KEY
        cachedOpenSubKey = prefs.getString(KEY_OPENSUBTITLES_API_KEY, null)?.ifBlank { null } ?: DEFAULT_OPENSUBTITLES_API_KEY
        cachedTorrentioUrl = prefs.getString(KEY_TORRENTIO_BASE_URL, null)?.ifBlank { null } ?: DEFAULT_TORRENTIO_BASE_URL
        cachedPoTokenServerUrl = prefs.getString(KEY_POTOKEN_SERVER_URL, null)?.ifBlank { null } ?: DEFAULT_PO_TOKEN_SERVER_URL
        cachedCustomPoToken = prefs.getString(KEY_CUSTOM_POTOKEN, null)?.ifBlank { null } ?: ""
        cachedVegaUrl = prefs.getString(KEY_VEGA_SERVER_URL, null)?.ifBlank { null } ?: DEFAULT_VEGA_SERVER_URL
        cachedDebridKey = prefs.getString(KEY_DEBRID_API_KEY, null)?.ifBlank { null } ?: ""
        cachedProxyHost = prefs.getString(KEY_TORRENT_PROXY_HOST, null)?.ifBlank { null } ?: ""
        cachedProxyPort = prefs.getInt(KEY_TORRENT_PROXY_PORT, 1080)
        cachedProxyUser = prefs.getString(KEY_TORRENT_PROXY_USER, null)?.ifBlank { null } ?: ""
        cachedProxyPass = prefs.getString(KEY_TORRENT_PROXY_PASS, null)?.ifBlank { null } ?: ""
        cachedProxyEnabled = prefs.getBoolean(KEY_TORRENT_PROXY_ENABLED, false)
        cachedTorznabUrl = prefs.getString(KEY_TORZNAB_BASE_URL, null)?.ifBlank { null } ?: ""
        cachedTorznabApiKey = prefs.getString(KEY_TORZNAB_API_KEY, null)?.ifBlank { null } ?: ""
    }

    fun getTorznabBaseUrl(): String = cachedTorznabUrl ?: ""

    fun setTorznabBaseUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedTorznabUrl = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TORZNAB_BASE_URL, clean)
            .apply()
    }

    fun getTorznabApiKey(): String = cachedTorznabApiKey ?: ""

    fun setTorznabApiKey(context: Context, key: String) {
        val clean = key.trim()
        cachedTorznabApiKey = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TORZNAB_API_KEY, clean)
            .apply()
    }

    fun getTmdbApiKey(): String = cachedTmdbKey ?: DEFAULT_TMDB_API_KEY

    fun setTmdbApiKey(context: Context, key: String) {
        val clean = key.trim()
        cachedTmdbKey = clean.ifBlank { DEFAULT_TMDB_API_KEY }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TMDB_API_KEY, clean)
            .apply()
    }

    fun getSubdlApiKey(): String = cachedSubdlKey ?: DEFAULT_SUBDL_API_KEY

    fun setSubdlApiKey(context: Context, key: String) {
        val clean = key.trim()
        cachedSubdlKey = clean.ifBlank { DEFAULT_SUBDL_API_KEY }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SUBDL_API_KEY, clean)
            .apply()
    }

    fun getOpenSubtitlesApiKey(): String = cachedOpenSubKey ?: DEFAULT_OPENSUBTITLES_API_KEY

    fun setOpenSubtitlesApiKey(context: Context, key: String) {
        val clean = key.trim()
        cachedOpenSubKey = clean.ifBlank { DEFAULT_OPENSUBTITLES_API_KEY }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_OPENSUBTITLES_API_KEY, clean)
            .apply()
    }

    fun getTorrentioBaseUrl(): String = cachedTorrentioUrl ?: DEFAULT_TORRENTIO_BASE_URL

    fun setTorrentioBaseUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedTorrentioUrl = clean.ifBlank { DEFAULT_TORRENTIO_BASE_URL }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TORRENTIO_BASE_URL, clean)
            .apply()
    }

    fun getPoTokenServerUrl(): String = cachedPoTokenServerUrl ?: DEFAULT_PO_TOKEN_SERVER_URL

    fun setPoTokenServerUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedPoTokenServerUrl = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_POTOKEN_SERVER_URL, clean)
            .apply()
    }

    fun getCustomPoToken(): String = cachedCustomPoToken ?: ""

    fun setCustomPoToken(context: Context, token: String) {
        val clean = token.trim()
        cachedCustomPoToken = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CUSTOM_POTOKEN, clean)
            .apply()
    }

    fun getVegaServerUrl(): String = cachedVegaUrl ?: DEFAULT_VEGA_SERVER_URL

    fun setVegaServerUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedVegaUrl = clean.ifBlank { DEFAULT_VEGA_SERVER_URL }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_VEGA_SERVER_URL, clean)
            .apply()
    }

    fun getDebridApiKey(): String = cachedDebridKey ?: ""

    fun setDebridApiKey(context: Context, key: String) {
        val clean = key.trim()
        cachedDebridKey = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DEBRID_API_KEY, clean)
            .apply()
    }

    fun isTorrentProxyEnabled(): Boolean = cachedProxyEnabled

    fun getTorrentProxyHost(): String = cachedProxyHost ?: ""

    fun getTorrentProxyPort(): Int = cachedProxyPort

    fun getTorrentProxyUser(): String = cachedProxyUser ?: ""

    fun getTorrentProxyPass(): String = cachedProxyPass ?: ""

    fun setTorrentProxyConfig(
        context: Context,
        enabled: Boolean,
        host: String,
        port: Int,
        user: String = "",
        pass: String = ""
    ) {
        cachedProxyEnabled = enabled
        cachedProxyHost = host.trim()
        cachedProxyPort = if (port in 1..65535) port else 1080
        cachedProxyUser = user.trim()
        cachedProxyPass = pass.trim()

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TORRENT_PROXY_ENABLED, cachedProxyEnabled)
            .putString(KEY_TORRENT_PROXY_HOST, cachedProxyHost)
            .putInt(KEY_TORRENT_PROXY_PORT, cachedProxyPort)
            .putString(KEY_TORRENT_PROXY_USER, cachedProxyUser)
            .putString(KEY_TORRENT_PROXY_PASS, cachedProxyPass)
            .apply()
    }
}
