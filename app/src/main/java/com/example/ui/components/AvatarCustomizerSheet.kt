package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.UserProfile

/**
 * Data model for built-in 3D cartoon character models and avatar presets
 */
data class CartoonAvatarModel(
    val id: String,
    val name: String,
    val tag: String,
    val emoji: String,
    val gradientColors: List<Color>,
    val imageUrl: String,
    val description: String
)

object BuiltinAvatarPresets {
    val models = listOf(
        CartoonAvatarModel(
            id = "3d_lucifer_demon",
            name = "Lucifer Cyber-Demon",
            tag = "3D DEMON",
            emoji = "😈",
            gradientColors = listOf(Color(0xFFE50914), Color(0xFF8E0E00), Color(0xFF1F0000)),
            imageUrl = "https://api.dicebear.com/7.x/bottts-neutral/png?seed=LuciferDemon99&radius=50&backgroundColor=b6e3f4,c0aede,d1d4f9",
            description = "Neon horns, dark cyberpunk devil persona"
        ),
        CartoonAvatarModel(
            id = "3d_cyber_cat",
            name = "VR Cyber Cat",
            tag = "3D CYBER",
            emoji = "🐱",
            gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF0052D4), Color(0xFF4364F7)),
            imageUrl = "https://api.dicebear.com/7.x/bottts/png?seed=CyberCatGamer&radius=50",
            description = "Futuristic VR tech goggles with cyan neon glow"
        ),
        CartoonAvatarModel(
            id = "3d_anime_shinobi",
            name = "Anime Shinobi",
            tag = "3D ANIME",
            emoji = "⚡",
            gradientColors = listOf(Color(0xFFFFD700), Color(0xFFFF8C00), Color(0xFF2C3E50)),
            imageUrl = "https://api.dicebear.com/7.x/adventurer/png?seed=AnimeLuciferHero&radius=50&backgroundColor=ffd5dc,ffdfbf",
            description = "Spiky silver hair with electric lightning aura"
        ),
        CartoonAvatarModel(
            id = "3d_gamer_pro",
            name = "Neon Pro Gamer",
            tag = "3D GAMER",
            emoji = "🎮",
            gradientColors = listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFF2B0938)),
            imageUrl = "https://api.dicebear.com/7.x/pixel-art/png?seed=LuciferGamer&radius=50",
            description = "RGB glow headset with studio lighting"
        ),
        CartoonAvatarModel(
            id = "3d_gold_sovereign",
            name = "Golden Sovereign",
            tag = "3D ROYAL",
            emoji = "👑",
            gradientColors = listOf(Color(0xFFFFD700), Color(0xFFB8860B), Color(0xFF000000)),
            imageUrl = "https://api.dicebear.com/7.x/thumbs/png?seed=RoyalKingLucifer&radius=50",
            description = "Majestic gold crown with obsidian finish"
        ),
        CartoonAvatarModel(
            id = "3d_astro_cosmic",
            name = "Cosmic Astronaut",
            tag = "3D SPACE",
            emoji = "🚀",
            gradientColors = listOf(Color(0xFF7F00FF), Color(0xFFE100FF), Color(0xFF0F0C29)),
            imageUrl = "https://api.dicebear.com/7.x/bottts/png?seed=AstroCosmicLucifer&radius=50",
            description = "Holographic galaxy visor & nebula aura"
        ),
        CartoonAvatarModel(
            id = "3d_kitsune_spirit",
            name = "Kitsune Fox Spirit",
            tag = "3D MYTHIC",
            emoji = "🦊",
            gradientColors = listOf(Color(0xFFFF512F), Color(0xFFDD2476), Color(0xFF1E130C)),
            imageUrl = "https://api.dicebear.com/7.x/lorelei/png?seed=KitsuneSpirit99&radius=50",
            description = "Mythical flame tail with cherry blossom glow"
        ),
        CartoonAvatarModel(
            id = "3d_mecha_sentinel",
            name = "Titanium Mecha",
            tag = "3D MECHA",
            emoji = "🤖",
            gradientColors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF141E30)),
            imageUrl = "https://api.dicebear.com/7.x/bottts-neutral/png?seed=MechaTitanium&radius=50",
            description = "High-tech armored frame with glowing core"
        ),
        CartoonAvatarModel(
            id = "3d_mythic_dragon",
            name = "Emerald Drake",
            tag = "3D DRAGON",
            emoji = "🐲",
            gradientColors = listOf(Color(0xFF00E676), Color(0xFF00897B), Color(0xFF004D40)),
            imageUrl = "https://api.dicebear.com/7.x/big-smile/png?seed=EmeraldDragon&radius=50",
            description = "Luminous emerald scales and ember breath"
        ),
        CartoonAvatarModel(
            id = "3d_lofi_vibes",
            name = "Lo-Fi Sunset",
            tag = "3D LO-FI",
            emoji = "🎧",
            gradientColors = listOf(Color(0xFFFF6A00), Color(0xFFEE0979), Color(0xFF2E0854)),
            imageUrl = "https://api.dicebear.com/7.x/micah/png?seed=LofiProducerLucifer&radius=50",
            description = "Chill retro aesthetic with vintage headphones"
        )
    )
}

