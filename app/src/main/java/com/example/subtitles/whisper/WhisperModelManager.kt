package com.example.subtitles.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

enum class WhisperModelType(val fileName: String, val downloadUrl: String, val approxSizeMb: Int) {
    TINY_EN("ggml-tiny.en.bin", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin", 75),
    BASE_EN("ggml-base.en.bin", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin", 142),
    SMALL_EN("ggml-small.en.bin", "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin", 466)
}

class WhisperModelManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperModelManager"
    }

    private val modelsDir = File(context.filesDir, "whisper_models").apply { mkdirs() }
    private val httpClient = OkHttpClient()

    fun isModelDownloaded(modelType: WhisperModelType = WhisperModelType.TINY_EN): Boolean {
        val file = File(modelsDir, modelType.fileName)
        return file.exists() && file.length() > 10 * 1024 * 1024L
    }

    fun getModelFile(modelType: WhisperModelType = WhisperModelType.TINY_EN): File? {
        val file = File(modelsDir, modelType.fileName)
        return if (file.exists()) file else null
    }

    suspend fun downloadModel(
        modelType: WhisperModelType = WhisperModelType.TINY_EN,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, modelType.fileName)
        if (targetFile.exists() && targetFile.length() > 10 * 1024 * 1024L) {
            return@withContext targetFile
        }

        try {
            val req = Request.Builder()
                .url(modelType.downloadUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.e(TAG, "Failed to download model ${modelType.fileName}: HTTP ${resp.code}")
                return@withContext null
            }

            val body = resp.body ?: return@withContext null
            val totalBytes = body.contentLength()
            val tempFile = File(modelsDir, "${modelType.fileName}.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var current = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        current += read
                        onProgress?.invoke(current, totalBytes)
                    }
                }
            }

            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Successfully downloaded GGML model: ${targetFile.absolutePath}")
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model download exception for ${modelType.fileName}: ${e.message}", e)
            null
        }
    }
}
