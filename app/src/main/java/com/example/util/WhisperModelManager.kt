package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class WhisperModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val isRecommended: Boolean = false,
    val isMultilingual: Boolean = true,
    val approximateSizeMb: String = "Calculating...",
    val isDownloaded: Boolean = false,
    val localSizeBytes: Long = 0L,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false
)

object WhisperModelManager {
    private const val TAG = "WhisperModelManager"
    private const val MODELS_DIR_NAME = "whisper-models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Official Hugging Face repository for ggml whisper models
    private val BASE_HF_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    val AVAILABLE_MODELS = listOf(
        WhisperModelInfo(
            id = "tiny",
            name = "Tiny (Multilingual)",
            description = "Lightweight & fast. Ideal for real-time mobile performance.",
            downloadUrl = "$BASE_HF_URL/ggml-tiny.bin",
            fileName = "ggml-tiny.bin",
            isRecommended = false,
            isMultilingual = true
        ),
        WhisperModelInfo(
            id = "tiny.q5_1",
            name = "Tiny Q5_1 (Quantized)",
            description = "Ultra-compact quantized model. Fastest inference speed.",
            downloadUrl = "$BASE_HF_URL/ggml-tiny-q5_1.bin",
            fileName = "ggml-tiny-q5_1.bin",
            isRecommended = false,
            isMultilingual = true
        ),
        WhisperModelInfo(
            id = "base",
            name = "Base (Multilingual)",
            description = "Recommended for Japanese & Chinese live speech recognition.",
            downloadUrl = "$BASE_HF_URL/ggml-base.bin",
            fileName = "ggml-base.bin",
            isRecommended = true,
            isMultilingual = true
        ),
        WhisperModelInfo(
            id = "base.q5_1",
            name = "Base Q5_1 (Quantized)",
            description = "Quantized Base model. High accuracy with lower RAM usage.",
            downloadUrl = "$BASE_HF_URL/ggml-base-q5_1.bin",
            fileName = "ggml-base-q5_1.bin",
            isRecommended = false,
            isMultilingual = true
        ),
        WhisperModelInfo(
            id = "small",
            name = "Small (Multilingual)",
            description = "High accuracy transcription. Recommended for powerful devices.",
            downloadUrl = "$BASE_HF_URL/ggml-small.bin",
            fileName = "ggml-small.bin",
            isRecommended = false,
            isMultilingual = true
        )
    )

    private val _modelsState = MutableStateFlow<List<WhisperModelInfo>>(AVAILABLE_MODELS)
    val modelsState: StateFlow<List<WhisperModelInfo>> = _modelsState.asStateFlow()

    private val _activeModelId = MutableStateFlow<String>("base")
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    private var activeDownloadCall: okhttp3.Call? = null
    private var isCancelled = false

    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, MODELS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(context: Context, modelId: String): File? {
        val info = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(getModelsDir(context), info.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isModelDownloaded(context: Context, modelId: String): Boolean {
        return getModelFile(context, modelId) != null
    }

    suspend fun refreshModelsList(context: Context) = withContext(Dispatchers.IO) {
        val modelsDir = getModelsDir(context)
        val updated = AVAILABLE_MODELS.map { model ->
            val localFile = File(modelsDir, model.fileName)
            val isDownloaded = localFile.exists() && localFile.length() > 0
            val localSize = if (isDownloaded) localFile.length() else 0L

            // Fetch live remote size via HEAD request if not downloaded
            var sizeStr = if (isDownloaded) {
                formatFileSize(localSize)
            } else {
                fetchRemoteFileSize(model.downloadUrl)
            }

            model.copy(
                isDownloaded = isDownloaded,
                localSizeBytes = localSize,
                approximateSizeMb = sizeStr,
                downloadProgress = if (isDownloaded) 1f else 0f
            )
        }

        _modelsState.value = updated
    }

    private fun fetchRemoteFileSize(url: String): String {
        return try {
            val req = Request.Builder().url(url).head().build()
            httpClient.newCall(req).execute().use { resp ->
                val len = resp.header("Content-Length")?.toLongOrNull()
                if (len != null && len > 0) {
                    formatFileSize(len)
                } else {
                    "Online"
                }
            }
        } catch (e: Exception) {
            "Online"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    fun setActiveModel(modelId: String) {
        _activeModelId.value = modelId
    }

    suspend fun downloadModel(
        context: Context,
        modelId: String,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return@withContext false
        val modelsDir = getModelsDir(context)
        val destFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        isCancelled = false

        updateModelDownloadingState(modelId, isDownloading = true, progress = 0f)

        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .header("User-Agent", "Butterfly-Whisper-Manager/1.0")
                .build()

            val call = httpClient.newCall(request)
            activeDownloadCall = call

            val response = call.execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed downloading whisper model: HTTP ${response.code}")
                updateModelDownloadingState(modelId, isDownloading = false, progress = 0f)
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        if (isCancelled) {
                            tempFile.delete()
                            updateModelDownloadingState(modelId, isDownloading = false, progress = 0f)
                            return@withContext false
                        }

                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100 || downloadedBytes == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                            withContext(Dispatchers.Main) {
                                onProgress(progress, downloadedBytes, totalBytes)
                            }
                            updateModelDownloadingState(modelId, isDownloading = true, progress = progress)
                        }
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 1024 * 1024) {
                // Verify file header integrity
                val isValidHeader = verifyModelHeader(tempFile)
                if (isValidHeader) {
                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)
                } else {
                    Log.e(TAG, "Downloaded model failed integrity check.")
                    tempFile.delete()
                    updateModelDownloadingState(modelId, isDownloading = false, progress = 0f)
                    return@withContext false
                }
            } else {
                tempFile.delete()
                updateModelDownloadingState(modelId, isDownloading = false, progress = 0f)
                return@withContext false
            }

            refreshModelsList(context)
            setActiveModel(modelId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model $modelId: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            updateModelDownloadingState(modelId, isDownloading = false, progress = 0f)
            false
        } finally {
            activeDownloadCall = null
        }
    }

    fun cancelDownload() {
        isCancelled = true
        activeDownloadCall?.cancel()
    }

    suspend fun deleteModel(context: Context, modelId: String): Boolean = withContext(Dispatchers.IO) {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return@withContext false
        val file = File(getModelsDir(context), model.fileName)
        val success = if (file.exists()) file.delete() else true
        refreshModelsList(context)
        success
    }

    private fun updateModelDownloadingState(modelId: String, isDownloading: Boolean, progress: Float) {
        val current = _modelsState.value.toMutableList()
        val index = current.indexOfFirst { it.id == modelId }
        if (index != -1) {
            current[index] = current[index].copy(
                isDownloading = isDownloading,
                downloadProgress = progress
            )
            _modelsState.value = current
        }
    }

    private fun verifyModelHeader(file: File): Boolean {
        return try {
            java.io.FileInputStream(file).use { input ->
                val buffer = ByteArray(4)
                val read = input.read(buffer)
                if (read == 4) {
                    val magic = java.nio.ByteBuffer.wrap(buffer).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                    // Check GGML / GGMF / GGJT / GGUF magic headers
                    magic == 0x67676d6c || magic == 0x67676d66 || magic == 0x67676a74 || magic == 0x46554747
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
