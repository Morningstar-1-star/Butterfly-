package com.example.ui.screens
import kotlinx.coroutines.async

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.util.TMDBHelper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class FeaturedMedia(
    val id: String,
    val title: String,
    val genres: String,
    val synopsis: String,
    val backdropUrl: String,
    val posterUrl: String,
    val providerId: String = "tmdb"
)

data class CuratedCategory(
    val id: String,
    val title: String,
    val emoji: String,
    val items: List<VideoItem>
)

val yellowAccent = Color(0xFFFFD600)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onMovieSelected: (VideoItem) -> Unit,
    onGenreSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 108.dp,
    bottomPadding: Dp = 160.dp
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val savedList by viewModel.watchLaterList.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val notInterestedChannels by viewModel.notInterestedChannels.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var heroItems by remember { mutableStateOf<List<FeaturedMedia>>(emptyList()) }
    var rawCategories by remember {
        mutableStateOf(
            listOf(
                CuratedCategory("mystery", "Mystery Mindbenders", "🔍", emptyList()),
                CuratedCategory("horror", "Horror Nights", "🍿", emptyList()),
                CuratedCategory("scifi", "Sci-Fi Dimensions", "✨", emptyList()),
                CuratedCategory("fantasy", "Fantasy Worlds", "🔮", emptyList()),
                CuratedCategory("anime", "Anime", "🎌", emptyList()),
                CuratedCategory("kids", "Kids & Family", "🟢", emptyList()),
                CuratedCategory("docu", "Documentaries", "📜", emptyList()),
                CuratedCategory("adult", "Adult / Steamy & JAV (18+)", "💖", emptyList())
            )
        )
    }

    val categories = remember(rawCategories, adultContentEnabled, hiddenVideoIds, notInterestedVideoIds, notInterestedChannels) {
        val base = if (adultContentEnabled) rawCategories else rawCategories.filter { it.id != "adult" }
        base.map { cat ->
            cat.copy(items = cat.items.filterNot { viewModel.isBlockedVideo(it) })
        }
    }
    val filteredHeroItems = remember(heroItems, hiddenVideoIds, notInterestedVideoIds) {
        heroItems.filterNot { hiddenVideoIds.contains(it.id) || notInterestedVideoIds.contains(it.id) }
    }
    var activeHeroIndex by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadLiveExploreData() {
        coroutineScope {
            val heroesDeferred = async { TMDBHelper.fetchExploreHeroItems() }
            val mysteryDeferred = async { TMDBHelper.fetchExploreCategoryMovies(9648, "Mystery") }
            val horrorDeferred = async { TMDBHelper.fetchExploreCategoryMovies(27, "Horror") }
            val scifiDeferred = async { TMDBHelper.fetchExploreCategoryMovies(878, "Sci-Fi") }
            val fantasyDeferred = async { TMDBHelper.fetchExploreCategoryMovies(14, "Fantasy") }
            val kidsDeferred = async { TMDBHelper.fetchExploreCategoryMovies(10751, "Family") }
            val docuDeferred = async { TMDBHelper.fetchExploreCategoryMovies(99, "Documentary") }
            val aniListDeferred = async { TMDBHelper.fetchAniListTrendingAnime() }
            val jikanDeferred = async { TMDBHelper.fetchJikanTopAnime() }
            val ytDeferred = async { try { com.example.extractor.YouTubeExtractorHelper.fetchYouTubeTrending() } catch (e: Exception) { emptyList() } }
            val musicDeferred = async { emptyList<com.example.model.VideoItem>() }
            val shortsDeferred = async { emptyList<com.example.model.VideoItem>() }
            val dailymotionDeferred = async { emptyList<com.example.model.VideoItem>() }
            val archiveDeferred = async {
                try {
                    com.example.extractor.ArchiveOrgProvider.getHome()
                } catch (e: Exception) { emptyList<com.example.model.VideoItem>() }
            }

            val liveHeroes = heroesDeferred.await()
            if (liveHeroes.isNotEmpty()) {
                heroItems = liveHeroes
            }

            val mystery = mysteryDeferred.await()
            val horror = horrorDeferred.await()
            val scifi = scifiDeferred.await()
            val fantasy = fantasyDeferred.await()
            val kids = kidsDeferred.await()
            val docu = docuDeferred.await()

            val aniListItems = aniListDeferred.await()
            val jikanItems = jikanDeferred.await()
            val anime = (aniListItems + jikanItems).distinctBy { it.id }

            val ytItems = ytDeferred.await()
            val musicItems = musicDeferred.await()
            val shortsItems = shortsDeferred.await()
            val dailymotionItems = dailymotionDeferred.await()
            val archiveItems = archiveDeferred.await()

            rawCategories = listOf(
                CuratedCategory("youtube", "YouTube Trending", "🔴", ytItems.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("music", "YouTube Music & Audio", "🎵", musicItems.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("shorts", "YouTube Shorts & Reels", "⚡", shortsItems.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("mystery", "Mystery Mindbenders", "🔍", mystery.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("horror", "Horror Nights", "🍿", horror.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("scifi", "Sci-Fi Dimensions", "✨", scifi.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("fantasy", "Fantasy Worlds", "🔮", fantasy.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("anime", "Anime", "🎌", anime.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("kids", "Kids & Family", "🟢", kids.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("docu", "Documentaries", "📜", docu.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("dailymotion", "Dailymotion Videos", "▶️", dailymotionItems.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }),
                CuratedCategory("archive_org", "Archive.org Audio & Video", "📜", archiveItems.filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) })
            )
            val allCategoryItems = rawCategories.flatMap { it.items }
            com.example.util.ThumbnailOptimizer.preloadThumbnails(context, allCategoryItems, maxCount = 60)
        }
    }

    LaunchedEffect(Unit) {
        isRefreshing = true
        loadLiveExploreData()
        isRefreshing = false
    }

    fun onRefresh() {
        coroutineScope.launch {
            isRefreshing = true
            loadLiveExploreData()
            isRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onRefresh() },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val currentHero = filteredHeroItems.getOrNull(activeHeroIndex.coerceIn(0, (filteredHeroItems.size - 1).coerceAtLeast(0)))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. TOP HEADER ("Watch Now" + User Avatar Badge)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Watch Now",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Discover movies, TV series, anime & channels",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Profile Avatar Badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { viewModel.navigateToScreen(com.example.model.AppScreen.ACCOUNT) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile.name.take(2).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 1.5. SWIPABLE PROVIDERS & SOURCES ROW
            item {
                val providersList = listOf(
                    "Prime Video" to "🎬",
                    "Apple TV" to "🍎",
                    "Z5" to "📺",
                    "Google Play" to "▶️",
                    "JioHotstar" to "🌟",
                    "YouTube" to "🔴",
                    "Music" to "🎵",
                    "Shorts" to "⚡",
                    "Dailymotion" to "▶️",
                    "Archive.org" to "📜"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(providersList) { (providerName, emoji) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                onGenreSelected(providerName)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = providerName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 2. HERO FEATURED BANNER
            item {
                if (currentHero != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val heroThumbRequest = remember(currentHero.backdropUrl) {
                                com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, currentHero.backdropUrl)
                            }
                            AsyncImage(
                                model = heroThumbRequest ?: currentHero.backdropUrl,
                                contentDescription = currentHero.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Gradient Overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.4f),
                                                Color.Black.copy(alpha = 0.95f)
                                            )
                                        )
                                    )
                            )

                            // Hero Content Overlay
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = currentHero.title,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentHero.genres,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentHero.synopsis,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Actions & Pagination Dots
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            onMovieSelected(
                                                VideoItem(
                                                    id = currentHero.id,
                                                    title = currentHero.title,
                                                    uploaderName = currentHero.genres,
                                                    thumbnailUrl = currentHero.posterUrl,
                                                    providerId = currentHero.providerId
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = yellowAccent,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Watch Now", fontWeight = FontWeight.Bold)
                                    }

                                    // Save to Library Button
                                    val isHeroSaved = savedList.any { it.id == currentHero.id }
                                    IconButton(
                                        onClick = {
                                            val item = VideoItem(
                                                id = currentHero.id,
                                                title = currentHero.title,
                                                uploaderName = currentHero.genres,
                                                thumbnailUrl = currentHero.posterUrl,
                                                providerId = currentHero.providerId
                                            )
                                            if (isHeroSaved) viewModel.removeFromWatchLater(item)
                                            else viewModel.addToWatchLater(item)
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                if (isHeroSaved) yellowAccent else Color.White.copy(alpha = 0.2f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isHeroSaved) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = "Save to Library",
                                            tint = if (isHeroSaved) Color.Black else Color.White
                                        )
                                    }

                                    // Pagination dots
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        filteredHeroItems.indices.forEach { index ->
                                            Box(
                                                modifier = Modifier
                                                    .size(if (index == activeHeroIndex) 10.dp else 8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (index == activeHeroIndex) yellowAccent else Color.White.copy(alpha = 0.5f)
                                                    )
                                                    .clickable { activeHeroIndex = index }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Shimmer loading hero container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = yellowAccent)
                    }
                }
            }

            // 3. CURATED SECTIONS
            categories.forEach { category ->
                item(key = category.id) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Category Section Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGenreSelected(category.title) }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = category.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = category.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "More",
                                tint = yellowAccent,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        // Horizontal Cards Row or Loading Skeletons
                        if (category.items.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(category.items.distinctBy { "${it.providerId}_${it.id}" }, key = { "${it.providerId}_${it.id}" }) { video ->
                                    val isSaved = savedList.any { it.id == video.id }
                                    ExploreMediaCard(
                                        video = video,
                                        isSaved = isSaved,
                                        onClick = { onMovieSelected(video) },
                                        onToggleSave = {
                                            if (isSaved) viewModel.removeFromWatchLater(video)
                                            else viewModel.addToWatchLater(video)
                                        }
                                    )
                                }
                            }
                        } else {
                            // Skeleton loading items
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(4) {
                                    Box(
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
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

@Composable
private fun ExploreMediaCard(
    video: VideoItem,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val posterRequest = remember(video.thumbnailUrl) {
        com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, video.thumbnailUrl, crossfadeMillis = 100)
    }

    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            ) {
                if (posterRequest != null) {
                    AsyncImage(
                        model = posterRequest,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Plus / Bookmark Button overlay
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(
                            if (isSaved) yellowAccent else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Save to Library",
                        tint = if (isSaved) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.uploaderName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
