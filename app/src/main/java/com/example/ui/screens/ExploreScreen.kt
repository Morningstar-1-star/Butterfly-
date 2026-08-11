package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.util.TMDBHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onMovieSelected: (VideoItem) -> Unit,
    onGenreSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val savedList by viewModel.watchLaterList.collectAsState()

    // Default Hero Fallback with guaranteed Unsplash / high-res backdrop URLs
    val defaultHeroItems = remember {
        listOf(
            FeaturedMedia(
                id = "movie_299536",
                title = "Avengers: Infinity War",
                genres = "Trending • Movie • 2018",
                synopsis = "The Avengers and their allies must be willing to sacrifice all in an attempt to defeat the powerful Thanos.",
                backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80"
            ),
            FeaturedMedia(
                id = "movie_693134",
                title = "Dune: Part Two",
                genres = "Trending • Sci-Fi • 2024",
                synopsis = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
                backdropUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1200&auto=format&fit=crop&q=80",
                posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80"
            )
        )
    }

    val initialMystery = remember {
        listOf(
            VideoItem("m1", "Backrooms", "2025 • Mystery", thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80"),
            VideoItem("m2", "Desire", "2024 • Thriller", thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80"),
            VideoItem("m3", "Zootopia 2", "2025 • Mystery", thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80")
        )
    }

    val initialHorror = remember {
        listOf(
            VideoItem("h1", "The Last House", "2025 • Horror", thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80"),
            VideoItem("h2", "Obsession", "2024 • Psychological", thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80"),
            VideoItem("h3", "Evil Dead Burn", "2025 • Horror", thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80")
        )
    }

    val initialScifi = remember {
        listOf(
            VideoItem("s1", "Spider-Man: Brand New Day", "2026 • Action", thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80"),
            VideoItem("s2", "Interstellar", "2014 • Sci-Fi", thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80")
        )
    }

    val initialFantasy = remember {
        listOf(
            VideoItem("f1", "The Odyssey", "2026 • Fantasy", thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80"),
            VideoItem("f2", "Frieren", "2024 • Fantasy", thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80")
        )
    }

    val initialAnime = remember {
        listOf(
            VideoItem("a1", "Attack on Titan", "2023 • Anime", thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80"),
            VideoItem("a2", "Demon Slayer", "2024 • Anime", thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80")
        )
    }

    val initialKids = remember {
        listOf(
            VideoItem("k1", "Toy Story 5", "2026 • Family", thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80"),
            VideoItem("k2", "Moana 2", "2025 • Family", thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80")
        )
    }

    val initialDocu = remember {
        listOf(
            VideoItem("d1", "1000 Men and Me", "2025 • Documentary", thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80"),
            VideoItem("d2", "Planet Earth III", "2024 • Nature", thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80")
        )
    }

    var heroItems by remember { mutableStateOf(defaultHeroItems) }
    var categories by remember {
        mutableStateOf(
            listOf(
                CuratedCategory("mystery", "Mystery Mindbenders", "🔍", initialMystery),
                CuratedCategory("horror", "Horror Nights", "🍿", initialHorror),
                CuratedCategory("scifi", "Sci-Fi Dimensions", "✨", initialScifi),
                CuratedCategory("fantasy", "Fantasy Worlds", "🔮", initialFantasy),
                CuratedCategory("anime", "Trending Anime (AniList & Jikan)", "🎌", initialAnime),
                CuratedCategory("kids", "Kids & Family", "🟢", initialKids),
                CuratedCategory("docu", "Documentaries", "📜", initialDocu),
                CuratedCategory("adult", "Adult / Steamy & JAV (18+)", "💖", emptyList())
            )
        )
    }
    var activeHeroIndex by remember { mutableStateOf(0) }

    // Live Parallel API Fetching on Launch
    LaunchedEffect(Unit) {
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
            val javInfoDeferred = async { TMDBHelper.fetchJavInfoAdultVideos() }

            val liveHeroes = heroesDeferred.await()
            if (liveHeroes.isNotEmpty()) {
                heroItems = liveHeroes
            }

            val mystery = mysteryDeferred.await().ifEmpty { initialMystery }
            val horror = horrorDeferred.await().ifEmpty { initialHorror }
            val scifi = scifiDeferred.await().ifEmpty { initialScifi }
            val fantasy = fantasyDeferred.await().ifEmpty { initialFantasy }
            val kids = kidsDeferred.await().ifEmpty { initialKids }
            val docu = docuDeferred.await().ifEmpty { initialDocu }
            
            val aniListItems = aniListDeferred.await()
            val jikanItems = jikanDeferred.await()
            val anime = (aniListItems + jikanItems).distinctBy { it.id }.ifEmpty { initialAnime }

            val javInfoItems = javInfoDeferred.await()

            categories = listOf(
                CuratedCategory("mystery", "Mystery Mindbenders", "🔍", mystery),
                CuratedCategory("horror", "Horror Nights", "🍿", horror),
                CuratedCategory("scifi", "Sci-Fi Dimensions", "✨", scifi),
                CuratedCategory("fantasy", "Fantasy Worlds", "🔮", fantasy),
                CuratedCategory("anime", "Trending Anime (AniList & Jikan)", "🎌", anime),
                CuratedCategory("kids", "Kids & Family", "🟢", kids),
                CuratedCategory("docu", "Documentaries", "📜", docu),
                CuratedCategory("adult", "Adult / Steamy & JAV (18+)", "💖", javInfoItems)
            )
        }
    }

    val currentHero = if (heroItems.isNotEmpty()) heroItems[activeHeroIndex.coerceIn(0, heroItems.size - 1)] else defaultHeroItems[0]

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 160.dp),
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

        // 2. HERO FEATURED BANNER ("Avatar 2" Carousel)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = currentHero.backdropUrl,
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
                                    containerColor = MaterialTheme.colorScheme.primary
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
                            IconButton(
                                onClick = {
                                    val item = VideoItem(
                                        id = currentHero.id,
                                        title = currentHero.title,
                                        uploaderName = currentHero.genres,
                                        thumbnailUrl = currentHero.posterUrl,
                                        providerId = currentHero.providerId
                                    )
                                    viewModel.addToWatchLater(item)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Save to Library",
                                    tint = Color.White
                                )
                            }

                            // Pagination dots
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                heroItems.indices.forEach { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (index == activeHeroIndex) 10.dp else 8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (index == activeHeroIndex) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                                            )
                                            .clickable { activeHeroIndex = index }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. CURATED SECTIONS (Mystery, Horror, Sci-Fi, Fantasy, Kids, Documentaries, Adult)
        categories.forEach { category ->
            item(key = category.id) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Category Section Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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

                        IconButton(
                            onClick = { onGenreSelected(category.title) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Horizontal Cards Row
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(category.items, key = { it.id }) { video ->
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
                if (!video.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
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
                            if (isSaved) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Save to Library",
                        tint = Color.White,
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
