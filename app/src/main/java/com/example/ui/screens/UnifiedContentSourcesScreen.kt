package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

enum class SourcePillCategory(val label: String, val icon: ImageVector, val color: Color) {
    ALL("All Sources", Icons.Outlined.GridView, Color(0xFF3F51B5)),
    NORMAL("Normal", Icons.Outlined.PlayCircle, Color(0xFF2196F3)),
    ADULT("18+ Adult", Icons.Default.Explicit, Color(0xFFE91E63)),
    VEGA("Vega (52+)", Icons.Outlined.Movie, Color(0xFFFF9800)),
    VIDSRC("VidSrc", Icons.Outlined.VideoLibrary, Color(0xFF9C27B0)),
    DECRYPTOR("Decryptor", Icons.Outlined.LockOpen, Color(0xFF00BCD4)),
    TMDB("TMDB Embeds", Icons.Outlined.MovieCreation, Color(0xFF4CAF50)),
    TORRENT("Torrents", Icons.Outlined.Radar, Color(0xFF673AB7)),
    CLOUD("Cloud & Bunkr", Icons.Outlined.Cloud, Color(0xFF009688))
}

data class UnifiedSourceItem(
    val id: String,
    val name: String,
    val category: SourcePillCategory,
    val description: String,
    val isEnabled: Boolean,
    val isInstalled: Boolean = true,
    val qualityTag: String = "1080p FHD",
    val isExtension: Boolean = false,
    val icon: ImageVector? = null,
    val healthStatus: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedContentSourcesScreen(
    viewModel: MainViewModel,
    onNavigateToCategory: ((SettingsCategory) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPill by remember { mutableStateOf(SourcePillCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var customSourceType by remember { mutableStateOf(SourcePillCategory.VEGA) }
    var customSourceUrl by remember { mutableStateOf("") }
    var customSourceName by remember { mutableStateOf("") }

    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val enabledProviderIds by viewModel.enabledProviderIds.collectAsState()

    val isVegaMasterEnabled by viewModel.isVegaMasterEnabled.collectAsState()
    val installedVega by viewModel.installedVegaProviders.collectAsState()
    val availableVega by viewModel.availableVegaProviders.collectAsState()
    val vegaHealthMap by viewModel.providerHealthMap.collectAsState()
    val isTestingVegaHealth by viewModel.isTestingVegaHealth.collectAsState()

    val isVidSrcMasterEnabled by viewModel.isVidSrcMasterEnabled.collectAsState()
    val installedVidSrc by viewModel.installedVidSrcProviders.collectAsState()
    val availableVidSrc = viewModel.availableVidSrcProviders
    val vidSrcHealthMap by viewModel.vidSrcHealthMap.collectAsState()

    val isDecryptorMasterEnabled by viewModel.isDecryptorMasterEnabled.collectAsState()
    val installedDecryptor by viewModel.installedDecryptorProviders.collectAsState()
    val availableDecryptor = viewModel.availableDecryptorProviders
    val decryptorHealthMap by viewModel.decryptorHealthMap.collectAsState()

    val isTMDBMasterEnabled by viewModel.isTMDBMasterEnabled.collectAsState()
    val installedTMDB by viewModel.installedTMDBProviders.collectAsState()
    val availableTMDB = viewModel.availableTMDBProviders
    val tmdbHealthMap by viewModel.tmdbHealthMap.collectAsState()

    // 1. Normal Mainstream Providers
    val normalProviders = remember(availableProviders, enabledProviderIds) {
        val adultIds = setOf(
            "xnxx", "hellporno", "stripchat", "chaturbate", "sextb", "supjav",
            "123av", "javtiful", "jav_all", "pornhub", "xvideos", "cam4", "cammodels",
            "noodlemagazine", "thisvid", "tnaflix", "spankbang", "playvid", "txxx",
            "eporner", "hanime1", "redtube", "xhamster", "beeg", "4tube", "rule34video", "youporn"
        )
        val defaultNormal = listOf(
            UnifiedSourceItem("youtube", "YouTube", SourcePillCategory.NORMAL, "Official YouTube Streams & Shorts via InnerTube API", enabledProviderIds.contains("youtube"), qualityTag = "4K / 1080p", icon = Icons.Outlined.PlayArrow),
            UnifiedSourceItem("tencent", "Tencent Video (v.qq.com)", SourcePillCategory.NORMAL, "Official Chinese drama, anime, movies & VIP series with multi-bitrate HLS", enabledProviderIds.contains("tencent"), qualityTag = "1080p FHD", icon = Icons.Outlined.LiveTv),
            UnifiedSourceItem("dailymotion", "Dailymotion", SourcePillCategory.NORMAL, "Global news, trending videos & creator channels", enabledProviderIds.contains("dailymotion"), qualityTag = "1080p HD", icon = Icons.Outlined.VideoLibrary),
            UnifiedSourceItem("bilibili", "Bilibili", SourcePillCategory.NORMAL, "Anime, gaming, creator streams & Danmaku community", enabledProviderIds.contains("bilibili"), qualityTag = "1080p 60FPS", icon = Icons.Outlined.Tv),
            UnifiedSourceItem("animepahe", "AnimePahe", SourcePillCategory.NORMAL, "High efficiency subbed & dubbed anime episodes", enabledProviderIds.contains("animepahe"), qualityTag = "720p/1080p", icon = Icons.Outlined.Animation),
            UnifiedSourceItem("gogoanime", "GogoAnime", SourcePillCategory.NORMAL, "Fast anime catalog with multiple video CDN mirrors", enabledProviderIds.contains("gogoanime"), qualityTag = "1080p Stream", icon = Icons.Outlined.Animation),
            UnifiedSourceItem("vk", "VK Video", SourcePillCategory.NORMAL, "VKontakte social media & community uploaded videos", enabledProviderIds.contains("vk"), qualityTag = "1080p HD", icon = Icons.Outlined.Public),
            UnifiedSourceItem("archive", "Internet Archive", SourcePillCategory.NORMAL, "Public domain movies, educational broadcasts & archives", enabledProviderIds.contains("archive"), qualityTag = "Direct MP4", icon = Icons.Outlined.AccountBalance),
            UnifiedSourceItem("w3schools", "Sample Videos", SourcePillCategory.NORMAL, "Test video feeds, Big Buck Bunny & Tears of Steel", enabledProviderIds.contains("w3schools"), qualityTag = "1080p Test", icon = Icons.Outlined.Science)
        )
        defaultNormal
    }

    // 2. Adult 18+ Providers
    val adultProviders = remember(enabledProviderIds) {
        listOf(
            UnifiedSourceItem("supjav", "SupJav (FHD Direct & Mirrors)", SourcePillCategory.ADULT, "SupJav Asian & Japanese adult video catalog with TVLogy & StreamWish 1080p HLS", enabledProviderIds.contains("supjav"), qualityTag = "1080p FHD HLS", icon = Icons.Default.Explicit),
            UnifiedSourceItem("xnxx", "XNXX", SourcePillCategory.ADULT, "High definition adult videos & trending streams", enabledProviderIds.contains("xnxx"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("hellporno", "HellPorno", SourcePillCategory.ADULT, "Fast CDN streaming adult video releases", enabledProviderIds.contains("hellporno"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("stripchat", "Stripchat", SourcePillCategory.ADULT, "Live interactive webcam streams & model rooms", enabledProviderIds.contains("stripchat"), qualityTag = "Live HLS", icon = Icons.Default.Explicit),
            UnifiedSourceItem("chaturbate", "Chaturbate", SourcePillCategory.ADULT, "Live adult cam community broadcasts", enabledProviderIds.contains("chaturbate"), qualityTag = "Live HLS", icon = Icons.Default.Explicit),
            UnifiedSourceItem("sextb", "SEXТB (StreamTB)", SourcePillCategory.ADULT, "Asian JAV & Western adult video master streams", enabledProviderIds.contains("sextb"), qualityTag = "1080p FHD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("123av", "123AV (JAVPlayer)", SourcePillCategory.ADULT, "Japanese adult video catalog & direct CDN embeds", enabledProviderIds.contains("123av"), qualityTag = "1080p HLS", icon = Icons.Default.Explicit),
            UnifiedSourceItem("javtiful", "Javtiful", SourcePillCategory.ADULT, "Fast-stream JAV catalog and mobile optimized embeds", enabledProviderIds.contains("javtiful"), qualityTag = "1080p Fast", icon = Icons.Default.Explicit),
            UnifiedSourceItem("jav_all", "All JAV Sources", SourcePillCategory.ADULT, "Unified multi-extractor JAV catalog aggregator", enabledProviderIds.contains("jav_all"), qualityTag = "Multi-Source", icon = Icons.Default.Explicit),
            UnifiedSourceItem("pornhub", "Pornhub", SourcePillCategory.ADULT, "Official Pornhub HLS master video streams", enabledProviderIds.contains("pornhub"), qualityTag = "1080p HLS", icon = Icons.Default.Explicit),
            UnifiedSourceItem("xvideos", "XVideos", SourcePillCategory.ADULT, "XVideos tube streams with multi-quality resolution", enabledProviderIds.contains("xvideos"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("cam4", "CAM4 Live", SourcePillCategory.ADULT, "Live model video feeds & public cams", enabledProviderIds.contains("cam4"), qualityTag = "Live HLS", icon = Icons.Default.Explicit),
            UnifiedSourceItem("cammodels", "CamModels Live", SourcePillCategory.ADULT, "BongaCams & CamModels live webcams", enabledProviderIds.contains("cammodels"), qualityTag = "Live HLS", icon = Icons.Default.Explicit),
            UnifiedSourceItem("noodlemagazine", "NoodleMagazine", SourcePillCategory.ADULT, "Fast video search engine and MP4/HLS streams", enabledProviderIds.contains("noodlemagazine"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("thisvid", "ThisVid", SourcePillCategory.ADULT, "Amateur and independent video uploads", enabledProviderIds.contains("thisvid"), qualityTag = "720p/1080p", icon = Icons.Default.Explicit),
            UnifiedSourceItem("tnaflix", "TNAFlix", SourcePillCategory.ADULT, "TNAFlix video network and CDN streams", enabledProviderIds.contains("tnaflix"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("spankbang", "SpankBang", SourcePillCategory.ADULT, "SpankBang high-bitrate video streams", enabledProviderIds.contains("spankbang"), qualityTag = "1080p/4K", icon = Icons.Default.Explicit),
            UnifiedSourceItem("playvid", "Playvid", SourcePillCategory.ADULT, "Playvids direct MP4 video streaming", enabledProviderIds.contains("playvid"), qualityTag = "720p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("txxx", "TXXX", SourcePillCategory.ADULT, "TXXX video tube and multi-resolution CDN", enabledProviderIds.contains("txxx"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("eporner", "Eporner", SourcePillCategory.ADULT, "4K Ultra HD & 1080p video player streams", enabledProviderIds.contains("eporner"), qualityTag = "4K / 1080p", icon = Icons.Default.Explicit),
            UnifiedSourceItem("hanime1", "Hanime1", SourcePillCategory.ADULT, "Anime adult animation catalog & series", enabledProviderIds.contains("hanime1"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("redtube", "RedTube", SourcePillCategory.ADULT, "RedTube official streaming player", enabledProviderIds.contains("redtube"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("xhamster", "XHamster", SourcePillCategory.ADULT, "XHamster high-speed video network", enabledProviderIds.contains("xhamster"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("beeg", "Beeg", SourcePillCategory.ADULT, "Clean modern design & direct MP4 streams", enabledProviderIds.contains("beeg"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("4tube", "4tube", SourcePillCategory.ADULT, "4tube & Fivetube video network", enabledProviderIds.contains("4tube"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("rule34video", "Rule34Video", SourcePillCategory.ADULT, "Animated 3D & 2D pop-culture adult animations", enabledProviderIds.contains("rule34video"), qualityTag = "1080p HD", icon = Icons.Default.Explicit),
            UnifiedSourceItem("youporn", "YouPorn", SourcePillCategory.ADULT, "YouPorn video streaming archive", enabledProviderIds.contains("youporn"), qualityTag = "1080p HD", icon = Icons.Default.Explicit)
        )
    }

    // 3. Vega Providers
    val vegaItems = remember(installedVega, availableVega, vegaHealthMap, isVegaMasterEnabled) {
        val installedMap = installedVega.associateBy { it.id.lowercase() }
        val allIds = (availableVega + installedVega.map { it.id }).distinct()
        allIds.map { provId ->
            val cleanId = provId.trim().lowercase()
            val inst = installedMap[cleanId]
            val isInst = inst != null
            val isEn = inst?.isEnabled ?: true
            val displayName = inst?.name ?: com.example.vega.VegaProviderClient.formatProviderDisplayName(cleanId)
            val mirrorUrl = com.example.vega.VegaProviderRegistry.getBaseUrl(cleanId)
            val desc = if (mirrorUrl.isNotBlank()) "Host: $mirrorUrl • Direct links" else "Built-in Vega native scraper & direct stream engine"
            UnifiedSourceItem(
                id = cleanId,
                name = displayName,
                category = SourcePillCategory.VEGA,
                description = desc,
                isEnabled = isEn && isInst && isVegaMasterEnabled,
                isInstalled = isInst,
                qualityTag = "Vega Extension",
                isExtension = true,
                icon = Icons.Outlined.Movie,
                healthStatus = vegaHealthMap[cleanId]
            )
        }
    }

    // 4. VidSrc Providers
    val vidSrcItems = remember(installedVidSrc, vidSrcHealthMap, isVidSrcMasterEnabled) {
        val installedMap = installedVidSrc.associateBy { it.id }
        availableVidSrc.map { prov ->
            val inst = installedMap[prov.id]
            val isInst = inst != null
            val isEn = inst?.isEnabled ?: true
            UnifiedSourceItem(
                id = prov.id,
                name = prov.name,
                category = SourcePillCategory.VIDSRC,
                description = prov.description,
                isEnabled = isEn && isInst && isVidSrcMasterEnabled,
                isInstalled = isInst,
                qualityTag = "Cloud Mirror",
                isExtension = true,
                icon = Icons.Outlined.VideoLibrary,
                healthStatus = vidSrcHealthMap[prov.id]
            )
        }
    }

    // 5. Decryptor Providers
    val decryptorItems = remember(installedDecryptor, decryptorHealthMap, isDecryptorMasterEnabled) {
        val installedMap = installedDecryptor.associateBy { it.id }
        availableDecryptor.map { prov ->
            val inst = installedMap[prov.id]
            val isInst = inst != null
            val isEn = inst?.isEnabled ?: true
            UnifiedSourceItem(
                id = prov.id,
                name = prov.name,
                category = SourcePillCategory.DECRYPTOR,
                description = prov.description,
                isEnabled = isEn && isInst && isDecryptorMasterEnabled,
                isInstalled = isInst,
                qualityTag = "HLS Decrypted",
                isExtension = true,
                icon = Icons.Outlined.LockOpen,
                healthStatus = decryptorHealthMap[prov.id]
            )
        }
    }

    // 6. TMDB Providers
    val tmdbItems = remember(installedTMDB, tmdbHealthMap, isTMDBMasterEnabled) {
        val installedMap = installedTMDB.associateBy { it.id }
        availableTMDB.map { prov ->
            val inst = installedMap[prov.id]
            val isInst = inst != null
            val isEn = inst?.isEnabled ?: true
            UnifiedSourceItem(
                id = prov.id,
                name = prov.name,
                category = SourcePillCategory.TMDB,
                description = prov.description,
                isEnabled = isEn && isInst && isTMDBMasterEnabled,
                isInstalled = isInst,
                qualityTag = "TMDB VIP",
                isExtension = true,
                icon = Icons.Outlined.MovieCreation,
                healthStatus = tmdbHealthMap[prov.id]
            )
        }
    }

    // 7. Torrent & Indexer Providers
    val torrentItems = remember {
        listOf(
            UnifiedSourceItem("prowlarr_v11", "Prowlarr V11 Engine", SourcePillCategory.TORRENT, "Automated YAML indexer proxy, dynamic tracker health testing & sync", true, qualityTag = "YAML Indexer", icon = Icons.Outlined.Radar),
            UnifiedSourceItem("torrentio", "Torrentio Debrid & P2P", SourcePillCategory.TORRENT, "Multi-tracker torrent aggregator with Real-Debrid & AllDebrid support", true, qualityTag = "P2P / Debrid", icon = Icons.Outlined.Download),
            UnifiedSourceItem("1337x", "1337x Movies & Series", SourcePillCategory.TORRENT, "Verified community torrent releases with seeds and high health", true, qualityTag = "4K / 1080p Torrent", icon = Icons.Outlined.Hub),
            UnifiedSourceItem("yts", "YTS / YIFY Movies", SourcePillCategory.TORRENT, "Small file size 720p, 1080p and 4K movie encodes", true, qualityTag = "1080p/4K YIFY", icon = Icons.Outlined.Movie),
            UnifiedSourceItem("torrentgalaxy", "TorrentGalaxy", SourcePillCategory.TORRENT, "High-speed torrent releases with metadata, posters & previews", true, qualityTag = "Direct Magnet", icon = Icons.Outlined.CloudDownload)
        )
    }

    // 8. Cloud & Bunkr Providers
    val cloudItems = remember {
        listOf(
            UnifiedSourceItem("telegram", "Telegram Cloud Channels", SourcePillCategory.CLOUD, "Direct streaming from connected public channels & saved files", true, qualityTag = "Cloud Stream", icon = Icons.Outlined.Send),
            UnifiedSourceItem("mega", "MEGA Cloud Storage", SourcePillCategory.CLOUD, "Stream videos directly from shared mega.nz folders & files", true, qualityTag = "Direct Cloud", icon = Icons.Outlined.Cloud),
            UnifiedSourceItem("bunkr", "Bunkr Albums & Direct CDN", SourcePillCategory.CLOUD, "Auto-extract high bitrate videos from bunkr.cr / bunkr.ac / bunkr.ws albums", true, qualityTag = "Bunkr Direct", icon = Icons.Outlined.CloudDownload)
        )
    }

    // Aggregate all items based on selected pill & search query
    val allItemsList = remember(normalProviders, adultProviders, vegaItems, vidSrcItems, decryptorItems, tmdbItems, torrentItems, cloudItems) {
        normalProviders + adultProviders + vegaItems + vidSrcItems + decryptorItems + tmdbItems + torrentItems + cloudItems
    }

    val filteredItems = remember(selectedPill, searchQuery, allItemsList) {
        val byPill = when (selectedPill) {
            SourcePillCategory.ALL -> allItemsList
            SourcePillCategory.NORMAL -> normalProviders
            SourcePillCategory.ADULT -> adultProviders
            SourcePillCategory.VEGA -> vegaItems
            SourcePillCategory.VIDSRC -> vidSrcItems
            SourcePillCategory.DECRYPTOR -> decryptorItems
            SourcePillCategory.TMDB -> tmdbItems
            SourcePillCategory.TORRENT -> torrentItems
            SourcePillCategory.CLOUD -> cloudItems
        }

        if (searchQuery.isBlank()) {
            byPill
        } else {
            val q = searchQuery.trim().lowercase()
            byPill.filter {
                it.name.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.id.lowercase().contains(q) ||
                        it.qualityTag.lowercase().contains(q)
            }
        }
    }

    val totalActiveCount = remember(allItemsList) {
        allItemsList.count { it.isEnabled && it.isInstalled }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 64.dp)
    ) {
        // TOP SEARCH & STATS HERO BANNER
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Content Sources & Providers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$totalActiveCount active / ${allItemsList.size} total sources across all engines",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "⚡ UNIFIED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search input field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search sources (e.g., SupJav, YouTube, Vega, VidSrc...)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }

        // CATEGORY FILTER PILLS ROW
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SourcePillCategory.values()) { pill ->
                    val isSelected = selectedPill == pill
                    val count = when (pill) {
                        SourcePillCategory.ALL -> allItemsList.size
                        SourcePillCategory.NORMAL -> normalProviders.size
                        SourcePillCategory.ADULT -> adultProviders.size
                        SourcePillCategory.VEGA -> vegaItems.size
                        SourcePillCategory.VIDSRC -> vidSrcItems.size
                        SourcePillCategory.DECRYPTOR -> decryptorItems.size
                        SourcePillCategory.TMDB -> tmdbItems.size
                        SourcePillCategory.TORRENT -> torrentItems.size
                        SourcePillCategory.CLOUD -> cloudItems.size
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPill = pill },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pill.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = pill.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else pill.color
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // ACTION BUTTONS (ADD REPO, TEST HEALTH, BATCH ENABLE/DISABLE)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showAddSourceDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Source / Repo", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = {
                        when (selectedPill) {
                            SourcePillCategory.VEGA -> viewModel.testVegaProvidersHealth()
                            SourcePillCategory.VIDSRC -> viewModel.testVidSrcHealth()
                            SourcePillCategory.DECRYPTOR -> viewModel.testDecryptorHealth()
                            SourcePillCategory.TMDB -> viewModel.testTMDBHealth()
                            else -> {
                                viewModel.testVegaProvidersHealth()
                                viewModel.testVidSrcHealth()
                                viewModel.testDecryptorHealth()
                                viewModel.testTMDBHealth()
                            }
                        }
                        Toast.makeText(context, "Testing provider health & CDN speeds...", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Health", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 18+ MASTER SWITCH BANNER (Shown when ADULT or ALL is selected)
        if (selectedPill == SourcePillCategory.ADULT || selectedPill == SourcePillCategory.ALL) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (adultContentEnabled) Color(0xFFE91E63).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (adultContentEnabled) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.35f)) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAdultContentEnabled(!adultContentEnabled) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE91E63).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explicit,
                                    contentDescription = null,
                                    tint = Color(0xFFE91E63),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "18+ Adult Content Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (adultContentEnabled) "Active • 18+ adult sources (SupJav, XNXX, SexTB, HellPorno, Stripchat...) are visible" else "Disabled • Adult sources are hidden from Home & Search",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = adultContentEnabled,
                            onCheckedChange = { viewModel.setAdultContentEnabled(it) }
                        )
                    }
                }
            }
        }

        // VEGA MASTER TOGGLE (Shown when VEGA is selected)
        if (selectedPill == SourcePillCategory.VEGA) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVegaMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setVegaMasterEnabled(!isVegaMasterEnabled) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Enable Vega Extensions (52+ Built-in)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isVegaMasterEnabled) "Vega extensions are active. Providers resolve catalog titles & media links." else "Vega is disabled. Background processing and server calls are stopped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isVegaMasterEnabled,
                            onCheckedChange = { viewModel.setVegaMasterEnabled(it) }
                        )
                    }
                }
            }
        }

        // VIDSRC MASTER TOGGLE (Shown when VIDSRC is selected)
        if (selectedPill == SourcePillCategory.VIDSRC) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVidSrcMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setVidSrcMasterEnabled(!isVidSrcMasterEnabled) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Enable VidSrc Multi-Mirror Servers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isVidSrcMasterEnabled) "VidSrc cloud mirrors and WASM HLS decryptors are active." else "VidSrc is disabled. Cloud mirrors and stream extractions are stopped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isVidSrcMasterEnabled,
                            onCheckedChange = { viewModel.setVidSrcMasterEnabled(it) }
                        )
                    }
                }
            }
        }

        // DECRYPTOR MASTER TOGGLE (Shown when DECRYPTOR is selected)
        if (selectedPill == SourcePillCategory.DECRYPTOR) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDecryptorMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDecryptorMasterEnabled(!isDecryptorMasterEnabled) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Enable Decryptor VIP Servers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDecryptorMasterEnabled) "Decryptor extracts TMDB media into multi-server streams (Vidhide, Turbo, Nxsha Fast)." else "Decryptor is disabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDecryptorMasterEnabled,
                            onCheckedChange = { viewModel.setDecryptorMasterEnabled(it) }
                        )
                    }
                }
            }
        }

        // TMDB MASTER TOGGLE (Shown when TMDB is selected)
        if (selectedPill == SourcePillCategory.TMDB) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTMDBMasterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setTMDBMasterEnabled(!isTMDBMasterEnabled) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Enable TMDB VIP Embed Sources",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isTMDBMasterEnabled) "TMDB multi-server embed extractors (VixSrc, Showbox, Videasy) are active." else "TMDB VIP embeds are disabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isTMDBMasterEnabled,
                            onCheckedChange = { viewModel.setTMDBMasterEnabled(it) }
                        )
                    }
                }
            }
        }

        // SOURCE CARDS LIST HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedPill.label.uppercase()} SOURCES (${filteredItems.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (selectedPill == SourcePillCategory.TORRENT) {
                    TextButton(onClick = { onNavigateToCategory?.invoke(SettingsCategory.PROWLARR_INDEXERS) }) {
                        Text("Open Full Prowlarr Indexers UI", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No sources match '$searchQuery'",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // INDIVIDUAL SOURCE CARDS
        items(filteredItems, key = { "${it.category.name}_${it.id}" }) { item ->
            UnifiedSourceCard(
                item = item,
                onToggleEnabled = { enabled ->
                    when (item.category) {
                        SourcePillCategory.NORMAL, SourcePillCategory.ADULT -> {
                            viewModel.toggleProviderEnabled(item.id)
                        }
                        SourcePillCategory.VEGA -> {
                            viewModel.toggleVegaProvider(item.id, enabled)
                        }
                        SourcePillCategory.VIDSRC -> {
                            viewModel.toggleVidSrcProvider(item.id, enabled)
                        }
                        SourcePillCategory.DECRYPTOR -> {
                            viewModel.toggleDecryptorProvider(item.id, enabled)
                        }
                        SourcePillCategory.TMDB -> {
                            viewModel.toggleTMDBProvider(item.id, enabled)
                        }
                        else -> {
                            viewModel.toggleProviderEnabled(item.id)
                        }
                    }
                },
                onInstall = {
                    when (item.category) {
                        SourcePillCategory.VEGA -> {
                            viewModel.installVegaProvider(item.id)
                        }
                        SourcePillCategory.VIDSRC -> {
                            viewModel.installVidSrcProvider(item.id)
                        }
                        SourcePillCategory.DECRYPTOR -> {
                            viewModel.installDecryptorProvider(item.id)
                        }
                        SourcePillCategory.TMDB -> {
                            viewModel.installTMDBProvider(item.id)
                        }
                        else -> {}
                    }
                    Toast.makeText(context, "Installed ${item.name}", Toast.LENGTH_SHORT).show()
                },
                onUninstall = {
                    when (item.category) {
                        SourcePillCategory.VEGA -> viewModel.uninstallVegaProvider(item.id)
                        SourcePillCategory.VIDSRC -> viewModel.uninstallVidSrcProvider(item.id)
                        SourcePillCategory.DECRYPTOR -> viewModel.uninstallDecryptorProvider(item.id)
                        SourcePillCategory.TMDB -> viewModel.uninstallTMDBProvider(item.id)
                        else -> {}
                    }
                    Toast.makeText(context, "Uninstalled ${item.name}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // ADD CUSTOM REPO / SOURCE DIALOG
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = {
                Text(text = "Add Custom Source / Repo", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Add a custom extension repository URL, mirror endpoint, or indexer definition:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Target Engine Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            SourcePillCategory.VEGA to "Vega Repo",
                            SourcePillCategory.VIDSRC to "VidSrc Mirror",
                            SourcePillCategory.DECRYPTOR to "Decryptor",
                            SourcePillCategory.TMDB to "TMDB"
                        ).forEach { (cat, label) ->
                            FilterChip(
                                selected = customSourceType == cat,
                                onClick = { customSourceType = cat },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customSourceName,
                        onValueChange = { customSourceName = it },
                        label = { Text("Source / Provider Name") },
                        placeholder = { Text("e.g., My Custom VIP Mirror") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customSourceUrl,
                        onValueChange = { customSourceUrl = it },
                        label = { Text("URL / Manifest Endpoint") },
                        placeholder = { Text("https://example.com/manifest.json") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUrl = customSourceUrl.trim()
                        val cleanName = customSourceName.trim().ifBlank { "Custom Source" }
                        if (cleanUrl.isNotBlank()) {
                            try {
                                com.example.util.AppEngineDiagnosticManager.addCustomRepo(
                                    context = context,
                                    ownerRepo = cleanUrl,
                                    name = cleanName,
                                    description = "Custom User Added Source ($cleanUrl)"
                                )
                            } catch (_: Exception) {}
                            Toast.makeText(context, "Added $cleanName successfully", Toast.LENGTH_SHORT).show()
                            showAddSourceDialog = false
                            customSourceUrl = ""
                            customSourceName = ""
                        } else {
                            Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add Source")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UnifiedSourceCard(
    item: UnifiedSourceItem,
    onToggleEnabled: (Boolean) -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEnabled && item.isInstalled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source Category Icon Box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(item.category.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon ?: item.category.icon,
                    contentDescription = null,
                    tint = item.category.color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Quality / Tag Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = item.category.color.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = item.qualityTag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = item.category.color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Health indicator if tested
                    if (item.healthStatus != null) {
                        val isOnline = item.healthStatus.contains("ms", ignoreCase = true) || item.healthStatus.contains("online", ignoreCase = true) || item.healthStatus.contains("ok", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isOnline) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFF44336).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = item.healthStatus,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions: Install / Delete for extensions, and Enable/Disable Switch
            if (item.isExtension) {
                if (item.isInstalled) {
                    IconButton(
                        onClick = onUninstall,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Uninstall",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Switch(
                        checked = item.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                } else {
                    OutlinedButton(
                        onClick = onInstall,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Install", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }
        }
    }
}
