package com.example.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseClient(private val context: Context) {

    private val tag = "SupabaseClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val baseUrl: String
        get() = SupabaseConfig.getUrl(context).trimEnd('/')

    private val anonKey: String
        get() = SupabaseConfig.getAnonKey(context)

    // =========================================================================
    // AUTH METHODS (GoTrue API)
    // =========================================================================

    suspend fun signUp(email: String, password: String, redirectTo: String = "butterfly://auth/callback"): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        try {
            val encodedRedirect = java.net.URLEncoder.encode(redirectTo, "UTF-8")
            val url = "$baseUrl/auth/v1/signup?redirect_to=$encodedRedirect"
            val bodyObj = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("email_redirect_to", redirectTo)
                put("options", JSONObject().apply {
                    put("emailRedirectTo", redirectTo)
                })
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(responseBody, "Sign up failed with HTTP ${response.code}")
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(responseBody)
            val sessionObj = json.optJSONObject("session")
            val accessToken = json.optString("access_token", sessionObj?.optString("access_token", "") ?: "")
            val refreshToken = json.optString("refresh_token", sessionObj?.optString("refresh_token", "") ?: "")
            val expiresIn = if (json.has("expires_in")) json.optLong("expires_in", 3600L) else sessionObj?.optLong("expires_in", 3600L) ?: 3600L

            val userObj = json.optJSONObject("user")
                ?: sessionObj?.optJSONObject("user")
                ?: json.optJSONObject("data")?.optJSONObject("user")
                ?: if (json.has("id") || json.has("email") || json.has("aud")) json else null

            val userId = userObj?.optString("id", "")?.takeIf { it.isNotBlank() }
                ?: json.optString("id", "")
            val userEmail = userObj?.optString("email", email)?.takeIf { it.isNotBlank() }
                ?: json.optString("email", email)
            val createdAt = userObj?.optString("created_at")
                ?: json.optString("created_at")

            val user = SupabaseUser(
                id = userId,
                email = userEmail,
                createdAt = createdAt
            )

            // If email confirmation is required, access_token may be empty until confirmed
            val session = SupabaseSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = user,
                expiresAt = if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000) else 0L
            )

            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "signUp error", e)
            Result.failure(e)
        }
    }

    suspend fun getUser(accessToken: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/user"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Failed to get user profile (${response.code})")
                return@withContext Result.failure(IOException(error))
            }

            val json = JSONObject(body)
            val user = SupabaseUser(
                id = json.optString("id", ""),
                email = json.optString("email", ""),
                createdAt = json.optString("created_at")
            )
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "getUser error", e)
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(tokenHash: String, type: String = "signup"): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/verify"
            val bodyObj = JSONObject().apply {
                put("type", type)
                put("token_hash", tokenHash)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Email verification failed (${response.code})")
                return@withContext Result.failure(IOException(error))
            }

            val json = JSONObject(body)
            val sessionObj = json.optJSONObject("session")
            val accessToken = json.optString("access_token", sessionObj?.optString("access_token", "") ?: "")
            val refreshToken = json.optString("refresh_token", sessionObj?.optString("refresh_token", "") ?: "")
            val expiresIn = if (json.has("expires_in")) json.optLong("expires_in", 3600L) else sessionObj?.optLong("expires_in", 3600L) ?: 3600L
            val userObj = json.optJSONObject("user") ?: sessionObj?.optJSONObject("user") ?: if (json.has("id")) json else null

            val user = SupabaseUser(
                id = userObj?.optString("id", "") ?: "",
                email = userObj?.optString("email", "") ?: "",
                createdAt = userObj?.optString("created_at")
            )

            val session = SupabaseSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = user,
                expiresAt = if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000) else 0L
            )
            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "verifyOtp error", e)
            Result.failure(e)
        }
    }

    suspend fun verifyEmailOtp(email: String, token: String, type: String = "signup"): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/verify"
            val bodyObj = JSONObject().apply {
                put("type", type)
                put("email", email.trim())
                put("token", token.trim())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Email verification failed (${response.code})")
                return@withContext Result.failure(IOException(error))
            }

            val json = JSONObject(body)
            val sessionObj = json.optJSONObject("session")
            val accessToken = json.optString("access_token", sessionObj?.optString("access_token", "") ?: "")
            val refreshToken = json.optString("refresh_token", sessionObj?.optString("refresh_token", "") ?: "")
            val expiresIn = if (json.has("expires_in")) json.optLong("expires_in", 3600L) else sessionObj?.optLong("expires_in", 3600L) ?: 3600L
            val userObj = json.optJSONObject("user") ?: sessionObj?.optJSONObject("user") ?: if (json.has("id")) json else null

            val user = SupabaseUser(
                id = userObj?.optString("id", "") ?: "",
                email = userObj?.optString("email", email.trim()) ?: email.trim(),
                createdAt = userObj?.optString("created_at")
            )

            val session = SupabaseSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = user,
                expiresAt = if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000) else 0L
            )
            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "verifyEmailOtp error", e)
            Result.failure(e)
        }
    }

    suspend fun resendVerification(email: String, type: String = "signup"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/resend"
            val bodyObj = JSONObject().apply {
                put("type", type)
                put("email", email.trim())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Resending confirmation email failed (${response.code})")
                return@withContext Result.failure(IOException(error))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "resendVerification error", e)
            Result.failure(e)
        }
    }

    suspend fun sendOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/otp"
            val bodyObj = JSONObject().apply {
                put("email", email.trim())
                put("create_user", false)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Sending OTP failed (${response.code})")
                return@withContext Result.failure(IOException(error))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "sendOtp error", e)
            Result.failure(e)
        }
    }

    suspend fun exchangeCodeForSession(authCode: String): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/token?grant_type=pkce"
            val bodyObj = JSONObject().apply {
                put("auth_code", authCode)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Code exchange failed (${response.code})")
                return@withContext Result.failure(IOException(error))
            }

            val json = JSONObject(body)
            val sessionObj = json.optJSONObject("session")
            val accessToken = json.optString("access_token", sessionObj?.optString("access_token", "") ?: "")
            val refreshToken = json.optString("refresh_token", sessionObj?.optString("refresh_token", "") ?: "")
            val expiresIn = if (json.has("expires_in")) json.optLong("expires_in", 3600L) else sessionObj?.optLong("expires_in", 3600L) ?: 3600L
            val userObj = json.optJSONObject("user") ?: sessionObj?.optJSONObject("user") ?: if (json.has("id")) json else null

            val user = SupabaseUser(
                id = userObj?.optString("id", "") ?: "",
                email = userObj?.optString("email", "") ?: "",
                createdAt = userObj?.optString("created_at")
            )

            val session = SupabaseSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = user,
                expiresAt = if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000) else 0L
            )
            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "exchangeCodeForSession error", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithPassword(email: String, password: String): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/token?grant_type=password"
            val bodyObj = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(responseBody, "Sign in failed with HTTP ${response.code}")
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(responseBody)
            val sessionObj = json.optJSONObject("session")
            val accessToken = json.optString("access_token", sessionObj?.optString("access_token", "") ?: "")
            val refreshToken = json.optString("refresh_token", sessionObj?.optString("refresh_token", "") ?: "")
            val expiresIn = if (json.has("expires_in")) json.optLong("expires_in", 3600L) else sessionObj?.optLong("expires_in", 3600L) ?: 3600L
            val userObj = json.optJSONObject("user") ?: sessionObj?.optJSONObject("user") ?: if (json.has("id")) json else null

            val user = SupabaseUser(
                id = userObj?.optString("id", "") ?: "",
                email = userObj?.optString("email", email) ?: email,
                createdAt = userObj?.optString("created_at")
            )

            val session = SupabaseSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = user,
                expiresAt = if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000) else 0L
            )

            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "signInWithPassword error", e)
            Result.failure(e)
        }
    }

    suspend fun refreshSession(refreshToken: String): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/v1/token?grant_type=refresh_token"
            val bodyObj = JSONObject().apply {
                put("refresh_token", refreshToken)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(responseBody, "Token refresh failed with HTTP ${response.code}")
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val newRefreshToken = json.optString("refresh_token", refreshToken)
            val expiresIn = json.optLong("expires_in", 3600)
            val userObj = json.optJSONObject("user")

            val user = if (userObj != null) {
                SupabaseUser(
                    id = userObj.getString("id"),
                    email = userObj.optString("email"),
                    createdAt = userObj.optString("created_at")
                )
            } else {
                SupabaseUser(id = "", email = "")
            }

            val session = SupabaseSession(
                accessToken = accessToken,
                refreshToken = newRefreshToken,
                user = user,
                expiresAt = System.currentTimeMillis() + (expiresIn * 1000)
            )

            Result.success(session)
        } catch (e: Exception) {
            Log.e(tag, "refreshSession error", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // POSTGREST API (Database CRUD with Row-Level Security)
    // =========================================================================

    suspend fun upsert(tableName: String, jsonPayload: String, accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/$tableName"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Upsert to $tableName failed (${response.code})")
                Log.w(tag, "Upsert error [$tableName]: $error")
                return@withContext Result.failure(IOException(error))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "upsert failed for $tableName", e)
            Result.failure(e)
        }
    }

    suspend fun select(tableName: String, queryParams: String, accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val queryStr = if (queryParams.isNotBlank()) "?$queryParams" else ""
            val url = "$baseUrl/rest/v1/$tableName$queryStr"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Select from $tableName failed (${response.code})")
                return@withContext Result.failure(IOException(error))
            }

            Result.success(body)
        } catch (e: Exception) {
            Log.e(tag, "select failed for $tableName", e)
            Result.failure(e)
        }
    }

    suspend fun delete(tableName: String, queryParams: String, accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/$tableName?$queryParams"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val error = extractErrorMessage(body, "Delete from $tableName failed (${response.code})")
                return@withContext Result.failure(IOException(error))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "delete failed for $tableName", e)
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            val msg = json.optString("msg")
            if (msg.isNotBlank()) return msg
            val desc = json.optString("error_description")
            if (desc.isNotBlank()) return desc
            val message = json.optString("message")
            if (message.isNotBlank()) return message
            val error = json.optString("error")
            if (error.isNotBlank()) return error
            fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
