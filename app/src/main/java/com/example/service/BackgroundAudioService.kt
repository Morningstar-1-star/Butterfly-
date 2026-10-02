package com.example.service

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
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import coil.ImageLoader
import coil.request.ImageRequest
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
import kotlinx.coroutines.withContext

/**
 * BackgroundAudioService: Keeps audio playback active in Background Audio-only mode
 * with full MediaSession integration, rich notification progress bar, seek scrubbing,
 * and artwork support.
 */
class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "butterfly_audio_playback"
        const val CHANNEL_NAME = "Audio Playback"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_PLAY_PAUSE = "com.example.service.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.service.action.NEXT"
        const val ACTION_PREV = "com.example.service.action.PREV"
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
    private var mediaSession: MediaSession? = null
    private var cachedArtwork: Bitmap? = null
    private var cachedArtworkUrl: String? = null

    private val mediaSessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            GlobalPlayerManager.play()
            updatePlaybackState()
            updateNotification()
        }

        override fun onPause() {
            GlobalPlayerManager.pause()
            updatePlaybackState()
            updateNotification()
        }

        override fun onSeekTo(pos: Long) {
            GlobalPlayerManager.seekTo(pos)
            updatePlaybackState()
            updateNotification()
        }

        override fun onSkipToNext() {
            GlobalPlayerManager.playNext()
            updatePlaybackState()
            updateNotification()
        }

        override fun onSkipToPrevious() {
            GlobalPlayerManager.seekBackward(10000L)
            updatePlaybackState()
            updateNotification()
        }

        override fun onFastForward() {
            GlobalPlayerManager.seekForward(10000L)
            updatePlaybackState()
            updateNotification()
        }

        override fun onRewind() {
            GlobalPlayerManager.seekBackward(10000L)
            updatePlaybackState()
            updateNotification()
        }

        override fun onStop() {
            GlobalPlayerManager.setBackgroundAudioOnly(false)
            GlobalPlayerManager.pause()
            stopForegroundInternal()
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        setupMediaSession()
        createNotificationChannel()
        startPlaybackObserver()
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(this, "ButterflyAudioSession").apply {
                setCallback(mediaSessionCallback)
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                isActive = true
            }
        } catch (_: Exception) {}
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
                updatePlaybackState()
                updateNotification()
            }
            ACTION_NEXT -> {
                GlobalPlayerManager.playNext()
                updatePlaybackState()
                updateNotification()
            }
            ACTION_PREV -> {
                GlobalPlayerManager.seekBackward(10000L)
                updatePlaybackState()
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
                    updatePlaybackState()
                    updateNotification()
                }
            }
            launch {
                GlobalPlayerManager.activeStreamData.collectLatest { stream ->
                    if (stream == null && !GlobalPlayerManager.hasLoadedMedia()) {
                        stopForegroundInternal()
                        stopSelf()
                    } else {
                        loadArtworkAsync(stream?.thumbnailUrl)
                        updateMediaSessionMetadata()
                        updatePlaybackState()
                        updateNotification()
                    }
                }
            }
            launch {
                GlobalPlayerManager.durationMs.collectLatest {
                    updateMediaSessionMetadata()
                    updatePlaybackState()
                }
            }
        }
    }

    private fun loadArtworkAsync(url: String?) {
        if (url.isNullOrBlank() || url == cachedArtworkUrl) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(this@BackgroundAudioService)
                val req = ImageRequest.Builder(this@BackgroundAudioService)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    cachedArtwork = drawable.bitmap
                    cachedArtworkUrl = url
                    withContext(Dispatchers.Main) {
                        updateMediaSessionMetadata()
                        updateNotification()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updatePlaybackState() {
        try {
            val isPlaying = GlobalPlayerManager.isPlaying.value
            val curPos = GlobalPlayerManager.currentPositionMs.value.coerceAtLeast(0L)
            val bufferedPos = GlobalPlayerManager.bufferedPositionMs.value.coerceAtLeast(0L)
            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val speed = if (isPlaying) 1.0f else 0.0f

            val actions = PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_REWIND or
                    PlaybackState.ACTION_STOP

            val stateBuilder = PlaybackState.Builder()
                .setActions(actions)
                .setState(state, curPos, speed, SystemClock.elapsedRealtime())
                .setBufferedPosition(bufferedPos)

            mediaSession?.setPlaybackState(stateBuilder.build())
        } catch (_: Exception) {}
    }

    private fun updateMediaSessionMetadata() {
        try {
            val stream = GlobalPlayerManager.activeStreamData.value
            val title = stream?.title ?: "Background Audio"
            val channel = stream?.channelName ?: "Playing audio in background"
            val durationMs = GlobalPlayerManager.durationMs.value.coerceAtLeast(0L)

            val metadataBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, channel)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, channel)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, channel)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)

            cachedArtwork?.let {
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            }

            mediaSession?.setMetadata(metadataBuilder.build())
        } catch (_: Exception) {}
    }

    private fun buildNotification(): Notification {
        val stream = GlobalPlayerManager.activeStreamData.value
        val title = stream?.title ?: "Background Audio"
        val channel = stream?.channelName ?: "Playing audio in background"
        val isPlaying = GlobalPlayerManager.isPlaying.value

        loadArtworkAsync(stream?.thumbnailUrl)
        updateMediaSessionMetadata()
        updatePlaybackState()

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            2,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pip_headphones)
            .setContentTitle(title)
            .setContentText(channel)
            .setSubText("Headphones Mode")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_pip_next,
                    "Next",
                    nextPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_pip_close,
                    "Close",
                    stopPendingIntent
                ).build()
            )

        cachedArtwork?.let {
            builder.setLargeIcon(it)
        }

        mediaSession?.let { session ->
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
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
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (_: Exception) {}
        stopForegroundInternal()
    }
}
