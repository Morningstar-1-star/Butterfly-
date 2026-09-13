package com.example.ui.player.dynamicisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import com.example.ui.player.GlobalPlayerManager

/**
 * BroadcastReceiver for handling Picture-in-Picture (PiP) remote controls,
 * specifically the Headphones Audio Mode toggle action.
 */
class ButterflyPipActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PIP_AUDIO_MODE = "com.example.butterfly.ACTION_PIP_AUDIO_MODE"
        const val ACTION_PIP_TOGGLE_PLAY = "com.example.butterfly.ACTION_PIP_TOGGLE_PLAY"
        const val ACTION_PIP_FORWARD_10 = "com.example.butterfly.ACTION_PIP_FORWARD_10"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        Log.i("ButterflyPipReceiver", "Received PiP action: ${intent.action}")

        when (intent.action) {
            ACTION_PIP_AUDIO_MODE -> {
                // Switch to Audio Mode + Dynamic Island
                AudioModeManager.enterAudioMode(context)

                // Dismiss / minimize the PiP window cleanly
                val minimizeIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("EXIT_PIP_TO_AUDIO", true)
                }
                context.startActivity(minimizeIntent)
            }
            ACTION_PIP_TOGGLE_PLAY -> {
                GlobalPlayerManager.togglePlayPause()
            }
            ACTION_PIP_FORWARD_10 -> {
                GlobalPlayerManager.seekForward(10000L)
            }
        }
    }
}
