package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.ui.MainViewModel

data class DirectorItem(
    val name: String,
    val avatarUrl: String
)

data class CustomMovieItem(
    val id: String,
    val title: String,
    val rating: String,
    val yearAndDuration: String,
    val posterUrl: String,
    val genre: String = "Action"
)

@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onMovieSelected: (VideoItem) -> Unit,
    onGenreSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val directors = listOf(
        DirectorItem("Gautham Menon", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop"),
        DirectorItem("Paa Ranjith", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&auto=format&fit=crop"),
        DirectorItem("Pradeep Ranga", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&auto=format&fit=crop"),
        DirectorItem("Vettri Maaran", "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=200&auto=format&fit=crop"),
        DirectorItem("Lokesh Kanagaraj", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=200&auto=format&fit=crop"),
        DirectorItem("Christopher Nolan", "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=200&auto=format&fit=crop"),
        DirectorItem("Denis Villeneuve", "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=200&auto=format&fit=crop")
    )

    val newTitles = listOf(
        CustomMovieItem("tt10300136", "Vikram", "8.3", "2022 • 2h 53m", "https://m.media-amazon.com/images/M/MV5BMmJhYjBkMDItNWQ5Ni00NzJjLWEzNTItN2RiNWExNWTVZWNlXkEyXkFqcGdeQXVyNTkzNDQ4ODU@._V1_SX300.jpg"),
        CustomMovieItem("tt15654328", "Leo", "8.0", "2023 • 2h 44m", "https://m.media-amazon.com/images/M/MV5BNmU4YjE0OTAtNTc5Ni00N2EzLTgyOTQtNWVlNWVlNmE2NWVlXkEyXkFqcGdeQXVyMTUzMTg2ODkz._V1_SX300.jpg"),
        CustomMovieItem("tt27495574", "Good Night", "7.9", "2023 • 2h 22m", "https://m.media-amazon.com/images/M/MV5BOTk1ZTRiNTctNmE4OS00NzdlLTgwNDItZGI2ZWVmNDkwNzg3XkEyXkFqcGdeQXVyMTE0MzY0NjE1._V1_SX300.jpg"),
        CustomMovieItem("tt1160419", "Dune: Part Two", "8.5", "2024 • 2h 46m", "https://m.media-amazon.com/images/M/MV5BN2QyZGUzMjctOWJiMy00NTlhLTgwMWQtZDVlYzhjN2JhZWBmXkEyXkFqcGdeQXVyMTA3MDk2NDg2._V1_SX300.jpg"),
        CustomMovieItem("tt15398776", "Oppenheimer", "8.9", "2023 • 3h 00m", "https://m.media-amazon.com/images/M/MV5BMDBmYTZjNjUtN2M1MS00MTQ5LTk4NTUtODgxM2EzOGU0Mzg0XkEyXkFqcGdeQXVyMzQwMTY2Nzk@._V1_SX300.jpg"),
        CustomMovieItem("tt6263850", "Deadpool & Wolverine", "8.1", "2024 • 2h 08m", "https://m.media-amazon.com/images/M/MV5BNzRiMjg0MzUtNTQwYi00NWQ5LTg4MDYtN2YzNjZhODljN2EwXkEyXkFqcGdeQXVyMTEyMjM2NDc2._V1_SX300.jpg")
    )

    val genres = listOf(
        "Action" to Color(0xFF332A15),
        "Thriller" to Color(0xFF4A4B1A),
        "Sci-Fi" to Color(0xFF2C1E3F),
        "Anime" to Color(0xFF3D1C2A),
        "Comedy" to Color(0xFF1E3A34),
        "Horror" to Color(0xFF3E1A1A),
        "Romance" to Color(0xFF3B1E2B),
        "Drama" to Color(0xFF2B3A42),
        "Fantasy" to Color(0xFF1A2B3E),
        "Mystery" to Color(0xFF3F2C1E),
        "Adventure" to Color(0xFF1E3F2C),
        "Crime" to Color(0xFF3E1E1E),
        "Animation" to Color(0xFF2A1E3D)
    )

    val comingSoon = listOf(
        CustomMovieItem("tt15239678", "Dune: Prophecy", "EXPECTED 9.0", "Releasing Nov 2026", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop"),
        CustomMovieItem("tt1087461", "Spider-Man: Beyond Spider-Verse", "EXPECTED 9.2", "Coming 2027", "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=600&auto=format&fit=crop")
    )

    val trendingSeries = listOf(
        CustomMovieItem("tt11198330", "House of the Dragon", "8.4", "2 Seasons", "https://m.media-amazon.com/images/M/MV5BM2QzM2JiNTMtOWM4NC00NmQ4LWE3YzItM2ViOTA5NGRlNTFlXkEyXkFqcGdeQXVyMjkwOTAyMDU@._V1_SX300.jpg"),
        CustomMovieItem("tt11280740", "Severance", "8.7", "2 Seasons", "https://m.media-amazon.com/images/M/MV5BOThjM3IzMWEtNzA4Mi00NWFhLTg4MjItNWExMGIyMDJhNWVjXkEyXkFqcGdeQXVyMTkxNjUyNQ@@._V1_SX300.jpg"),
        CustomMovieItem("tt2788316", "Shogun", "8.8", "1 Season", "https://m.media-amazon.com/images/M/MV5BNTNhY2JmYTItNWYzNi00MGVkLTg5ZGMtN2FiMTdhNzQzZmVkXkEyXkFqcGdeQXVyMTkxNjUyNQ@@._V1_SX300.jpg"),
        CustomMovieItem("tt4574334", "Stranger Things", "8.7", "4 Seasons", "https://m.media-amazon.com/images/M/MV5BMDZkYmVhNjMtNWU4MC00MDQxLWE3MjYtZGJlNjJhZzk1Njg5XkEyXkFqcGdeQXVyMTkxNjUyNQ@@._V1_SX300.jpg")
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Popular Directors Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Popular Directors",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(directors) { director ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(80.dp)
                                .clickable {
                                    onGenreSelected(director.name)
                                }
                        ) {
                            AsyncImage(
                                model = director.avatarUrl,
                                contentDescription = director.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = director.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. New Titles Section (Posters + Rating Badges)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "New titles",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(newTitles) { movie ->
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable {
                                    val videoItem = VideoItem(
                                        id = movie.id,
                                        title = movie.title,
                                        uploaderName = "Unified Torrents Multi-Indexer",
                                        thumbnailUrl = movie.posterUrl,
                                        providerId = "unified_torrents"
                                    )
                                    onMovieSelected(videoItem)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                ) {
                                    AsyncImage(
                                        model = movie.posterUrl,
                                        contentDescription = movie.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Green Rating Badge (Top Right)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2E7D32))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = movie.rating,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // Quick Play Floating Icon
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = movie.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = movie.yearAndDuration,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Genres Section (Dark Colorful Styled Cards)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Genres",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(genres) { (genreName, cardBgColor) ->
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBgColor)
                                .clickable { onGenreSelected(genreName) }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = genreName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 4. Coming Soon Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Coming soon",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    comingSoon.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable { onGenreSelected(item.title) },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = item.posterUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = item.yearAndDuration,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = item.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Trending TV Series
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Trending TV Series",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(trendingSeries) { series ->
                        Card(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable {
                                    val videoItem = VideoItem(
                                        id = series.id,
                                        title = series.title,
                                        uploaderName = "Unified Torrents Multi-Indexer",
                                        thumbnailUrl = series.posterUrl,
                                        providerId = "unified_torrents"
                                    )
                                    onMovieSelected(videoItem)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                ) {
                                    AsyncImage(
                                        model = series.posterUrl,
                                        contentDescription = series.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2E7D32))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "★ ${series.rating}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = series.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = series.yearAndDuration,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
