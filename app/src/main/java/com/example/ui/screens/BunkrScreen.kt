package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.bunkr.model.BunkrAlbum
import com.example.bunkr.model.BunkrFile
import com.example.bunkr.repository.BunkrRepository
import com.example.model.PlayableStreamOption
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunkrScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 90.dp,
    bottomPadding: Dp = 100.dp
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bunkrRepository = remember { BunkrRepository.getInstance(context) }

    val albums by bunkrRepository.allAlbums.collectAsState(initial = emptyList())
    val files by bunkrRepository.allFiles.collectAsState(initial = emptyList())
    val isScanning by bunkrRepository.isScanning.collectAsState()
    val scanReport by bunkrRepository.scanReport.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Albums, 1: Videos
    var selectedAlbum by remember { mutableStateOf<BunkrAlbum?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var addUrlInput by remember { mutableStateOf("") }

    BackHandler(enabled = selectedAlbum != null) {
        selectedAlbum = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedAlbum != null) selectedAlbum!!.title else "Bunkr Albums & Direct CDN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val totalVideos = if (selectedAlbum != null) {
                            files.count { it.albumId == selectedAlbum!!.albumId }
                        } else files.size
                        Text(
                            text = if (selectedAlbum != null) "$totalVideos videos in album" else "${albums.size} Albums • ${files.size} Total Videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedAlbum != null) {
                            selectedAlbum = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                Toast.makeText(context, "Rescanning Bunkr albums...", Toast.LENGTH_SHORT).show()
                                bunkrRepository.refreshAlbums(
                                    if (selectedAlbum != null) listOf(selectedAlbum!!.albumId) else emptyList()
                                )
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }

                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Albums")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedAlbum != null) {
                val albumFiles = files.filter { it.albumId == selectedAlbum!!.albumId && it.isAvailable }
                if (albumFiles.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            viewModel.playVideo(albumFiles.first().sourceUrl, "bunkr")
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                        text = { Text("Play All (${albumFiles.size})") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        modifier = modifier.padding(top = topPadding, bottom = bottomPadding)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedAlbum != null) {
                // Album Detail Screen
                val currentAlbumFiles = files.filter { it.albumId == selectedAlbum!!.albumId }
                if (currentAlbumFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No files in this album", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                coroutineScope.launch {
                                    bunkrRepository.refreshAlbums(listOf(selectedAlbum!!.albumId))
                                }
                            }) {
                                Text("Rescan Album")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentAlbumFiles, key = { it.fileId }) { file ->
                            BunkrFileRow(
                                file = file,
                                albumTitle = selectedAlbum!!.title,
                                onPlay = {
                                    viewModel.playVideo(file.sourceUrl, "bunkr")
                                },
                                onPlayAllFromHere = {
                                    viewModel.playVideo(file.sourceUrl, "bunkr")
                                },
                                onDownload = {
                                    coroutineScope.launch {
                                        try {
                                            val stream = bunkrRepository.resolveStreamForFile(file)
                                            viewModel.startDownload(
                                                videoId = "bunkr_${file.fileId}",
                                                title = file.title,
                                                channelName = "Bunkr • ${selectedAlbum!!.title}",
                                                thumbnailUrl = file.thumbnailUrl,
                                                qualityLabel = file.resolution.ifBlank { "HD" },
                                                streamOption = PlayableStreamOption(
                                                    qualityLabel = "HD",
                                                    format = "mp4",
                                                    isMuxed = true,
                                                    videoUrl = stream.streamUrl
                                                )
                                            )
                                            Toast.makeText(context, "Download queued: ${file.title}", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to resolve stream for download: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onRetry = {
                                    coroutineScope.launch {
                                        try {
                                            bunkrRepository.resolveStreamForFile(file)
                                            Toast.makeText(context, "Resolved stream successfully!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Retry failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        bunkrRepository.deleteFile(file.fileId)
                                    }
                                },
                                onOpenOriginal = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(file.sourceUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Main Bunkr Overview Tabs (Albums | Videos)
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Albums (${albums.size})") },
                            icon = { Icon(Icons.Outlined.Folder, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("All Videos (${files.size})") },
                            icon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) }
                        )
                    }

                    if (selectedTab == 0) {
                        // Albums Tab
                        if (albums.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No Bunkr Albums Added Yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Paste album or file URLs to stream or download instantly.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { showAddDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Bunkr URLs")
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(albums, key = { it.albumId }) { album ->
                                    val albumFileCount = files.count { it.albumId == album.albumId }
                                    val coverThumb = files.firstOrNull { it.albumId == album.albumId && !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl

                                    BunkrAlbumCard(
                                        album = album,
                                        fileCount = albumFileCount,
                                        coverThumbnail = coverThumb,
                                        onClick = { selectedAlbum = album },
                                        onPlayAll = {
                                            val albumFiles = files.filter { it.albumId == album.albumId && it.isAvailable }
                                            if (albumFiles.isNotEmpty()) {
                                                viewModel.playVideo(albumFiles.first().sourceUrl, "bunkr")
                                            } else {
                                                Toast.makeText(context, "No playable videos in album", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDelete = {
                                            coroutineScope.launch {
                                                bunkrRepository.deleteAlbum(album.albumId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // All Videos Tab
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search Bunkr videos...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            val filteredFiles = remember(files, searchQuery) {
                                if (searchQuery.isBlank()) files else {
                                    files.filter { it.title.contains(searchQuery, ignoreCase = true) || it.fileId.contains(searchQuery, ignoreCase = true) }
                                }
                            }

                            if (filteredFiles.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No matching videos found")
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredFiles, key = { it.fileId }) { file ->
                                        val parentAlbumTitle = albums.firstOrNull { it.albumId == file.albumId }?.title ?: "Bunkr"
                                        BunkrFileRow(
                                            file = file,
                                            albumTitle = parentAlbumTitle,
                                            onPlay = {
                                                viewModel.playVideo(file.sourceUrl, "bunkr")
                                            },
                                            onPlayAllFromHere = {
                                                viewModel.playVideo(file.sourceUrl, "bunkr")
                                            },
                                            onDownload = {
                                                coroutineScope.launch {
                                                    try {
                                                        val stream = bunkrRepository.resolveStreamForFile(file)
                                                        viewModel.startDownload(
                                                            videoId = "bunkr_${file.fileId}",
                                                            title = file.title,
                                                            channelName = "Bunkr • $parentAlbumTitle",
                                                            thumbnailUrl = file.thumbnailUrl,
                                                            qualityLabel = file.resolution.ifBlank { "HD" },
                                                            streamOption = PlayableStreamOption(
                                                                qualityLabel = "HD",
                                                                format = "mp4",
                                                                isMuxed = true,
                                                                videoUrl = stream.streamUrl
                                                            )
                                                        )
                                                        Toast.makeText(context, "Download queued!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            onRetry = {
                                                coroutineScope.launch {
                                                    try {
                                                        bunkrRepository.resolveStreamForFile(file)
                                                        Toast.makeText(context, "Resolved stream!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Retry failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            onDelete = {
                                                coroutineScope.launch { bunkrRepository.deleteFile(file.fileId) }
                                            },
                                            onOpenOriginal = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(file.sourceUrl))
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Albums Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Bunkr Albums or Files") },
            text = {
                Column {
                    Text(
                        "Paste one or more Bunkr album (/a/...) or file (/f/...) URLs below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addUrlInput,
                        onValueChange = { addUrlInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("https://bunkr.cr/a/cmFzH1Cf\nhttps://bunkr.cr/f/DJOH6o7Gg5UeN") },
                        maxLines = 10,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    addUrlInput = if (addUrlInput.isBlank()) text else "$addUrlInput\n$text"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = addUrlInput.trim()
                        if (input.isNotBlank()) {
                            showAddDialog = false
                            addUrlInput = ""
                            coroutineScope.launch {
                                Toast.makeText(context, "Processing Bunkr URLs...", Toast.LENGTH_SHORT).show()
                                val report = bunkrRepository.importUrls(input)
                                if (report.errors.isEmpty()) {
                                    Toast.makeText(context, "Added ${report.totalAlbumsProcessed} albums & ${report.totalItemsDiscovered} videos!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Processed with ${report.errors.size} errors. ${report.totalItemsDiscovered} videos added.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Add & Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BunkrAlbumCard(
    album: BunkrAlbum,
    fileCount: Int,
    coverThumbnail: String?,
    onClick: () -> Unit,
    onPlayAll: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!coverThumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverThumbnail)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.FolderSpecial,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = "$fileCount videos",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = album.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayAll, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete Album", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun BunkrFileRow(
    file: BunkrFile,
    albumTitle: String,
    onPlay: () -> Unit,
    onPlayAllFromHere: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpenOriginal: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!file.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (!file.isAvailable) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Warning, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = file.fileSize.ifBlank { "HD Media" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (!file.isAvailable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unavailable",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Video") },
                        onClick = {
                            showMenu = false
                            onPlay()
                        },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Play All From Here") },
                        onClick = {
                            showMenu = false
                            onPlayAllFromHere()
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = {
                            showMenu = false
                            onDownload()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Retry / Resolve Stream") },
                        onClick = {
                            showMenu = false
                            onRetry()
                        },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Open Bunkr URL") },
                        onClick = {
                            showMenu = false
                            onOpenOriginal()
                        },
                        leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Item") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}
