package com.example.supabase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

object SupabaseAuthManager {

    private const val TAG = "SupabaseAuthManager"
    private const val PREFS_AUTH = "butterfly_supabase_auth"
    private const val KEY_SESSION_JSON = "session_json"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val refreshMutex = Mutex()

    @Volatile
    private var appContext: Context? = null
    private var client: SupabaseClient? = null

    private val _session = MutableStateFlow<SupabaseSession?>(null)
    val session: StateFlow<SupabaseSession?> = _session.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<SupabaseUser?>(null)
    val currentUser: StateFlow<SupabaseUser?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        client = SupabaseClient(app)
        restoreSessionFromPrefs(app)
    }

    private fun getClient(): SupabaseClient {
        return client ?: synchronized(this) {
            val ctx = appContext ?: throw IllegalStateException("SupabaseAuthManager not initialized with Context")
            val c = SupabaseClient(ctx)
            client = c
            c
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
    }

    private fun restoreSessionFromPrefs(context: Context) {
        try {
            val jsonStr = getPrefs(context).getString(KEY_SESSION_JSON, null) ?: return
            val json = JSONObject(jsonStr)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val expiresAt = json.optLong("expires_at", 0L)
            val userObj = json.optJSONObject("user")

            if (accessToken.isNotBlank() && userObj != null) {
                val user = SupabaseUser(
                    id = userObj.optString("id"),
                    email = userObj.optString("email"),
                    createdAt = userObj.optString("created_at")
                )
                val restored = SupabaseSession(accessToken, refreshToken, user, expiresAt)
                _session.value = restored
                _currentUser.value = user
                _isLoggedIn.value = true
                Log.i(TAG, "Restored Supabase session for ${user.email}")

                // If close to expiry, refresh in background
                if (System.currentTimeMillis() > expiresAt - 300_000L) {
                    scope.launch { refreshSessionInternal() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore session", e)
        }
    }

    private fun persistSession(session: SupabaseSession?) {
        val ctx = appContext ?: return
        val prefs = getPrefs(ctx)
        if (session == null) {
            prefs.edit().remove(KEY_SESSION_JSON).apply()
        } else {
            val json = JSONObject().apply {
                put("access_token", session.accessToken)
                put("refresh_token", session.refreshToken)
                put("expires_at", session.expiresAt)
                put("user", JSONObject().apply {
                    put("id", session.user.id)
                    put("email", session.user.email)
                    put("created_at", session.user.createdAt ?: "")
                })
            }
            prefs.edit().putString(KEY_SESSION_JSON, json.toString()).apply()
        }
    }

    suspend fun signUp(email: String, password: String): Result<SupabaseSession> {
        _authError.value = null
        val res = getClient().signUp(email.trim(), password)
        if (res.isSuccess) {
            val sess = res.getOrThrow()
            if (sess.accessToken.isNotBlank()) {
                _session.value = sess
                _currentUser.value = sess.user
                _isLoggedIn.value = true
                persistSession(sess)
            }
        } else {
            _authError.value = res.exceptionOrNull()?.message ?: "Sign up failed"
        }
        return res
    }

    suspend fun signIn(email: String, password: String): Result<SupabaseSession> {
        _authError.value = null
        val res = getClient().signInWithPassword(email.trim(), password)
        if (res.isSuccess) {
            val sess = res.getOrThrow()
            _session.value = sess
            _currentUser.value = sess.user
            _isLoggedIn.value = true
            persistSession(sess)
        } else {
            _authError.value = res.exceptionOrNull()?.message ?: "Sign in failed"
        }
        return res
    }

    suspend fun signOut() {
        _session.value = null
        _currentUser.value = null
        _isLoggedIn.value = false
        _authError.value = null
        persistSession(null)
        Log.i(TAG, "User signed out of Supabase")
    }

    suspend fun getValidAccessToken(): String? = refreshMutex.withLock {
        val curr = _session.value ?: return null
        // If still valid for at least 60 seconds
        if (System.currentTimeMillis() < curr.expiresAt - 60_000L && curr.accessToken.isNotBlank()) {
            return curr.accessToken
        }
        // Needs refresh
        return refreshSessionInternal()
    }

    private suspend fun refreshSessionInternal(): String? {
        val curr = _session.value ?: return null
        if (curr.refreshToken.isBlank()) return null
        val res = getClient().refreshSession(curr.refreshToken)
        return if (res.isSuccess) {
            val newSession = res.getOrThrow()
            _session.value = newSession
            _currentUser.value = newSession.user
            _isLoggedIn.value = true
            persistSession(newSession)
            newSession.accessToken
        } else {
            Log.w(TAG, "Refresh session failed: ${res.exceptionOrNull()?.message}")
            null
        }
    }
}
