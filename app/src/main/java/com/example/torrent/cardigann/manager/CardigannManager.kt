package com.example.torrent.cardigann.manager

import android.content.Context
import android.util.Log
import com.example.torrent.cardigann.executor.CardigannIndexerExecutor
import com.example.torrent.cardigann.model.*
import com.example.torrent.cardigann.parser.CardigannYamlParser
import com.example.torrent.engine.TorrentSearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Central Manager for Cardigann / Prowlarr V11 Indexers in Butterfly.
 * Handles loading bundled YAML definitions, custom definitions, persistence,
 * live health testing, update synchronization, and registration with TorrentSearchEngine.
 */
class CardigannManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CardigannManager"
        private const val PREFS_NAME = "cardigann_prefs"
        private const val KEY_ENABLED_PREFIX = "indexer_enabled_"
        private const val KEY_MIRROR_PREFIX = "indexer_mirror_"
        private const val KEY_LAST_SYNC = "indexers_last_sync"
        private const val DEFINITIONS_DIR = "cardigann_definitions"

        const val PROWLARR_INDEXERS_RAW_URL = "https://raw.githubusercontent.com/Prowlarr/Indexers/master/definitions/v11"

        @Volatile
        private var instance: CardigannManager? = null

        fun getInstance(context: Context): CardigannManager {
            return instance ?: synchronized(this) {
                instance ?: CardigannManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val definitionsMap = ConcurrentHashMap<String, CardigannIndexerDefinition>()
    private val executorsMap = ConcurrentHashMap<String, CardigannIndexerExecutor>()
    private val statusesMap = ConcurrentHashMap<String, IndexerStatus>()

    private val _indexersFlow = MutableStateFlow<List<CardigannIndexerDefinition>>(emptyList())
    val indexersFlow: StateFlow<List<CardigannIndexerDefinition>> = _indexersFlow.asStateFlow()

    private val _statusesFlow = MutableStateFlow<Map<String, IndexerStatus>>(emptyMap())
    val statusesFlow: StateFlow<Map<String, IndexerStatus>> = _statusesFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        loadAllDefinitions()
    }

    fun initialize(searchEngine: TorrentSearchEngine = TorrentSearchEngine.getInstance()) {
        registerWithSearchEngine(searchEngine)
    }

    private fun loadAllDefinitions() {
        // 1. Load built-in bundled YAML definitions
        loadBundledDefinitions()

        // 2. Load custom / updated definitions from local disk
        loadDiskDefinitions()

        // 3. Build executors and update state flows
        rebuildExecutors()
    }

    private fun loadBundledDefinitions() {
        for ((id, yaml) in BundledCardigannDefinitions.DEFINITIONS) {
            val def = CardigannYamlParser.parse(yaml)
            if (def != null) {
                definitionsMap[def.id] = def.copy(isBuiltIn = true, isCustom = false)
            } else {
                Log.w(TAG, "Failed to parse bundled definition: $id")
            }
        }
    }

    private fun loadDiskDefinitions() {
        val dir = File(context.filesDir, DEFINITIONS_DIR)
        if (!dir.exists()) dir.mkdirs()

        val files = dir.listFiles { _, name -> name.endsWith(".yml") || name.endsWith(".yaml") } ?: return
        for (file in files) {
            try {
                val yaml = file.readText()
                val def = CardigannYamlParser.parse(yaml)
                if (def != null) {
                    definitionsMap[def.id] = def.copy(isBuiltIn = false, isCustom = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading disk definition ${file.name}: ${e.message}")
            }
        }
    }

    private fun rebuildExecutors() {
        val list = definitionsMap.values.toList().sortedBy { it.name }
        _indexersFlow.value = list

        val newStatuses = mutableMapOf<String, IndexerStatus>()
        for (def in list) {
            val isEnabled = isIndexerEnabled(def.id)
            val customMirror = getCustomMirror(def.id)

            val executor = CardigannIndexerExecutor(def, client, customMirror)
            executorsMap[def.id] = executor

            val existingStatus = statusesMap[def.id]
            val status = existingStatus ?: IndexerStatus(
                indexerId = def.id,
                isEnabled = isEnabled,
                state = if (isEnabled) IndexerHealthState.UNKNOWN else IndexerHealthState.DISABLED,
                activeMirror = executor.getActiveUrl()
            )
            statusesMap[def.id] = status
            newStatuses[def.id] = status
        }
        _statusesFlow.value = newStatuses
    }

    fun registerWithSearchEngine(searchEngine: TorrentSearchEngine = TorrentSearchEngine.getInstance()) {
        for ((id, executor) in executorsMap) {
            if (isIndexerEnabled(id)) {
                searchEngine.registerProvider(executor)
            } else {
                searchEngine.unregisterProvider(id)
            }
        }
    }

    fun isIndexerEnabled(id: String): Boolean {
        // By default, enable major popular public indexers
        val defaultEnabled = when (id) {
            "1337x", "yts", "torrentgalaxy", "eztv", "nyaasi", "subsplease", "solidtorrents", "bitsearch", "thepiratebay", "limetorrents" -> true
            else -> false
        }
        return prefs.getBoolean(KEY_ENABLED_PREFIX + id, defaultEnabled)
    }

    fun setIndexerEnabled(id: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED_PREFIX + id, enabled).apply()
        val executor = executorsMap[id]
        if (executor != null) {
            if (enabled) {
                TorrentSearchEngine.getInstance().registerProvider(executor)
            } else {
                TorrentSearchEngine.getInstance().unregisterProvider(id)
            }
        }

        val cur = statusesMap[id]
        if (cur != null) {
            val updated = cur.copy(
                isEnabled = enabled,
                state = if (enabled) (if (cur.state == IndexerHealthState.DISABLED) IndexerHealthState.UNKNOWN else cur.state) else IndexerHealthState.DISABLED
            )
            statusesMap[id] = updated
            _statusesFlow.value = HashMap(statusesMap)
        }
    }

    fun setCustomMirror(id: String, mirror: String) {
        val clean = mirror.trimEnd('/')
        prefs.edit().putString(KEY_MIRROR_PREFIX + id, clean).apply()
        val executor = executorsMap[id]
        if (executor != null) {
            executor.setActiveUrl(clean)
        }
    }

    fun getCustomMirror(id: String): String? {
        return prefs.getString(KEY_MIRROR_PREFIX + id, null)?.takeIf { it.isNotBlank() }
    }

    suspend fun testIndexer(id: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val executor = executorsMap[id] ?: return@withContext Pair(false, "Indexer not found")
        val cur = statusesMap[id]

        statusesMap[id] = (cur ?: IndexerStatus(id)).copy(state = IndexerHealthState.TESTING)
        _statusesFlow.value = HashMap(statusesMap)

        val startTime = System.currentTimeMillis()
        val (success, message) = executor.test()
        val latency = System.currentTimeMillis() - startTime

        val finalStatus = IndexerStatus(
            indexerId = id,
            isEnabled = isIndexerEnabled(id),
            state = if (success) IndexerHealthState.ONLINE else IndexerHealthState.DEGRADED,
            latencyMs = latency,
            lastCheckedTimestamp = System.currentTimeMillis(),
            lastError = if (success) null else message,
            activeMirror = executor.getActiveUrl()
        )
        statusesMap[id] = finalStatus
        _statusesFlow.value = HashMap(statusesMap)

        Pair(success, message)
    }

    suspend fun testAllEnabledIndexers() = withContext(Dispatchers.IO) {
        val enabledIds = definitionsMap.keys.filter { isIndexerEnabled(it) }
        for (id in enabledIds) {
            testIndexer(id)
        }
    }

    suspend fun addCustomDefinition(yamlContent: String): Result<CardigannIndexerDefinition> = withContext(Dispatchers.IO) {
        try {
            val def = CardigannYamlParser.parse(yamlContent)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid Cardigann YAML definition"))

            val customDef = def.copy(isCustom = true, isBuiltIn = false)
            val dir = File(context.filesDir, DEFINITIONS_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${customDef.id}.yml")
            file.writeText(yamlContent)

            definitionsMap[customDef.id] = customDef
            rebuildExecutors()
            setIndexerEnabled(customDef.id, true)

            Result.success(customDef)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeCustomDefinition(id: String): Boolean = withContext(Dispatchers.IO) {
        val def = definitionsMap[id] ?: return@withContext false
        if (!def.isCustom) return@withContext false

        val dir = File(context.filesDir, DEFINITIONS_DIR)
        val file = File(dir, "${id}.yml")
        if (file.exists()) file.delete()

        definitionsMap.remove(id)
        executorsMap.remove(id)
        statusesMap.remove(id)
        TorrentSearchEngine.getInstance().unregisterProvider(id)

        _indexersFlow.value = definitionsMap.values.toList().sortedBy { it.name }
        _statusesFlow.value = HashMap(statusesMap)
        true
    }

    suspend fun updateDefinitionsFromRemote(baseUrl: String = PROWLARR_INDEXERS_RAW_URL): Result<Int> = withContext(Dispatchers.IO) {
        var updatedCount = 0
        try {
            val bundledKeys = BundledCardigannDefinitions.DEFINITIONS.keys
            for (key in bundledKeys) {
                val url = "$baseUrl/$key.yml"
                try {
                    val req = Request.Builder().url(url).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = CardigannYamlParser.parse(body)
                            if (parsed != null) {
                                val dir = File(context.filesDir, DEFINITIONS_DIR)
                                if (!dir.exists()) dir.mkdirs()
                                File(dir, "$key.yml").writeText(body)
                                definitionsMap[key] = parsed.copy(isBuiltIn = true)
                                updatedCount++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed updating remote indexer $key: ${e.message}")
                }
            }

            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
            rebuildExecutors()
            registerWithSearchEngine()
            Result.success(updatedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    fun getAllDefinitions(): List<CardigannIndexerDefinition> = definitionsMap.values.toList()
}
