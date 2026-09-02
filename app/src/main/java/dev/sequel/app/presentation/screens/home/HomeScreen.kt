package dev.sequel.app.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.presentation.components.ShowCard
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pagedShows = viewModel.pagedShows.collectAsLazyPagingItems()
    val currentType by viewModel.mediaType.collectAsState()
    
    val listState = rememberLazyListState()
    val isScrollingUp = listState.firstVisibleItemIndex == 0 || listState.firstVisibleItemScrollOffset == 0
    
    val view = LocalView.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp) // Space for floating bottom nav
        ) {
            // ── Hero Banner ──
            item {
                val heroShow = if (pagedShows.itemCount > 0) pagedShows[0] else null
                HeroBanner(
                    show = heroShow,
                    onMarkWatched = { show -> viewModel.markAsWatched(show) }
                )
            }

            // ── Trending Feed ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Trending Now",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                val trendingRowState = rememberLazyListState()
                LazyRow(
                    state = trendingRowState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = trendingRowState),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        count = pagedShows.itemCount,
                        key = pagedShows.itemKey { it.id },
                        contentType = pagedShows.itemContentType { "ShowCard" }
                    ) { index ->
                        val show = pagedShows[index]
                        if (show != null && index != 0) { // Skip hero
                            Box(modifier = Modifier.width(140.dp)) {
                                ShowCard(
                                    show = show,
                                    onClick = { onShowClick(show.id, show.mediaType) },
                                    onLongClick = { viewModel.addToWatchlist(show) }
                                )
                            }
                        }
                    }
                    
                    if (pagedShows.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                modifier = Modifier.height(210.dp).width(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            
            // ── Recommended Feed (Placeholder UI for now) ──
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Recommended For You",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                val recommendedRowState = rememberLazyListState()
                LazyRow(
                    state = recommendedRowState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = recommendedRowState),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        count = pagedShows.itemCount,
                        key = pagedShows.itemKey { "rec_${it.id}" }
                    ) { index ->
                        val show = pagedShows[pagedShows.itemCount - 1 - index] // reverse for variety
                        if (show != null) {
                            Box(modifier = Modifier.width(140.dp)) {
                                ShowCard(
                                    show = show,
                                    onClick = { onShowClick(show.id, show.mediaType) },
                                    onLongClick = { viewModel.addToWatchlist(show) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Filter Pill ──
        AnimatedVisibility(
            visible = isScrollingUp,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        ) {
            Row(
                modifier = Modifier
                    .glassmorphicBackground(RoundedCornerShape(24.dp), blurRadius = 12.dp)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = currentType == "tv",
                    onClick = { viewModel.setMediaType("tv") },
                    label = { Text("TV Shows") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    border = null,
                    shape = RoundedCornerShape(20.dp)
                )
                FilterChip(
                    selected = currentType == "movie",
                    onClick = { viewModel.setMediaType("movie") },
                    label = { Text("Movies") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    border = null,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}

@Composable
fun HeroBanner(
    show: ShowEntity?,
    onMarkWatched: (ShowEntity) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (show != null) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w780${show.posterPath}",
                contentDescription = show.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.background
                            ),
                            startY = 200f
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .glassmorphicBackground(RoundedCornerShape(8.dp), blurRadius = 8.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "UP NEXT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = show.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .glassmorphicBackground(RoundedCornerShape(16.dp))
                        .hapticClickable { onMarkWatched(show) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Mark Watched", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark Watched", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
