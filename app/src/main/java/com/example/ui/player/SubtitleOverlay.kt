package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleOverlay(modifier: Modifier = Modifier) {
    val subtitleMode by GlobalPlayerManager.subtitleMode.collectAsState()
    val bilibiliOrigText by GlobalPlayerManager.currentActiveSubtitleText.collectAsState()
    val bilibiliTransText by GlobalPlayerManager.currentActiveTranslatedText.collectAsState()

    if (subtitleMode == GlobalPlayerManager.SubtitleMode.OFF) return

    val (origText, transText) = when (subtitleMode) {
        GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL -> Pair(bilibiliOrigText, "")
        GlobalPlayerManager.SubtitleMode.BILIBILI_TRANSLATED -> Pair(bilibiliOrigText, bilibiliTransText)
        GlobalPlayerManager.SubtitleMode.EXTERNAL_PROVIDER -> Pair(bilibiliOrigText, bilibiliTransText)
        else -> Pair("", "")
    }

    val hasContent = origText.isNotBlank() || transText.isNotBlank()
    val isPositionTop = false
    val bgOpacity = 0.5f
    val fontSize = 16f
    val dualEnabled = true

    AnimatedVisibility(
        visible = hasContent,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = if (isPositionTop) 60.dp else 40.dp),
            contentAlignment = if (isPositionTop) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = bgOpacity),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (transText.isNotBlank() && subtitleMode != GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL) {
                    Text(
                        text = transText,
                        color = Color(0xFFFFFF00),
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = (fontSize * 1.3f).sp
                    )
                }
                if (origText.isNotBlank() && (dualEnabled || subtitleMode == GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL || transText.isBlank())) {
                    if (transText.isNotBlank() && subtitleMode != GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL) {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                    Text(
                        text = origText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = (fontSize * 0.85f).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = (fontSize * 1.15f).sp
                    )
                }
            }
        }
    }
}
