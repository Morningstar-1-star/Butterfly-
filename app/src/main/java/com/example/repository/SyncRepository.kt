package com.example.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncStatusState(
    val userEmail: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L
)

object SyncRepository {

    private val _syncStatus = MutableStateFlow(SyncStatusState())
    val syncStatus: StateFlow<SyncStatusState> = _syncStatus.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("account_sync_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs.getString("user_email", null)
        _syncStatus.value = _syncStatus.value.copy(userEmail = savedEmail)
    }

    fun setUserEmail(context: Context, email: String?) {
        val prefs = context.getSharedPreferences("account_sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_email", email).apply()
        _syncStatus.value = _syncStatus.value.copy(userEmail = email)
    }

    fun triggerSync() {
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true)
        _syncStatus.value = _syncStatus.value.copy(
            isSyncing = false,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    fun signOut(context: Context) {
        setUserEmail(context, null)
    }
}
