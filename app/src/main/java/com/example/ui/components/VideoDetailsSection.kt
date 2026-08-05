package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.StreamData

@Composable
fun VideoDetailsSection(
    streamData: StreamData,
    selectedOption: PlayableStreamOption?,
    selectedCaption: CaptionOption?,
    onSelectOption: (PlayableStreamOption) -> Unit,
    onSelectCaption: (CaptionOption?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isQualityMenuExpanded by remember { mutableStateOf(false) }
    var isCaptionMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Video Title
        Text(
            text = streamData.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Views & Date
        Text(
            text = buildString {
                if (streamData.viewCount >= 0) {
                    append("${streamData.viewCount} views")
                }
                if (!streamData.uploadDate.isNullOrEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append("Uploaded ${streamData.uploadDate}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Channel Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!streamData.channelAvatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = streamData.channelAvatarUrl,
                    contentDescription = streamData.channelName,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = streamData.channelName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = streamData.channelName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!streamData.subscriberCountText.isNullOrEmpty()) {
                    Text(
                        text = streamData.subscriberCountText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Subscribe", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quality and Caption Selectors Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quality Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { isQualityMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedOption?.qualityLabel ?: "Select Quality",
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                DropdownMenu(
                    expanded = isQualityMenuExpanded,
                    onDismissRequest = { isQualityMenuExpanded = false }
                ) {
                    streamData.availableStreamOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${option.qualityLabel} (${option.format})",
                                    fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectOption(option)
                                isQualityMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Captions Dropdown
            if (streamData.captionOptions.isNotEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isCaptionMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedCaption?.languageName ?: "Captions Off",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = isCaptionMenuExpanded,
                        onDismissRequest = { isCaptionMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Captions Off") },
                            onClick = {
                                onSelectCaption(null)
                                isCaptionMenuExpanded = false
                            }
                        )
                        streamData.captionOptions.forEach { caption ->
                            DropdownMenuItem(
                                text = { Text(caption.languageName) },
                                onClick = {
                                    onSelectCaption(caption)
                                    isCaptionMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Description Card
        if (!streamData.description.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (isDescriptionExpanded) "Show Less" else "Show More",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = streamData.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
