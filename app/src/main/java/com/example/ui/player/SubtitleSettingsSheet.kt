package com.example.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CaptionOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSheet(
    availableCaptions: List<CaptionOption> = emptyList(),
    selectedCaption: CaptionOption? = null,
    onSelectCaption: (CaptionOption?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val currentSubMode by GlobalPlayerManager.subtitleMode.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Subtitles",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Subtitles & Captions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 1: Off
            SubtitleOptionRow(
                title = "Subtitles Off",
                subtitle = "Turn off all captions and subtitles",
                icon = Icons.Default.ClosedCaptionDisabled,
                isSelected = currentSubMode == GlobalPlayerManager.SubtitleMode.OFF && selectedCaption == null,
                onClick = {
                    GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.OFF)
                    onSelectCaption(null)
                    onDismiss()
                }
            )

            // Available Embedded / Video Captions
            if (availableCaptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Video Captions (${availableCaptions.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                availableCaptions.forEach { caption ->
                    val isThisSelected = selectedCaption?.languageCode == caption.languageCode || selectedCaption?.languageName == caption.languageName
                    SubtitleOptionRow(
                        title = caption.languageName.ifBlank { "English Subtitle" },
                        subtitle = if (caption.languageCode.contains("auto", ignoreCase = true)) "Auto-generated" else "Official Caption (${caption.format.uppercase()})",
                        icon = Icons.Default.Subtitles,
                        isSelected = isThisSelected && currentSubMode != GlobalPlayerManager.SubtitleMode.OFF,
                        onClick = {
                            GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.EXTERNAL_PROVIDER)
                            onSelectCaption(caption)
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AI & Translation Features",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Option: AI Live Captions
            val isWhisperNativeAvailable = com.example.subtitles.whisper.WhisperJni.isAvailable()
            SubtitleOptionRow(
                title = "Whisper AI Live Captions",
                subtitle = if (isWhisperNativeAvailable) "Generate real-time speech-to-text captions" else "Speech-to-text (Native whisper.cpp engine not bundled in build)",
                icon = Icons.Default.GraphicEq,
                isSelected = isWhisperNativeAvailable && currentSubMode == GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS,
                enabled = isWhisperNativeAvailable,
                onClick = {
                    if (isWhisperNativeAvailable) {
                        GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS)
                        onDismiss()
                    }
                }
            )

            // Option: Search External Subtitles
            SubtitleOptionRow(
                title = "Search Online Subtitles",
                subtitle = "Find SRT subtitles from OpenSubtitles / Web",
                icon = Icons.Default.Search,
                isSelected = false,
                onClick = {
                    GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.EXTERNAL_PROVIDER)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubtitleOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.35f else 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
