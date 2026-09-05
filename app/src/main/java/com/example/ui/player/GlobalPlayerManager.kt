package com.example.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

object GlobalPlayerManager {

    private var appContext: Context? = null
    private var exoPlayerInstance: ExoPlayer? = null
    private var scope = CoroutineScope(Dispatchers.Main)
    private var progressTrackerJob: Job? = null
    private var pendingResumePositionMs: Long? = null

    private val mediaHeaderInterceptor = okhttp3.Interceptor { chain ->
        var request = chain.request()
        val urlStr = request.url.toString().lowercase()
        val builder = request.newBuilder()

        // Default Desktop Chrome User-Agent if missing or generic okhttp
        val existingUa = request.header("User-Agent")
        if (existingUa.isNullOrBlank() || existingUa.startsWith("okhttp", ignoreCase = true)) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        }

        when {
            urlStr.contains("googlevideo.com") || urlStr.contains("youtube.com") || urlStr.contains("youtu.be") || urlStr.contains("ytimg.com") ||
            urlStr.contains("googleapis.com") || urlStr.contains("storage.googleapis") || urlStr.contains("commondatastorage") || urlStr.contains("w3schools") || urlStr.contains("githubusercontent") -> {
                builder.removeHeader("Referer")
                builder.removeHeader("referer")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                builder.removeHeader("Cookie")
                builder.removeHeader("cookie")
            }
            urlStr.contains("vk.com") || urlStr.contains("vkuser.net") || urlStr.contains("vkuservideo.net") || urlStr.contains("mycdn.me") || urlStr.contains("vk-cdn.me") || urlStr.contains("userapi.com") -> {
                builder.header("Referer", "https://vk.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                builder.removeHeader("Cookie")
                builder.removeHeader("cookie")
            }
            urlStr.contains("bilibili") || urlStr.contains("bilivideo") || urlStr.contains("biliapi") || urlStr.contains("hdslb") || urlStr.contains("szbdyd") || urlStr.contains("mcdn") || urlStr.contains("acgvideo") || urlStr.contains("upgcxcode") || urlStr.contains("upos-") || urlStr.contains("akamaized") -> {
                builder.header("Referer", "https://www.bilibili.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.header("Accept", "*/*")
                builder.header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                builder.header("Sec-Fetch-Mode", "no-cors")
                builder.header("Sec-Fetch-Site", "cross-site")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
            }
            urlStr.contains("eporner") || urlStr.contains("static-cluster") || urlStr.contains("dwn") || urlStr.contains("eporner-cdn") -> {
                builder.header("Referer", "https://www.eporner.com/")
                builder.header("Origin", "https://www.eporner.com")
            }
            urlStr.contains("dailymotion") || urlStr.contains("dmcdn") || urlStr.contains("dai.ly") || urlStr.contains("dm-event") -> {
                builder.header("Referer", "https://www.dailymotion.com/")
                builder.header("Origin", "https://www.dailymotion.com")
            }
            urlStr.contains("archive.org") || urlStr.contains("us.archive.org") || urlStr.contains("ia60") || urlStr.contains("ia80") || urlStr.contains("ia90") -> {
                if (request.header("Referer") == null) {
                    builder.header("Referer", "https://archive.org/")
                }
            }
            urlStr.contains("pornhub.com") || urlStr.contains("phncdn.com") -> {
                builder.header("Referer", "https://www.pornhub.com/")
                builder.header("Origin", "https://www.pornhub.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en")
            }
            urlStr.contains("4tube.com") || urlStr.contains("ttcache.com") || urlStr.contains("f-cdn.com") || urlStr.contains("foursex.com") || urlStr.contains("pornerbros.com") || urlStr.contains("fux.com") -> {
                builder.header("Referer", "https://www.4tube.com/")
                builder.header("Origin", "https://www.4tube.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; ft_mature=1; consent=1; has_consent=1")
            }
            urlStr.contains("beeg.com") || urlStr.contains("externulls.com") -> {
                builder.header("Referer", "https://beeg.com/")
                builder.header("Origin", "https://beeg.com")
            }
            urlStr.contains("xvideos.com") || urlStr.contains("xv-cdn.com") -> {
                builder.header("Referer", "https://www.xvideos.com/")
            }
            urlStr.contains("youporn.com") || urlStr.contains("ypncdn.com") -> {
                builder.header("Referer", "https://www.youporn.com/")
                builder.header("Origin", "https://www.youporn.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc")
            }
            urlStr.contains("xhamster.com") || urlStr.contains("xhcdn.com") -> {
                builder.header("Referer", "https://xhamster.com/")
                builder.header("Origin", "https://xhamster.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc")
            }
            urlStr.contains("vimeo.com") || urlStr.contains("vimeocdn.com") || (urlStr.contains("vimeo") && !urlStr.contains("bili")) -> {
                builder.header("Referer", "https://vimeo.com/")
                builder.header("Origin", "https://vimeo.com")
            }
            urlStr.contains("hotstar.com") || urlStr.contains("hotstar-cdn") || urlStr.contains("jiohotstar") || urlStr.contains("starott.com") || urlStr.contains("hs-cdn") -> {
                builder.header("Referer", "https://www.hotstar.com/")
                builder.header("Origin", "https://www.hotstar.com")
            }
            urlStr.contains("amazon.in") || urlStr.contains("minitv") || urlStr.contains("aiv-cdn") || urlStr.contains("amazonvideo") -> {
                builder.header("Referer", "https://www.amazon.in/")
                builder.header("Origin", "https://www.amazon.in")
            }
            urlStr.contains("cam4.com") || urlStr.contains("stream.cam4.com") -> {
                builder.header("Referer", "https://www.cam4.com/")
                builder.header("Origin", "https://www.cam4.com")
            }
            urlStr.contains("bigo.tv") || urlStr.contains("bigolive.tv") || urlStr.contains("bigocdn.com") || urlStr.contains("live.bigo.tv") || urlStr.contains("cubetecn.com") || urlStr.contains("bigo.sg") -> {
                builder.header("Referer", "https://www.bigo.tv/")
                builder.header("Origin", "https://www.bigo.tv")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            urlStr.contains("cammodels.com") || urlStr.contains("stripchat.com") || urlStr.contains("doppiocdn.com") || urlStr.contains("strpst.com") -> {
                builder.header("Referer", "https://stripchat.com/")
                builder.header("Origin", "https://stripchat.com")
            }
            urlStr.contains("chaturbate.com") || urlStr.contains("highwebmedia.com") -> {
                builder.header("Referer", "https://chaturbate.com/")
                builder.header("Origin", "https://chaturbate.com")
            }
            urlStr.contains("discoveryplus") -> {
                builder.header("Referer", "https://www.discoveryplus.in/")
                builder.header("Origin", "https://www.discoveryplus.in")
            }
            urlStr.contains("disneyplus") -> {
                builder.header("Referer", "https://www.disneyplus.com/")
                builder.header("Origin", "https://www.disneyplus.com")
            }
            urlStr.contains("drive.google.com") || urlStr.contains("googleusercontent.com") || urlStr.contains("drive.usercontent.google.com") -> {
                builder.header("Referer", "https://drive.google.com/")
            }
            urlStr.contains("mxplayer.in") || urlStr.contains("mxplay.com") -> {
                builder.header("Referer", "https://www.mxplayer.in/")
                builder.header("Origin", "https://www.mxplayer.in")
            }
            urlStr.contains("imdb.com") || urlStr.contains("media-amazon.com") -> {
                builder.header("Referer", "https://www.imdb.com/")
                builder.header("Origin", "https://www.imdb.com")
            }
            urlStr.contains("noodlemagazine.com") -> {
                builder.header("Referer", "https://noodlemagazine.com/")
                builder.header("Origin", "https://noodlemagazine.com")
            }
            urlStr.contains("popcorntime") -> {
                builder.header("Referer", "https://popcorntime.pro/")
                builder.header("Origin", "https://popcorntime.pro")
            }
            urlStr.contains("sonyliv.com") -> {
                builder.header("Referer", "https://www.sonyliv.com/")
                builder.header("Origin", "https://www.sonyliv.com")
            }
            urlStr.contains("thisvid.com") -> {
                builder.header("Referer", "https://thisvid.com/")
                builder.header("Origin", "https://thisvid.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc")
            }
            urlStr.contains("tnaflix.com") -> {
                builder.header("Referer", "https://www.tnaflix.com/")
                builder.header("Origin", "https://www.tnaflix.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; has_consent=1")
            }
        }

        val response = chain.proceed(builder.build())
        if (!response.isSuccessful) return@Interceptor response

        val isBigoStream = urlStr.contains("cubetecn.com") || urlStr.contains("bigo.tv") || urlStr.contains("bigolive.tv") || urlStr.contains("bigo.sg") || urlStr.contains("bigocdn.com") || _activeStreamData.value?.providerId == "bigo"

        if (isBigoStream) {
            val responseBody = response.body ?: return@Interceptor response
            val mediaType = responseBody.contentType()
            val mediaTypeStr = mediaType?.toString()?.lowercase() ?: ""
            val isM3u8 = urlStr.contains(".m3u8") || mediaTypeStr.contains("mpegurl") || mediaTypeStr.contains("vnd.apple.mpegurl")

            if (isM3u8) {
                val rawText = responseBody.string()
                if (rawText.contains("#EXT-X-BIGO-WEB-PROTECTION") || rawText.contains("#EXTM3U")) {
                    val cleanText = rawText.replace(Regex("""#EXT-X-BIGO-WEB-PROTECTION:[^\r\n]*\r?\n?"""), "")
                    val newBody = okhttp3.ResponseBody.create(mediaType, cleanText)
                    return@Interceptor response.newBuilder().body(newBody).build()
                } else {
                    val newBody = okhttp3.ResponseBody.create(mediaType, rawText)
                    return@Interceptor response.newBuilder().body(newBody).build()
                }
            } else {
                val bytes = responseBody.bytes()
                if (bytes.size >= 376 && bytes[0] != 0x47.toByte() && bytes[376] == 0x47.toByte()) {
                    val repaired = repairBigoTsSegment(bytes)
                    val newBody = okhttp3.ResponseBody.create(mediaType, repaired)
                    return@Interceptor response.newBuilder().body(newBody).build()
                } else {
                    val newBody = okhttp3.ResponseBody.create(mediaType, bytes)
                    return@Interceptor response.newBuilder().body(newBody).build()
                }
            }
        }

        response
    }

    private fun repairBigoTsSegment(rawBytes: ByteArray): ByteArray {
        if (rawBytes.size < 376 || rawBytes[0] == 0x47.toByte() || rawBytes[376] != 0x47.toByte()) {
            return rawBytes
        }
        val fixed = rawBytes.clone()

        // 1. Reconstruct Standard MPEG-TS PAT packet (188 bytes, PID 0x0000, Program 1 -> PMT PID 0x1000)
        for (i in 0 until 188) fixed[i] = 0xFF.toByte()
        val patHeader = byteArrayOf(
            0x47.toByte(), 0x40.toByte(), 0x00.toByte(), 0x10.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xb0.toByte(), 0x0d.toByte(),
            0x00.toByte(), 0x01.toByte(), 0xc1.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0xf0.toByte(),
            0x00.toByte(), 0x2a.toByte(), 0xb1.toByte(), 0x04.toByte(),
            0xb2.toByte()
        )
        System.arraycopy(patHeader, 0, fixed, 0, patHeader.size)

        // 2. Reconstruct Standard MPEG-TS PMT packet (188 bytes, PID 0x1000, PCR PID 0x100, Stream 1: AAC Audio PID 0x101, Stream 2: AVC Video PID 0x100)
        for (i in 188 until 376) fixed[i] = 0xFF.toByte()
        val pmtHeader = byteArrayOf(
            0x47.toByte(), 0x50.toByte(), 0x00.toByte(), 0x10.toByte(),
            0x00.toByte(), 0x02.toByte(), 0xb0.toByte(), 0x17.toByte(),
            0x00.toByte(), 0x01.toByte(), 0xc1.toByte(), 0x00.toByte(),
            0x00.toByte(), 0xe1.toByte(), 0x00.toByte(), 0xf0.toByte(),
            0x00.toByte(), 0x0f.toByte(), 0xe1.toByte(), 0x01.toByte(),
            0xf0.toByte(), 0x00.toByte(), 0x1b.toByte(), 0xe1.toByte(),
            0x00.toByte(), 0xf0.toByte(), 0x00.toByte(), 0xf2.toByte(),
            0xd9.toByte(), 0x15.toByte(), 0x63.toByte()
        )
        System.arraycopy(pmtHeader, 0, fixed, 188, pmtHeader.size)

        return fixed
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor(mediaHeaderInterceptor)
        .dns(com.example.util.SecureDnsManager.appDns)
        .build()

    private val _activeStreamData = MutableStateFlow<StreamData?>(null)
    val activeStreamData: StateFlow<StreamData?> = _activeStreamData.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

    private val _bufferedFraction = MutableStateFlow(0f)
    val bufferedFraction: StateFlow<Float> = _bufferedFraction.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _firstFrameRendered = MutableStateFlow(false)
    val firstFrameRendered: StateFlow<Boolean> = _firstFrameRendered.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<com.example.model.AudioTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<com.example.model.AudioTrackOption>> = _audioTracks.asStateFlow()

    // Subtitles & Real-time AI Captioning
    enum class SubtitleMode {
        OFF,
        BILIBILI_ORIGINAL,
        BILIBILI_TRANSLATED,
        EXTERNAL_PROVIDER,
        AI_LIVE_CAPTIONS
    }

    private val _subtitleMode = MutableStateFlow(SubtitleMode.OFF)
    val subtitleMode: StateFlow<SubtitleMode> = _subtitleMode.asStateFlow()

    private val _bilibiliSubtitleTracks = MutableStateFlow<List<CaptionOption>>(emptyList())
    val bilibiliSubtitleTracks: StateFlow<List<CaptionOption>> = _bilibiliSubtitleTracks.asStateFlow()

    private val _selectedSubtitleTrack = MutableStateFlow<CaptionOption?>(null)
    val selectedSubtitleTrack: StateFlow<CaptionOption?> = _selectedSubtitleTrack.asStateFlow()

    private val _bilibiliCues = MutableStateFlow<List<com.example.util.SubtitleCue>>(emptyList())
    val bilibiliCues: StateFlow<List<com.example.util.SubtitleCue>> = _bilibiliCues.asStateFlow()

    private val _targetCaptionLanguage = MutableStateFlow("en")
    val targetCaptionLanguage: StateFlow<String> = _targetCaptionLanguage.asStateFlow()

    private val _currentActiveSubtitleText = MutableStateFlow("")
    val currentActiveSubtitleText: StateFlow<String> = _currentActiveSubtitleText.asStateFlow()

    private val _currentActiveTranslatedText = MutableStateFlow("")
    val currentActiveTranslatedText: StateFlow<String> = _currentActiveTranslatedText.asStateFlow()

    private val _isLoopEnabled = MutableStateFlow(false)
    val isLoopEnabled: StateFlow<Boolean> = _isLoopEnabled.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(16f / 9f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    fun setLoopVideo(enabled: Boolean, context: Context? = null) {
        _isLoopEnabled.value = enabled
        exoPlayerInstance?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        context?.let { ctx ->
            com.example.util.PlaybackPreferences.getInstance(ctx).setLoopVideoEnabled(enabled)
        }
    }

    fun setSubtitleMode(mode: SubtitleMode, context: Context? = null) {
        _subtitleMode.value = mode
        if (mode == SubtitleMode.AI_LIVE_CAPTIONS) {
        } else {
        }
    }

    fun setTargetCaptionLanguage(langCode: String) {
        _targetCaptionLanguage.value = langCode
        com.example.subtitles.SubtitleManager.setSelectedLanguage(langCode)
        // If Bilibili cues are loaded, re-translate them
        val cues = _bilibiliCues.value
        if (cues.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val translated = com.example.util.SubtitleTranslator.translateCues(cues, targetLang = langCode)
                _bilibiliCues.value = translated
            }
        }
    }

    fun selectBilibiliSubtitleTrack(option: CaptionOption?) {
        _selectedSubtitleTrack.value = option
        if (option == null) {
            _bilibiliCues.value = emptyList()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(option.url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                val jsonStr = okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (!jsonStr.isNullOrBlank()) {
                    val rawCues = com.example.util.SubtitleTranslator.parseBilibiliSubtitleJson(jsonStr)
                    val targetLang = _targetCaptionLanguage.value
                    val translatedCues = com.example.util.SubtitleTranslator.translateCues(rawCues, targetLang = targetLang)
                    _bilibiliCues.value = translatedCues
                    if (_subtitleMode.value == SubtitleMode.OFF) {
                        _subtitleMode.value = SubtitleMode.BILIBILI_TRANSLATED
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("GlobalPlayerManager", "Failed to load Bilibili subtitle JSON: ${e.message}")
            }
        }
    }

    private val _areControlsVisible = MutableStateFlow(true)
    val areControlsVisible: StateFlow<Boolean> = _areControlsVisible.asStateFlow()

    private var autoHideControlsJob: Job? = null

    fun scheduleControlsAutoHide(delayMs: Long = 2700L) {
        autoHideControlsJob?.cancel()
        autoHideControlsJob = scope.launch {
            delay(delayMs)
            _areControlsVisible.value = false
        }
    }

    fun setControlsVisibility(visible: Boolean) {
        _areControlsVisible.value = visible
        if (visible) {
            scheduleControlsAutoHide()
        } else {
            autoHideControlsJob?.cancel()
        }
    }

    fun showControls(autoHideDelayMs: Long = 2700L) {
        _areControlsVisible.value = true
        scheduleControlsAutoHide(autoHideDelayMs)
    }

    fun hideControls() {
        autoHideControlsJob?.cancel()
        _areControlsVisible.value = false
    }

    fun toggleControlsVisibility(autoHideDelayMs: Long = 2700L) {
        val next = !_areControlsVisible.value
        _areControlsVisible.value = next
        if (next) {
            scheduleControlsAutoHide(autoHideDelayMs)
        } else {
            autoHideControlsJob?.cancel()
        }
    }

    private val _playbackEnded = MutableStateFlow(false)
    val playbackEnded: StateFlow<Boolean> = _playbackEnded.asStateFlow()

    fun clearPlaybackEnded() {
        _playbackEnded.value = false
    }

    private var currentLoadedMediaKey: String? = null

    fun notifyFirstFrameRendered() {
        _firstFrameRendered.value = true
    }

    fun resetFirstFrameState() {
        _firstFrameRendered.value = false
    }

    private var playbackFailedListener: ((Int?) -> Unit)? = null

    fun setPlaybackFailedListener(listener: ((Int?) -> Unit)?) {
        playbackFailedListener = listener
    }

    fun getExoPlayer(context: Context): ExoPlayer {
        appContext = context.applicationContext
        val existing = exoPlayerInstance
        return if (existing == null) {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,   // minBufferMs (15s minimum buffer to prevent bandwidth thrashing and memory pressure)
                    60_000,   // maxBufferMs (60s)
                    800,      // bufferForPlaybackMs (800ms for instant initial playback startup)
                    1_500     // bufferForPlaybackAfterRebufferMs (1.5s fast resume after buffering)
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(10_000, true)
                .build()

            val audioEnhancementProcessor = com.example.ui.player.audio.AudioEnhancementEngine.getAudioProcessor()
            val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink? {
                    return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(audioEnhancementProcessor))
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build()
                }
            }.apply {
                setEnableDecoderFallback(true)
                setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                forceDisableMediaCodecAsynchronousQueueing()
            }

            val player = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .build()
            player.playWhenReady = true
            val isLooping = com.example.util.PlaybackPreferences.getInstance(context.applicationContext).loopVideoEnabled.value
            _isLoopEnabled.value = isLooping
            player.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                        val ratio = (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
                        if (ratio in 0.4f..2.5f) {
                            _videoAspectRatio.value = ratio
                        }
                        com.example.effects.VideoEnhancementEngine.updateVideoDimensions(videoSize.width, videoSize.height)
                    }
                }

                override fun onRenderedFirstFrame() {
                    _firstFrameRendered.value = true
                    com.example.util.PlaybackPipelineTracker.logFirstFrame(player.duration)
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    updatePlayerPositions(player)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _isPlaying.value = player.isPlaying
                    _isBuffering.value = (state == Player.STATE_BUFFERING)
                    updatePlayerPositions(player)
                    if (state == Player.STATE_READY) {
                        if (player.playWhenReady) {
                            _isBuffering.value = false
                        }
                        pendingResumePositionMs?.let { targetPos ->
                            if (targetPos > 0L && kotlin.math.abs(player.currentPosition - targetPos) > 1000L) {
                                player.seekTo(targetPos)
                            }
                            pendingResumePositionMs = null
                        }
                    }
                    if (state == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _isBuffering.value = false
                        _playbackEnded.value = true
                        appContext?.let { ctx ->
                            _activeStreamData.value?.videoId?.let { vid ->
                                com.example.util.PlaybackResumeManager.clearPosition(ctx, vid)
                            }
                        }
                    }
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateAudioTracks(tracks)
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    updatePlayerPositions(player)
                    if (!timeline.isEmpty) {
                        pendingResumePositionMs?.let { targetPos ->
                            if (targetPos > 0L && kotlin.math.abs(player.currentPosition - targetPos) > 1000L) {
                                player.seekTo(targetPos)
                            }
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    updatePlayerPositions(player)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val rootCause = error.cause
                    val httpStatus = (rootCause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                    val errorCodeName = error.errorCodeName

                    android.util.Log.e(
                        "ButterflyTorrent",
                        "Media3 Player Error: ${error.message}, Cause: ${rootCause?.message}, Code: $errorCodeName (${error.errorCode}), HTTP: $httpStatus, Uri: ${exoPlayerInstance?.currentMediaItem?.localConfiguration?.uri}"
                    )

                    com.example.util.PlaybackPipelineTracker.logPlaybackError(
                        errorCodeName = errorCodeName,
                        errorCode = error.errorCode,
                        message = error.message,
                        causeName = rootCause?.javaClass?.simpleName,
                        causeMessage = rootCause?.message,
                        httpStatus = httpStatus
                    )

                    val detailedError = StringBuilder("Media3 Error [$errorCodeName / ${error.errorCode}]: ${error.message ?: "Unknown error"}")
                    if (rootCause != null) {
                        detailedError.append("\nCause: [${rootCause.javaClass.simpleName}] ${rootCause.message}")
                    }
                    if (httpStatus != null) {
                        detailedError.append(" (HTTP Status $httpStatus)")
                    }

                    _playerError.value = detailedError.toString()
                    _isPlaying.value = false
                    playbackFailedListener?.invoke(httpStatus)
                }
            })
            exoPlayerInstance = player
            startProgressTracker()
            attachVideoEffectsPipeline(player)
            player
        } else {
            existing
        }
    }

    private var videoEffectsJob: Job? = null
    private var hasAppliedCustomEffects = false

    private fun attachVideoEffectsPipeline(player: ExoPlayer) {
        videoEffectsJob?.cancel()
        videoEffectsJob = CoroutineScope(Dispatchers.Main).launch {
            com.example.effects.VideoEnhancementEngine.config.collect { config ->
                try {
                    val isAnime = com.example.effects.VideoEnhancementEngine.telemetry.value.isAnimeDetected
                    val effect = com.example.effects.ShaderEnhancementLoader.createEffect(config, isAnime)
                    if (effect != null) {
                        player.setVideoEffects(listOf(effect))
                        hasAppliedCustomEffects = true
                    } else if (hasAppliedCustomEffects) {
                        player.setVideoEffects(emptyList())
                        hasAppliedCustomEffects = false
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GlobalPlayerManager", "Video effects update notice: ${e.message}")
                }
            }
        }
    }

    private fun updatePlayerPositions(player: ExoPlayer) {
        val cur = player.currentPosition
        val dur = player.duration
        val buf = player.bufferedPosition
        if (dur > 0 && cur >= 0) {
            _currentPositionMs.value = cur
            _durationMs.value = dur
            _bufferedPositionMs.value = buf.coerceAtLeast(cur)
            _progressFraction.value = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            _bufferedFraction.value = (buf.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            
            appContext?.let { ctx ->
                _activeStreamData.value?.videoId?.let { vid ->
                    if (vid.isNotBlank() && cur > 0L) {
                        com.example.util.PlaybackResumeManager.savePosition(ctx, vid, cur, dur)
                    }
                }
            }
        } else if (cur >= 0) {
            _currentPositionMs.value = cur
            appContext?.let { ctx ->
                _activeStreamData.value?.videoId?.let { vid ->
                    if (vid.isNotBlank() && cur > 0L) {
                        com.example.util.PlaybackResumeManager.savePosition(ctx, vid, cur, _durationMs.value)
                    }
                }
            }
        }

        com.example.subtitles.SubtitleManager.updatePlaybackPosition(cur)
        appContext?.let { ctx ->
            com.example.smartskip.SmartSkipPlayerEngine.onPlaybackPositionUpdate(ctx, cur)
        }

        val posSec = cur / 1000f
        val cues = _bilibiliCues.value
        if (cues.isNotEmpty() && _subtitleMode.value != SubtitleMode.OFF && _subtitleMode.value != SubtitleMode.AI_LIVE_CAPTIONS && _subtitleMode.value != SubtitleMode.EXTERNAL_PROVIDER) {
            val activeCue = cues.find { posSec >= it.fromSeconds && posSec <= it.toSeconds }
            if (activeCue != null) {
                _currentActiveSubtitleText.value = activeCue.text
                _currentActiveTranslatedText.value = activeCue.translatedText ?: activeCue.text
            } else {
                _currentActiveSubtitleText.value = ""
                _currentActiveTranslatedText.value = ""
            }
        } else if (_subtitleMode.value == SubtitleMode.EXTERNAL_PROVIDER) {
            _currentActiveSubtitleText.value = com.example.subtitles.SubtitleManager.currentActiveOriginalText.value
            _currentActiveTranslatedText.value = com.example.subtitles.SubtitleManager.currentActiveTranslatedText.value
        }
    }

    private fun updateAudioTracks(tracks: androidx.media3.common.Tracks) {
        val list = mutableListOf<com.example.model.AudioTrackOption>()
        var groupIdx = 0
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val rawLang = format.language ?: ""
                    val displayLang = when (rawLang.lowercase()) {
                        "jpn", "ja", "japanese" -> "Japanese (日本語)"
                        "eng", "en", "english" -> "English"
                        "hin", "hi", "hindi" -> "Hindi (हिंदी)"
                        "spa", "es", "spanish" -> "Spanish (Español)"
                        "fre", "fra", "fr", "french" -> "French (Français)"
                        "ger", "deu", "de", "german" -> "German (Deutsch)"
                        "chi", "zho", "zh", "chinese" -> "Chinese (中文)"
                        "kor", "ko", "korean" -> "Korean (한국어)"
                        "por", "pt", "portuguese" -> "Portuguese"
                        "rus", "ru", "russian" -> "Russian"
                        "" -> "Track ${list.size + 1}"
                        else -> rawLang.replaceFirstChar { it.uppercase() }
                    }
                    val label = if (!format.label.isNullOrBlank()) {
                        "${format.label} ($displayLang)"
                    } else {
                        displayLang
                    }
                    val channels = if (format.channelCount > 0) {
                        if (format.channelCount == 2) "Stereo"
                        else if (format.channelCount == 6) "5.1 Surround"
                        else "${format.channelCount} Ch"
                    } else ""

                    val isSelected = group.isTrackSelected(i)
                    list.add(
                        com.example.model.AudioTrackOption(
                            groupIndex = groupIdx,
                            trackIndex = i,
                            label = label,
                            languageCode = rawLang.ifBlank { "default" },
                            displayLanguage = displayLang,
                            isSelected = isSelected,
                            channelInfo = channels,
                            trackGroup = group
                        )
                    )
                }
            }
            groupIdx++
        }
        _audioTracks.value = list
    }

    fun selectAudioTrack(option: com.example.model.AudioTrackOption) {
        exoPlayerInstance?.let { player ->
            val override = androidx.media3.common.TrackSelectionOverride(
                option.trackGroup.mediaTrackGroup,
                option.trackIndex
            )
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .build()
            updateAudioTracks(player.currentTracks)
        }
    }

    fun setPreferredAudioLanguage(languageCode: String) {
        exoPlayerInstance?.let { player ->
            val builder = player.trackSelectionParameters.buildUpon()
            if (languageCode == "auto" || languageCode.isBlank()) {
                builder.setPreferredAudioLanguage(null)
            } else {
                builder.setPreferredAudioLanguage(languageCode)
            }
            player.trackSelectionParameters = builder.build()
            updateAudioTracks(player.currentTracks)
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                try {
                    val player = exoPlayerInstance
                    if (player != null && player.playbackState != Player.STATE_IDLE) {
                        updatePlayerPositions(player)
                        val delayMs = if (player.isPlaying) 250L else 500L
                        delay(delayMs)
                    } else {
                        delay(500L)
                    }
                } catch (e: Exception) {
                    delay(500L)
                }
            }
        }
    }

    fun prepareAndPlay(
        context: Context,
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        hlsUrl: String?,
        captionOption: CaptionOption?,
        initialPos: Long = 0L
    ) {
        val player = getExoPlayer(context)
        _playbackEnded.value = false
        if (streamData != null) {
            _activeStreamData.value = streamData
            _bilibiliSubtitleTracks.value = streamData.captionOptions
            if (streamData.captionOptions.isNotEmpty()) {
                val firstOption = streamData.captionOptions.first()
                selectBilibiliSubtitleTrack(firstOption)
            } else {
                _selectedSubtitleTrack.value = null
                _bilibiliCues.value = emptyList()
            }
        }
        _playerError.value = null

        val rawUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl
        if (rawUrl.isNullOrEmpty()) {
            currentLoadedMediaKey = null
            _firstFrameRendered.value = false
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
            _bufferedPositionMs.value = 0L
            _progressFraction.value = 0f
            _bufferedFraction.value = 0f
            exoPlayerInstance?.let { p ->
                p.playWhenReady = false
                p.stop()
                p.clearMediaItems()
            }
            return
        }

        val effectivePlayableUrl = rawUrl
        val mediaKey = "${effectivePlayableUrl}_${captionOption?.languageCode}"
        if (mediaKey == currentLoadedMediaKey && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            player.playWhenReady = true
            _isPlaying.value = true
            return
        }

        val previousPos = _currentPositionMs.value.coerceAtLeast(try { exoPlayerInstance?.currentPosition ?: 0L } catch (_: Throwable) { 0L })
        val isQualitySwitch = streamData?.videoId != null && streamData.videoId == _activeStreamData.value?.videoId && previousPos > 0L

        val effectiveResumePos = when {
            initialPos > 0L -> initialPos
            isQualitySwitch -> previousPos
            streamData?.videoId != null -> com.example.util.PlaybackResumeManager.getSavedPosition(context, streamData.videoId)
            else -> 0L
        }

        _firstFrameRendered.value = false
        _currentPositionMs.value = effectiveResumePos
        _durationMs.value = 0L
        _bufferedPositionMs.value = 0L
        _progressFraction.value = 0f
        _bufferedFraction.value = 0f
        _playerError.value = null
        currentLoadedMediaKey = mediaKey
        pendingResumePositionMs = effectiveResumePos.takeIf { it > 0L }

        try {
            player.stop()
            player.clearMediaItems()

            val combinedHeaders = mutableMapOf<String, String>()
            streamData?.headers?.let { combinedHeaders.putAll(it) }
            streamOption?.headers?.let { combinedHeaders.putAll(it) }

            val uriHost = try { Uri.parse(rawUrl).host ?: "unknown" } catch (_: Exception) { "unknown" }
            android.util.Log.i("GlobalPlayerManager", "[PLAYBACK_START] provider=${streamData?.providerId ?: "direct"}, videoId=${streamData?.videoId}, format=${streamOption?.format}, isMuxed=${streamOption?.isMuxed}, host=$uriHost, quality='${streamOption?.qualityLabel}', headersCount=${combinedHeaders.size}, headerKeys=${combinedHeaders.keys}")

            val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_READ_SEF_DATA)
                .setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK)
                .setMatroskaExtractorFlags(androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)

            val errorHandlingPolicy = object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1) {
                override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    val rootCause = loadErrorInfo.exception
                    if (rootCause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        if (rootCause.responseCode in 400..599) {
                            return androidx.media3.common.C.TIME_UNSET
                        }
                    }
                    return super.getRetryDelayMsFor(loadErrorInfo)
                }
            }

            fun createMediaSourceFactory(targetUrl: String, specificHeaders: Map<String, String>): androidx.media3.exoplayer.source.DefaultMediaSourceFactory {
                val dsFactory = OkHttpDataSource.Factory(okHttpClient)
                val headersMap = mutableMapOf<String, String>()
                streamData?.headers?.let { headersMap.putAll(it) }
                headersMap.putAll(specificHeaders)

                var customUserAgentSet = false
                val reqHeaders = mutableMapOf<String, String>()
                headersMap.forEach { (k, v) ->
                    if (k.equals("User-Agent", ignoreCase = true)) {
                        dsFactory.setUserAgent(v)
                        customUserAgentSet = true
                    } else {
                        reqHeaders[k] = v
                    }
                }
                if (!customUserAgentSet) {
                    dsFactory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                }

                // Inject domain-specific referer & origin headers if not explicitly specified
                val lowerTarget = targetUrl.lowercase()
                val isGoogleStorageOrPublic = lowerTarget.contains("googlevideo.com") || lowerTarget.contains("youtube.com") || lowerTarget.contains("youtu.be") || lowerTarget.contains("ytimg.com") ||
                        lowerTarget.contains("googleapis.com") || lowerTarget.contains("storage.googleapis") || lowerTarget.contains("commondatastorage") || lowerTarget.contains("w3schools") || lowerTarget.contains("githubusercontent")
                val isBilibiliStream = lowerTarget.contains("bilibili") || lowerTarget.contains("bilivideo") || lowerTarget.contains("biliapi") || lowerTarget.contains("hdslb") || lowerTarget.contains("szbdyd") || lowerTarget.contains("mcdn") || lowerTarget.contains("acgvideo") || lowerTarget.contains("upgcxcode") || lowerTarget.contains("upos-") || lowerTarget.contains("akamaized") || streamData?.providerId == "bilibili"

                val isVkStream = lowerTarget.contains("vk.com") || lowerTarget.contains("vkuser") || lowerTarget.contains("mycdn.me") || lowerTarget.contains("vk-cdn") || lowerTarget.contains("userapi.com")

                if (isGoogleStorageOrPublic) {
                    reqHeaders.remove("Referer")
                    reqHeaders.remove("referer")
                    reqHeaders.remove("Origin")
                    reqHeaders.remove("origin")
                    reqHeaders.remove("Cookie")
                    reqHeaders.remove("cookie")
                } else if (isVkStream) {
                    reqHeaders["Referer"] = "https://vk.com/"
                    reqHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    reqHeaders.remove("Origin")
                    reqHeaders.remove("origin")
                    reqHeaders.remove("Cookie")
                    reqHeaders.remove("cookie")
                } else if (isBilibiliStream) {
                    reqHeaders["Referer"] = "https://www.bilibili.com/"
                    reqHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    reqHeaders["Accept"] = "*/*"
                    reqHeaders["Accept-Language"] = "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7"
                    reqHeaders["Sec-Fetch-Mode"] = "no-cors"
                    reqHeaders["Sec-Fetch-Site"] = "cross-site"
                    reqHeaders.remove("Origin")
                    reqHeaders.remove("origin")
                } else {
                    val hasReferer = reqHeaders.keys.any { it.equals("Referer", ignoreCase = true) }
                    if (!hasReferer) {
                        when {
                            lowerTarget.contains("dailymotion.com") || lowerTarget.contains("dmcdn.net") || lowerTarget.contains("dai.ly") || lowerTarget.contains("cdndirector") -> {
                                reqHeaders["Referer"] = "https://www.dailymotion.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.dailymotion.com"
                                }
                            }
                            lowerTarget.contains("archive.org") || lowerTarget.contains("us.archive.org") || lowerTarget.contains("ia60") || lowerTarget.contains("ia80") || lowerTarget.contains("ia90") || streamData?.providerId == "archive_org" || streamData?.providerId == "archive" -> {
                                reqHeaders["Referer"] = "https://archive.org/"
                            }
                            lowerTarget.contains("pornhub.com") || lowerTarget.contains("phncdn.com") || (streamData?.providerId == "pornhub") -> {
                                reqHeaders["Referer"] = "https://www.pornhub.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.pornhub.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en"
                                }
                            }
                            lowerTarget.contains("eporner") || lowerTarget.contains("static-cluster") || streamData?.providerId == "eporner" -> {
                                reqHeaders["Referer"] = "https://www.eporner.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.eporner.com"
                                }
                            }
                            lowerTarget.contains("4tube.com") || lowerTarget.contains("fbtcdn.com") || lowerTarget.contains("ttcache.com") || streamData?.providerId == "4tube" || streamData?.providerId == "fourtube" -> {
                                reqHeaders["Referer"] = "https://www.4tube.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.4tube.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; ft_mature=1; platform=pc; consent=1"
                                }
                            }
                            lowerTarget.contains("redtube.com") || lowerTarget.contains("rdtcdn.com") || streamData?.providerId == "redtube" -> {
                                reqHeaders["Referer"] = "https://www.redtube.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.redtube.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; platform=pc; has_consent=1"
                                }
                            }
                            lowerTarget.contains("youporn.com") || lowerTarget.contains("ypncdn.com") || streamData?.providerId == "youporn" -> {
                                reqHeaders["Referer"] = "https://www.youporn.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.youporn.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; platform=pc"
                                }
                            }
                            lowerTarget.contains("xhamster.com") -> {
                                reqHeaders["Referer"] = "https://xhamster.com/"
                            }
                            lowerTarget.contains("xvideos.com") -> {
                                reqHeaders["Referer"] = "https://www.xvideos.com/"
                            }
                            lowerTarget.contains("vimeo.com") || lowerTarget.contains("vimeocdn.com") || (streamData?.providerId == "vimeo" && !isBilibiliStream) -> {
                                reqHeaders["Referer"] = "https://vimeo.com/"
                                reqHeaders["Origin"] = "https://vimeo.com"
                            }
                            (lowerTarget.contains("hotstar.com") || lowerTarget.contains("hotstar-cdn") || lowerTarget.contains("jiohotstar") || lowerTarget.contains("starott.com") || (streamData?.providerId == "hotstar")) && !isGoogleStorageOrPublic -> {
                                reqHeaders["Referer"] = "https://www.hotstar.com/"
                                reqHeaders["Origin"] = "https://www.hotstar.com"
                            }
                            lowerTarget.contains("helvid") || lowerTarget.contains("upload18") || lowerTarget.contains("apijav") -> {
                                reqHeaders["Referer"] = "https://upload18.org/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://upload18.org"
                                }
                            }
                            lowerTarget.contains("tnaflix.com") || lowerTarget.contains("tnaflix") || lowerTarget.contains("tnacdn") -> {
                                reqHeaders["Referer"] = "https://www.tnaflix.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.tnaflix.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; platform=pc; has_consent=1"
                                }
                            }
                            lowerTarget.contains("hanime1") || lowerTarget.contains("hanime.tv") || lowerTarget.contains("hanime") || lowerTarget.contains("hembed.com") || lowerTarget.contains("vdownload") -> {
                                reqHeaders["Referer"] = "https://hanime1.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://hanime1.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; country=US; language=en"
                                }
                            }
                            lowerTarget.contains("noodlemagazine.com") || lowerTarget.contains("noodlemag") -> {
                                reqHeaders["Referer"] = "https://noodlemagazine.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://noodlemagazine.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; platform=pc; ft_mature=1; consent=1"
                                }
                            }
                            lowerTarget.contains("thisvid.com") || lowerTarget.contains("tvid") -> {
                                reqHeaders["Referer"] = "https://thisvid.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://thisvid.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; platform=pc; has_consent=1"
                                }
                            }
                            lowerTarget.contains("hqporner.com") || lowerTarget.contains("hqporner") || lowerTarget.contains("hqplayer") -> {
                                reqHeaders["Referer"] = "https://hqporner.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://hqporner.com"
                                }
                                if (!reqHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                                    reqHeaders["Cookie"] = "age_verified=1; country=US; consent=1"
                                }
                            }
                            lowerTarget.contains("beeg.com") || lowerTarget.contains("externulls.com") || lowerTarget.contains("ahacdn.me") -> {
                                reqHeaders["Referer"] = "https://beeg.com/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://beeg.com"
                                }
                            }
                            lowerTarget.contains("bigo.tv") || lowerTarget.contains("bigolive.tv") || lowerTarget.contains("bigo.sg") || lowerTarget.contains("cubetecn.com") || lowerTarget.contains("bigocdn.com") -> {
                                reqHeaders["Referer"] = "https://www.bigo.tv/"
                                if (!reqHeaders.keys.any { it.equals("Origin", ignoreCase = true) }) {
                                    reqHeaders["Origin"] = "https://www.bigo.tv"
                                }
                                reqHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            }
                        }
                    }
                }

                if (reqHeaders.isNotEmpty()) {
                    dsFactory.setDefaultRequestProperties(reqHeaders)
                }

                return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsFactory, extractorsFactory)
                    .setLoadErrorHandlingPolicy(errorHandlingPolicy)
            }

            fun sanitizeMediaUrl(rawUrl: String?): String? {
                if (rawUrl.isNullOrBlank()) return null
                var trimmed = rawUrl.trim()
                if (trimmed.isEmpty()) return null

                if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") &&
                    !trimmed.startsWith("file://") && !trimmed.startsWith("content://") &&
                    !trimmed.startsWith("asset://") && !trimmed.startsWith("rtmp://") &&
                    !trimmed.startsWith("rtsp://") && !trimmed.startsWith("udp://")
                ) {
                    if (trimmed.contains(".") && !trimmed.startsWith("/")) {
                        trimmed = "https://$trimmed"
                    } else {
                        return null
                    }
                }

                return try {
                    val sanitized = trimmed
                        .replace("\n", "")
                        .replace("\r", "")
                        .replace("\t", "")
                        .replace(" ", "%20")
                        .replace("\"", "%22")
                        .replace("<", "%3C")
                        .replace(">", "%3E")
                        .replace("\\", "/")

                    val parsed = Uri.parse(sanitized)
                    if (parsed.scheme.isNullOrEmpty()) return null
                    sanitized
                } catch (_: Throwable) {
                    null
                }
            }

            fun buildMediaItem(
                rawUrl: String,
                format: String? = null,
                subtitles: List<MediaItem.SubtitleConfiguration> = emptyList()
            ): MediaItem? {
                val cleanUrl = sanitizeMediaUrl(rawUrl) ?: return null
                val uri = Uri.parse(cleanUrl)
                val lowerUrl = cleanUrl.lowercase()
                val lowerFormat = format?.lowercase()

                val builder = MediaItem.Builder().setUri(uri)

                if (lowerFormat == "hls" || lowerFormat == "m3u8" || lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8")) {
                    builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                } else if (lowerFormat == "mpd" || lowerUrl.contains(".mpd")) {
                    builder.setMimeType(MimeTypes.APPLICATION_MPD)
                } else if (lowerFormat == "mkv" || lowerUrl.contains(".mkv") || lowerUrl.contains("video%2fx-matroska") || lowerUrl.contains("video/x-matroska")) {
                    builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
                } else if (lowerFormat == "audio_webm" || (lowerFormat == "audio" && lowerUrl.contains("webm")) || lowerUrl.contains("mime=audio%2fwebm") || lowerUrl.contains("mime=audio/webm")) {
                    builder.setMimeType(MimeTypes.AUDIO_WEBM)
                } else if (lowerFormat == "webm" || lowerUrl.contains("mime=video%2fwebm") || lowerUrl.contains("mime=video/webm") || lowerUrl.contains(".webm")) {
                    builder.setMimeType(MimeTypes.VIDEO_WEBM)
                } else if (lowerFormat == "audio" || lowerFormat == "audio_mp4" || lowerFormat == "m4a" || lowerFormat == "aac" || lowerFormat == "mp3" ||
                    lowerUrl.contains("mime=audio%2fmp4") || lowerUrl.contains("mime=audio/mp4") || lowerUrl.contains("mime=audio%2fm4a") || lowerUrl.contains(".m4a") ||
                    lowerUrl.contains("-30280.m4s") || lowerUrl.contains("-30232.m4s") || lowerUrl.contains("-30216.m4s") || lowerUrl.contains("-30250.m4s") || lowerUrl.contains("-30251.m4s") || lowerUrl.contains("_da3-1-302") || lowerUrl.contains("-302")
                ) {
                    builder.setMimeType(MimeTypes.AUDIO_MP4)
                } else if (lowerFormat == "video" || lowerFormat == "video_mp4" || lowerFormat == "mp4" || lowerUrl.contains("mime=video%2fmp4") || lowerUrl.contains("mime=video/mp4") || lowerUrl.contains(".mp4")) {
                    builder.setMimeType(MimeTypes.VIDEO_MP4)
                }

                if (subtitles.isNotEmpty()) {
                    builder.setSubtitleConfigurations(subtitles)
                }

                return builder.build()
            }

            var mediaSourceSet = false
            if (streamOption != null) {
                val vUrl = streamOption.videoUrl ?: streamOption.videoStream?.url
                val aUrl = streamOption.audioUrl ?: streamOption.audioStream?.url

                val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
                if (captionOption != null && !captionOption.url.isNullOrEmpty()) {
                    val cleanCapUrl = sanitizeMediaUrl(captionOption.url)
                    if (cleanCapUrl != null) {
                        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(cleanCapUrl))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(captionOption.languageCode)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        subtitleConfigs.add(subtitleConfig)
                    }
                }

                if (streamOption.isMuxed && !vUrl.isNullOrEmpty()) {
                    val mediaSourceFactory = createMediaSourceFactory(vUrl, streamOption.headers)
                    val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                    if (item != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                } else if (!streamOption.isMuxed && !vUrl.isNullOrEmpty() && !aUrl.isNullOrEmpty()) {
                    val videoSourceFactory = createMediaSourceFactory(vUrl, streamOption.headers)
                    val audioHeaders = if (streamOption.audioHeaders.isNotEmpty()) streamOption.audioHeaders else streamOption.headers
                    val audioSourceFactory = createMediaSourceFactory(aUrl, audioHeaders)

                    val videoItem = buildMediaItem(vUrl, streamOption.format.ifEmpty { "video" }, subtitleConfigs)
                    val audioItem = buildMediaItem(aUrl, "audio")
                    if (videoItem != null && audioItem != null) {
                        val videoSource = videoSourceFactory.createMediaSource(videoItem)
                        val audioSource = audioSourceFactory.createMediaSource(audioItem)
                        val mergedSource = MergingMediaSource(videoSource, audioSource)
                        player.setMediaSource(mergedSource)
                        mediaSourceSet = true
                    } else if (videoItem != null) {
                        val videoSource = videoSourceFactory.createMediaSource(videoItem)
                        player.setMediaSource(videoSource)
                        mediaSourceSet = true
                    }
                } else if (!vUrl.isNullOrEmpty()) {
                    val mediaSourceFactory = createMediaSourceFactory(vUrl, streamOption.headers)
                    val item = buildMediaItem(vUrl, streamOption.format, subtitleConfigs)
                    if (item != null) {
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }
            }

            if (!mediaSourceSet && !hlsUrl.isNullOrEmpty()) {
                val cleanHls = sanitizeMediaUrl(hlsUrl)
                if (cleanHls != null) {
                    val item = buildMediaItem(cleanHls, "hls")
                    if (item != null) {
                        val mediaSourceFactory = createMediaSourceFactory(cleanHls, streamData?.headers ?: emptyMap())
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }
            } else if (!mediaSourceSet && !rawUrl.isNullOrEmpty()) {
                val cleanRaw = sanitizeMediaUrl(rawUrl)
                if (cleanRaw != null) {
                    val item = buildMediaItem(cleanRaw, streamOption?.format)
                    if (item != null) {
                        val mediaSourceFactory = createMediaSourceFactory(cleanRaw, streamOption?.headers ?: streamData?.headers ?: emptyMap())
                        val mediaSource = mediaSourceFactory.createMediaSource(item)
                        player.setMediaSource(mediaSource)
                        mediaSourceSet = true
                    }
                }
            }

            if (!mediaSourceSet) {
                _playerError.value = "Unable to parse valid video stream URL"
                playbackFailedListener?.invoke(null)
                return
            }

            if (effectiveResumePos > 0L) {
                player.seekTo(effectiveResumePos)
            }

            com.example.util.PlaybackPipelineTracker.logPrepare(
                urlSnippet = effectivePlayableUrl.take(60),
                headersCount = combinedHeaders.size
            )

            player.prepare()
            player.playWhenReady = true
            _isPlaying.value = true

            // Save to Room Database for offline access and zero-buffering preloading
            val ctx = context.applicationContext

            // Smart Skip / SponsorBlock segment resolution
            if (streamData != null) {
                com.example.effects.VideoEffectsManager.onVideoChanged(streamData.videoId)
                com.example.effects.VideoEnhancementEngine.onVideoLoaded(
                    videoId = streamData.videoId,
                    title = streamData.title,
                    channel = streamData.channelName,
                    tags = null,
                    description = streamData.description,
                    width = 0,
                    height = 0
                )
                com.example.smartskip.SmartSkipPlayerEngine.onVideoChanged(
                    context = ctx,
                    videoId = streamData.videoId,
                    durationMs = _durationMs.value,
                    title = streamData.title,
                    channelName = streamData.channelName,
                    providerId = streamData.providerId
                )
            }

            // Trigger External Subtitle discovery with strict fallback order: Embedded -> Bilibili -> External -> Cached -> Whisper.cpp
            if (streamData != null) {
                com.example.subtitles.SubtitleManager.resolveSubtitlesForPlayback(
                    context = ctx,
                    streamData = streamData,
                    onUsableSubtitleFound = { item ->
                        android.util.Log.i("GlobalPlayerManager", "Subtitle auto-resolved from: ${item.providerName} (${item.languageCode})")
                        if (_subtitleMode.value == SubtitleMode.OFF) {
                            _subtitleMode.value = if (item.providerId == "bilibili") SubtitleMode.BILIBILI_TRANSLATED else SubtitleMode.EXTERNAL_PROVIDER
                        }
                    },
                )
            }

            scope.launch(Dispatchers.IO) {
                try {
                    val videoRepo = com.example.db.VideoCacheRepository(ctx)
                    if (streamData != null) {
                        videoRepo.cacheVideoMetadata(
                            videoId = streamData.videoId,
                            title = streamData.title,
                            channelName = streamData.channelName,
                            thumbnailUrl = "https://i.ytimg.com/vi/${streamData.videoId}/hqdefault.jpg",
                            description = streamData.description,
                            duration = "",
                            providerId = streamData.providerId
                        )
                        if (effectivePlayableUrl.isNotBlank()) {
                            videoRepo.cachePreloadedStream(
                                videoId = streamData.videoId,
                                streamUrl = effectivePlayableUrl,
                                hlsUrl = hlsUrl ?: streamData.hlsUrl,
                                qualityLabel = streamOption?.qualityLabel ?: "Auto"
                            )
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            _playerError.value = e.localizedMessage ?: "Playback initialization failed"
        }
    }

    fun hasLoadedMedia(): Boolean = currentLoadedMediaKey != null && (exoPlayerInstance?.mediaItemCount ?: 0) > 0

    fun togglePlayPause() {
        exoPlayerInstance?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else if (player.mediaItemCount > 0) {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun play() {
        exoPlayerInstance?.let { player ->
            if (player.mediaItemCount > 0) {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun pause() {
        exoPlayerInstance?.let { player ->
            player.pause()
            _isPlaying.value = false
            appContext?.let { ctx ->
                _activeStreamData.value?.videoId?.let { vid ->
                    val cur = player.currentPosition
                    val dur = player.duration
                    if (cur > 0L) {
                        com.example.util.PlaybackResumeManager.savePosition(ctx, vid, cur, dur)
                    }
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayerInstance
        val playerDur = player?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: _durationMs.value
        val safeMax = if (playerDur > 1000L) playerDur - 500L else if (playerDur > 0L) playerDur else Long.MAX_VALUE
        val target = positionMs.coerceIn(0L, safeMax)

        _currentPositionMs.value = target
        if (playerDur > 0) {
            _progressFraction.value = (target.toFloat() / playerDur.toFloat()).coerceIn(0f, 1f)
        }

        try {
            if (player != null && player.playbackState != Player.STATE_IDLE) {
                player.seekTo(target)
            }
        } catch (e: Throwable) {
            android.util.Log.w("GlobalPlayerManager", "seekTo error: ${e.message}")
        }
    }

    fun seekForward(deltaMs: Long = 10000L) {
        val cur = _currentPositionMs.value
        val dur = _durationMs.value
        val target = if (dur > 0) (cur + deltaMs).coerceAtMost(dur) else cur + deltaMs
        seekTo(target)
    }

    fun seekBackward(deltaMs: Long = 10000L) {
        val cur = _currentPositionMs.value
        val target = (cur - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayerInstance?.playbackParameters = PlaybackParameters(speed)
    }

    fun stopAndClear() {
        autoHideControlsJob?.cancel()
        pendingResumePositionMs = null
        appContext?.let { ctx ->
            _activeStreamData.value?.videoId?.let { vid ->
                val cur = _currentPositionMs.value
                val dur = _durationMs.value
                if (cur > 0L) {
                    com.example.util.PlaybackResumeManager.savePosition(ctx, vid, cur, dur)
                }
            }
        }
        currentLoadedMediaKey = null
        _activeStreamData.value = null
        _progressFraction.value = 0f
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _bufferedPositionMs.value = 0L
        _bufferedFraction.value = 0f
        _isPlaying.value = false
        _firstFrameRendered.value = false
        _playerError.value = null
        com.example.smartskip.SmartSkipPlayerEngine.reset()
        exoPlayerInstance?.let { player ->
            try {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            } catch (ignored: Throwable) {}
        }
    }
}
