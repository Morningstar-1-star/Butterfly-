package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import coil.Coil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * BatterySaverManager provides real-time battery status monitoring,
 * intelligent power-saving triggers, and performance optimizations
 * to make the app significantly lighter, cooler, and faster.
 */
class BatterySaverManager private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Battery hardware status
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _isOsPowerSave = MutableStateFlow(false)
    val isOsPowerSave: StateFlow<Boolean> = _isOsPowerSave.asStateFlow()

    // Configurable Settings
    private val _manualEnabled = MutableStateFlow(prefs.getBoolean(KEY_MANUAL_ENABLED, false))
    val manualEnabled: StateFlow<Boolean> = _manualEnabled.asStateFlow()

    private val _autoOnLowBattery = MutableStateFlow(prefs.getBoolean(KEY_AUTO_LOW_BATTERY, true))
    val autoOnLowBattery: StateFlow<Boolean> = _autoOnLowBattery.asStateFlow()

    private val _lowBatteryThreshold = MutableStateFlow(prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, 20))
    val lowBatteryThreshold: StateFlow<Int> = _lowBatteryThreshold.asStateFlow()

    private val _resolutionCap = MutableStateFlow(prefs.getString(KEY_RESOLUTION_CAP, "480p") ?: "480p")
    val resolutionCap: StateFlow<String> = _resolutionCap.asStateFlow()

    private val _disableAmbientGlow = MutableStateFlow(prefs.getBoolean(KEY_DISABLE_AMBIENT, true))
    val disableAmbientGlow: StateFlow<Boolean> = _disableAmbientGlow.asStateFlow()

    private val _lowPowerTorrent = MutableStateFlow(prefs.getBoolean(KEY_LOW_POWER_TORRENT, true))
    val lowPowerTorrent: StateFlow<Boolean> = _lowPowerTorrent.asStateFlow()

    private val _disableAnimations = MutableStateFlow(prefs.getBoolean(KEY_DISABLE_ANIMATIONS, true))
    val disableAnimations: StateFlow<Boolean> = _disableAnimations.asStateFlow()

    private val _pureBlackAmoled = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK_AMOLED, true))
    val pureBlackAmoled: StateFlow<Boolean> = _pureBlackAmoled.asStateFlow()

    private val _audioOnlyForMusic = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_ONLY_MUSIC, false))
    val audioOnlyForMusic: StateFlow<Boolean> = _audioOnlyForMusic.asStateFlow()

    // Aggregated Effective Power Saving State
    private val _isPowerSaveActive = MutableStateFlow(false)
    val isPowerSaveActive: StateFlow<Boolean> = _isPowerSaveActive.asStateFlow()

    init {
        registerBatteryReceiver()
        updateEffectiveState()
    }

    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null) return
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL

                            _batteryLevel.value = pct
                            _isCharging.value = charging
                            updateEffectiveState()
                        }
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            _isOsPowerSave.value = powerManager?.isPowerSaveMode == true
                            updateEffectiveState()
                        }
                    }
                }
            }

            val stickyIntent = appContext.registerReceiver(receiver, filter)
            if (stickyIntent != null) {
                val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                _batteryLevel.value = pct
                _isCharging.value = charging
            }

            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            _isOsPowerSave.value = powerManager?.isPowerSaveMode == true
            updateEffectiveState()
        } catch (e: Exception) {
            Log.w("BatterySaverManager", "Could not register battery receiver: ${e.message}")
        }
    }

    private fun updateEffectiveState() {
        val manual = _manualEnabled.value
        val auto = _autoOnLowBattery.value
        val isLow = !_isCharging.value && (_batteryLevel.value <= _lowBatteryThreshold.value || _isOsPowerSave.value)
        _isPowerSaveActive.value = manual || (auto && isLow)
    }

    fun setManualEnabled(enabled: Boolean) {
        _manualEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MANUAL_ENABLED, enabled).apply()
        updateEffectiveState()
    }

    fun setAutoOnLowBattery(enabled: Boolean) {
        _autoOnLowBattery.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_LOW_BATTERY, enabled).apply()
        updateEffectiveState()
    }

    fun setLowBatteryThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(5, 50)
        _lowBatteryThreshold.value = clamped
        prefs.edit().putInt(KEY_LOW_BATTERY_THRESHOLD, clamped).apply()
        updateEffectiveState()
    }

    fun setResolutionCap(cap: String) {
        _resolutionCap.value = cap
        prefs.edit().putString(KEY_RESOLUTION_CAP, cap).apply()
    }

    fun setDisableAmbientGlow(disabled: Boolean) {
        _disableAmbientGlow.value = disabled
        prefs.edit().putBoolean(KEY_DISABLE_AMBIENT, disabled).apply()
    }

    fun setLowPowerTorrent(enabled: Boolean) {
        _lowPowerTorrent.value = enabled
        prefs.edit().putBoolean(KEY_LOW_POWER_TORRENT, enabled).apply()
    }

    fun setDisableAnimations(disabled: Boolean) {
        _disableAnimations.value = disabled
        prefs.edit().putBoolean(KEY_DISABLE_ANIMATIONS, disabled).apply()
    }

    fun setPureBlackAmoled(enabled: Boolean) {
        _pureBlackAmoled.value = enabled
        prefs.edit().putBoolean(KEY_PURE_BLACK_AMOLED, enabled).apply()
    }

    fun setAudioOnlyForMusic(enabled: Boolean) {
        _audioOnlyForMusic.value = enabled
        prefs.edit().putBoolean(KEY_AUDIO_ONLY_MUSIC, enabled).apply()
    }

    /**
     * Calculates total temporary, video, and image cache size in bytes.
     */
    fun calculateCacheSizeBytes(): Long {
        var totalBytes = 0L
        try {
            appContext.cacheDir?.let { dir ->
                totalBytes += getDirSize(dir)
            }
            appContext.externalCacheDir?.let { dir ->
                totalBytes += getDirSize(dir)
            }
        } catch (_: Exception) {}
        return totalBytes
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (!dir.exists()) return 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    /**
     * Clears disk caches, Coil image caches, and temporary data to make the app lighter.
     * Returns total freed bytes.
     */
    fun clearAppCaches(): Long {
        val initialSize = calculateCacheSizeBytes()
        try {
            // Trim Coil Memory and Disk Caches
            Coil.imageLoader(appContext).memoryCache?.clear()
            Coil.imageLoader(appContext).diskCache?.clear()

            // Clear internal cache directory contents
            appContext.cacheDir?.let { dir ->
                deleteDirContents(dir)
            }

            // Clear external cache directory contents
            appContext.externalCacheDir?.let { dir ->
                deleteDirContents(dir)
            }

            // Explicitly run garbage collection to reclaim native & JVM memory
            System.gc()
        } catch (e: Exception) {
            Log.w("BatterySaverManager", "Cache clear error: ${e.message}")
        }
        return (initialSize - calculateCacheSizeBytes()).coerceAtLeast(0L)
    }

    private fun deleteDirContents(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                deleteDirContents(file)
                file.delete()
            } else {
                file.delete()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "butterfly_battery_saver_prefs"
        private const val KEY_MANUAL_ENABLED = "manual_enabled"
        private const val KEY_AUTO_LOW_BATTERY = "auto_low_battery"
        private const val KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        private const val KEY_RESOLUTION_CAP = "resolution_cap"
        private const val KEY_DISABLE_AMBIENT = "disable_ambient"
        private const val KEY_LOW_POWER_TORRENT = "low_power_torrent"
        private const val KEY_DISABLE_ANIMATIONS = "disable_animations"
        private const val KEY_PURE_BLACK_AMOLED = "pure_black_amoled"
        private const val KEY_AUDIO_ONLY_MUSIC = "audio_only_music"

        @Volatile
        private var INSTANCE: BatterySaverManager? = null

        fun getInstance(context: Context): BatterySaverManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BatterySaverManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
