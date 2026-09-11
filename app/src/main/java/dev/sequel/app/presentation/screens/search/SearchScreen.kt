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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import dev.sequel.app.presentation.components.SharedActionBottomSheet
import dev.sequel.app.presentation.components.ShowCard
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable
import dev.sequel.app.presentation.state.BottomSheetUiState

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
    
    val isProcessingAction by viewModel.isProcessingAction.collectAsState()
    val bottomSheetState by viewModel.bottomSheetState.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(selectedItemForAction) {
        selectedItemForAction?.let { viewModel.openBottomSheet(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = 100.dp)
        ) {
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
                            modifier = Modifier.size(24.dp).semantics { role = Role.Button }.hapticClickable { viewModel.onQueryChange("") }
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
                            onCategoryClick = { genreName, genreId -> viewModel.onCategoryClick(genreName, genreId) }
                        )
                    }
                    is SearchUiState.Loading -> {
                        SearchResultsShimmer()
                    }
                    is SearchUiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(state.message, color = MaterialTheme.colorScheme.onBackground.copy(0.7f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::retrySearch, modifier = Modifier.semantics { role = Role.Button }) {
                                Text("Retry")
                            }
                        }
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

        // Contextual Filters – appear when search input is focused or query is not empty
        AnimatedVisibility(
            visible = isSearchFocused || query.isNotEmpty(),
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
fun ZeroStateDiscovery(
    onTagClick: (String) -> Unit,
    onCategoryClick: (String, Int) -> Unit
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
                            .semantics { role = Role.Button }
                            .glassmorphicBackground(RoundedCornerShape(16.dp))
                            .hapticClickable { onTagClick(tags[index]) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
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
            Triple("Action", 28, listOf(Color(0xFFEF4444), Color(0xFF991B1B))),
            Triple("Sci-Fi", 878, listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A))),
            Triple("Comedy", 35, listOf(Color(0xFFF59E0B), Color(0xFF92400E))),
            Triple("Drama", 18, listOf(Color(0xFF10B981), Color(0xFF064E3B))),
            Triple("Horror", 27, listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95))),
            Triple("Anime", 16, listOf(Color(0xFFEC4899), Color(0xFF831843)))
        )
        items(categories.size) { index ->
            val (name, id, colors) = categories[index]
            Box(
                modifier = Modifier.height(80.dp)
                    .background(Brush.linearGradient(colors), RoundedCornerShape(16.dp))
                    .semantics { role = Role.Button }
                    .hapticClickable { onCategoryClick(name, id) }
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
