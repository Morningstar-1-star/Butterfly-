package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.player.GlobalPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * BackgroundAudioService: Keeps audio playback active when transitioning
 * from Picture-in-Picture (PiP) mode to Background Audio-only mode.
 */
class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "butterfly_audio_playback"
        const val CHANNEL_NAME = "Audio Playback"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_PLAY_PAUSE = "com.example.service.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.service.action.NEXT"
        const val ACTION_STOP = "com.example.service.action.STOP"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            if (isRunning) {
                val intent = Intent(context, BackgroundAudioService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startPlaybackObserver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_PLAY_PAUSE -> {
                GlobalPlayerManager.togglePlayPause()
                updateNotification()
            }
            ACTION_NEXT -> {
                GlobalPlayerManager.playNext()
                updateNotification()
            }
            ACTION_STOP -> {
                GlobalPlayerManager.setBackgroundAudioOnly(false)
                GlobalPlayerManager.pause()
                stopForegroundInternal()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startPlaybackObserver() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            launch {
                GlobalPlayerManager.isPlaying.collectLatest {
                    updateNotification()
                }
            }
            launch {
                GlobalPlayerManager.activeStreamData.collectLatest { stream ->
                    if (stream == null && !GlobalPlayerManager.hasLoadedMedia()) {
                        stopForegroundInternal()
                        stopSelf()
                    } else {
                        updateNotification()
                    }
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val stream = GlobalPlayerManager.activeStreamData.value
        val title = stream?.title ?: "Background Audio"
        val channel = stream?.channelName ?: "Playing audio in background"
        val isPlaying = GlobalPlayerManager.isPlaying.value

        // Intent to return to the app and restore video playback
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Play/Pause
        val playPauseIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Next
        val nextIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            2,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop / Close
        val stopIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pip_headphones)
            .setContentTitle(title)
            .setContentText(channel)
            .setSubText("Headphones Mode")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                R.drawable.ic_pip_next,
                "Next",
                nextPendingIntent
            )
            .addAction(
                R.drawable.ic_pip_close,
                "Close",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        if (!isRunning) return
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {}
    }

    private fun stopForegroundInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio-only playback"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        observerJob?.cancel()
        serviceScope.cancel()
        stopForegroundInternal()
    }
}
