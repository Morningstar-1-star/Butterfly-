package com.example.cloudsocial.telegram

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class TelegramAuthState {
    DISCONNECTED,
    WAITING_PHONE,
    WAITING_CODE,
    WAITING_PASSWORD,
    AUTHENTICATED
}

data class TelegramUserSession(
    val phoneNumber: String = "",
    val userId: String = "",
    val username: String = "",
    val sessionToken: String = "",
    val isLoggedIn: Boolean = false,
    val is2faEnabled: Boolean = false
)

class TelegramSessionManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("butterfly_telegram_session", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(TelegramAuthState.DISCONNECTED)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _userSession = MutableStateFlow(TelegramUserSession())
    val userSession: StateFlow<TelegramUserSession> = _userSession.asStateFlow()

    var pendingPhone: String = ""
    var pendingPhoneHash: String = ""

    init {
        loadSession()
    }

    private fun loadSession() {
        val token = prefs.getString("session_token", null)
        val phone = prefs.getString("phone_number", "") ?: ""
        val username = prefs.getString("username", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""

        if (!token.isNullOrBlank()) {
            _userSession.value = TelegramUserSession(
                phoneNumber = phone,
                userId = userId,
                username = username,
                sessionToken = token,
                isLoggedIn = true
            )
            _authState.value = TelegramAuthState.AUTHENTICATED
        } else {
            _authState.value = TelegramAuthState.DISCONNECTED
        }
    }

    suspend fun requestPhoneCode(phone: String): Result<String> {
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.length < 8) return Result.failure(IllegalArgumentException("Invalid phone number length"))

        pendingPhone = cleanPhone
        // Mock / Web MTProto handshake hash simulation for demo/testing
        pendingPhoneHash = "tg_hash_${System.currentTimeMillis()}"
        _authState.value = TelegramAuthState.WAITING_CODE
        return Result.success("Code sent to $cleanPhone (via Telegram app or SMS)")
    }

    suspend fun verifyCode(code: String, password2FA: String? = null): Result<TelegramUserSession> {
        val cleanCode = code.trim()
        if (cleanCode.length < 4) return Result.failure(IllegalArgumentException("Code must be at least 4-5 digits"))

        // Check 2FA requirement
        if (cleanCode == "2fa" || password2FA?.isNotBlank() == false && _authState.value == TelegramAuthState.WAITING_PASSWORD) {
            _authState.value = TelegramAuthState.WAITING_PASSWORD
            return Result.failure(IllegalStateException("2FA Password Required"))
        }

        val generatedToken = "session_token_tg_${System.currentTimeMillis()}_${pendingPhone.takeLast(4)}"
        val session = TelegramUserSession(
            phoneNumber = pendingPhone,
            userId = "tg_user_${pendingPhone.takeLast(6)}",
            username = if (pendingPhone.isNotBlank()) "user_${pendingPhone.takeLast(4)}" else "telegram_user",
            sessionToken = generatedToken,
            isLoggedIn = true
        )

        saveSession(session)
        _userSession.value = session
        _authState.value = TelegramAuthState.AUTHENTICATED
        return Result.success(session)
    }

    fun logout() {
        prefs.edit().clear().apply()
        _userSession.value = TelegramUserSession()
        _authState.value = TelegramAuthState.DISCONNECTED
    }

    private fun saveSession(session: TelegramUserSession) {
        prefs.edit()
            .putString("session_token", session.sessionToken)
            .putString("phone_number", session.phoneNumber)
            .putString("username", session.username)
            .putString("user_id", session.userId)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: TelegramSessionManager? = null

        fun getInstance(context: Context): TelegramSessionManager {
            return instance ?: synchronized(this) {
                instance ?: TelegramSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
