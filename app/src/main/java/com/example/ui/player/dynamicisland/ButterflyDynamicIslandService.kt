package com.example.ui.player.dynamicisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import com.example.ui.player.GlobalPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ButterflyDynamicIslandService:
 * Foreground playback service providing MediaSession integration,
 * background lock screen controls, and the hardware-adaptive Dynamic Island overlay.
 */
class ButterflyDynamicIslandService : Service() {

    companion object {
        const val CHANNEL_ID = "butterfly_playback_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START_ISLAND = "com.example.butterfly.ACTION_START_ISLAND"
        const val ACTION_STOP_ISLAND = "com.example.butterfly.ACTION_STOP_ISLAND"
        const val ACTION_TOGGLE_PLAY = "com.example.butterfly.ACTION_TOGGLE_PLAY"
        const val ACTION_SEEK_BACK = "com.example.butterfly.ACTION_SEEK_BACK"
        const val ACTION_SEEK_FORWARD = "com.example.butterfly.ACTION_SEEK_FORWARD"
        const val ACTION_RETURN_TO_VIDEO = "com.example.butterfly.ACTION_RETURN_TO_VIDEO"
    }

    private val TAG = "DynamicIslandService"
    private val scope = CoroutineScope(Dispatchers.Main)
    private var overlayManager: DynamicIslandOverlayManager? = null
    private var mediaSession: MediaSession? = null
    private var playbackObserverJob: Job? = null
    private var cachedArtwork: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Dynamic Island Service onCreate")
        createNotificationChannel()

        try {
            val player = GlobalPlayerManager.getExoPlayer(this)
            mediaSession = MediaSession.Builder(this, player).build()
        } catch (e: Exception) {
            Log.w(TAG, "MediaSession setup warning: ${e.message}")
        }

        overlayManager = DynamicIslandOverlayManager(
            context = this,
            onOpenApp = {
                AudioModeManager.exitAudioMode(this)
            },
            onCloseIsland = {
                AudioModeManager.stopAudioModeAndService(this)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ISLAND -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PLAY -> {
                GlobalPlayerManager.togglePlayPause()
            }
            ACTION_SEEK_BACK -> {
                GlobalPlayerManager.seekBackward(10000L)
            }
            ACTION_SEEK_FORWARD -> {
                GlobalPlayerManager.seekForward(10000L)
            }
            ACTION_RETURN_TO_VIDEO -> {
                AudioModeManager.exitAudioMode(this)
            }
            else -> {
                startForegroundWithNotification()
                overlayManager?.show()
                startObservingPlayback()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = buildNotification(
            title = GlobalPlayerManager.activeStreamData.value?.title ?: "Butterfly Media Playback",
            artist = GlobalPlayerManager.activeStreamData.value?.channelName ?: "Audio Mode Active",
            isPlaying = GlobalPlayerManager.isPlaying.value,
            artwork = cachedArtwork
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error: ${e.message}", e)
        }
    }

    private fun startObservingPlayback() {
        playbackObserverJob?.cancel()
        playbackObserverJob = scope.launch {
            // Observe stream data for artwork & metadata updates
            launch {
                GlobalPlayerManager.activeStreamData.collectLatest { stream ->
                    if (stream == null) {
                        stopForeground(true)
                        stopSelf()
                    } else {
                        fetchArtworkAndRefreshNotification(stream.thumbnailUrl)
                    }
                }
            }
            // Observe playing status to toggle Play/Pause action icon
            launch {
                GlobalPlayerManager.isPlaying.collectLatest { playing ->
                    val stream = GlobalPlayerManager.activeStreamData.value
                    val notif = buildNotification(
                        title = stream?.title ?: "Butterfly Media Playback",
                        artist = stream?.channelName ?: "Audio Mode Active",
                        isPlaying = playing,
                        artwork = cachedArtwork
                    )
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notif)
                }
            }
        }
    }

    private fun fetchArtworkAndRefreshNotification(url: String?) {
        if (url.isNullOrBlank()) {
            cachedArtwork = null
            refreshNotification()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(this@ButterflyDynamicIslandService)
                val request = ImageRequest.Builder(this@ButterflyDynamicIslandService)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    withContext(Dispatchers.Main) {
                        cachedArtwork = bitmap
                        refreshNotification()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load notification artwork: ${e.message}")
            }
        }
    }

    private fun refreshNotification() {
        val stream = GlobalPlayerManager.activeStreamData.value ?: return
        val notif = buildNotification(
            title = stream.title,
            artist = stream.channelName,
            isPlaying = GlobalPlayerManager.isPlaying.value,
            artwork = cachedArtwork
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artwork: Bitmap?
    ): Notification {
        // PendingIntent to return to app video player
        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("FROM_DYNAMIC_ISLAND", true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val seekBackIntent = Intent(this, ButterflyDynamicIslandService::class.java).apply {
            action = ACTION_SEEK_BACK
        }
        val seekBackPendingIntent = PendingIntent.getService(
            this,
            101,
            seekBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePlayIntent = Intent(this, ButterflyDynamicIslandService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }
        val togglePlayPendingIntent = PendingIntent.getService(
            this,
            102,
            togglePlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val seekForwardIntent = Intent(this, ButterflyDynamicIslandService::class.java).apply {
            action = ACTION_SEEK_FORWARD
        }
        val seekForwardPendingIntent = PendingIntent.getService(
            this,
            103,
            seekForwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ButterflyDynamicIslandService::class.java).apply {
            action = ACTION_STOP_ISLAND
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            104,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_replay_10, "Rewind 10s", seekBackPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying) "Pause" else "Play",
                togglePlayPendingIntent
            )
            .addAction(R.drawable.ic_forward_10, "Forward 10s", seekForwardPendingIntent)

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Butterfly Audio Mode"
            val descriptionText = "Persistent background audio playback and Dynamic Island controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Dynamic Island Service onDestroy")
        playbackObserverJob?.cancel()
        overlayManager?.dismiss()
        overlayManager = null
        try {
            mediaSession?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaSession: ${e.message}")
        }
        mediaSession = null
    }
}
