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
    private const val KEY_JAVINIZER_ENABLED = "javinizer_enabled"
    private const val KEY_JAVINIZER_API_URL = "javinizer_api_url"
    private const val KEY_JAVINIZER_TIMEOUT_SECONDS = "javinizer_timeout_seconds"
    private const val KEY_JAVINIZER_FALLBACK_ENABLED = "javinizer_fallback_enabled"
    private const val KEY_MEDIAFLOW_ENABLED = "mediaflow_enabled"
    private const val KEY_MEDIAFLOW_SERVER_URL = "mediaflow_server_url"
    private const val KEY_MEDIAFLOW_API_PASSWORD = "mediaflow_api_password"
    private const val KEY_MEDIAFLOW_LIGHT_MODE = "mediaflow_light_mode"
    private const val KEY_JAVAPI_ENABLED = "javapi_enabled"
    private const val KEY_JAVAPI_SERVER_URL = "javapi_server_url"
    private const val KEY_YARR_ENABLED = "yarr_enabled"
    private const val KEY_YARR_SERVER_URL = "yarr_server_url"
    private const val KEY_MAGNETIO_ENABLED = "magnetio_enabled"

    // Default working keys & mirrors
    const val DEFAULT_TMDB_API_KEY = "b4ef3b290130df4d8de63d410db2bdfc"
    const val DEFAULT_TMDB_READ_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIzMTU1ZmRiNDk3Zjc1NzVhMTQ0ZjI2YWRlYmNiZjk4MCIsIm5iZiI6MTc3NTcwNjIzNy43NDUsInN1YiI6IjY5ZDcyMDdkNmJiYTRiYzAzOTEwNjI2ZSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ._4WdklzSx9za_YAGeMlABQ1jMEOPCLa6mEWtGQLqrw0"
    const val DEFAULT_TRAKT_CLIENT_ID = "9434714b83b9485c37a7865f58ad942ec7e1f64c372d0cf304f6ac882d307bb7"
    const val DEFAULT_WATCHMODE_API_KEY = "wm_-yvlXIr6cXJxkpC90gpkhfp5VHMWhXJH7PJ1ht9OLjg"
    const val DEFAULT_SUBDL_API_KEY = "subdl_Mp42hcrZJOddEWEGyUjzp1q2A1NsdWxkAd2pDD8PCwg"
    const val DEFAULT_OPENSUBTITLES_API_KEY = "p1Q8N8Z6eB0s6Z6A5t8Y4U1I3O9P2L5K"
    const val DEFAULT_TORRENTIO_BASE_URL = "https://torrentio.strem.fun"
    const val DEFAULT_VEGA_SERVER_URL = "https://vega.strem.fun"
    const val DEFAULT_JAVINIZER_API_URL = "http://localhost:8765"
    const val DEFAULT_JAVINIZER_TIMEOUT_SECONDS = 15
    const val DEFAULT_JAVINIZER_ENABLED = true
    const val DEFAULT_JAVINIZER_FALLBACK_ENABLED = true
    const val DEFAULT_MEDIAFLOW_ENABLED = false
    const val DEFAULT_MEDIAFLOW_SERVER_URL = "http://localhost:8888"
    const val DEFAULT_MEDIAFLOW_LIGHT_MODE = true
    const val DEFAULT_JAVAPI_ENABLED = true
    const val DEFAULT_JAVAPI_SERVER_URL = "https://javapi.vercel.app"
    const val DEFAULT_YARR_ENABLED = true
    const val DEFAULT_YARR_SERVER_URL = "https://yarr.fly.dev"
    const val DEFAULT_MAGNETIO_ENABLED = true
    val DEFAULT_PO_TOKEN_SERVER_URL: String get() = com.example.BuildConfig.PO_TOKEN_SERVER_URL

    @Volatile
    private var cachedMediaFlowEnabled: Boolean = DEFAULT_MEDIAFLOW_ENABLED

    @Volatile
    private var cachedMediaFlowServerUrl: String? = null

    @Volatile
    private var cachedMediaFlowApiPassword: String? = null

    @Volatile
    private var cachedMediaFlowLightMode: Boolean = DEFAULT_MEDIAFLOW_LIGHT_MODE

    @Volatile
    private var cachedJavapiEnabled: Boolean = DEFAULT_JAVAPI_ENABLED

    @Volatile
    private var cachedJavapiServerUrl: String? = null

    @Volatile
    private var cachedYarrEnabled: Boolean = DEFAULT_YARR_ENABLED

    @Volatile
    private var cachedYarrServerUrl: String? = null

    @Volatile
    private var cachedMagnetioEnabled: Boolean = DEFAULT_MAGNETIO_ENABLED

    @Volatile
    private var cachedTorznabUrl: String? = null

    @Volatile
    private var cachedTorznabApiKey: String? = null

    @Volatile
    private var cachedJavinizerEnabled: Boolean = DEFAULT_JAVINIZER_ENABLED

    @Volatile
    private var cachedJavinizerApiUrl: String? = null

    @Volatile
    private var cachedJavinizerTimeoutSeconds: Int = DEFAULT_JAVINIZER_TIMEOUT_SECONDS

    @Volatile
    private var cachedJavinizerFallbackEnabled: Boolean = DEFAULT_JAVINIZER_FALLBACK_ENABLED

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
        cachedJavinizerEnabled = prefs.getBoolean(KEY_JAVINIZER_ENABLED, DEFAULT_JAVINIZER_ENABLED)
        cachedJavinizerApiUrl = prefs.getString(KEY_JAVINIZER_API_URL, null)?.ifBlank { null } ?: DEFAULT_JAVINIZER_API_URL
        cachedJavinizerTimeoutSeconds = prefs.getInt(KEY_JAVINIZER_TIMEOUT_SECONDS, DEFAULT_JAVINIZER_TIMEOUT_SECONDS)
        cachedJavinizerFallbackEnabled = prefs.getBoolean(KEY_JAVINIZER_FALLBACK_ENABLED, DEFAULT_JAVINIZER_FALLBACK_ENABLED)
        cachedMediaFlowEnabled = prefs.getBoolean(KEY_MEDIAFLOW_ENABLED, DEFAULT_MEDIAFLOW_ENABLED)
        cachedMediaFlowServerUrl = prefs.getString(KEY_MEDIAFLOW_SERVER_URL, null)?.ifBlank { null } ?: DEFAULT_MEDIAFLOW_SERVER_URL
        cachedMediaFlowApiPassword = prefs.getString(KEY_MEDIAFLOW_API_PASSWORD, null)?.ifBlank { null } ?: ""
        cachedMediaFlowLightMode = prefs.getBoolean(KEY_MEDIAFLOW_LIGHT_MODE, DEFAULT_MEDIAFLOW_LIGHT_MODE)
        cachedJavapiEnabled = prefs.getBoolean(KEY_JAVAPI_ENABLED, DEFAULT_JAVAPI_ENABLED)
        cachedJavapiServerUrl = prefs.getString(KEY_JAVAPI_SERVER_URL, null)?.ifBlank { null } ?: DEFAULT_JAVAPI_SERVER_URL
        cachedYarrEnabled = prefs.getBoolean(KEY_YARR_ENABLED, DEFAULT_YARR_ENABLED)
        cachedYarrServerUrl = prefs.getString(KEY_YARR_SERVER_URL, null)?.ifBlank { null } ?: DEFAULT_YARR_SERVER_URL
        cachedMagnetioEnabled = prefs.getBoolean(KEY_MAGNETIO_ENABLED, DEFAULT_MAGNETIO_ENABLED)
    }

    fun isMediaFlowEnabled(): Boolean = cachedMediaFlowEnabled

    fun setMediaFlowEnabled(context: Context, enabled: Boolean) {
        cachedMediaFlowEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MEDIAFLOW_ENABLED, enabled)
            .apply()
    }

    fun getMediaFlowServerUrl(): String = cachedMediaFlowServerUrl ?: DEFAULT_MEDIAFLOW_SERVER_URL

    fun setMediaFlowServerUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedMediaFlowServerUrl = clean.ifBlank { DEFAULT_MEDIAFLOW_SERVER_URL }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MEDIAFLOW_SERVER_URL, clean)
            .apply()
    }

    fun getMediaFlowApiPassword(): String = cachedMediaFlowApiPassword ?: ""

    fun setMediaFlowApiPassword(context: Context, pass: String) {
        val clean = pass.trim()
        cachedMediaFlowApiPassword = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MEDIAFLOW_API_PASSWORD, clean)
            .apply()
    }

    fun isMediaFlowLightMode(): Boolean = cachedMediaFlowLightMode

    fun setMediaFlowLightMode(context: Context, light: Boolean) {
        cachedMediaFlowLightMode = light
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MEDIAFLOW_LIGHT_MODE, light)
            .apply()
    }

    fun isJavapiEnabled(): Boolean = cachedJavapiEnabled

    fun setJavapiEnabled(context: Context, enabled: Boolean) {
        cachedJavapiEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_JAVAPI_ENABLED, enabled)
            .apply()
    }

    fun getJavapiServerUrl(): String = cachedJavapiServerUrl ?: DEFAULT_JAVAPI_SERVER_URL

    fun setJavapiServerUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedJavapiServerUrl = clean.ifBlank { DEFAULT_JAVAPI_SERVER_URL }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_JAVAPI_SERVER_URL, clean)
            .apply()
    }

    fun isYarrEnabled(): Boolean = cachedYarrEnabled

    fun setYarrEnabled(context: Context, enabled: Boolean) {
        cachedYarrEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_YARR_ENABLED, enabled)
            .apply()
    }

    fun getYarrServerUrl(): String = cachedYarrServerUrl ?: DEFAULT_YARR_SERVER_URL

    fun setYarrServerUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedYarrServerUrl = clean.ifBlank { DEFAULT_YARR_SERVER_URL }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_YARR_SERVER_URL, clean)
            .apply()
    }

    fun isMagnetioEnabled(): Boolean = cachedMagnetioEnabled

    fun setMagnetioEnabled(context: Context, enabled: Boolean) {
        cachedMagnetioEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MAGNETIO_ENABLED, enabled)
            .apply()
    }

    fun isJavinizerEnabled(): Boolean = cachedJavinizerEnabled

    fun setJavinizerEnabled(context: Context, enabled: Boolean) {
        cachedJavinizerEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_JAVINIZER_ENABLED, enabled)
            .apply()
    }

    fun getJavinizerApiUrl(): String = cachedJavinizerApiUrl ?: DEFAULT_JAVINIZER_API_URL

    fun setJavinizerApiUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        cachedJavinizerApiUrl = clean.ifBlank { DEFAULT_JAVINIZER_API_URL }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_JAVINIZER_API_URL, clean)
            .apply()
    }

    fun getJavinizerTimeoutSeconds(): Int = cachedJavinizerTimeoutSeconds

    fun setJavinizerTimeoutSeconds(context: Context, seconds: Int) {
        val validSec = if (seconds in 2..120) seconds else DEFAULT_JAVINIZER_TIMEOUT_SECONDS
        cachedJavinizerTimeoutSeconds = validSec
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_JAVINIZER_TIMEOUT_SECONDS, validSec)
            .apply()
    }

    fun isJavinizerFallbackEnabled(): Boolean = cachedJavinizerFallbackEnabled

    fun setJavinizerFallbackEnabled(context: Context, enabled: Boolean) {
        cachedJavinizerFallbackEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_JAVINIZER_FALLBACK_ENABLED, enabled)
            .apply()
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
