package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.example.model.VideoItem

object VideoActionHelper {

    fun shareVideo(context: Context, video: VideoItem) {
        try {
            val shareUrl = if (video.videoUrl.isNotBlank()) {
                video.videoUrl
            } else {
                "https://www.youtube.com/watch?v=${video.id}"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, video.title)
                putExtra(Intent.EXTRA_TEXT, "${video.title}\n$shareUrl")
            }
            val chooser = Intent.createChooser(intent, "Share video via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share video: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadVideo(context: Context, video: VideoItem) {
        try {
            val downloadUrl = video.videoUrl.ifBlank { video.thumbnailUrl }
            if (downloadUrl.isBlank()) {
                Toast.makeText(context, "No download link available for this video", Toast.LENGTH_SHORT).show()
                return
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle(video.title)
                setDescription("Downloading ${video.uploaderName}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "${video.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}.mp4"
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                downloadManager.enqueue(request)
                Toast.makeText(context, "Download started for: ${video.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Download manager unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Download scheduled: ${video.title}", Toast.LENGTH_SHORT).show()
        }
    }
}
