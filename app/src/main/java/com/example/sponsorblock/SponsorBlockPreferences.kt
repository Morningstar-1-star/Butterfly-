package com.example.sponsorblock

import android.content.Context
import android.content.SharedPreferences
import com.example.sponsorblock.model.SponsorBlockAction
import com.example.sponsorblock.model.SponsorBlockCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SponsorBlockPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Flow states for UI reactivity
    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _showVotingButton = MutableStateFlow(prefs.getBoolean(KEY_SHOW_VOTING_BUTTON, false))
    val showVotingButton: StateFlow<Boolean> = _showVotingButton.asStateFlow()

    private val _useCompactSkipButton = MutableStateFlow(prefs.getBoolean(KEY_USE_COMPACT_SKIP_BUTTON, false))
    val useCompactSkipButton: StateFlow<Boolean> = _useCompactSkipButton.asStateFlow()

    private val _autoHideSkipButton = MutableStateFlow(prefs.getBoolean(KEY_AUTO_HIDE_SKIP_BUTTON, true))
    val autoHideSkipButton: StateFlow<Boolean> = _autoHideSkipButton.asStateFlow()

    private val _skipButtonDurationSeconds = MutableStateFlow(prefs.getInt(KEY_SKIP_BUTTON_DURATION, 5))
    val skipButtonDurationSeconds: StateFlow<Int> = _skipButtonDurationSeconds.asStateFlow()

    private val _showUndoSkipNotification = MutableStateFlow(prefs.getBoolean(KEY_SHOW_UNDO_NOTIFICATION, true))
    val showUndoSkipNotification: StateFlow<Boolean> = _showUndoSkipNotification.asStateFlow()

    private val _skipNotificationDurationSeconds = MutableStateFlow(prefs.getInt(KEY_SKIP_NOTIFICATION_DURATION, 4))
    val skipNotificationDurationSeconds: StateFlow<Int> = _skipNotificationDurationSeconds.asStateFlow()

    private val _showVideoLengthWithoutSegments = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LENGTH_WITHOUT_SEGMENTS, false))
    val showVideoLengthWithoutSegments: StateFlow<Boolean> = _showVideoLengthWithoutSegments.asStateFlow()

    private val _useSquareLayout = MutableStateFlow(prefs.getBoolean(KEY_USE_SQUARE_LAYOUT, false))
    val useSquareLayout: StateFlow<Boolean> = _useSquareLayout.asStateFlow()

    private val _showConnectionErrorAlerts = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ERROR_ALERTS, true))
    val showConnectionErrorAlerts: StateFlow<Boolean> = _showConnectionErrorAlerts.asStateFlow()

    private val _enableSkipCountTracking = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_SKIP_TRACKING, true))
    val enableSkipCountTracking: StateFlow<Boolean> = _enableSkipCountTracking.asStateFlow()

    private val _minimumSegmentDurationSeconds = MutableStateFlow(prefs.getFloat(KEY_MIN_SEGMENT_DURATION, 1.0f))
    val minimumSegmentDurationSeconds: StateFlow<Float> = _minimumSegmentDurationSeconds.asStateFlow()

    private val _privateUserId = MutableStateFlow(
        prefs.getString(KEY_PRIVATE_USER_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_PRIVATE_USER_ID, it).apply()
        }
    )
    val privateUserId: StateFlow<String> = _privateUserId.asStateFlow()

    private val _apiUrl = MutableStateFlow(prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL)
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _timeAdjustmentStepMs = MutableStateFlow(prefs.getLong(KEY_TIME_ADJUSTMENT_STEP, 5000L))
    val timeAdjustmentStepMs: StateFlow<Long> = _timeAdjustmentStepMs.asStateFlow()

    private val _skippedSegmentsCount = MutableStateFlow(prefs.getLong(KEY_SKIPPED_SEGMENTS_COUNT, 0L))
    val skippedSegmentsCount: StateFlow<Long> = _skippedSegmentsCount.asStateFlow()

    private val _skippedTimeSeconds = MutableStateFlow(
        Double.fromBits(prefs.getLong(KEY_SKIPPED_TIME_SECONDS_BITS, 0.0.toRawBits()))
    )
    val skippedTimeSeconds: StateFlow<Double> = _skippedTimeSeconds.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
    }

    fun getCategoryAction(category: SponsorBlockCategory): SponsorBlockAction {
        val name = prefs.getString(KEY_CATEGORY_PREFIX + category.key, category.defaultAction.name)
        return SponsorBlockAction.fromName(name ?: category.defaultAction.name)
    }

    fun setCategoryAction(category: SponsorBlockCategory, action: SponsorBlockAction) {
        prefs.edit().putString(KEY_CATEGORY_PREFIX + category.key, action.name).apply()
    }

    fun setShowVotingButton(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_VOTING_BUTTON, enabled).apply()
        _showVotingButton.value = enabled
    }

    fun setUseCompactSkipButton(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_COMPACT_SKIP_BUTTON, enabled).apply()
        _useCompactSkipButton.value = enabled
    }

    fun setAutoHideSkipButton(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_SKIP_BUTTON, enabled).apply()
        _autoHideSkipButton.value = enabled
    }

    fun setSkipButtonDurationSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SKIP_BUTTON_DURATION, seconds).apply()
        _skipButtonDurationSeconds.value = seconds
    }

    fun setShowUndoSkipNotification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_UNDO_NOTIFICATION, enabled).apply()
        _showUndoSkipNotification.value = enabled
    }

    fun setSkipNotificationDurationSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SKIP_NOTIFICATION_DURATION, seconds).apply()
        _skipNotificationDurationSeconds.value = seconds
    }

    fun setShowVideoLengthWithoutSegments(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LENGTH_WITHOUT_SEGMENTS, enabled).apply()
        _showVideoLengthWithoutSegments.value = enabled
    }

    fun setUseSquareLayout(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SQUARE_LAYOUT, enabled).apply()
        _useSquareLayout.value = enabled
    }

    fun setShowConnectionErrorAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ERROR_ALERTS, enabled).apply()
        _showConnectionErrorAlerts.value = enabled
    }

    fun setEnableSkipCountTracking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_SKIP_TRACKING, enabled).apply()
        _enableSkipCountTracking.value = enabled
    }

    fun setMinimumSegmentDurationSeconds(seconds: Float) {
        prefs.edit().putFloat(KEY_MIN_SEGMENT_DURATION, seconds).apply()
        _minimumSegmentDurationSeconds.value = seconds
    }

    fun setPrivateUserId(userId: String) {
        prefs.edit().putString(KEY_PRIVATE_USER_ID, userId).apply()
        _privateUserId.value = userId
    }

    fun setApiUrl(url: String) {
        val clean = if (url.endsWith("/")) url.dropLast(1) else url
        prefs.edit().putString(KEY_API_URL, clean).apply()
        _apiUrl.value = clean
    }

    fun setTimeAdjustmentStepMs(ms: Long) {
        prefs.edit().putLong(KEY_TIME_ADJUSTMENT_STEP, ms).apply()
        _timeAdjustmentStepMs.value = ms
    }

    fun recordSkip(durationSeconds: Double) {
        val newCount = _skippedSegmentsCount.value + 1
        val newTime = _skippedTimeSeconds.value + durationSeconds.coerceAtLeast(0.0)

        prefs.edit()
            .putLong(KEY_SKIPPED_SEGMENTS_COUNT, newCount)
            .putLong(KEY_SKIPPED_TIME_SECONDS_BITS, newTime.toRawBits())
            .apply()

        _skippedSegmentsCount.value = newCount
        _skippedTimeSeconds.value = newTime
    }

    fun resetStats() {
        prefs.edit()
            .putLong(KEY_SKIPPED_SEGMENTS_COUNT, 0L)
            .putLong(KEY_SKIPPED_TIME_SECONDS_BITS, 0.0.toRawBits())
            .apply()

        _skippedSegmentsCount.value = 0L
        _skippedTimeSeconds.value = 0.0
    }

    fun exportSettingsJson(): String {
        val json = org.json.JSONObject()
        json.put("enabled", _isEnabled.value)
        json.put("apiUrl", _apiUrl.value)
        json.put("privateUserId", _privateUserId.value)
        json.put("showVotingButton", _showVotingButton.value)
        json.put("useCompactSkipButton", _useCompactSkipButton.value)
        json.put("autoHideSkipButton", _autoHideSkipButton.value)
        json.put("skipButtonDurationSeconds", _skipButtonDurationSeconds.value)
        json.put("showUndoSkipNotification", _showUndoSkipNotification.value)
        json.put("skipNotificationDurationSeconds", _skipNotificationDurationSeconds.value)
        json.put("showVideoLengthWithoutSegments", _showVideoLengthWithoutSegments.value)
        json.put("useSquareLayout", _useSquareLayout.value)
        json.put("showConnectionErrorAlerts", _showConnectionErrorAlerts.value)
        json.put("enableSkipCountTracking", _enableSkipCountTracking.value)
        json.put("minimumSegmentDurationSeconds", _minimumSegmentDurationSeconds.value)
        json.put("timeAdjustmentStepMs", _timeAdjustmentStepMs.value)

        val categoriesObj = org.json.JSONObject()
        SponsorBlockCategory.values().forEach { cat ->
            categoriesObj.put(cat.key, getCategoryAction(cat).name)
        }
        json.put("categories", categoriesObj)
        return json.toString(2)
    }

    fun importSettingsJson(jsonString: String): Boolean {
        return try {
            val json = org.json.JSONObject(jsonString)
            if (json.has("enabled")) setEnabled(json.getBoolean("enabled"))
            if (json.has("apiUrl")) setApiUrl(json.getString("apiUrl"))
            if (json.has("privateUserId")) setPrivateUserId(json.getString("privateUserId"))
            if (json.has("showVotingButton")) setShowVotingButton(json.getBoolean("showVotingButton"))
            if (json.has("useCompactSkipButton")) setUseCompactSkipButton(json.getBoolean("useCompactSkipButton"))
            if (json.has("autoHideSkipButton")) setAutoHideSkipButton(json.getBoolean("autoHideSkipButton"))
            if (json.has("skipButtonDurationSeconds")) setSkipButtonDurationSeconds(json.getInt("skipButtonDurationSeconds"))
            if (json.has("showUndoSkipNotification")) setShowUndoSkipNotification(json.getBoolean("showUndoSkipNotification"))
            if (json.has("skipNotificationDurationSeconds")) setSkipNotificationDurationSeconds(json.getInt("skipNotificationDurationSeconds"))
            if (json.has("showVideoLengthWithoutSegments")) setShowVideoLengthWithoutSegments(json.getBoolean("showVideoLengthWithoutSegments"))
            if (json.has("useSquareLayout")) setUseSquareLayout(json.getBoolean("useSquareLayout"))
            if (json.has("showConnectionErrorAlerts")) setShowConnectionErrorAlerts(json.getBoolean("showConnectionErrorAlerts"))
            if (json.has("enableSkipCountTracking")) setEnableSkipCountTracking(json.getBoolean("enableSkipCountTracking"))
            if (json.has("minimumSegmentDurationSeconds")) setMinimumSegmentDurationSeconds(json.getDouble("minimumSegmentDurationSeconds").toFloat())
            if (json.has("timeAdjustmentStepMs")) setTimeAdjustmentStepMs(json.getLong("timeAdjustmentStepMs"))

            if (json.has("categories")) {
                val catObj = json.getJSONObject("categories")
                SponsorBlockCategory.values().forEach { cat ->
                    if (catObj.has(cat.key)) {
                        val actName = catObj.getString(cat.key)
                        setCategoryAction(cat, SponsorBlockAction.fromName(actName))
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val DEFAULT_API_URL = "https://sponsor.ajay.app"
        private const val PREFS_NAME = "sponsorblock_preferences"

        private const val KEY_ENABLED = "sb_enabled"
        private const val KEY_CATEGORY_PREFIX = "sb_cat_"
        private const val KEY_SHOW_VOTING_BUTTON = "sb_show_voting"
        private const val KEY_USE_COMPACT_SKIP_BUTTON = "sb_compact_skip"
        private const val KEY_AUTO_HIDE_SKIP_BUTTON = "sb_autohide_skip"
        private const val KEY_SKIP_BUTTON_DURATION = "sb_skip_duration"
        private const val KEY_SHOW_UNDO_NOTIFICATION = "sb_show_undo"
        private const val KEY_SKIP_NOTIFICATION_DURATION = "sb_undo_duration"
        private const val KEY_SHOW_LENGTH_WITHOUT_SEGMENTS = "sb_length_without_segments"
        private const val KEY_USE_SQUARE_LAYOUT = "sb_square_layout"
        private const val KEY_SHOW_ERROR_ALERTS = "sb_show_error_alerts"
        private const val KEY_ENABLE_SKIP_TRACKING = "sb_enable_skip_tracking"
        private const val KEY_MIN_SEGMENT_DURATION = "sb_min_segment_duration"
        private const val KEY_PRIVATE_USER_ID = "sb_private_user_id"
        private const val KEY_API_URL = "sb_api_url"
        private const val KEY_TIME_ADJUSTMENT_STEP = "sb_time_adjustment_step"
        private const val KEY_SKIPPED_SEGMENTS_COUNT = "sb_skipped_segments_count"
        private const val KEY_SKIPPED_TIME_SECONDS_BITS = "sb_skipped_time_seconds_bits"

        @Volatile
        private var INSTANCE: SponsorBlockPreferences? = null

        fun getInstance(context: Context): SponsorBlockPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SponsorBlockPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
