package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentResult
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Magnetio Multi-Indexer Provider (Adapted from peterdsp/Magnetio).
 * Aggregates 1337x and TorrentGalaxy indexers into a single consolidated source.
 */
class MagnetioProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : TorrentProvider {

    companion object {
        private const val TAG = "MagnetioProvider"
    }

    override val id: String = "magnetio"
    override val name: String = "Magnetio Multi-Indexer"
    override val isEnabled: Boolean
        get() = AppConfig.isMagnetioEnabled()

    private val x1337Provider = X1337Provider(client)
    private val tgxProvider = TorrentGalaxyProvider(client)

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> = withContext(Dispatchers.IO) {
        val combined = mutableListOf<TorrentResult>()
        try {
            val results1337 = x1337Provider.search(query, identity)
            combined.addAll(results1337.map { it.copy(source = "1337x (Magnetio)") })
        } catch (e: Exception) {
            Log.w(TAG, "1337x query note: ${e.message}")
        }

        try {
            val resultsTgx = tgxProvider.search(query, identity)
            combined.addAll(resultsTgx.map { it.copy(source = "TGx (Magnetio)") })
        } catch (e: Exception) {
            Log.w(TAG, "TGx query note: ${e.message}")
        }

        // Deduplicate by infoHash
        combined.distinctBy { it.infoHash }
    }

    suspend fun search(query: String): List<TorrentResult> {
        return search(query, MediaIdentity(title = query))
    }
}
