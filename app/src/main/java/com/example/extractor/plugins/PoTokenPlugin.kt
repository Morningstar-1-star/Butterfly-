package com.example.extractor.plugins

import android.content.Context
import android.util.Log
import com.example.extractor.YouTubeExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Proof of Origin (PO-Token) Provider Plugin.
 * (Adapted from coletdjnz/yt-dlp-getpot-wpc)
 *
 * Generates and fetches valid YouTube BotGuard/Visitor Data Proof of Origin tokens
 * to bypass bot-detection and SABR throttling on restricted streams.
 */
object PoTokenPlugin : YouTubeExtractorHelper.CustomPoTokenProvider {

    private const val TAG = "PoTokenPlugin"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0L

    fun initPlugin() {
        YouTubeExtractorHelper.setPoTokenProvider(this)
        Log.i(TAG, "PO-Token engine registered with YouTubeExtractorHelper")
    }

    override fun getPoToken(visitorData: String?): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiry) {
            return cachedToken
        }
        return cachedToken
    }

    /**
     * Refreshes PO token from remote or local WPC generation server.
     */
    suspend fun refreshPoToken(serverUrl: String? = null): String? = withContext(Dispatchers.IO) {
        val endpoint = serverUrl ?: "https://potoken.butterfly.local/generate"
        try {
            val jsonBody = JSONObject().apply {
                put("client", "WEB")
                put("timestamp", System.currentTimeMillis())
            }

            val req = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val respJson = JSONObject(resp.body?.string() ?: "{}")
                val token = respJson.optString("po_token", null)
                if (!token.isNullOrBlank()) {
                    cachedToken = token
                    tokenExpiry = System.currentTimeMillis() + 6 * 3600 * 1000L // 6 hours
                    Log.i(TAG, "Successfully acquired fresh PO-Token")
                    return@withContext token
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh PO-token from remote: ${e.message}")
        }
        null
    }
}

/**
 * Remote JavaScript Cipher Decryption Plugin.
 * (Adapted from coletdjnz/yt-dlp-remote-cipher)
 *
 * Offloads JavaScript signature descrambling (n-sig & s-sig) when local QuickJS
 * or webview is throttled.
 */
object RemoteCipherPlugin {

    private const val TAG = "RemoteCipherPlugin"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun descrambleSignature(
        cipherText: String,
        playerUrl: String,
        remoteServerUrl: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val endpoint = remoteServerUrl ?: "https://cipher.butterfly.local/solve"
        try {
            val payload = JSONObject().apply {
                put("cipher", cipherText)
                put("player_url", playerUrl)
            }

            val req = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string() ?: "{}")
                return@withContext json.optString("solved_signature", null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote cipher resolution note: ${e.message}")
        }
        null
    }
}
