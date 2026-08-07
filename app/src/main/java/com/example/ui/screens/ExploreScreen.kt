package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.VideoCard

data class ExploreCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val query: String
)

@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onMovieSelected: (VideoItem) -> Unit,
    onGenreSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()

    var selectedCategory by remember { mutableStateOf("trending") }

    val categories = remember {
        listOf(
            ExploreCategory("trending", "Trending", Icons.Default.TrendingUp, Color(0xFFE53935), "trending"),
            ExploreCategory("music", "Music", Icons.Default.Headphones, Color(0xFF00ACC1), "music"),
            ExploreCategory("gaming", "Gaming", Icons.Default.SportsEsports, Color(0xFF43A047), "gaming"),
            ExploreCategory("news", "News", Icons.Default.Newspaper, Color(0xFF1E88E5), "news"),
            ExploreCategory("films", "Films", Icons.Default.Movie, Color(0xFFF57C00), "movies"),
            ExploreCategory("fashion", "Fashion & beauty", Icons.Default.Checkroom, Color(0xFFD81B60), "fashion"),
            ExploreCategory("learning", "Learning", Icons.Default.Lightbulb, Color(0xFF8E24AA), "learning"),
            ExploreCategory("live", "Live", Icons.Default.Sensors, Color(0xFFE040FB), "live"),
            ExploreCategory("sport", "Sport", Icons.Default.EmojiEvents, Color(0xFF00897B), "sports")
        )
    }

    val activeCategory = categories.firstOrNull { it.id == selectedCategory } ?: categories.first()

    LaunchedEffect(selectedCategory) {
        if (selectedCategory != "trending") {
            viewModel.performSearch(activeCategory.query)
        }
    }

    val displayVideos = remember(selectedCategory, searchResults, trendingVideos) {
        if (selectedCategory == "trending") {
            if (trendingVideos.isNotEmpty()) trendingVideos else searchResults
        } else {
            if (searchResults.isNotEmpty()) searchResults else trendingVideos
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. CATEGORY GRID CARDS (MATCHING IMAGE 1)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Explore Categories",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 2-column or 3-column Grid for Category Cards
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val rows = categories.chunked(2)
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (cat in row) {
                                CategoryCard(
                                    category = cat,
                                    isSelected = (selectedCategory == cat.id),
                                    onClick = {
                                        selectedCategory = cat.id
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 2. ACTIVE CATEGORY HEADER & RESULTS FEED
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = activeCategory.icon,
                        contentDescription = null,
                        tint = activeCategory.color,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${activeCategory.title} Videos",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (isSearching || isLoadingTrending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 3. WORKING INTERACTIVE VIDEO LIST
        if (displayVideos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching ${activeCategory.title} streams from active providers...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayVideos, key = { (it.providerId ?: "") + "_" + it.id }) { video ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    VideoCard(
                        video = video,
                        onClick = { onMovieSelected(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: ExploreCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(54.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                category.color
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.25f) else category.color.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.title,
                    tint = if (isSelected) Color.White else category.color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
