package com.example.ui.player.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.FileProvider
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * PlayerFrameCaptureHelper captures high-resolution video frames directly from
 * the PlayerView's video surface for Circle-to-Search, Google Lens, and OCR text recognition.
 */
object PlayerFrameCaptureHelper {

    private const val TAG = "PlayerFrameCapture"
    private var activePlayerViewRef: WeakReference<PlayerView>? = null

    fun registerPlayerView(playerView: PlayerView) {
        activePlayerViewRef = WeakReference(playerView)
    }

    fun unregisterPlayerView(playerView: PlayerView) {
        if (activePlayerViewRef?.get() == playerView) {
            activePlayerViewRef = null
        }
    }

    /**
     * Captures the current video frame as a Bitmap.
     */
    fun captureCurrentFrame(): Bitmap? {
        val playerView = activePlayerViewRef?.get() ?: return null

        // 1. Try TextureView first (instant, hardware accelerated)
        val surfaceView = playerView.videoSurfaceView
        if (surfaceView is TextureView) {
            try {
                if (surfaceView.isAvailable) {
                    val bmp = surfaceView.bitmap
                    if (bmp != null) {
                        return bmp
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "TextureView bitmap capture failed: ${e.message}")
            }
        }

        // 2. Try PixelCopy on SurfaceView / View if Android O+
        if (surfaceView is SurfaceView && surfaceView.holder.surface.isValid) {
            try {
                val width = surfaceView.width.takeIf { it > 0 } ?: 1280
                val height = surfaceView.height.takeIf { it > 0 } ?: 720
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val latch = CountDownLatch(1)
                var success = false

                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { copyResult ->
                        success = (copyResult == PixelCopy.SUCCESS)
                        latch.countDown()
                    },
                    Handler(Looper.getMainLooper())
                )

                latch.await(350, TimeUnit.MILLISECONDS)
                if (success) {
                    return bitmap
                }
            } catch (e: Throwable) {
                Log.w(TAG, "PixelCopy on SurfaceView failed: ${e.message}")
            }
        }

        // 3. Fallback: Software Canvas snapshot
        return try {
            val w = playerView.width.takeIf { it > 0 } ?: 1280
            val h = playerView.height.takeIf { it > 0 } ?: 720
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            playerView.draw(canvas)
            bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "All frame capture methods failed: ${e.message}")
            null
        }
    }

    /**
     * Crops the bitmap according to normalized coordinates (0.0f..1.0f).
     */
    fun cropNormalized(source: Bitmap, normRect: RectF): Bitmap {
        val left = (normRect.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (normRect.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (normRect.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (normRect.bottom * source.height).toInt().coerceIn(top + 1, source.height)

        val cropWidth = max(1, right - left)
        val cropHeight = max(1, bottom - top)

        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }

    /**
     * Saves a bitmap to the app cache directory and generates a secure content:// URI.
     */
    fun saveBitmapToTempUri(context: Context, bitmap: Bitmap, fileName: String = "lens_search_${System.currentTimeMillis()}.jpg"): Uri? {
        return try {
            val lensDir = File(context.cacheDir, "lens").apply { mkdirs() }
            val file = File(lensDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save bitmap to URI: ${e.message}", e)
            null
        }
    }
}