/**
 * Bottom Sheet for selecting built-in 3D Cartoon avatars or picking a custom logo from device gallery
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCustomizerSheet(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onAvatarSelected: (avatarUrl: String?, presetId: String) -> Unit
) {
    var selectedPresetId by remember { mutableStateOf(userProfile.avatarPreset) }
    var customUrlInput by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }

    // System Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onAvatarSelected(uri.toString(), "custom_gallery")
            onDismiss()
        }
    }

    // Generic Gallery Intent launcher fallback
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onAvatarSelected(uri.toString(), "custom_gallery")
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121A),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Customize Profile Avatar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Pick a 3D cartoon character model or upload your logo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current Active Avatar Showcase
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E2C),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Circle with Neon Ring
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFFFF007F))
                                )
                            )
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF12121A)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userProfile.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile.avatarUrl,
                                contentDescription = "Active Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val activeModel = BuiltinAvatarPresets.models.find { it.id == userProfile.avatarPreset }
                            if (activeModel != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(activeModel.gradientColors)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = activeModel.emoji, fontSize = 28.sp)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.name.ifBlank { "Lucifer" },
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (userProfile.avatarPreset == "custom_gallery") "Custom Gallery Logo"
                            else BuiltinAvatarPresets.models.find { it.id == userProfile.avatarPreset }?.name ?: "Default Avatar",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // Reset to default button
                    if (!userProfile.avatarUrl.isNullOrBlank() || userProfile.avatarPreset != "purple") {
                        TextButton(
                            onClick = {
                                onAvatarSelected(null, "purple")
                                onDismiss()
                            }
                        ) {
                            Text("Reset", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Upload from Gallery + Enter Image URL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Upload from Gallery Button
                Surface(
                    onClick = {
                        try {
                            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable()) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                galleryLauncher.launch("image/*")
                            }
                        } catch (e: Exception) {
                            galleryLauncher.launch("image/*")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Upload from Gallery",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upload Gallery",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Custom Web URL Button
                Surface(
                    onClick = { showUrlDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E2C),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Image Link",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Image URL",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section: 3D Cartoon Models Grid
            Text(
                text = "INBUILT 3D CARTOON MODELS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(BuiltinAvatarPresets.models, key = { it.id }) { model ->
                    val isSelected = (userProfile.avatarPreset == model.id) || (userProfile.avatarUrl == model.imageUrl)

                    Surface(
                        onClick = {
                            selectedPresetId = model.id
                            onAvatarSelected(model.imageUrl, model.id)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color(0xFF1E1E2C),
                        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF00E5FF)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 3D Model Avatar Avatar Bubble
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(model.gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = model.imageUrl,
                                    contentDescription = model.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = model.gradientColors.first().copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = model.tag,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Custom URL Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Enter Avatar / Logo Image URL", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Paste any direct image link (.png, .jpg, .webp, .svg):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        label = { Text("https://example.com/logo.png") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customUrlInput.isNotBlank()) {
                            onAvatarSelected(customUrlInput.trim(), "custom_url")
                            showUrlDialog = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
