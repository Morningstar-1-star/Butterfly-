package com.example.ui.player.lens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.player.core.PlayerFrameCaptureHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerLensOverlay(
    onDismiss: () -> Unit,
    videoTitle: String? = null,
    videoArtist: String? = null,
    onPlayToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturing by remember { mutableStateOf(true) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var recognizedBlocks by remember { mutableStateOf<List<RecognizedTextBlock>>(emptyList()) }
    var translatedBlocks by remember { mutableStateOf<List<RecognizedTextBlock>>(emptyList()) }

    // Video Text Translation state
    var isTranslationActive by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var targetLanguage by remember { mutableStateOf("en") }
    var selectedBlockForDetails by remember { mutableStateOf<RecognizedTextBlock?>(null) }

    // Touch and Circle-to-Search state
    var touchPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawingCircle by remember { mutableStateOf(false) }
    var activeSelectionNormRect by remember { mutableStateOf<RectF?>(null) }
    var selectedText by remember { mutableStateOf<String?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeCroppedUri by remember { mutableStateOf<Uri?>(null) }
    var publicImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }

    // Sliding Results Sheet state
    var showResultsSheet by remember { mutableStateOf(false) }
    var showMusicSearchSheet by remember { mutableStateOf(false) }
    var isVideoAudioPlaying by remember { mutableStateOf(false) }
    var activeEngine by remember { mutableStateOf(VisualSearchEngine.AI_OVERVIEW) }
    var searchQueryInput by remember { mutableStateOf("") }
    var aiAnalysis by remember { mutableStateOf<VisualSearchAnalysis?>(null) }
    var isAnalyzingAi by remember { mutableStateOf(false) }

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

                val fullUri = PlayerFrameCaptureHelper.saveBitmapToTempUri(context, bmp, "lens_full_${System.currentTimeMillis()}.jpg")
                activeCroppedUri = fullUri
            }
        }
    }

    // Live Video Text Translation Trigger
    fun triggerTranslation(lang: String = targetLanguage) {
        if (isTranslating) return
        targetLanguage = lang
        isTranslationActive = true
        isTranslating = true
        coroutineScope.launch(Dispatchers.Default) {
            val translated = VisualLensEngine.translateBlocks(recognizedBlocks, targetLanguage)
            translatedBlocks = translated
            isTranslating = false
        }
    }

    // Function to update selection crop and trigger AI analysis & bottom sheet
    fun triggerSelectionSearch(normRect: RectF?, customQuery: String? = null) {
        activeSelectionNormRect = normRect
        val bmp = frameBitmap ?: return
        coroutineScope.launch(Dispatchers.Default) {
            val cropped = if (normRect != null) {
                PlayerFrameCaptureHelper.cropNormalized(bmp, normRect)
            } else {
                bmp
            }
            croppedBitmap = cropped
            val uri = PlayerFrameCaptureHelper.saveBitmapToTempUri(context, cropped)
            activeCroppedUri = uri

            // Extract text in this region
            val matchedText = if (normRect != null) {
                recognizedBlocks.filter { block ->
                    RectF.intersects(block.normalizedBoundingBox, normRect)
                }.joinToString(" ") { it.text }
            } else {
                ""
            }
            selectedText = matchedText.ifBlank { null }

            val initialQuery = customQuery ?: matchedText.ifBlank { videoTitle?.let { "$it visual search" } ?: "" }
            if (initialQuery.isNotBlank() && searchQueryInput.isBlank()) {
                searchQueryInput = initialQuery
            }

            // Immediately display results sheet and show quick progress indicators
            showResultsSheet = true
            isAnalyzingAi = true
            isUploadingImage = true
            publicImageUrl = null

            // 1. Upload cropped image asynchronously for authentic multi-engine reverse search (Google Lens, Bing, Yandex, TinEye)
            val uploadedUrl = VisualLensEngine.uploadCroppedImage(cropped)
            publicImageUrl = uploadedUrl
            isUploadingImage = false

            // 2. Generate comprehensive AI Overview and visual links
            val analysis = VisualLensEngine.generateAiOverview(
                context = context,
                extractedText = matchedText.ifBlank { null },
                userCustomQuery = customQuery ?: searchQueryInput.ifBlank { null },
                videoContextTitle = videoTitle,
                publicImageUrl = uploadedUrl
            )
            aiAnalysis = analysis
            isAnalyzingAi = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
    ) {
        val capturedBmp = frameBitmap
        if (capturedBmp != null) {
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
                .pointerInput(recognizedBlocks, capturedBmp, isTranslationActive) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val normX = (tapOffset.x / size.width).coerceIn(0f, 1f)
                            val normY = (tapOffset.y / size.height).coerceIn(0f, 1f)

                            val blocksPool = if (isTranslationActive && translatedBlocks.isNotEmpty()) translatedBlocks else recognizedBlocks
                            val tappedBlock = blocksPool.firstOrNull { block ->
                                block.normalizedBoundingBox.contains(normX, normY)
                            }

                            if (tappedBlock != null) {
                                selectedBlockForDetails = tappedBlock
                                selectedText = tappedBlock.text
                            } else {
                                touchPoints = emptyList()
                                activeSelectionNormRect = null
                                selectedText = null
                                selectedBlockForDetails = null
                            }
                        },
                        onLongPress = { pressOffset ->
                            val normX = (pressOffset.x / size.width).coerceIn(0f, 1f)
                            val normY = (pressOffset.y / size.height).coerceIn(0f, 1f)

                            val radiusNormX = 0.14f
                            val radiusNormY = 0.10f
                            val longPressRect = RectF(
                                (normX - radiusNormX).coerceAtLeast(0f),
                                (normY - radiusNormY).coerceAtLeast(0f),
                                (normX + radiusNormX).coerceAtMost(1f),
                                (normY + radiusNormY).coerceAtMost(1f)
                            )
                            triggerSelectionSearch(longPressRect)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            isDrawingCircle = true
                            touchPoints = listOf(startOffset)
                        },
                        onDrag = { change, _ ->
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
                                    triggerSelectionSearch(normRect)
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

            // 1. Draw glowing highlights & lasso trail on Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (!isTranslationActive) {
                    // Draw OCR bounding highlights
                    for (block in recognizedBlocks) {
                        val rect = block.normalizedBoundingBox
                        val left = rect.left * canvasWidth
                        val top = rect.top * canvasHeight
                        val right = rect.right * canvasWidth
                        val bottom = rect.bottom * canvasHeight
                        val w = max(right - left, 1f)
                        val h = max(bottom - top, 1f)

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
                }

                // Draw active selection bounding box if exists
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

                // Draw freeform glowing lasso / circle trail
                if (touchPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(touchPoints.first().x, touchPoints.first().y)
                        for (i in 1 until touchPoints.size) {
                            lineTo(touchPoints[i].x, touchPoints[i].y)
                        }
                    }

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

            // 2. In-Place Video Text Translation Badges (Samsung Galaxy Style)
            if (isTranslationActive) {
                val displayBlocks = if (translatedBlocks.isNotEmpty()) translatedBlocks else recognizedBlocks
                displayBlocks.forEach { block ->
                    val rect = block.normalizedBoundingBox
                    val leftPx = (rect.left * canvasWidth).toInt()
                    val topPx = (rect.top * canvasHeight).toInt()
                    val widthPx = max(((rect.right - rect.left) * canvasWidth).toInt(), 60)

                    val textToShow = block.translatedText ?: block.text

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (leftPx / LocalContext.current.resources.displayMetrics.density).dp,
                                y = (topPx / LocalContext.current.resources.displayMetrics.density).dp
                            )
                            .widthIn(min = 40.dp, max = 320.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xE6141416))
                            .border(1.dp, Color(0xFF4285F4).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable {
                                selectedBlockForDetails = block
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "文A",
                                color = Color(0xFF4285F4),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = textToShow,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Top Navigation & Tool Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isOcrLoading) "Detecting text..."
                        else if (isTranslating) "Translating video text..."
                        else "Circle object or select text",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. TRANSLATE VIDEO TEXT BUTTON (Samsung Style)
                FilledTonalButton(
                    onClick = {
                        if (isTranslationActive) {
                            isTranslationActive = false
                        } else {
                            triggerTranslation(targetLanguage)
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isTranslationActive) Color(0xFF4285F4) else Color(0xFF28282C).copy(alpha = 0.95f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translate Video Text",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTranslationActive) targetLanguage.uppercase() else "Translate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. GOOGLE MUSIC SEARCH BUTTON (♪)
                IconButton(
                    onClick = { showMusicSearchSheet = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF28282C).copy(alpha = 0.95f), CircleShape)
                        .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music & Sound Search",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3. Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Circle to Search",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Pop-up Card for Tapped Text Block (Original vs Translated)
        selectedBlockForDetails?.let { block ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 80.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1C1C20).copy(alpha = 0.98f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.5f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Video Text Translation",
                                    color = Color(0xFF4285F4),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { selectedBlockForDetails = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Original Text Box
                        Text(text = "Original:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = block.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Translated Text Box
                        val trans = block.translatedText
                        Text(text = "Translated (${targetLanguage.uppercase()}):", color = Color(0xFF4285F4), fontSize = 11.sp)
                        Text(
                            text = trans ?: "Tap 'Translate' above to generate translation",
                            color = if (trans != null) Color(0xFF81D4FA) else Color.LightGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val textToCopy = block.translatedText ?: block.text
                                    VisualLensEngine.copyToClipboard(context, textToCopy)
                                    selectedBlockForDetails = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4285F4),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (block.translatedText != null) "Copy Translated" else "Copy Text", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val query = block.translatedText ?: block.text
                                    triggerSelectionSearch(block.normalizedBoundingBox, query)
                                    selectedBlockForDetails = null
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF2D2D32),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Search on Google", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Quick Action Bar when sheet is collapsed (Samsung Galaxy Style)
        if (!showResultsSheet && !showMusicSearchSheet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF141416).copy(alpha = 0.96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. AI Overview / Circle to Search Trigger
                        Button(
                            onClick = { triggerSelectionSearch(activeSelectionNormRect) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4285F4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Circle to Search", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // 2. In-Video Translation Quick Button
                        FilledTonalButton(
                            onClick = {
                                if (isTranslationActive) {
                                    isTranslationActive = false
                                } else {
                                    triggerTranslation(targetLanguage)
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isTranslationActive) Color(0xFF7C4DFF) else Color(0xFF28282C),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = if (isTranslationActive) Color.White else Color(0xFFB388FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTranslationActive) "Translated (${targetLanguage.uppercase()})" else "Translate Video", fontSize = 13.sp)
                        }

                        // 3. Music Detection Quick Button (♪)
                        FilledTonalButton(
                            onClick = { showMusicSearchSheet = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2C2518),
                                contentColor = Color(0xFFFFB300)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Music Search", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // 4. Multi-Engine Launchers
                        VisualSearchEngine.values().filter { it != VisualSearchEngine.AI_OVERVIEW }.forEach { engine ->
                            FilledTonalButton(
                                onClick = {
                                    activeEngine = engine
                                    if (aiAnalysis == null && activeCroppedUri == null) {
                                        triggerSelectionSearch(activeSelectionNormRect)
                                    } else {
                                        showResultsSheet = true
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF26262A),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(engine.displayName, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // MUSIC DETECTION & SOUND SEARCH SHEET (♪)
        // ==========================================
        if (showMusicSearchSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMusicSearchSheet = false },
                containerColor = Color(0xFF141418),
                contentColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.5f),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Badge
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFFFFB300).copy(alpha = 0.4f), Color(0xFF1E1E24))
                                )
                            )
                            .border(2.dp, Color(0xFFFFB300), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Song & Music Search",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Identify music playing in video, hummed tunes, or singing",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Animated Equalizer Waveform Bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1D1D24))
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barCount = 14
                        for (i in 0 until barCount) {
                            val barTransition = rememberInfiniteTransition(label = "eq_bar_$i")
                            val barHeightRatio by barTransition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 0.95f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 350 + (i * 45) % 400,
                                        easing = FastOutSlowInEasing
                                    ),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bar_$i"
                            )

                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight(barHeightRatio)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFF4285F4), Color(0xFFFFB300))
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action 1: Google Sound Search & Hum to Search
                    Button(
                        onClick = {
                            VisualLensEngine.launchGoogleSoundSearch(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search with Google Sound Search", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action 2: Identify Song from Current Video Metadata & Soundtrack
                    if (!videoTitle.isNullOrBlank()) {
                        FilledTonalButton(
                            onClick = {
                                showMusicSearchSheet = false
                                val musicQuery = "$videoTitle song soundtrack"
                                searchQueryInput = musicQuery
                                activeEngine = VisualSearchEngine.GOOGLE_LENS
                                triggerSelectionSearch(null, musicQuery)
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF26262E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Find Video Soundtrack on Google", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action 3: Video Audio Playback Toggle (for letting Google hear background video audio)
                    FilledTonalButton(
                        onClick = {
                            isVideoAudioPlaying = !isVideoAudioPlaying
                            onPlayToggle?.invoke(isVideoAudioPlaying)
                            Toast.makeText(
                                context,
                                if (isVideoAudioPlaying) "Playing video audio for music detection..." else "Video audio paused",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isVideoAudioPlaying) Color(0xFF1B5E20) else Color(0xFF2A2A30),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideoAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isVideoAudioPlaying) Color(0xFF00E676) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isVideoAudioPlaying) "Video Audio Playing (Google can listen)" else "Play Video Audio Aloud",
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // ========================================================
        // IN-APP CIRCLE TO SEARCH SLIDING DRAWER (Samsung Style)
        // ========================================================
        if (showResultsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showResultsSheet = false },
                containerColor = Color(0xFF161618),
                contentColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.4f),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                ) {
                    // 1. Samsung "Ask anything..." Search Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cropped Object Thumbnail Badge
                        croppedBitmap?.let { bmp ->
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.5.dp, Color(0xFF4285F4), RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Cropped Object",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Search Input TextField
                        OutlinedTextField(
                            value = searchQueryInput,
                            onValueChange = { searchQueryInput = it },
                            placeholder = {
                                Text(
                                    text = "Ask anything about this image...",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            showMusicSearchSheet = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = "Music Detection",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            triggerSelectionSearch(activeSelectionNormRect, searchQueryInput)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search Query",
                                            tint = Color(0xFF4285F4)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                triggerSelectionSearch(activeSelectionNormRect, searchQueryInput)
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF242428),
                                unfocusedContainerColor = Color(0xFF242428),
                                focusedBorderColor = Color(0xFF4285F4),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        )
                    }

                    // 2. Engine Selector Tabs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VisualSearchEngine.values().forEach { engine ->
                            val isSelected = activeEngine == engine
                            FilterChip(
                                selected = isSelected,
                                onClick = { activeEngine = engine },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (engine == VisualSearchEngine.AI_OVERVIEW) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else Color(0xFF4285F4),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(engine.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4285F4),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF28282C),
                                    labelColor = Color.LightGray
                                ),
                                shape = RoundedCornerShape(18.dp)
                            )
                        }

                        // Direct Music Search Tab in Drawer
                        FilledTonalButton(
                            onClick = {
                                showMusicSearchSheet = true
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2C2416),
                                contentColor = Color(0xFFFFB300)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Song Search", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // 3. Tab Content View Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (isAnalyzingAi) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF4285F4))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Analyzing visual object...", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        } else if (activeEngine == VisualSearchEngine.AI_OVERVIEW) {
                            // AI Overview Card View
                            val analysis = aiAnalysis
                            if (analysis != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Quick Lens App Launcher Strip
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF1E2430),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CameraAlt,
                                                    contentDescription = null,
                                                    tint = Color(0xFF4285F4),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Column {
                                                    Text("Google Lens App", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text("Search exact image match", color = Color.Gray, fontSize = 11.sp)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    VisualLensEngine.launchGoogleLensApp(context, activeCroppedUri)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF4285F4),
                                                    contentColor = Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Open Lens", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    // AI Header Card
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF222226),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = Color(0xFF4285F4),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "AI Overview",
                                                        color = Color(0xFF4285F4),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF1E293B)
                                                ) {
                                                    Text(
                                                        text = analysis.category,
                                                        color = Color(0xFF90CAF9),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = analysis.title,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = analysis.overview,
                                                color = Color.LightGray,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )

                                            if (!analysis.extractedText.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF18181A)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "Video Text Detected:",
                                                                color = Color(0xFFFFD54F),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Row {
                                                                TextButton(
                                                                    onClick = {
                                                                        showResultsSheet = false
                                                                        triggerTranslation(targetLanguage)
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                                                ) {
                                                                    Icon(imageVector = Icons.Default.Translate, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(14.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Translate in Video", fontSize = 11.sp, color = Color(0xFF4285F4))
                                                                }
                                                                IconButton(
                                                                    onClick = {
                                                                        VisualLensEngine.copyToClipboard(context, analysis.extractedText)
                                                                    },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.ContentCopy,
                                                                        contentDescription = "Copy",
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = "\"${analysis.extractedText}\"",
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Related Multi-Engine Visual Matches
                                    Text(
                                        text = "Multi-Engine Visual Results",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    analysis.visualMatches.forEach { match ->
                                        Surface(
                                            onClick = {
                                                when (match.badge) {
                                                    "Google Lens" -> activeEngine = VisualSearchEngine.GOOGLE_LENS
                                                    "Lenso.ai" -> activeEngine = VisualSearchEngine.LENSO_AI
                                                    "Bing Visual" -> activeEngine = VisualSearchEngine.BING_VISUAL
                                                    "Yandex" -> activeEngine = VisualSearchEngine.YANDEX_IMAGES
                                                    "TinEye" -> activeEngine = VisualSearchEngine.TINEYE
                                                    else -> VisualLensEngine.openInBrowser(context, match.url)
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFF222226),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFF333338)
                                                        ) {
                                                            Text(
                                                                text = match.badge,
                                                                color = Color(0xFF81D4FA),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = match.sourceDomain,
                                                            color = Color.Gray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = match.title,
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = match.subtitle,
                                                        color = Color.LightGray,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = "View",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Interactive In-App WebView Result Slider (Google Lens, Lenso, Bing, Yandex, TinEye)
                            val targetUrl = remember(activeEngine, aiAnalysis?.query, searchQueryInput, publicImageUrl) {
                                val q = aiAnalysis?.query ?: searchQueryInput.ifBlank { videoTitle ?: "visual search" }
                                VisualLensEngine.buildSearchUrl(activeEngine, q, activeCroppedUri, publicImageUrl)
                            }

                            var webViewInstance by remember { mutableStateOf<WebView?>(null) }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E1E22))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isUploadingImage) {
                                             CircularProgressIndicator(
                                                 color = Color(0xFF4285F4),
                                                 strokeWidth = 2.dp,
                                                 modifier = Modifier.size(12.dp)
                                             )
                                             Text(
                                                 text = "Uploading visual crop...",
                                                 color = Color(0xFF81D4FA),
                                                 fontSize = 11.sp,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                        } else {
                                             Text(
                                                 text = if (publicImageUrl != null) "${activeEngine.displayName} (Image Ready)" else "Browsing ${activeEngine.displayName}...",
                                                 color = if (publicImageUrl != null) Color(0xFF81D4FA) else Color.Gray,
                                                 fontSize = 11.sp,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (!publicImageUrl.isNullOrBlank()) {
                                            TextButton(
                                                onClick = {
                                                    VisualLensEngine.copyToClipboard(context, publicImageUrl ?: "", showToast = true)
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Link,
                                                    contentDescription = null,
                                                    tint = Color(0xFF81D4FA),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Copy Link", fontSize = 11.sp, color = Color(0xFF81D4FA))
                                            }
                                        }

                                        if (activeEngine == VisualSearchEngine.GOOGLE_LENS && activeCroppedUri != null) {
                                            TextButton(
                                                onClick = { VisualLensEngine.launchGoogleLensApp(context, activeCroppedUri) },
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CameraAlt,
                                                    contentDescription = null,
                                                    tint = Color(0xFF4285F4),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Lens App", fontSize = 11.sp, color = Color(0xFF4285F4))
                                            }
                                        }

                                        IconButton(
                                            onClick = { webViewInstance?.reload() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reload",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        TextButton(
                                            onClick = { VisualLensEngine.openInBrowser(context, targetUrl) },
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = Color(0xFF4285F4),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("External", fontSize = 11.sp, color = Color(0xFF4285F4))
                                        }
                                    }
                                }

                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.databaseEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            settings.allowFileAccess = true
                                            settings.allowContentAccess = true
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            settings.cacheMode = WebSettings.LOAD_DEFAULT

                                            val cookieManager = CookieManager.getInstance()
                                            cookieManager.setAcceptCookie(true)
                                            cookieManager.setAcceptThirdPartyCookies(this, true)

                                            webChromeClient = WebChromeClient()
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                    val url = request?.url?.toString() ?: return false
                                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                                        return false
                                                    }
                                                    return try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                        ctx.startActivity(intent)
                                                        true
                                                    } catch (e: Exception) {
                                                        true
                                                    }
                                                }
                                            }
                                            webViewInstance = this
                                        }
                                    },
                                    update = { webView ->
                                        webViewInstance = webView
                                        if (targetUrl.isNotBlank() && webView.url != targetUrl && webView.originalUrl != targetUrl) {
                                            webView.loadUrl(targetUrl)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
