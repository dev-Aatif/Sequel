package dev.sequel.app.presentation.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.presentation.components.ShowCard
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsState()
    val filter by viewModel.searchFilter.collectAsState()
    val uiState by viewModel.searchState.collectAsState()

    var selectedItemForAction by remember { mutableStateOf<ShowEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var isSearchFocused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp)) {
            // Glassmorphic Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .glassmorphicBackground(RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search movies & tv shows...", color = MaterialTheme.colorScheme.onSurface.copy(0.5f), style = MaterialTheme.typography.bodyLarge)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isSearchFocused = it.isFocused }
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, "Clear",
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.size(24.dp).hapticClickable { viewModel.onQueryChange("") }
                        )
                    }
                }
            }

            // Main Content
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is SearchUiState.Idle -> {
                        ZeroStateDiscovery(
                            onTagClick = { tag -> viewModel.onQueryChange(tag) },
                            onCategoryClick = { genre -> viewModel.onQueryChange(genre) }
                        )
                    }
                    is SearchUiState.Loading -> {
                        SearchResultsShimmer()
                    }
                    is SearchUiState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                    }
                    is SearchUiState.Success -> {
                        val filteredResults = state.results.filter {
                            when (filter) {
                                "TV Shows" -> it.mediaType == "tv"
                                "Movies" -> it.mediaType == "movie"
                                else -> true
                            }
                        }
                        if (filteredResults.isEmpty()) {
                            Text("No results found for '$query'", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(0.5f), modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredResults, key = { it.id }) { show ->
                                    ShowCard(show, onClick = { onShowClick(show.id, show.mediaType) }, onLongClick = { selectedItemForAction = show })
                                }
                            }
                        }
                    }
                }
            }
        }

        // Contextual Filters – appear when search input is focused
        AnimatedVisibility(
            visible = isSearchFocused,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp)
        ) {
            Row(
                modifier = Modifier.glassmorphicBackground(RoundedCornerShape(24.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "TV Shows", "Movies").forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { viewModel.onFilterChange(f) },
                        label = { Text(f) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White),
                        border = null
                    )
                }
            }
        }
    }

    // Bottom sheet for long-press actions
    if (selectedItemForAction != null) {
        val show = selectedItemForAction!!
        ModalBottomSheet(onDismissRequest = { selectedItemForAction = null }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(show.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { viewModel.addToWatchlist(show); selectedItemForAction = null }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add to Watchlist")
                }
                Button(onClick = { viewModel.markAsWatched(show); selectedItemForAction = null }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(8.dp))
                    Text(if (show.mediaType == "movie") "Mark as Watched" else "Mark S1E1 as Watched")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ZeroStateDiscovery(
    onTagClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(2) }) {
            Text("Top Searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }
        item(span = { GridItemSpan(2) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tags = listOf("Dune", "Shogun", "Fallout", "Breaking Bad", "The Bear")
                items(tags.size) { index ->
                    Box(
                        modifier = Modifier
                            .glassmorphicBackground(RoundedCornerShape(16.dp))
                            .hapticClickable { onTagClick(tags[index]) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(tags[index], color = Color.White)
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Spacer(Modifier.height(16.dp))
            Text("Browse Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }
        val categories = listOf(
            "Action" to listOf(Color(0xFFEF4444), Color(0xFF991B1B)),
            "Sci-Fi" to listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A)),
            "Comedy" to listOf(Color(0xFFF59E0B), Color(0xFF92400E)),
            "Drama" to listOf(Color(0xFF10B981), Color(0xFF064E3B)),
            "Horror" to listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)),
            "Anime" to listOf(Color(0xFFEC4899), Color(0xFF831843))
        )
        items(categories.size) { index ->
            val (name, colors) = categories[index]
            Box(
                modifier = Modifier.height(80.dp)
                    .background(Brush.linearGradient(colors), RoundedCornerShape(16.dp))
                    .hapticClickable { onCategoryClick(name) }
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ── Search results shimmer skeleton ──
@Composable
fun SearchResultsShimmer() {
    val transition = rememberInfiniteTransition(label = "search_shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1200)),
        label = "search_shimmer_offset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surface
        ),
        start = Offset(shimmerOffset - 300f, 0f),
        end = Offset(shimmerOffset, 0f)
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(9) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}
