package com.example.ui.player.lens

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturing by remember { mutableStateOf(true) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var recognizedBlocks by remember { mutableStateOf<List<RecognizedTextBlock>>(emptyList()) }

    // Touch and Circle-to-Search state
    var touchPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawingCircle by remember { mutableStateOf(false) }
    var activeSelectionNormRect by remember { mutableStateOf<RectF?>(null) }
    var selectedText by remember { mutableStateOf<String?>(null) }
    var activeCroppedUri by remember { mutableStateOf<Uri?>(null) }

    // Sliding Results Sheet state
    var showResultsSheet by remember { mutableStateOf(false) }
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

            // Generate AI Overview
            isAnalyzingAi = true
            val analysis = VisualLensEngine.generateAiOverview(context, matchedText, customQuery ?: searchQueryInput)
            aiAnalysis = analysis
            isAnalyzingAi = false
            showResultsSheet = true
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
                .pointerInput(recognizedBlocks, capturedBmp) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val normX = (tapOffset.x / size.width).coerceIn(0f, 1f)
                            val normY = (tapOffset.y / size.height).coerceIn(0f, 1f)

                            val tappedBlock = recognizedBlocks.firstOrNull { block ->
                                block.normalizedBoundingBox.contains(normX, normY)
                            }

                            if (tappedBlock != null) {
                                VisualLensEngine.copyToClipboard(context, tappedBlock.text)
                                selectedText = tappedBlock.text
                                triggerSelectionSearch(tappedBlock.normalizedBoundingBox)
                            } else {
                                touchPoints = emptyList()
                                activeSelectionNormRect = null
                                selectedText = null
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
                        text = if (isOcrLoading) "Detecting text..." else "Circle image object to search",
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

        // Bottom Quick Action Bar when sheet is collapsed
        if (!showResultsSheet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                        // AI Overview Trigger
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

                        // Direct Engine Launchers opening the in-app slider
                        VisualSearchEngine.values().filter { it != VisualSearchEngine.AI_OVERVIEW }.forEach { engine ->
                            FilledTonalButton(
                                onClick = {
                                    activeEngine = engine
                                    triggerSelectionSearch(activeSelectionNormRect)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF2C2C2C),
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

        // IN-APP CIRCLE TO SEARCH SLIDING BOTTOM SHEET DRAWER (Samsung Style)
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
                        .fillMaxHeight(0.85f)
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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f))

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
                                    Text("Analyzing circled visual area...", color = Color.Gray, fontSize = 13.sp)
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
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Detected Text: \"${analysis.extractedText}\"",
                                                            color = Color(0xFFFFD54F),
                                                            fontSize = 12.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
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
                                            }
                                        }
                                    }

                                    // Related Visual Matches Cards
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
                            // Interactive In-App WebView Result Slider (Google Lens, Lenso, Bing, Yandex)
                            val targetUrl = remember(activeEngine, aiAnalysis?.query, activeCroppedUri) {
                                VisualLensEngine.buildSearchUrl(activeEngine, aiAnalysis?.query ?: searchQueryInput, activeCroppedUri)
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E1E22))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Browsing ${activeEngine.displayName} results...",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { VisualLensEngine.openInBrowser(context, targetUrl) },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            tint = Color(0xFF4285F4),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("External Browser", fontSize = 11.sp, color = Color(0xFF4285F4))
                                    }
                                }

                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                    return false
                                                }
                                            }
                                        }
                                    },
                                    update = { webView ->
                                        webView.loadUrl(targetUrl)
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

