package com.example.torrent.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import com.example.torrent.model.*
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.libtorrent4j.Priority
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages persistent background torrent downloads for Butterfly.
 * Handles task lifecycle (start/pause/resume/cancel/delete/retry), network/charging restrictions,
 * state persistence across process death, and libtorrent-verified completion.
 */
class TorrentDownloadManager(
    private val context: Context,
    private val engine: LibtorrentEngine
) {
    companion object {
        private const val TAG = "TorrentDownloadMgr"
        private const val PREFS_NAME = "butterfly_torrent_downloads"
        private const val KEY_TASKS = "download_tasks_json"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tasksMap = ConcurrentHashMap<String, TorrentDownloadTask>()

    private val _tasks = MutableStateFlow<List<TorrentDownloadTask>>(emptyList())
    val tasks: StateFlow<List<TorrentDownloadTask>> = _tasks.asStateFlow()

    private val _settings = MutableStateFlow(TorrentSettings())
    val settings: StateFlow<TorrentSettings> = _settings.asStateFlow()

    private var monitorJob: Job? = null

    init {
        loadTasksFromStorage()
        registerSystemMonitors()
        startMonitorLoop()
    }

    fun addDownload(
        release: TorrentRelease,
        selectedFileIndices: List<Int> = emptyList(),
        isSequential: Boolean = false
    ): TorrentDownloadTask {
        val infoHash = release.infoHash.lowercase()
        val taskId = "dl_$infoHash"

        val targetDir = File(engine.downloadDir, release.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_"))
        targetDir.mkdirs()

        val task = TorrentDownloadTask(
            id = taskId,
            infoHash = infoHash,
            title = release.title,
            savePath = targetDir.absolutePath,
            totalBytes = release.sizeBytes,
            downloadedBytes = 0L,
            progress = 0f,
            speedBps = 0L,
            etaSeconds = 0L,
            state = TorrentEngineState.CONNECTING_TRACKERS,
            isSequential = isSequential,
            selectedFileIndices = selectedFileIndices,
            dateAdded = System.currentTimeMillis()
        )

        tasksMap[taskId] = task
        _tasks.value = tasksMap.values.sortedByDescending { it.dateAdded }
        saveTasksToStorage()

        scope.launch {
            startTorrentDownload(release, task, targetDir)
        }

        return task
    }

    private suspend fun startTorrentDownload(
        release: TorrentRelease,
        task: TorrentDownloadTask,
        saveDir: File
    ) = withContext(Dispatchers.IO) {
        try {
            if (!canDownloadNow()) {
                updateTaskState(task.id, TorrentEngineState.PAUSED)
                return@withContext
            }

            engine.start(_settings.value)
            val infoHash = release.infoHash.lowercase()
            val magnetUrl = if (release.magnetUrl.isNotBlank()) {
                release.magnetUrl
            } else {
                MagnetParser.buildMagnetUrl(infoHash, release.title, release.trackerUrls)
            }

            updateTaskState(task.id, TorrentEngineState.FETCHING_METADATA)

            var ti = engine.findHandle(infoHash)?.torrentFile()
            if (ti == null) {
                ti = engine.fetchMagnetMetadata(magnetUrl, timeoutSec = 60)
            }

            if (ti != null) {
                val numFiles = ti.numFiles()
                val priorities = if (task.selectedFileIndices.isNotEmpty()) {
                    Array(numFiles) { idx ->
                        if (task.selectedFileIndices.contains(idx)) Priority.DEFAULT else Priority.IGNORE
                    }
                } else null

                engine.download(
                    torrentInfo = ti,
                    saveDir = saveDir,
                    filePriorities = priorities,
                    sequential = task.isSequential
                )
                updateTaskState(task.id, TorrentEngineState.BUFFERING)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download ${task.title}: ${e.message}", e)
            updateTaskState(task.id, TorrentEngineState.ERROR)
        }
    }

    fun pauseDownload(taskId: String) {
        val task = tasksMap[taskId] ?: return
        engine.pause(task.infoHash)
        updateTaskState(taskId, TorrentEngineState.PAUSED)
    }

    fun resumeDownload(taskId: String) {
        val task = tasksMap[taskId] ?: return
        if (!canDownloadNow()) return
        engine.resume(task.infoHash)
        updateTaskState(taskId, TorrentEngineState.BUFFERING)
    }

    fun cancelDownload(taskId: String) {
        val task = tasksMap[taskId] ?: return
        engine.remove(task.infoHash, deleteFiles = false)
        tasksMap.remove(taskId)
        _tasks.value = tasksMap.values.sortedByDescending { it.dateAdded }
        saveTasksToStorage()
    }

    fun deleteDownload(taskId: String, deleteFiles: Boolean = true) {
        val task = tasksMap[taskId] ?: return
        engine.remove(task.infoHash, deleteFiles = deleteFiles)
        if (deleteFiles) {
            try {
                File(task.savePath).deleteRecursively()
            } catch (_: Exception) {}
        }
        tasksMap.remove(taskId)
        _tasks.value = tasksMap.values.sortedByDescending { it.dateAdded }
        saveTasksToStorage()
    }

    fun retryDownload(taskId: String) {
        val task = tasksMap[taskId] ?: return
        val release = TorrentRelease(
            title = task.title,
            infoHash = task.infoHash,
            magnetUrl = "",
            provider = "DownloadManager",
            sizeBytes = task.totalBytes
        )
        scope.launch {
            startTorrentDownload(release, task, File(task.savePath))
        }
    }

    fun updateSettings(newSettings: TorrentSettings) {
        _settings.value = newSettings
        engine.updateSettings(newSettings)
        checkRestrictionsAndApply()
    }

    private fun updateTaskState(taskId: String, state: TorrentEngineState) {
        val task = tasksMap[taskId] ?: return
        val updated = task.copy(state = state)
        tasksMap[taskId] = updated
        _tasks.value = tasksMap.values.sortedByDescending { it.dateAdded }
        saveTasksToStorage()
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                var changed = false
                for ((id, task) in tasksMap) {
                    if (task.state == TorrentEngineState.PAUSED || task.state == TorrentEngineState.ERROR) {
                        continue
                    }

                    val th = engine.findHandle(task.infoHash)
                    if (th != null && th.isValid) {
                        val st = th.status()
                        val done = st.totalWantedDone()
                        val total = if (st.totalWanted() > 0) st.totalWanted() else task.totalBytes
                        val speed = st.downloadRate().toLong()
                        val progress = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else st.progress()

                        val eta = if (speed > 0 && total > done) {
                            (total - done) / speed
                        } else 0L

                        val isComplete = (st.isFinished || st.isSeeding || (total > 0 && done >= total)) && done > 0

                        val newState = if (isComplete) {
                            TorrentEngineState.COMPLETED
                        } else if (speed > 0) {
                            TorrentEngineState.STREAMING
                        } else if (st.numPeers() > 0) {
                            TorrentEngineState.BUFFERING
                        } else {
                            task.state
                        }

                        val updated = task.copy(
                            downloadedBytes = done,
                            totalBytes = if (total > 0) total else task.totalBytes,
                            progress = progress,
                            speedBps = speed,
                            etaSeconds = eta,
                            state = newState
                        )
                        tasksMap[id] = updated
                        changed = true
                    }
                }

                if (changed) {
                    _tasks.value = tasksMap.values.sortedByDescending { it.dateAdded }
                }
                delay(1000)
            }
        }
    }

    private fun canDownloadNow(): Boolean {
        val s = _settings.value

        if (s.downloadOnlyOnWifi) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            if (!isWifi) return false
        }

        if (s.downloadOnlyWhenCharging) {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            if (!isCharging) return false
        }

        return true
    }

    private fun checkRestrictionsAndApply() {
        val allowed = canDownloadNow()
        for ((id, task) in tasksMap) {
            if (!allowed && task.state != TorrentEngineState.COMPLETED && task.state != TorrentEngineState.PAUSED) {
                pauseDownload(id)
            } else if (allowed && task.state == TorrentEngineState.PAUSED) {
                resumeDownload(id)
            }
        }
    }

    private fun registerSystemMonitors() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, intent: Intent?) {
                    checkRestrictionsAndApply()
                }
            }, filter)
        } catch (_: Exception) {}
    }

    private fun saveTasksToStorage() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            tasksMap.values.forEach { t ->
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("infoHash", t.infoHash)
                    put("title", t.title)
                    put("savePath", t.savePath)
                    put("totalBytes", t.totalBytes)
                    put("downloadedBytes", t.downloadedBytes)
                    put("progress", t.progress.toDouble())
                    put("state", t.state.name)
                    put("isSequential", t.isSequential)
                    put("dateAdded", t.dateAdded)
                    val filesArray = JSONArray()
                    t.selectedFileIndices.forEach { filesArray.put(it) }
                    put("selectedFileIndices", filesArray)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_TASKS, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tasks: ${e.message}")
        }
    }

    private fun loadTasksFromStorage() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_TASKS, null) ?: return
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val files = mutableListOf<Int>()
                val filesArr = obj.optJSONArray("selectedFileIndices")
                if (filesArr != null) {
                    for (f in 0 until filesArr.length()) {
                        files.add(filesArr.getInt(f))
                    }
                }
                val task = TorrentDownloadTask(
                    id = id,
                    infoHash = obj.getString("infoHash"),
                    title = obj.getString("title"),
                    savePath = obj.getString("savePath"),
                    totalBytes = obj.optLong("totalBytes", 0L),
                    downloadedBytes = obj.optLong("downloadedBytes", 0L),
                    progress = obj.optDouble("progress", 0.0).toFloat(),
                    state = try {
                        val s = TorrentEngineState.valueOf(obj.getString("state"))
                        if (s == TorrentEngineState.COMPLETED) s else TorrentEngineState.PAUSED
                    } catch (_: Exception) {
                        TorrentEngineState.PAUSED
                    },
                    isSequential = obj.optBoolean("isSequential", false),
                    selectedFileIndices = files,
                    dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                )
                tasksMap[id] = task
            }
            _tasks.value = tasksMap.values.sortedByDescending { it.dateAdded }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tasks: ${e.message}")
        }
    }
}
