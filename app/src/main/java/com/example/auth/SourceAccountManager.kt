package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Supported streaming and cloud platforms for independent account logins.
 */
enum class SourcePlatform(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val defaultLoginUrl: String,
    val primaryDomain: String,
    val brandColor: Color,
    val icon: ImageVector,
    val supportsPremiumStream: Boolean = true
) {
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        subtitle = "Personal feed, uploads, liked videos & playlists",
        defaultLoginUrl = "https://accounts.google.com/ServiceLogin?service=youtube",
        primaryDomain = ".youtube.com",
        brandColor = Color(0xFFFF0000),
        icon = Icons.Outlined.VideoLibrary,
        supportsPremiumStream = true
    ),
    GOOGLE_DRIVE(
        id = "googledrive",
        displayName = "Google Drive",
        subtitle = "Cloud video streaming vault & resumable uploads",
        defaultLoginUrl = "https://accounts.google.com/ServiceLogin?continue=https://drive.google.com",
        primaryDomain = ".google.com",
        brandColor = Color(0xFF4285F4),
        icon = Icons.Outlined.CloudQueue,
        supportsPremiumStream = true
    ),
    CRUNCHYROLL(
        id = "crunchyroll",
        displayName = "Crunchyroll Anime",
        subtitle = "Simulcasts, premium 1080p anime catalog & watchlist",
        defaultLoginUrl = "https://www.crunchyroll.com/login",
        primaryDomain = ".crunchyroll.com",
        brandColor = Color(0xFFF47521),
        icon = Icons.Outlined.Animation,
        supportsPremiumStream = true
    ),
    HOTSTAR(
        id = "hotstar",
        displayName = "Disney+ Hotstar",
        subtitle = "Blockbusters, live cricket & sports, Disney & HBO hits",
        defaultLoginUrl = "https://www.hotstar.com/in/mypage",
        primaryDomain = ".hotstar.com",
        brandColor = Color(0xFF001435),
        icon = Icons.Outlined.Tv,
        supportsPremiumStream = true
    ),
    SONYLIV(
        id = "sonyliv",
        displayName = "SonyLIV",
        subtitle = "LIV originals, UEFA Champions League, WWE & cinema",
        defaultLoginUrl = "https://www.sonyliv.com/signin",
        primaryDomain = ".sonyliv.com",
        brandColor = Color(0xFF003087),
        icon = Icons.Outlined.LiveTv,
        supportsPremiumStream = true
    ),
    TELEGRAM(
        id = "telegram",
        displayName = "Telegram Cloud",
        subtitle = "Direct cloud streaming from Telegram channels & bot bridge",
        defaultLoginUrl = "https://web.telegram.org",
        primaryDomain = ".telegram.org",
        brandColor = Color(0xFF229ED9),
        icon = Icons.Outlined.Send,
        supportsPremiumStream = false
    );

    companion object {
        fun fromId(id: String): SourcePlatform? {
            return entries.find { it.id.equals(id, ignoreCase = true) }
        }
    }
}

/**
 * Represents an isolated session for a specific streaming source.
 */
data class SourceAccountSession(
    val platformId: String,
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val cookies: String? = null,
    val authToken: String? = null,
    val isPremium: Boolean = false,
    val planType: String = "Free / Guest",
    val lastLoginTimestamp: Long = 0L,
    val preferPremiumStream: Boolean = true,
    val fallbackToFreeResolvers: Boolean = true // CRITICAL: Guarantees existing free resolvers remain safe!
)

/**
 * Manages multi-account logins, sessions, and cookies per platform (Grayjay model).
 * Each source maintains an isolated session storage so accounts never interfere.
 */
object SourceAccountManager {

    private const val PREFS_NAME = "butterfly_source_accounts_vault"
    private var prefs: SharedPreferences? = null

