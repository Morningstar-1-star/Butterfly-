package com.example.ui.vault

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.util.GoogleDriveSyncManager
import com.example.vault.LocalVideoInfo
import com.example.vault.UploadProgress
import com.example.vault.VaultStorageType
import com.example.vault.VideoVaultManager
import kotlinx.coroutines.launch
import java.io.File

private enum class VaultSheetMode {
    MENU,
    M3U8_FORM,
    DEVICE_VIDEO_DETAILS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButterflyUploadSheet(
    onDismiss: () -> Unit,
    onPlayVideo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vaultManager = remember { VideoVaultManager.getInstance(context) }

    var currentMode by remember { mutableStateOf(VaultSheetMode.MENU) }

    // M3U8 Form State
    var m3u8Url by remember { mutableStateOf("") }
    var m3u8Title by remember { mutableStateOf("") }
    var m3u8Desc by remember { mutableStateOf("") }
    var m3u8Thumb by remember { mutableStateOf("") }
    var m3u8Tags by remember { mutableStateOf("") }
    var m3u8Folder by remember { mutableStateOf("Saved Streams") }
    var isSavingM3u8 by remember { mutableStateOf(false) }

    // Device Video State
    var selectedVideoInfo by remember { mutableStateOf<LocalVideoInfo?>(null) }
    var isExtractingMetadata by remember { mutableStateOf(false) }
    var devTitle by remember { mutableStateOf("") }
    var devDesc by remember { mutableStateOf("") }
    var devTags by remember { mutableStateOf("") }
    var devFolder by remember { mutableStateOf("Device Videos") }
    var selectedDestination by remember { mutableStateOf(VaultStorageType.GOOGLE_DRIVE) }
    var keepOriginal by remember { mutableStateOf(true) }
    var trimRange by remember { mutableStateOf(0f..1f) }

    // Telegram Credentials State
    var tgBotToken by remember { mutableStateOf(vaultManager.getSavedTelegramBotToken()) }
    var tgChatId by remember { mutableStateOf(vaultManager.getSavedTelegramChatId()) }

    // Upload In-Progress State
    var uploadProgress by remember { mutableStateOf(UploadProgress()) }

    // Video File Picker Launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isExtractingMetadata = true
            coroutineScope.launch {
                try {
                    val info = vaultManager.extractLocalVideoMetadata(uri)
                    selectedVideoInfo = info
                    devTitle = info.fileName.substringBeforeLast(".")
                    trimRange = 0f..(info.durationMs.toFloat().coerceAtLeast(1000f))
                    currentMode = VaultSheetMode.DEVICE_VIDEO_DETAILS
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not load video: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isExtractingMetadata = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!uploadProgress.isUploading) onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentMode != VaultSheetMode.MENU && !uploadProgress.isUploading) {
                        IconButton(
                            onClick = { currentMode = VaultSheetMode.MENU },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = when (currentMode) {
                            VaultSheetMode.MENU -> "Add to Butterfly"
                            VaultSheetMode.M3U8_FORM -> "Save M3U8 Stream"
                            VaultSheetMode.DEVICE_VIDEO_DETAILS -> "Upload Device Video"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!uploadProgress.isUploading) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // UPLOAD PROGRESS OVERLAY
            if (uploadProgress.isUploading || uploadProgress.error != null) {
                UploadProgressCard(
                    progress = uploadProgress,
                    onRetry = {
                        uploadProgress = UploadProgress()
                    },
                    onDismissError = {
                        uploadProgress = UploadProgress()
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                when (currentMode) {
                    VaultSheetMode.MENU -> {
                        // Choice 1: Add M3U8 Link
                        UploadMenuOptionCard(
                            icon = Icons.Default.Link,
                            title = "Add M3U8 Link",
                            subtitle = "Save a stream URL without downloading it",
                            badgeText = "0 MB Storage",
                            badgeColor = MaterialTheme.colorScheme.primary,
                            onClick = { currentMode = VaultSheetMode.M3U8_FORM }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Choice 2: Upload Device Video
                        UploadMenuOptionCard(
                            icon = Icons.Default.VideoLibrary,
                            title = "Upload Device Video",
                            subtitle = "Edit and upload a video from this device",
                            badgeText = "Drive / Telegram",
                            badgeColor = MaterialTheme.colorScheme.tertiary,
                            onClick = { videoPickerLauncher.launch("video/*") }
                        )

                        if (isExtractingMetadata) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Analyzing video metadata...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }

                    VaultSheetMode.M3U8_FORM -> {
                        // URL Input with paste button
                        OutlinedTextField(
                            value = m3u8Url,
                            onValueChange = { m3u8Url = it },
                            label = { Text("M3U8 Stream URL *") },
                            placeholder = { Text("https://example.com/stream/index.m3u8") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val text = clip.getItemAt(0).text?.toString() ?: ""
                                            if (text.isNotBlank()) m3u8Url = text.trim()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Title
                        OutlinedTextField(
                            value = m3u8Title,
                            onValueChange = { m3u8Title = it },
                            label = { Text("Title (Optional)") },
                            placeholder = { Text("E.g., Live Sports Stream") },
                            leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        OutlinedTextField(
                            value = m3u8Desc,
                            onValueChange = { m3u8Desc = it },
                            label = { Text("Description (Optional)") },
                            placeholder = { Text("Add notes or stream details") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Thumbnail URL
                        OutlinedTextField(
                            value = m3u8Thumb,
                            onValueChange = { m3u8Thumb = it },
                            label = { Text("Thumbnail URL (Optional)") },
                            placeholder = { Text("https://example.com/poster.jpg") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom Tags & Folder in a Row
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = m3u8Tags,
                                onValueChange = { m3u8Tags = it },
                                label = { Text("Tags") },
                                placeholder = { Text("live, hd") },
                                leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = m3u8Folder,
                                onValueChange = { m3u8Folder = it },
                                label = { Text("Folder") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Notice
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Direct stream reference only. Butterfly will not download, convert, or cache this video file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (m3u8Url.isBlank()) {
                                        Toast.makeText(context, "Please enter an M3U8 URL", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    isSavingM3u8 = true
                                    coroutineScope.launch {
                                        try {
                                            val tagsList = m3u8Tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                            vaultManager.saveM3U8Stream(
                                                m3u8Url = m3u8Url,
                                                title = m3u8Title,
                                                description = m3u8Desc,
                                                thumbnailUrl = m3u8Thumb.ifBlank { null },
                                                tags = tagsList,
                                                folder = m3u8Folder
                                            )
                                            Toast.makeText(context, "M3U8 stream saved to Vault!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSavingM3u8 = false
                                        }
                                    }
                                },
                                enabled = !isSavingM3u8,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save to Vault")
                            }

                            Button(
                                onClick = {
                                    if (m3u8Url.isBlank()) {
                                        Toast.makeText(context, "Please enter an M3U8 URL", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isSavingM3u8 = true
                                    coroutineScope.launch {
                                        try {
                                            val tagsList = m3u8Tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                            val saved = vaultManager.saveM3U8Stream(
                                                m3u8Url = m3u8Url,
                                                title = m3u8Title,
                                                description = m3u8Desc,
                                                thumbnailUrl = m3u8Thumb.ifBlank { null },
                                                tags = tagsList,
                                                folder = m3u8Folder
                                            )
                                            Toast.makeText(context, "Stream ready!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                            onPlayVideo(saved.sourceUrl, "m3u8")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSavingM3u8 = false
                                        }
                                    }
                                },
                                enabled = !isSavingM3u8,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Play")
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }

                    VaultSheetMode.DEVICE_VIDEO_DETAILS -> {
                        selectedVideoInfo?.let { videoInfo ->
                            // Video Preview Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 110.dp, height = 75.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!videoInfo.localThumbnailPath.isNullOrBlank()) {
                                            AsyncImage(
                                                model = File(videoInfo.localThumbnailPath),
                                                contentDescription = "Video Thumbnail",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        // Duration badge
                                        if (videoInfo.durationMs > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.Black.copy(alpha = 0.8f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = formatDuration(videoInfo.durationMs),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = videoInfo.fileName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${videoInfo.formattedSize} • ${if (videoInfo.width > 0) "${videoInfo.width}x${videoInfo.height}" else "HD"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        TextButton(
                                            onClick = { videoPickerLauncher.launch("video/*") },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Change video", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Trim & Crop Settings
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.ContentCut,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Keep original full video",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Switch(
                                            checked = keepOriginal,
                                            onCheckedChange = { keepOriginal = it }
                                        )
                                    }

                                    if (!keepOriginal && videoInfo.durationMs > 1000) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Trim Range: ${formatDuration(trimRange.start.toLong())} - ${formatDuration(trimRange.endInclusive.toLong())}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        RangeSlider(
                                            value = trimRange,
                                            onValueChange = { trimRange = it },
                                            valueRange = 0f..(videoInfo.durationMs.toFloat()),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Metadata Inputs
                            OutlinedTextField(
                                value = devTitle,
                                onValueChange = { devTitle = it },
                                label = { Text("Title *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = devDesc,
                                onValueChange = { devDesc = it },
                                label = { Text("Description (Optional)") },
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = devTags,
                                    onValueChange = { devTags = it },
                                    label = { Text("Tags") },
                                    placeholder = { Text("vacation, 4k") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = devFolder,
                                    onValueChange = { devFolder = it },
                                    label = { Text("Folder") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Destination Selector
                            Text(
                                text = "Select Upload Destination",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. Google Drive Option
                            DestinationRadioCard(
                                icon = Icons.Default.CloudUpload,
                                title = "Google Drive",
                                subtitle = "Resumable streaming upload to your Google Drive",
                                selected = selectedDestination == VaultStorageType.GOOGLE_DRIVE,
                                onClick = { selectedDestination = VaultStorageType.GOOGLE_DRIVE }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 2. Telegram Channel Option
                            DestinationRadioCard(
                                icon = Icons.Default.Send,
                                title = "Telegram Channel / Chat",
                                subtitle = "Stream upload to Telegram with bot & channel ID",
                                selected = selectedDestination == VaultStorageType.TELEGRAM,
                                onClick = { selectedDestination = VaultStorageType.TELEGRAM }
                            )

                            if (selectedDestination == VaultStorageType.TELEGRAM) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        OutlinedTextField(
                                            value = tgBotToken,
                                            onValueChange = { tgBotToken = it },
                                            label = { Text("Bot Token") },
                                            placeholder = { Text("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = tgChatId,
                                            onValueChange = { tgChatId = it },
                                            label = { Text("Chat ID / Channel (@channelname or -100...)") },
                                            placeholder = { Text("@my_vault_channel or -100123456789") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Keep Locally (No Cloud Upload)
                            DestinationRadioCard(
                                icon = Icons.Default.PhoneAndroid,
                                title = "Device Local Vault (No Cloud Upload)",
                                subtitle = "Save directly on device without uploading",
                                selected = selectedDestination == VaultStorageType.NONE,
                                onClick = { selectedDestination = VaultStorageType.NONE }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Bottom Upload / Save Button
                            Button(
                                onClick = {
                                    val tagsList = devTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    val trimStart = if (keepOriginal) 0L else trimRange.start.toLong()
                                    val trimEnd = if (keepOriginal) 0L else trimRange.endInclusive.toLong()

                                    when (selectedDestination) {
                                        VaultStorageType.NONE -> {
                                            coroutineScope.launch {
                                                try {
                                                    val saved = vaultManager.saveLocalDeviceVideo(
                                                        info = videoInfo,
                                                        title = devTitle,
                                                        description = devDesc,
                                                        tags = tagsList,
                                                        folder = devFolder,
                                                        trimStartMs = trimStart,
                                                        trimEndMs = trimEnd
                                                    )
                                                    Toast.makeText(context, "Saved to Device Vault!", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                    onPlayVideo(saved.sourceUrl, "local")
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }

                                        VaultStorageType.GOOGLE_DRIVE -> {
                                            coroutineScope.launch {
                                                val res = vaultManager.uploadToGoogleDrive(
                                                    info = videoInfo,
                                                    title = devTitle,
                                                    description = devDesc,
                                                    tags = tagsList,
                                                    folder = devFolder,
                                                    trimStartMs = trimStart,
                                                    trimEndMs = trimEnd,
                                                    onProgress = { p -> uploadProgress = p }
                                                )
                                                if (res.isSuccess) {
                                                    val item = res.getOrNull()
                                                    Toast.makeText(context, "Uploaded to Google Drive!", Toast.LENGTH_LONG).show()
                                                    onDismiss()
                                                    if (item != null) {
                                                        onPlayVideo(item.directStreamUrl, "gdrive")
                                                    }
                                                }
                                            }
                                        }

                                        VaultStorageType.TELEGRAM -> {
                                            if (tgBotToken.isBlank() || tgChatId.isBlank()) {
                                                Toast.makeText(context, "Please enter Bot Token & Chat ID", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            coroutineScope.launch {
                                                val res = vaultManager.uploadToTelegram(
                                                    info = videoInfo,
                                                    title = devTitle,
                                                    description = devDesc,
                                                    tags = tagsList,
                                                    folder = devFolder,
                                                    botToken = tgBotToken,
                                                    chatId = tgChatId,
                                                    trimStartMs = trimStart,
                                                    trimEndMs = trimEnd,
                                                    onProgress = { p -> uploadProgress = p }
                                                )
                                                if (res.isSuccess) {
                                                    val item = res.getOrNull()
                                                    Toast.makeText(context, "Uploaded to Telegram!", Toast.LENGTH_LONG).show()
                                                    onDismiss()
                                                    if (item != null) {
                                                        onPlayVideo(item.directStreamUrl, "telegram")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(
                                    imageVector = when (selectedDestination) {
                                        VaultStorageType.GOOGLE_DRIVE -> Icons.Default.CloudUpload
                                        VaultStorageType.TELEGRAM -> Icons.Default.Send
                                        VaultStorageType.NONE -> Icons.Default.Save
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (selectedDestination) {
                                        VaultStorageType.GOOGLE_DRIVE -> "Upload to Google Drive"
                                        VaultStorageType.TELEGRAM -> "Upload to Telegram"
                                        VaultStorageType.NONE -> "Save to Device Vault"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadMenuOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DestinationRadioCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UploadProgressCard(
    progress: UploadProgress,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (progress.error != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Upload Interrupted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = progress.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissError) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Retry")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = progress.statusMessage.ifBlank { "Uploading video..." },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${progress.progressPercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Streaming chunks safely without overloading RAM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
