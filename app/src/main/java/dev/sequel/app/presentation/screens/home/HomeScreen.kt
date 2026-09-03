package dev.sequel.app.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    
    // ── Proper scroll-direction tracking ──
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    var isScrollingUp by remember { mutableStateOf(true) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (previousIndex != listState.firstVisibleItemIndex) {
            isScrollingUp = previousIndex > listState.firstVisibleItemIndex
        } else {
            isScrollingUp = previousScrollOffset >= listState.firstVisibleItemScrollOffset
        }
        previousIndex = listState.firstVisibleItemIndex
        previousScrollOffset = listState.firstVisibleItemScrollOffset
    }

    val view = LocalView.current
    val isInitialLoad = pagedShows.loadState.refresh is LoadState.Loading

    Box(modifier = Modifier.fillMaxSize()) {

        if (isInitialLoad) {
            // ── Shimmer Skeleton while initial data loads ──
            HomeShimmerSkeleton()
        } else if (pagedShows.itemCount == 0 && pagedShows.loadState.refresh is LoadState.NotLoading) {
            // ── Zero-history onboarding empty state ──
            ZeroHistoryOnboarding()
        } else {
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
                
                // ── Recommended Feed ──
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
        }

        // ── Floating Filter Pill (thumb zone — directly above bottom nav) ──
        AnimatedVisibility(
            visible = isScrollingUp && !isInitialLoad,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp) // Directly above the floating bottom nav bar
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
            // ── Shimmer skeleton for hero while loading ──
            ShimmerBox(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ── Zero-history onboarding state ──
@Composable
fun ZeroHistoryOnboarding() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Your feed is empty",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start exploring trending shows and movies.\nSearch for your favorites to build your feed.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { /* Navigate to search – handled by parent nav */ },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Discover Shows", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Shimmer skeleton composables ──

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200)
        ),
        label = "shimmer_offset"
    )
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surface
                    ),
                    start = Offset(shimmerOffset - 300f, 0f),
                    end = Offset(shimmerOffset, 0f)
                )
            )
    )
}

@Composable
fun HomeShimmerSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        userScrollEnabled = false
    ) {
        // Hero skeleton
        item {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            )
        }
        // Section title skeleton
        item {
            Spacer(modifier = Modifier.height(24.dp))
            ShimmerBox(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .width(160.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        // Horizontal row skeleton
        item {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(3) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .width(140.dp)
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .width(100.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
        // Second section skeleton
        item {
            Spacer(modifier = Modifier.height(32.dp))
            ShimmerBox(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .width(200.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(3) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .width(140.dp)
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .width(100.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}
