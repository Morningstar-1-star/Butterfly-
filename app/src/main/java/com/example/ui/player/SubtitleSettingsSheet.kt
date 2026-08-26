package com.example.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CaptionOption
import com.example.util.AiCaptionEngine
import com.example.util.WhisperModelInfo
import com.example.util.WhisperModelManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val subtitleMode by GlobalPlayerManager.subtitleMode.collectAsState()
    val bilibiliTracks by GlobalPlayerManager.bilibiliSubtitleTracks.collectAsState()
    val selectedTrack by GlobalPlayerManager.selectedSubtitleTrack.collectAsState()
    val targetLang by GlobalPlayerManager.targetCaptionLanguage.collectAsState()

    val discoveredSubtitles by com.example.subtitles.SubtitleManager.discoveredSubtitles.collectAsState()
    val activeSubtitleItem by com.example.subtitles.SubtitleManager.activeSubtitleItem.collectAsState()
    val isSearchingSubtitles by com.example.subtitles.SubtitleManager.isSearching.collectAsState()

    val aiState by AiCaptionEngine.captionState.collectAsState()
    val whisperModels by WhisperModelManager.modelsState.collectAsState()
    val activeWhisperModelId by WhisperModelManager.activeModelId.collectAsState()

    LaunchedEffect(Unit) {
        WhisperModelManager.refreshModelsList(context)
    }

    val supportedLanguages = listOf(
        Pair("en", "English"),
        Pair("hi", "Hindi (हिंदी)"),
        Pair("ja", "Japanese (日本語)"),
        Pair("zh", "Chinese (中文)"),
        Pair("ko", "Korean (한국어)"),
        Pair("es", "Spanish (Español)"),
        Pair("fr", "French (Français)"),
        Pair("de", "German (Deutsch)"),
        Pair("ru", "Russian (Русский)"),
        Pair("pt", "Portuguese (Português)"),
        Pair("it", "Italian (Italiano)")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Subtitles & AI Live Captions",
                        style = MaterialTheme.typography.titleMedium,
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Caption Mode Selection
                item {
                    Text(
                        text = "CAPTION SOURCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CaptionModeChip(
                            label = "Off",
                            selected = subtitleMode == GlobalPlayerManager.SubtitleMode.OFF,
                            onClick = {
                                GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.OFF, context)
                            }
                        )
                        if (discoveredSubtitles.isNotEmpty()) {
                            CaptionModeChip(
                                label = "External Subtitles",
                                selected = subtitleMode == GlobalPlayerManager.SubtitleMode.EXTERNAL_PROVIDER,
                                onClick = {
                                    GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.EXTERNAL_PROVIDER, context)
                                }
                            )
                        }
                        if (bilibiliTracks.isNotEmpty()) {
                            CaptionModeChip(
                                label = "Bilibili Translated",
                                selected = subtitleMode == GlobalPlayerManager.SubtitleMode.BILIBILI_TRANSLATED,
                                onClick = {
                                    GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.BILIBILI_TRANSLATED, context)
                                }
                            )
                            CaptionModeChip(
                                label = "Original",
                                selected = subtitleMode == GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL,
                                onClick = {
                                    GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL, context)
                                }
                            )
                        }
                        CaptionModeChip(
                            label = "Whisper AI Live",
                            selected = subtitleMode == GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS,
                            onClick = {
                                GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS, context)
                            }
                        )
                    }
                }

                // 2. Translation Target Language
                if (subtitleMode != GlobalPlayerManager.SubtitleMode.OFF && subtitleMode != GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL) {
                    item {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TRANSLATE TO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val topLanguages = supportedLanguages.take(4)
                            topLanguages.forEach { (code, name) ->
                                FilterChip(
                                    selected = targetLang == code,
                                    onClick = {
                                        GlobalPlayerManager.setTargetCaptionLanguage(code)
                                    },
                                    label = { Text(name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF222232),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val remaining = supportedLanguages.drop(4)
                            remaining.take(4).forEach { (code, name) ->
                                FilterChip(
                                    selected = targetLang == code,
                                    onClick = {
                                        GlobalPlayerManager.setTargetCaptionLanguage(code)
                                    },
                                    label = { Text(name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF222232),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Bilibili Native Subtitle Tracks
                if (bilibiliTracks.isNotEmpty() && subtitleMode != GlobalPlayerManager.SubtitleMode.OFF && subtitleMode != GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS) {
                    item {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "AVAILABLE BILIBILI SUBTITLE TRACKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(bilibiliTracks) { track ->
                        val isSelected = selectedTrack?.url == track.url
                        Surface(
                            onClick = {
                                GlobalPlayerManager.selectBilibiliSubtitleTrack(track)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = track.languageName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Language: ${track.languageCode}",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3.5 External Subtitle Provider Tracks (OpenSubtitles, SubDL, Jimaku, SubSource)
                if (discoveredSubtitles.isNotEmpty() && subtitleMode != GlobalPlayerManager.SubtitleMode.OFF && subtitleMode != GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS) {
                    item {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "EXTERNAL SUBTITLE TRACKS (${discoveredSubtitles.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold
                            )
                            if (isSearchingSubtitles) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(discoveredSubtitles) { subItem ->
                        val isSelected = activeSubtitleItem?.id == subItem.id
                        Surface(
                            onClick = {
                                com.example.subtitles.SubtitleManager.selectSubtitle(context, subItem)
                                GlobalPlayerManager.setSubtitleMode(GlobalPlayerManager.SubtitleMode.EXTERNAL_PROVIDER, context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = subItem.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = subItem.providerName.uppercase(),
                                                color = Color(0xFF00E5FF),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = "${subItem.languageName} • ${subItem.format.name}",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp
                                        )
                                        if (subItem.isHearingImpaired) {
                                            Text(
                                                text = "• CC/HI",
                                                color = Color(0xFFFFD700),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Whisper AI Speech-to-Text Model Management
                if (subtitleMode == GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS) {
                    item {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WHISPER AI SPEECH RECOGNITION MODELS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Download compact GGML models directly to your device. On-device inference, dynamic sizing, and delete anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    items(whisperModels) { model ->
                        WhisperModelCard(
                            model = model,
                            isActive = activeWhisperModelId == model.id,
                            onSelect = {
                                WhisperModelManager.setActiveModel(model.id)
                            },
                            onDownload = {
                                scope.launch {
                                    WhisperModelManager.downloadModel(context, model.id) { _, _, _ -> }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    WhisperModelManager.deleteModel(context, model.id)
                                }
                            }
                        )
                    }
                }

                // 5. Display Customization
                if (subtitleMode != GlobalPlayerManager.SubtitleMode.OFF) {
                    item {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SUBTITLE APPEARANCE & LAYOUT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Dual Subtitles Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dual Subtitles (Original + Translated)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Displays original Chinese/Japanese along with translation",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = aiState.dualSubtitleEnabled,
                                onCheckedChange = { AiCaptionEngine.setDualSubtitles(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = Color(0xFF00E5FF)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Font Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font Size: ${aiState.fontSizeSp.toInt()}sp",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        Slider(
                            value = aiState.fontSizeSp,
                            onValueChange = { AiCaptionEngine.setFontSize(it) },
                            valueRange = 12f..28f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )

                        // Background Opacity
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Background Opacity: ${(aiState.backgroundOpacity * 100).toInt()}%",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        Slider(
                            value = aiState.backgroundOpacity,
                            onValueChange = { AiCaptionEngine.setBackgroundOpacity(it) },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF00E5FF),
            selectedLabelColor = Color.Black,
            containerColor = Color(0xFF222232),
            labelColor = Color.White
        )
    )
}

@Composable
private fun WhisperModelCard(
    model: WhisperModelInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = model.isDownloaded) { onSelect() },
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color(0xFF00E5FF) else Color.White,
                            fontSize = 15.sp
                        )
                        if (model.isRecommended) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "RECOMMENDED",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = model.description,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "Size: ${model.approximateSizeMb}",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Action buttons (Download / Delete / Active)
                if (model.isDownloading) {
                    CircularProgressIndicator(
                        progress = { model.downloadProgress },
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.dp
                    )
                } else if (model.isDownloaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active Model",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Button(
                                onClick = onSelect,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334A)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Use", fontSize = 12.sp, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Model",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (model.isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFF00E5FF),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}
