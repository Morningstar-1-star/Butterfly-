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
                .padding(horizontal = 24.dp, vertical = if (isPositionTop) 60.dp else 36.dp),
            contentAlignment = if (isPositionTop) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isDualTextDifferent = origText.isNotBlank() && transText.isNotBlank() && !origText.trim().equals(transText.trim(), ignoreCase = true)
                if (transText.isNotBlank() && subtitleMode != GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL) {
                    OutlinedSubtitleText(
                        text = transText,
                        fontSize = fontSize
                    )
                }
                if (origText.isNotBlank() && (subtitleMode == GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL || (dualEnabled && isDualTextDifferent) || transText.isBlank())) {
                    if (transText.isNotBlank() && subtitleMode != GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL && isDualTextDifferent) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    OutlinedSubtitleText(
                        text = origText,
                        fontSize = fontSize * 0.9f,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlinedSubtitleText(
    text: String,
    fontSize: Float,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Text(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                lineHeight = (fontSize * 1.25f).sp,
                color = Color.Black,
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 4.5f,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        )
        Text(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                lineHeight = (fontSize * 1.25f).sp,
                color = Color.White
            )
        )
    }
}
