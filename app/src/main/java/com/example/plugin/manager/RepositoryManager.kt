package com.example.plugin.manager

import android.content.Context
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.model.PluginManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class Repository(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val lastUpdated: Long = 0L,
    val plugins: List<PluginManifest> = emptyList()
)

data class RepositoryFeed(
    val name: String,
    val description: String? = null,
    val plugins: List<PluginManifest> = emptyList()
)

class RepositoryManager(
    private val context: Context,
    private val pluginManager: PluginManager
) {
    private val prefs = context.getSharedPreferences("butterfly_repository_mgr", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val reposListAdapter = moshi.adapter<List<Repository>>(
        Types.newParameterizedType(List::class.java, Repository::class.java)
    )
    private val repoFeedAdapter = moshi.adapter(RepositoryFeed::class.java)

    private val http = HttpBridge()

    private val _repositories = MutableStateFlow<List<Repository>>(emptyList())
    val repositories: StateFlow<List<Repository>> = _repositories.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(
        prefs.getBoolean("auto_update_plugins", true)
    )
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    suspend fun loadRepositories() = withContext(Dispatchers.IO) {
        val json = prefs.getString("repositories_json", null)
        val loadedRepos = if (!json.isNullOrBlank()) {
            try {
                reposListAdapter.fromJson(json) ?: getDefaultRepositories()
            } catch (e: Exception) {
                getDefaultRepositories()
            }
        } else {
            getDefaultRepositories()
        }
        _repositories.value = loadedRepos.sortedByDescending { it.priority }
        saveRepositoriesToDisk()
    }

    private fun getDefaultRepositories(): List<Repository> {
        return listOf(
            Repository(
                id = "official_butterfly_repo",
                name = "Butterfly Official Repository",
                url = "https://raw.githubusercontent.com/butterfly-app/plugins/main/repository.json",
                enabled = true,
                priority = 10,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    private fun saveRepositoriesToDisk() {
        val json = reposListAdapter.toJson(_repositories.value)
        prefs.edit().putString("repositories_json", json).apply()
    }

    suspend fun addRepository(url: String, name: String = "Custom Repository") = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return@withContext

        val id = "repo_" + cleanUrl.hashCode().toString()
        val current = _repositories.value.toMutableList()
        if (current.any { it.url == cleanUrl }) return@withContext

        val newRepo = Repository(
            id = id,
            name = name,
            url = cleanUrl,
            enabled = true,
            priority = 0,
            lastUpdated = System.currentTimeMillis()
        )
        current.add(newRepo)
        _repositories.value = current.sortedByDescending { it.priority }
        saveRepositoriesToDisk()
        refreshRepository(id)
    }

    suspend fun removeRepository(repoId: String) = withContext(Dispatchers.IO) {
        val current = _repositories.value.filterNot { it.id == repoId }
        _repositories.value = current
        saveRepositoriesToDisk()
    }

    suspend fun toggleRepository(repoId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = _repositories.value.map { repo ->
            if (repo.id == repoId) repo.copy(enabled = enabled) else repo
        }
        _repositories.value = current.sortedByDescending { it.priority }
        saveRepositoriesToDisk()
    }

    suspend fun setPriority(repoId: String, priority: Int) = withContext(Dispatchers.IO) {
        val current = _repositories.value.map { repo ->
            if (repo.id == repoId) repo.copy(priority = priority) else repo
        }
        _repositories.value = current.sortedByDescending { it.priority }
        saveRepositoriesToDisk()
    }

    fun setAutoUpdate(enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        prefs.edit().putBoolean("auto_update_plugins", enabled).apply()
    }

    suspend fun refreshRepository(repoId: String): Repository? = withContext(Dispatchers.IO) {
        val repo = _repositories.value.find { it.id == repoId } ?: return@withContext null
        if (!repo.enabled) return@withContext repo

        try {
            val response = http.get(repo.url)
            if (response.statusCode == 200) {
                val feed = repoFeedAdapter.fromJson(response.body)
                val updatedRepo = repo.copy(
                    name = feed?.name ?: repo.name,
                    lastUpdated = System.currentTimeMillis(),
                    plugins = feed?.plugins ?: emptyList()
                )
                val currentList = _repositories.value.map { if (it.id == repoId) updatedRepo else it }
                _repositories.value = currentList.sortedByDescending { it.priority }
                saveRepositoriesToDisk()
                return@withContext updatedRepo
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        repo
    }

    suspend fun checkAndApplyAutoUpdates(): Int = withContext(Dispatchers.IO) {
        if (!_autoUpdateEnabled.value) return@withContext 0

        var updateCount = 0
        val updates = pluginManager.checkForUpdates()
        for (update in updates) {
            try {
                val resp = http.get(update.downloadUrl)
                if (resp.statusCode == 200) {
                    val stream = resp.body.byteInputStream()
                    if (pluginManager.installPluginFromZip(stream)) {
                        updateCount++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateCount
    }
}
