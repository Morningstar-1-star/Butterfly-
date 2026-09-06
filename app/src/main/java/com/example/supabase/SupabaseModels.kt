package com.example.supabase

data class SupabaseUser(
    val id: String,
    val email: String,
    val createdAt: String? = null
)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val user: SupabaseUser,
    val expiresAt: Long = 0L
)

data class SupabaseSyncState(
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val syncMessage: String = "Idle",
    val pendingQueueCount: Int = 0,
    val autoSyncEnabled: Boolean = true
)

sealed class SupabaseAuthResult {
    data class Success(val session: SupabaseSession) : SupabaseAuthResult()
    data class Error(val message: String, val statusCode: Int? = null) : SupabaseAuthResult()
}
