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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
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
import dev.sequel.app.presentation.components.SharedActionBottomSheet
import dev.sequel.app.presentation.components.ShowCard
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable
import dev.sequel.app.presentation.state.BottomSheetUiState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pagedShows = viewModel.pagedShows.collectAsLazyPagingItems()
    val currentType by viewModel.mediaType.collectAsState()
    val continueWatchingTvShows by viewModel.continueWatchingTvShows.collectAsState()
    val isProcessingAction by viewModel.isProcessingAction.collectAsState()
    val bottomSheetState by viewModel.bottomSheetState.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    var selectedItemForAction by remember { mutableStateOf<ShowEntity?>(null) }

    LaunchedEffect(selectedItemForAction) {
        selectedItemForAction?.let { viewModel.openBottomSheet(it) }
    }
    
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

        val hasError = pagedShows.loadState.refresh is LoadState.Error && pagedShows.itemCount == 0

        if (isInitialLoad) {
            // ── Shimmer Skeleton while initial data loads ──
            HomeShimmerSkeleton()
        } else if (hasError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.Search, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text("Failed to load feed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text("Check your connection and try again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { pagedShows.retry() }) {
                        Text("Retry")
                    }
                }
            }
        } else if (pagedShows.itemCount == 0 && continueWatchingTvShows.isEmpty()) {
            ZeroHistoryOnboarding(onNavigateToSearch = onNavigateToSearch)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp) // Space for floating bottom nav
            ) {
                // ── Hero Section ──
                item {
                    var heroIndex by rememberSaveable(currentType) { mutableIntStateOf(0) }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val firstShow = if (pagedShows.itemCount > heroIndex) pagedShows[heroIndex] else if (pagedShows.itemCount > 0) pagedShows[0] else null
                    HeroBanner(
                        show = firstShow,
                        onShowClick = { onShowClick(it.id, it.mediaType) },
                        onAddToWatchlist = { show ->
                            if (!isProcessingAction) {
                                viewModel.addToWatchlist(show)
                                android.widget.Toast.makeText(context, "Added to Watchlist", android.widget.Toast.LENGTH_SHORT).show()
                                heroIndex++
                            }
                        }
                    )
                }

                // ── Dynamic Top Section ──
                item {
                    if (currentType == "tv") {
                        if (continueWatchingTvShows.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Continue Watching", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { onNavigateToWatchlist() }) {
                                        Text("View All", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                val cwState = rememberLazyListState()
                                LazyRow(
                                    state = cwState,
                                    flingBehavior = rememberSnapFlingBehavior(lazyListState = cwState),
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(continueWatchingTvShows, key = { it.id }) { show ->
                                        Box(modifier = Modifier.width(140.dp)) {
                                            ShowCard(
                                                show = show,
                                                onClick = { onShowClick(show.id, show.mediaType) },
                                                onLongClick = { selectedItemForAction = show }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (currentType == "movie") {
                        val trendingThisWeekMovies by viewModel.trendingThisWeekMovies.collectAsState()
                        if (trendingThisWeekMovies.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                Text("Trending This Week", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                                val loopState = rememberLazyListState()
                                LazyRow(
                                    state = loopState,
                                    flingBehavior = rememberSnapFlingBehavior(lazyListState = loopState),
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(trendingThisWeekMovies, key = { it.id }) { show ->
                                        Box(modifier = Modifier.width(140.dp)) {
                                            ShowCard(
                                                show = show,
                                                onClick = { onShowClick(show.id, show.mediaType) },
                                                onLongClick = { selectedItemForAction = show }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                                        onLongClick = { selectedItemForAction = show }
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

    if (selectedItemForAction != null) {
        SharedActionBottomSheet(
            sheetState = sheetState,
            bottomSheetState = bottomSheetState,
            isProcessingAction = isProcessingAction,
            onDismissRequest = { selectedItemForAction = null },
            onToggleWatchlist = { onSuccess ->
                viewModel.toggleWatchlist(onSuccess)
            },
            onToggleWatched = { onSuccess ->
                viewModel.toggleWatched(onSuccess)
            },
            onShowDetailClick = {
                onShowClick(selectedItemForAction!!.id, selectedItemForAction!!.mediaType)
            }
        )
    }
}

@Composable
fun HeroBanner(
    show: ShowEntity?,
    onShowClick: (ShowEntity) -> Unit,
    onAddToWatchlist: (ShowEntity) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(MaterialTheme.colorScheme.surface)
            .hapticClickable { if (show != null) onShowClick(show) }
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
                        text = "FEATURED",
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
                        .semantics { role = androidx.compose.ui.semantics.Role.Button }
                        .glassmorphicBackground(RoundedCornerShape(16.dp))
                        .hapticClickable { onAddToWatchlist(show) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add to Watchlist", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Watchlist", color = Color.White, fontWeight = FontWeight.SemiBold)
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
fun ZeroHistoryOnboarding(
    onNavigateToSearch: () -> Unit
) {
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
                onClick = onNavigateToSearch,
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
        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp),
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

    }
}
