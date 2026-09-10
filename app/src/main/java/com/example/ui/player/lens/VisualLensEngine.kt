package com.example.ui.player.lens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class RecognizedTextBlock(
    val text: String,
    val normalizedBoundingBox: RectF,
    val rawBoundingBox: Rect? = null
)

enum class VisualSearchEngine(val displayName: String, val iconResName: String, val url: String) {
    GOOGLE_LENS("Google Lens", "ic_google", "https://lens.google.com"),
    LENSO_AI("Lenso.ai", "ic_lenso", "https://lenso.ai/en"),
    BING_VISUAL("Bing Visual", "ic_bing", "https://www.bing.com/visualsearch"),
    YANDEX_IMAGES("Yandex Images", "ic_yandex", "https://yandex.com/images/search")
}

object VisualLensEngine {

    private const val TAG = "VisualLensEngine"
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Extracts text blocks with normalized coordinates from the provided Bitmap using on-device ML Kit.
     */
    suspend fun recognizeText(bitmap: Bitmap): List<RecognizedTextBlock> = withContext(Dispatchers.Default) {
        val width = bitmap.width.toFloat().takeIf { it > 0f } ?: 1f
        val height = bitmap.height.toFloat().takeIf { it > 0f } ?: 1f

        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = mutableListOf<RecognizedTextBlock>()
                        for (block in visionText.textBlocks) {
                            val box = block.boundingBox
                            if (box != null) {
                                val normRect = RectF(
                                    (box.left.toFloat() / width).coerceIn(0f, 1f),
                                    (box.top.toFloat() / height).coerceIn(0f, 1f),
                                    (box.right.toFloat() / width).coerceIn(0f, 1f),
                                    (box.bottom.toFloat() / height).coerceIn(0f, 1f)
                                )
                                blocks.add(
                                    RecognizedTextBlock(
                                        text = block.text,
                                        normalizedBoundingBox = normRect,
                                        rawBoundingBox = box
                                    )
                                )
                            }
                        }
                        continuation.resume(blocks)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Text recognition failed: ${e.message}")
                        continuation.resume(emptyList())
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Error initiating text recognition: ${e.message}", e)
                continuation.resume(emptyList())
            }
        }
    }

    /**
     * Launches Google Lens via Android Intent or Google Search App.
     */
    fun launchGoogleLens(context: Context, imageUri: Uri) {
        try {
            // 1. Try Google Lens direct action intent
            val lensIntent = Intent("android.intent.action.VIEW").apply {
                setDataAndType(imageUri, "image/*")
                setPackage("com.google.ar.lens")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (isIntentResolvable(context, lensIntent)) {
                context.startActivity(lensIntent)
                return
            }

            // 2. Try Google App / Google QuickSearchBox Image Search
            val googleSendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (isIntentResolvable(context, googleSendIntent)) {
                context.startActivity(googleSendIntent)
                return
            }

            // 3. Fallback: Generic Image Share / Visual Search browser
            shareImage(context, imageUri, "Search image with Google Lens")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Google Lens: ${e.message}", e)
            openInBrowser(context, "https://lens.google.com")
        }
    }

    /**
     * Launches Visual Search for Lenso.ai, Bing, or Yandex.
     */
    fun launchVisualSearch(context: Context, imageUri: Uri, engine: VisualSearchEngine) {
        when (engine) {
            VisualSearchEngine.GOOGLE_LENS -> launchGoogleLens(context, imageUri)
            VisualSearchEngine.LENSO_AI -> {
                // Share to Lenso or open browser portal
                shareOrBrowse(context, imageUri, "https://lenso.ai/en", "Search with Lenso.ai")
            }
            VisualSearchEngine.BING_VISUAL -> {
                shareOrBrowse(context, imageUri, "https://www.bing.com/visualsearch", "Search with Bing Visual")
            }
            VisualSearchEngine.YANDEX_IMAGES -> {
                shareOrBrowse(context, imageUri, "https://yandex.com/images/search", "Search with Yandex Images")
            }
        }
    }

    private fun shareOrBrowse(context: Context, imageUri: Uri, webUrl: String, shareTitle: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, webUrl)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, shareTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            openInBrowser(context, webUrl)
        }
    }

    /**
     * Shares the cropped or full image via the system share sheet.
     */
    fun shareImage(context: Context, imageUri: Uri, title: String = "Share Video Frame") {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share image: ${e.message}")
            Toast.makeText(context, "Failed to share image", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies text to the Android system clipboard and displays a clean confirmation toast.
     */
    fun copyToClipboard(context: Context, text: String, showToast: Boolean = true) {
        if (text.isBlank()) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Video Text", text)
            clipboard.setPrimaryClip(clip)
            if (showToast) {
                Toast.makeText(context, "Copied to clipboard: \"${text.take(30)}${if (text.length > 30) "..." else ""}\"", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text: ${e.message}")
        }
    }

    private fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open browser: ${e.message}")
        }
    }

    private fun isIntentResolvable(context: Context, intent: Intent): Boolean {
        return try {
            val matches = context.packageManager.queryIntentActivities(intent, 0)
            matches.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }
}
