package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Authentic brand logo renderer for all media sources.
 * Replaces generic material icons with pixel-perfect, authentic brand marks.
 */
@Composable
fun SourceBrandLogo(
    providerId: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    isAdultMode: Boolean = false
) {
    val cleanId = providerId.lowercase().trim()

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            // ALL SOURCES / AGGREGATED
            cleanId == "all" -> {
                if (isAdultMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFE91E63), Color(0xFFC2185B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "18+",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = (size.value * 0.42f).sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFB300), Color(0xFFFF8F00))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_butterfly_vector),
                            contentDescription = "All Sources",
                            modifier = Modifier.size(size * 0.72f),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                        )
                    }
                }
            }

            // YOUTUBE (Red rounded rect with white play triangle)
            cleanId == "youtube" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFFFF0000),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val trianglePath = Path().apply {
                        val cx = this@Canvas.size.width
                        val cy = this@Canvas.size.height
                        moveTo(cx * 0.38f, cy * 0.28f)
                        lineTo(cx * 0.72f, cy * 0.50f)
                        lineTo(cx * 0.38f, cy * 0.72f)
                        close()
                    }
                    drawPath(trianglePath, color = Color.White, style = Fill)
                }
            }

            // TENCENT VIDEO (v.qq.com) - Authentic 3-color swoosh play emblem on dark blue
            cleanId == "tencent" || cleanId.contains("qq") -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Deep Tencent navy-blue background
                    drawRoundRect(
                        color = Color(0xFF001B44),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Upper Green swoosh
                    val greenPath = Path().apply {
                        moveTo(w * 0.30f, h * 0.24f)
                        cubicTo(w * 0.40f, h * 0.22f, w * 0.58f, h * 0.32f, w * 0.68f, h * 0.46f)
                        cubicTo(w * 0.58f, h * 0.42f, w * 0.42f, h * 0.38f, w * 0.30f, h * 0.36f)
                        close()
                    }
                    drawPath(greenPath, color = Color(0xFF00C35A), style = Fill)

                    // Right Electric Blue swoosh
                    val bluePath = Path().apply {
                        moveTo(w * 0.70f, h * 0.46f)
                        cubicTo(w * 0.72f, h * 0.54f, w * 0.56f, h * 0.68f, w * 0.38f, h * 0.76f)
                        cubicTo(w * 0.48f, h * 0.68f, w * 0.62f, h * 0.58f, w * 0.62f, h * 0.48f)
                        close()
                    }
                    drawPath(bluePath, color = Color(0xFF0075FF), style = Fill)

                    // Left Orange / Amber swoosh
                    val orangePath = Path().apply {
                        moveTo(w * 0.28f, h * 0.36f)
                        cubicTo(w * 0.30f, h * 0.52f, w * 0.32f, h * 0.66f, w * 0.38f, h * 0.76f)
                        cubicTo(w * 0.34f, h * 0.62f, w * 0.34f, h * 0.48f, w * 0.32f, h * 0.38f)
                        close()
                    }
                    drawPath(orangePath, color = Color(0xFFFF7A00), style = Fill)

                    // Center white accent dot
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.08f,
                        center = Offset(w * 0.46f, h * 0.50f)
                    )
                }
            }

            // TWITCH (Twitch Purple with chat bubble and eyes)
            cleanId == "twitch" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF9146FF),
                        cornerRadius = CornerRadius(this.size.width * 0.20f, this.size.height * 0.20f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    val bubble = Path().apply {
                        moveTo(w * 0.22f, h * 0.22f)
                        lineTo(w * 0.78f, h * 0.22f)
                        lineTo(w * 0.78f, h * 0.68f)
                        lineTo(w * 0.56f, h * 0.68f)
                        lineTo(w * 0.46f, h * 0.80f)
                        lineTo(w * 0.46f, h * 0.68f)
                        lineTo(w * 0.22f, h * 0.68f)
                        close()
                    }
                    drawPath(bubble, color = Color.White, style = Fill)

                    // Eyes inside
                    drawRoundRect(
                        color = Color(0xFF9146FF),
                        topLeft = Offset(w * 0.38f, h * 0.36f),
                        size = Size(w * 0.08f, h * 0.18f),
                        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
                    )
                    drawRoundRect(
                        color = Color(0xFF9146FF),
                        topLeft = Offset(w * 0.54f, h * 0.36f),
                        size = Size(w * 0.08f, h * 0.18f),
                        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
                    )
                }
            }

            // BILIBILI (Iconic Cyan TV mascot with antennas and eyes)
            cleanId == "bilibili" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF00A1D6),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Antennas
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.36f, h * 0.32f),
                        end = Offset(w * 0.25f, h * 0.18f),
                        strokeWidth = w * 0.08f
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.64f, h * 0.32f),
                        end = Offset(w * 0.75f, h * 0.18f),
                        strokeWidth = w * 0.08f
                    )

                    // White TV screen body
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.20f, h * 0.32f),
                        size = Size(w * 0.60f, h * 0.44f),
                        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
                    )

                    // Two eyes ( • • )
                    drawCircle(color = Color(0xFF00A1D6), radius = w * 0.06f, center = Offset(w * 0.38f, h * 0.52f))
                    drawCircle(color = Color(0xFF00A1D6), radius = w * 0.06f, center = Offset(w * 0.62f, h * 0.52f))
                }
            }

            // DAILYMOTION (Official Blue circular "d" logo)
            cleanId == "dailymotion" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF002244)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "d",
                        color = Color(0xFF0066DC),
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.72f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // CRUNCHYROLL (Vibrant Orange with crescent & inner eye)
            cleanId == "crunchyroll" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color(0xFFF47521))
                    val w = this.size.width
                    val h = this.size.height

                    // Outer white crescent
                    drawArc(
                        color = Color.White,
                        startAngle = 30f,
                        sweepAngle = 260f,
                        useCenter = true,
                        topLeft = Offset(w * 0.22f, h * 0.22f),
                        size = Size(w * 0.56f, h * 0.56f)
                    )
                    // Inner orange cutout
                    drawCircle(
                        color = Color(0xFFF47521),
                        radius = w * 0.22f,
                        center = Offset(w * 0.52f, h * 0.48f)
                    )
                    // Center white pupil
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.10f,
                        center = Offset(w * 0.52f, h * 0.48f)
                    )
                }
            }

            // SONYLIV (Deep blue with multi-color 4-facet diamond)
            cleanId == "sonyliv" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF080C1E),
                        cornerRadius = CornerRadius(this.size.width * 0.20f, this.size.height * 0.20f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Diamond 1 (Cyan)
                    val p1 = Path().apply {
                        moveTo(w * 0.5f, h * 0.22f)
                        lineTo(w * 0.72f, h * 0.38f)
                        lineTo(w * 0.5f, h * 0.48f)
                        lineTo(w * 0.36f, h * 0.34f)
                        close()
                    }
                    drawPath(p1, Color(0xFF00D4FF))

                    // Diamond 2 (Pink/Purple)
                    val p2 = Path().apply {
                        moveTo(w * 0.5f, h * 0.48f)
                        lineTo(w * 0.72f, h * 0.38f)
                        lineTo(w * 0.64f, h * 0.66f)
                        lineTo(w * 0.5f, h * 0.78f)
                        close()
                    }
                    drawPath(p2, Color(0xFFFF007A))

                    // Diamond 3 (Yellow/Orange)
                    val p3 = Path().apply {
                        moveTo(w * 0.5f, h * 0.48f)
                        lineTo(w * 0.5f, h * 0.78f)
                        lineTo(w * 0.30f, h * 0.62f)
                        lineTo(w * 0.36f, h * 0.34f)
                        close()
                    }
                    drawPath(p3, Color(0xFFFFD200))
                }
            }

            // HOTSTAR / DISNEY+ HOTSTAR (Midnight navy with radiant gold star)
            cleanId == "hotstar" || cleanId == "disney" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF061426),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Golden Star mark
                    val star = Path().apply {
                        val cx = w * 0.5f
                        val cy = h * 0.5f
                        val outer = w * 0.32f
                        val inner = w * 0.14f
                        for (i in 0 until 5) {
                            val a1 = Math.toRadians((i * 72 - 90).toDouble())
                            val a2 = Math.toRadians((i * 72 + 36 - 90).toDouble())
                            val x1 = (cx + outer * Math.cos(a1)).toFloat()
                            val y1 = (cy + outer * Math.sin(a1)).toFloat()
                            val x2 = (cx + inner * Math.cos(a2)).toFloat()
                            val y2 = (cy + inner * Math.sin(a2)).toFloat()
                            if (i == 0) moveTo(x1, y1) else lineTo(x1, y1)
                            lineTo(x2, y2)
                        }
                        close()
                    }
                    drawPath(star, Color(0xFFFFCC00))
                }
            }

            // AMAZON MINITV (Dark badge with bright orange curved smile)
            cleanId == "amazonminitv" || cleanId.contains("minitv") -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF131921),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Smile Curve
                    val smile = Path().apply {
                        moveTo(w * 0.24f, h * 0.52f)
                        cubicTo(w * 0.38f, h * 0.72f, w * 0.62f, h * 0.72f, w * 0.76f, h * 0.54f)
                    }
                    drawPath(smile, color = Color(0xFFFF9900), style = Stroke(width = w * 0.08f))

                    // Smile Arrow
                    val arrow = Path().apply {
                        moveTo(w * 0.70f, h * 0.44f)
                        lineTo(w * 0.80f, h * 0.54f)
                        lineTo(w * 0.68f, h * 0.62f)
                        close()
                    }
                    drawPath(arrow, color = Color(0xFFFF9900), style = Fill)
                }
            }

            // XNXX (Official Royal Blue badge with crisp bold white XNXX lettering)
            cleanId == "xnxx" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF005FB8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "XNXX",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.33f).sp,
                        letterSpacing = (-0.5).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // HELLPORNO (Jet black badge with crimson outline & fiery HP crest)
            cleanId == "hellporno" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101014))
                        .border(1.dp, Color(0xFFFF1744), RoundedCornerShape(size * 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HP",
                        color = Color(0xFFFF1744),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.48f).sp,
                        letterSpacing = (-0.5).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // STRIPCHAT (Dark circle with white webcam & vibrant glowing red live recording dot)
            cleanId == "stripchat" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color(0xFF181922))
                    val w = this.size.width
                    val h = this.size.height

                    // White camera body
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.24f, h * 0.34f),
                        size = Size(w * 0.36f, h * 0.36f),
                        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
                    )
                    // Camera lens triangle
                    val lens = Path().apply {
                        moveTo(w * 0.60f, h * 0.42f)
                        lineTo(w * 0.74f, h * 0.34f)
                        lineTo(w * 0.74f, h * 0.68f)
                        lineTo(w * 0.60f, h * 0.60f)
                        close()
                    }
                    drawPath(lens, color = Color.White, style = Fill)

                    // Radiant LIVE red beacon dot at top right
                    drawCircle(
                        color = Color(0xFFFF1744),
                        radius = w * 0.12f,
                        center = Offset(w * 0.76f, h * 0.26f)
                    )
                }
            }

            // CHATURBATE (Signature bright orange pill with white camera mark)
            cleanId == "chaturbate" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFFFF6B00),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // White camera body
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.22f, h * 0.32f),
                        size = Size(w * 0.36f, h * 0.38f),
                        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
                    )
                    // Camera cone
                    val cone = Path().apply {
                        moveTo(w * 0.58f, h * 0.40f)
                        lineTo(w * 0.76f, h * 0.30f)
                        lineTo(w * 0.76f, h * 0.70f)
                        lineTo(w * 0.58f, h * 0.62f)
                        close()
                    }
                    drawPath(cone, color = Color.White, style = Fill)
                }
            }

            // PORNHUB (Black badge with white "Porn" and amber pill "hub")
            cleanId == "pornhub" -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141414))
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "P",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.44f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFA31A), RoundedCornerShape(2.dp))
                            .padding(horizontal = 2.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "h",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = (size.value * 0.44f).sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // XVIDEOS (Dark badge with bold XV)
            cleanId == "xvideos" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color(0xFFD32F2F), RoundedCornerShape(size * 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "XV",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.44f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // SUPJAV (Deep magenta badge with white SJ)
            cleanId == "supjav" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8E24AA)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SJ",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.44f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // 123AV (Dark crimson badge with 123)
            cleanId == "123av" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF880E4F)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "123",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.36f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // IMDB (Yellow badge with black bold text)
            cleanId == "imdb" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF5C518)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "IMDb",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.28f).sp,
                        letterSpacing = (-0.5).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // MX PLAYER (Blue badge with white play triangle)
            cleanId == "mxplayer" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0084FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MX",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.44f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // GOOGLE DRIVE (Tri-color Drive badge)
            cleanId == "googledrive" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height

                    // Yellow top-right
                    val pYellow = Path().apply {
                        moveTo(w * 0.50f, h * 0.20f)
                        lineTo(w * 0.80f, h * 0.72f)
                        lineTo(w * 0.65f, h * 0.72f)
                        lineTo(w * 0.35f, h * 0.20f)
                        close()
                    }
                    drawPath(pYellow, Color(0xFFFFBA00))

                    // Green bottom
                    val pGreen = Path().apply {
                        moveTo(w * 0.25f, h * 0.72f)
                        lineTo(w * 0.80f, h * 0.72f)
                        lineTo(w * 0.65f, h * 0.86f)
                        lineTo(w * 0.10f, h * 0.86f)
                        close()
                    }
                    drawPath(pGreen, Color(0xFF00AC47))

                    // Blue left
                    val pBlue = Path().apply {
                        moveTo(w * 0.10f, h * 0.86f)
                        lineTo(w * 0.50f, h * 0.20f)
                        lineTo(w * 0.35f, h * 0.20f)
                        lineTo(w * 0.25f, h * 0.38f)
                        close()
                    }
                    drawPath(pBlue, Color(0xFF2684FC))
                }
            }

            // POPCORN TV / CINEMA
            cleanId == "popcorntv" || cleanId == "decryptor" || cleanId == "vidsrc" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFF3366), Color(0xFFFF6B6B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "4K",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.44f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // VIMEO (Official Vimeo Cerulean Blue with classic bold italic 'v')
            cleanId == "vimeo" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1AB7EA)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "v",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.72f).sp,
                        fontFamily = FontFamily.Serif
                    )
                }
            }

            // BIGO LIVE (Iconic Cyan/Teal brand styling with bold BIGO mark)
            cleanId == "bigo" || cleanId == "bigolive" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BIGO",
                        color = Color(0xFF0D1B2A),
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.28f).sp,
                        letterSpacing = (-0.5).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // TELEGRAM (Official sky blue circle with white folded paper airplane)
            cleanId == "telegram" || cleanId == "tg" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color(0xFF2AABEE))
                    val w = this.size.width
                    val h = this.size.height

                    val airplane = Path().apply {
                        moveTo(w * 0.20f, h * 0.50f)
                        lineTo(w * 0.80f, h * 0.24f)
                        lineTo(w * 0.65f, h * 0.78f)
                        lineTo(w * 0.48f, h * 0.60f)
                        lineTo(w * 0.44f, h * 0.72f)
                        lineTo(w * 0.40f, h * 0.58f)
                        lineTo(w * 0.20f, h * 0.50f)
                        close()
                    }
                    drawPath(airplane, color = Color.White, style = Fill)
                }
            }

            // MEGA (Official vibrant red circle with crisp bold white 'M')
            cleanId == "mega" || cleanId.startsWith("mega_") -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFD9272E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.64f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // BUNKR (Dark slate with neon cyan hexagon / B mark)
            cleanId == "bunkr" || cleanId.startsWith("bunkr_") -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF06B6D4), RoundedCornerShape(size * 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "B",
                        color = Color(0xFF06B6D4),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.55f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // INTERNET ARCHIVE (Classical library pillars facade)
            cleanId == "archive_org" || cleanId == "archive" || cleanId == "internet_archive" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF22252A),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Pediment (triangle roof)
                    val pediment = Path().apply {
                        moveTo(w * 0.18f, h * 0.32f)
                        lineTo(w * 0.50f, h * 0.18f)
                        lineTo(w * 0.82f, h * 0.32f)
                        close()
                    }
                    drawPath(pediment, Color(0xFFE0E0E0))

                    // Architrave beam
                    drawRect(
                        color = Color(0xFFE0E0E0),
                        topLeft = Offset(w * 0.18f, h * 0.33f),
                        size = Size(w * 0.64f, h * 0.05f)
                    )

                    // 4 Pillars
                    val pillarWidth = w * 0.08f
                    val pillarHeight = h * 0.36f
                    val startY = h * 0.40f
                    val spacing = (w * 0.64f - pillarWidth * 4) / 3f
                    for (i in 0..3) {
                        val px = w * 0.18f + i * (pillarWidth + spacing)
                        drawRect(
                            color = Color(0xFFE0E0E0),
                            topLeft = Offset(px, startY),
                            size = Size(pillarWidth, pillarHeight)
                        )
                    }

                    // Base plinth
                    drawRect(
                        color = Color(0xFFE0E0E0),
                        topLeft = Offset(w * 0.14f, h * 0.78f),
                        size = Size(w * 0.72f, h * 0.06f)
                    )
                }
            }

            // DISCOVERY+ (Midnight blue with radiant colorful ring)
            cleanId == "discoveryplus" || cleanId == "discovery" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF001435),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Glowing colorful outer ring
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFFFF3D00), Color(0xFFFFD600), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFFF3D00))
                        ),
                        radius = w * 0.30f,
                        center = Offset(w * 0.46f, h * 0.50f),
                        style = Stroke(width = w * 0.09f)
                    )
                    // Plus mark on right
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.72f, h * 0.50f),
                        end = Offset(w * 0.86f, h * 0.50f),
                        strokeWidth = w * 0.07f
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.79f, h * 0.43f),
                        end = Offset(w * 0.79f, h * 0.57f),
                        strokeWidth = w * 0.07f
                    )
                }
            }

            // HBO MAX / MAX (Royal purple badge with iconic bold MAX typography)
            cleanId == "hbo" || cleanId == "hbomax" || cleanId == "max" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF5822B4), Color(0xFF002BFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MAX",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.32f).sp,
                        letterSpacing = (-0.5).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // CURIOSITY STREAM (Crimson red badge with crisp bold Q / C)
            cleanId == "curiositystream" || cleanId == "curiosity" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE50914)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "C",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.60f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // TORRENT / P2P (Deep indigo badge with green/white swarm loop)
            cleanId == "torrent" || cleanId == "torrentio" || cleanId == "yts" || cleanId == "eztv" || cleanId == "1337x" || cleanId == "nyaa" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFF1E1035),
                        cornerRadius = CornerRadius(this.size.width * 0.22f, this.size.height * 0.22f)
                    )
                    val w = this.size.width
                    val h = this.size.height

                    // Swarm download arrows loop
                    drawArc(
                        color = Color(0xFF65B32E),
                        startAngle = 180f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(w * 0.22f, h * 0.22f),
                        size = Size(w * 0.56f, h * 0.56f),
                        style = Stroke(width = w * 0.08f)
                    )
                    drawArc(
                        color = Color(0xFF00E5FF),
                        startAngle = 0f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(w * 0.22f, h * 0.22f),
                        size = Size(w * 0.56f, h * 0.56f),
                        style = Stroke(width = w * 0.08f)
                    )
                    // Down arrow in center
                    val arrow = Path().apply {
                        moveTo(w * 0.50f, h * 0.64f)
                        lineTo(w * 0.38f, h * 0.48f)
                        lineTo(w * 0.62f, h * 0.48f)
                        close()
                    }
                    drawPath(arrow, color = Color.White, style = Fill)
                }
            }

            // ANILIST / JIKAN ANIME (Deep violet with clean anime star / A)
            cleanId == "anilist" || cleanId == "jikan" || cleanId == "jikan_anime" || cleanId == "anime" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2B2D42), Color(0xFF3F37C9))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AL",
                        color = Color(0xFF00F5D4),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.40f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // EPORNER (Magenta badge with crisp bold E)
            cleanId == "eporner" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE91E63)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EP",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.42f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // HANIME (Deep violet/pink anime crest)
            cleanId == "hanime1" || cleanId == "hanime" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6A0572), Color(0xFFAB83A1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "H",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.58f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // BEEG (Dark slate minimalist with clean b)
            cleanId == "beeg" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1C1C1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "beeg",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.28f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // CAM4 / CAMMODELS
            cleanId == "cam4" || cleanId == "cammodels" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFC62828)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = (size.value * 0.30f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // DEFAULT CLEAN BRAND MONOGRAM
            else -> {
                val monogram = cleanId.take(2).uppercase()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isAdultMode) Color(0xFF2B1B26) else Color(0xFF1E2230)
                        )
                        .border(
                            0.5.dp,
                            if (isAdultMode) Color(0xFFE91E63).copy(alpha = 0.4f) else Color(0xFF00B0FF).copy(alpha = 0.4f),
                            RoundedCornerShape(size * 0.22f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = monogram,
                        color = if (isAdultMode) Color(0xFFFF80AB) else Color(0xFF80D8FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.42f).sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
