package com.example.ui.animation

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * MTV Opening Sound Manager:
 * Plays the iconic sound of MTV featuring:
 * - Lunar landing countdown & space rumble
 * - Heavy flagpole ground impact thud
 * - The iconic vocal singing: "I want my MTV"
 * - Screaming distorted rock guitar power-chord sting & retro MTV chimes
 *
 * Uses native MediaPlayer backed by assets/mtv_opening_sound.mp3
 * with USAGE_MEDIA / CONTENT_TYPE_MUSIC at maximum volume.
 */
object MtvSoundSynthesizer {

    private const val TAG = "MtvSoundSynthesizer"
    private var activeMediaPlayer: MediaPlayer? = null

    /**
     * Plays the authentic MTV audio with "I Want My MTV" vocal and rock guitar sting.
     */
    fun playMtvSting(context: Context) {
        try {
            stop()

            val afd: AssetFileDescriptor = context.applicationContext.assets.openFd("mtv_opening_sound.mp3")
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setVolume(1.0f, 1.0f)
            mp.setOnCompletionListener {
                try {
                    it.release()
                } catch (_: Exception) {}
                if (activeMediaPlayer == it) {
                    activeMediaPlayer = null
                }
            }
            mp.prepare()
            activeMediaPlayer = mp
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play MTV opening sound: ${e.message}")
        }
    }

    /**
     * Stops any currently playing audio immediately.
     */
    fun stop() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            activeMediaPlayer = null
        } catch (_: Exception) {}
    }
}