    private val _sessions = MutableStateFlow<Map<String, SourceAccountSession>>(emptyMap())
    val sessions: StateFlow<Map<String, SourceAccountSession>> = _sessions.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadAllSessions()
        }
    }

    private fun loadAllSessions() {
        val sp = prefs ?: return
        val map = mutableMapOf<String, SourceAccountSession>()
        for (platform in SourcePlatform.entries) {
            val jsonStr = sp.getString("session_${platform.id}", null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val json = JSONObject(jsonStr)
                    map[platform.id] = SourceAccountSession(
                        platformId = platform.id,
                        isLoggedIn = json.optBoolean("isLoggedIn", false),
                        username = json.optString("username", ""),
                        displayName = json.optString("displayName", ""),
                        email = json.optString("email", ""),
                        avatarUrl = json.optString("avatarUrl", "").takeIf { it.isNotBlank() },
                        cookies = json.optString("cookies", "").takeIf { it.isNotBlank() },
                        authToken = json.optString("authToken", "").takeIf { it.isNotBlank() },
                        isPremium = json.optBoolean("isPremium", false),
                        planType = json.optString("planType", "Free / Guest"),
                        lastLoginTimestamp = json.optLong("lastLoginTimestamp", 0L),
                        preferPremiumStream = json.optBoolean("preferPremiumStream", true),
                        fallbackToFreeResolvers = json.optBoolean("fallbackToFreeResolvers", true)
                    )
                } catch (_: Exception) {
                    map[platform.id] = SourceAccountSession(platformId = platform.id)
                }
            } else {
                map[platform.id] = SourceAccountSession(platformId = platform.id)
            }
        }
        _sessions.value = map
    }

    fun getSession(platformId: String): SourceAccountSession {
        return _sessions.value[platformId] ?: SourceAccountSession(platformId = platformId)
    }

    fun saveSession(session: SourceAccountSession) {
        val sp = prefs ?: return
        try {
            val json = JSONObject().apply {
                put("platformId", session.platformId)
                put("isLoggedIn", session.isLoggedIn)
                put("username", session.username)
                put("displayName", session.displayName)
                put("email", session.email)
                put("avatarUrl", session.avatarUrl ?: "")
                put("cookies", session.cookies ?: "")
                put("authToken", session.authToken ?: "")
                put("isPremium", session.isPremium)
                put("planType", session.planType)
                put("lastLoginTimestamp", session.lastLoginTimestamp)
                put("preferPremiumStream", session.preferPremiumStream)
                put("fallbackToFreeResolvers", session.fallbackToFreeResolvers)
            }
            sp.edit().putString("session_${session.platformId}", json.toString()).apply()

            val updated = _sessions.value.toMutableMap()
            updated[session.platformId] = session
            _sessions.value = updated
        } catch (_: Exception) {}
    }

    /**
     * Imports extracted browser cookies and saves the active session.
     */
    fun onLoginSuccess(
        platformId: String,
        cookies: String,
        username: String = "",
        isPremium: Boolean = true,
        planType: String = "Premium Member"
    ) {
        val current = getSession(platformId)
        val updated = current.copy(
            isLoggedIn = true,
            cookies = cookies,
            username = username.ifBlank { "Logged In User" },
            displayName = username.ifBlank { SourcePlatform.fromId(platformId)?.displayName ?: "Account" },
            isPremium = isPremium,
            planType = planType,
            lastLoginTimestamp = System.currentTimeMillis()
        )
        saveSession(updated)
    }

    /**
     * Clears cookies and resets the session for a specific source.
     */
    fun logout(platformId: String) {
        val sp = prefs ?: return
        sp.edit().remove("session_$platformId").apply()

        // Clear webview cookies for the platform domain if possible
        try {
            val platform = SourcePlatform.fromId(platformId)
            if (platform != null) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setCookie(platform.primaryDomain, "")
                cookieManager.flush()
            }
        } catch (_: Exception) {}

        val updated = _sessions.value.toMutableMap()
        updated[platformId] = SourceAccountSession(platformId = platformId)
        _sessions.value = updated
    }

    fun setPreferPremiumStream(platformId: String, prefer: Boolean) {
        val current = getSession(platformId)
        saveSession(current.copy(preferPremiumStream = prefer))
    }

    fun setFallbackToFreeResolvers(platformId: String, fallback: Boolean) {
        val current = getSession(platformId)
        saveSession(current.copy(fallbackToFreeResolvers = fallback))
    }

    /**
     * Retrieves cookies to attach when requesting streams from a provider.
     */
    fun getCookiesForStream(platformId: String): String? {
        val session = getSession(platformId)
        if (session.isLoggedIn && !session.cookies.isNullOrBlank()) {
            return session.cookies
        }
        return null
    }

    fun isSourceLoggedIn(platformId: String): Boolean {
        return getSession(platformId).isLoggedIn
    }

    fun isSourcePremium(platformId: String): Boolean {
        val s = getSession(platformId)
        return s.isLoggedIn && s.isPremium
    }
}
