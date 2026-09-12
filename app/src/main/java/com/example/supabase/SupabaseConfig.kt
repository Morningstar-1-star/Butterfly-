package com.example.supabase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object SupabaseConfig {
    private const val PREFS_NAME = "butterfly_supabase_prefs"
    private const val KEY_URL = "supabase_url"
    private const val KEY_ANON_KEY = "supabase_anon_key"

    // Default configuration for permanent Supabase project; user can override anytime in settings
    private const val DEFAULT_URL = "https://wlyqppcnywrftvdducgz.supabase.co"
    private const val DEFAULT_ANON_KEY = "sb_publishable_gsEuLQOAZrxBWcG5qMMmxQ_h7aQjCX1"

    @Volatile
    private var cachedUrl: String? = null
    @Volatile
    private var cachedAnonKey: String? = null

    fun getUrl(context: Context): String {
        cachedUrl?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        val effectiveUrl = if (url.contains("aistudio-butterfly.supabase.co") || url.contains("placeholder") || url.contains("wlyqppcnywrftvducgz")) DEFAULT_URL else url
        cachedUrl = effectiveUrl
        return effectiveUrl
    }

    fun getAnonKey(context: Context): String {
        cachedAnonKey?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_ANON_KEY, DEFAULT_ANON_KEY) ?: DEFAULT_ANON_KEY
        val effectiveKey = if (key.contains("placeholder")) DEFAULT_ANON_KEY else key
        cachedAnonKey = effectiveKey
        return effectiveKey
    }

    fun saveConfig(context: Context, url: String, anonKey: String) {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_URL, cleanUrl)
            .putString(KEY_ANON_KEY, cleanKey)
            .apply()
        cachedUrl = cleanUrl
        cachedAnonKey = cleanKey
        Log.i("SupabaseConfig", "Supabase configuration updated: $cleanUrl")
    }

    fun isConfigured(context: Context): Boolean {
        val url = getUrl(context)
        val key = getAnonKey(context)
        return url.isNotBlank() && key.isNotBlank() && !url.contains("placeholder")
    }
}
