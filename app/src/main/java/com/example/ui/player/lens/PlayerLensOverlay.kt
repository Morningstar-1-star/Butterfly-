package com.example.ui.player.lens

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.player.core.PlayerFrameCaptureHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Composable
fun PlayerLensOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturing by remember { mutableStateOf(true) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var recognizedBlocks by remember { mutableStateOf<List<RecognizedTextBlock>>(emptyList()) }

    // Touch and Circle-to-Search state
    var touchPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawingCircle by remember { mutableStateOf(false) }
    var activeSelectionNormRect by remember { mutableStateOf<RectF?>(null) }
    var selectedText by remember { mutableStateOf<String?>(null) }
    var activeCroppedUri by remember { mutableStateOf<Uri?>(null) }
    var showAllTextDialog by remember { mutableStateOf(false) }

    // Pulse animation for detected text highlights
    val infiniteTransition = rememberInfiniteTransition(label = "lens_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Capture the frozen frame on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val bmp = PlayerFrameCaptureHelper.captureCurrentFrame()
            frameBitmap = bmp
            isCapturing = false

            if (bmp != null) {
                isOcrLoading = true
                val blocks = VisualLensEngine.recognizeText(bmp)
                recognizedBlocks = blocks
                isOcrLoading = false

                // Default URI for full frame
                val fullUri = PlayerFrameCaptureHelper.saveBitmapToTempUri(context, bmp, "lens_full_${System.currentTimeMillis()}.jpg")
                activeCroppedUri = fullUri
            }
        }
    }

    // Update active crop URI whenever selection changes
    fun updateSelectionCrop(normRect: RectF?) {
        activeSelectionNormRect = normRect
        val bmp = frameBitmap ?: return
        coroutineScope.launch(Dispatchers.Default) {
            val croppedBmp = if (normRect != null) {
                PlayerFrameCaptureHelper.cropNormalized(bmp, normRect)
            } else {
                bmp
            }
            val uri = PlayerFrameCaptureHelper.saveBitmapToTempUri(context, croppedBmp)
            activeCroppedUri = uri

            // Extract text in this region
            if (normRect != null) {
                val matchedText = recognizedBlocks.filter { block ->
                    RectF.intersects(block.normalizedBoundingBox, normRect)
                }.joinToString(" ") { it.text }
                selectedText = matchedText.ifBlank { null }
            } else {
                selectedText = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
    ) {
        val capturedBmp = frameBitmap
        if (capturedBmp != null) {
            // Render the captured frozen frame in background for seamless alignment
            Image(
                bitmap = capturedBmp.asImageBitmap(),
                contentDescription = "Frozen Video Frame",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gesture canvas for Circle-to-Search and Text Selection
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(recognizedBlocks, capturedBmp) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val normX = (tapOffset.x / size.width).coerceIn(0f, 1f)
                            val normY = (tapOffset.y / size.height).coerceIn(0f, 1f)

                            // Check if tapped on any recognized text block
                            val tappedBlock = recognizedBlocks.firstOrNull { block ->
                                block.normalizedBoundingBox.contains(normX, normY)
                            }

                            if (tappedBlock != null) {
                                VisualLensEngine.copyToClipboard(context, tappedBlock.text)
                                selectedText = tappedBlock.text
                                updateSelectionCrop(tappedBlock.normalizedBoundingBox)
                            } else {
                                // Tap on open space resets selection
                                touchPoints = emptyList()
                                updateSelectionCrop(null)
                            }
                        },
                        onLongPress = { pressOffset ->
                            val normX = (pressOffset.x / size.width).coerceIn(0f, 1f)
                            val normY = (pressOffset.y / size.height).coerceIn(0f, 1f)

                            // Expand a search area around the long press
                            val radiusNormX = 0.12f
                            val radiusNormY = 0.08f
                            val longPressRect = RectF(
                                (normX - radiusNormX).coerceAtLeast(0f),
                                (normY - radiusNormY).coerceAtLeast(0f),
                                (normX + radiusNormX).coerceAtMost(1f),
                                (normY + radiusNormY).coerceAtMost(1f)
                            )
                            updateSelectionCrop(longPressRect)

                            // Also copy text in this area if available
                            val matchedText = recognizedBlocks.filter { block ->
                                RectF.intersects(block.normalizedBoundingBox, longPressRect)
                            }.joinToString(" ") { it.text }
                            if (matchedText.isNotBlank()) {
                                VisualLensEngine.copyToClipboard(context, matchedText)
                                selectedText = matchedText
                            } else {
                                Toast.makeText(context, "Area selected for Visual Search", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            isDrawingCircle = true
                            touchPoints = listOf(startOffset)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            touchPoints = touchPoints + change.position
                        },
                        onDragEnd = {
                            isDrawingCircle = false
                            if (touchPoints.size > 3) {
                                val minX = touchPoints.minOf { it.x }.coerceAtLeast(0f)
                                val maxX = touchPoints.maxOf { it.x }.coerceAtMost(size.width.toFloat())
                                val minY = touchPoints.minOf { it.y }.coerceAtLeast(0f)
                                val maxY = touchPoints.maxOf { it.y }.coerceAtMost(size.height.toFloat())

                                if (maxX - minX > 20 && maxY - minY > 20) {
                                    val normRect = RectF(
                                        minX / size.width,
                                        minY / size.height,
                                        maxX / size.width,
                                        maxY / size.height
                                    )
                                    updateSelectionCrop(normRect)
                                }
                            }
                        },
                        onDragCancel = {
                            isDrawingCircle = false
                        }
                    )
                }
        ) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()

            // Draw glowing OCR text bounding boxes and Circle-to-Search lasso trail
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 1. Draw glowing text highlights
                for (block in recognizedBlocks) {
                    val rect = block.normalizedBoundingBox
                    val left = rect.left * canvasWidth
                    val top = rect.top * canvasHeight
                    val right = rect.right * canvasWidth
                    val bottom = rect.bottom * canvasHeight
                    val w = max(right - left, 1f)
                    val h = max(bottom - top, 1f)

                    // Draw translucent highlight box
                    drawRoundRect(
                        color = Color(0xFF4285F4).copy(alpha = pulseAlpha * 0.45f),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = Color(0xFF64B5F6).copy(alpha = pulseAlpha),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        style = Stroke(width = 2f)
                    )
                }

                // 2. Draw active selection bounding box if exists
                activeSelectionNormRect?.let { selRect ->
                    val selLeft = selRect.left * canvasWidth
                    val selTop = selRect.top * canvasHeight
                    val selW = max((selRect.right - selRect.left) * canvasWidth, 2f)
                    val selH = max((selRect.bottom - selRect.top) * canvasHeight, 2f)

                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))
                        ),
                        topLeft = Offset(selLeft, selTop),
                        size = Size(selW, selH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                        style = Stroke(width = 3.5f)
                    )
                    drawRoundRect(
                        color = Color(0x334285F4),
                        topLeft = Offset(selLeft, selTop),
                        size = Size(selW, selH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                    )
                }

                // 3. Draw freeform glowing lasso / circle trail
                if (touchPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(touchPoints.first().x, touchPoints.first().y)
                        for (i in 1 until touchPoints.size) {
                            lineTo(touchPoints[i].x, touchPoints[i].y)
                        }
                    }

                    // Outer glow
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF4285F4), Color(0xFF9C27B0), Color(0xFF00E5FF))
                        ),
                        style = Stroke(
                            width = 10f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        ),
                        alpha = 0.5f
                    )
                    // Inner bright beam
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(
                            width = 4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        // Top Navigation & Hint Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOcrLoading) "Detecting text..." else "Circle object or tap text to copy",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recognizedBlocks.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            val allText = recognizedBlocks.joinToString("\n") { it.text }
                            VisualLensEngine.copyToClipboard(context, allText)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF333333).copy(alpha = 0.9f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy All Text",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy All", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lens",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Action Bar & Visual Search Engines Pill
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selected text preview pill if text was selected
            AnimatedVisibility(
                visible = !selectedText.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E1E).copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected Text",
                                color = Color(0xFF90CAF9),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedText ?: "",
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedText?.let { VisualLensEngine.copyToClipboard(context, it) }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Text",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Engine Launchers Row (Google Lens, Lenso.ai, Bing, Yandex, Share)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF141414).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google Lens Action (Primary)
                    Button(
                        onClick = {
                            val uri = activeCroppedUri
                            if (uri != null) {
                                VisualLensEngine.launchGoogleLens(context, uri)
                            } else {
                                Toast.makeText(context, "Capturing frame...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Google Lens",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Lenso.ai
                    FilledTonalButton(
                        onClick = {
                            val uri = activeCroppedUri
                            if (uri != null) {
                                VisualLensEngine.launchVisualSearch(context, uri, VisualSearchEngine.LENSO_AI)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2C2C2C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TravelExplore,
                            contentDescription = null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lenso.ai", fontSize = 13.sp)
                    }

                    // Bing Visual
                    FilledTonalButton(
                        onClick = {
                            val uri = activeCroppedUri
                            if (uri != null) {
                                VisualLensEngine.launchVisualSearch(context, uri, VisualSearchEngine.BING_VISUAL)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2C2C2C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ImageSearch,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bing", fontSize = 13.sp)
                    }

                    // Yandex Images
                    FilledTonalButton(
                        onClick = {
                            val uri = activeCroppedUri
                            if (uri != null) {
                                VisualLensEngine.launchVisualSearch(context, uri, VisualSearchEngine.YANDEX_IMAGES)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2C2C2C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yandex", fontSize = 13.sp)
                    }

                    // Share Frame / Selection
                    IconButton(
                        onClick = {
                            val uri = activeCroppedUri
                            if (uri != null) {
                                VisualLensEngine.shareImage(context, uri)
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2C2C2C), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Image",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
