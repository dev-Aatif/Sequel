package dev.sequel.app.presentation.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.presentation.components.ShowCard
import dev.sequel.app.presentation.components.glassmorphicBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsState()
    val filter by viewModel.searchFilter.collectAsState()
    val uiState by viewModel.searchState.collectAsState()

    var active by remember { mutableStateOf(false) }
    var selectedItemForAction by remember { mutableStateOf<ShowEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val view = LocalView.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 120.dp)) {
            // ── Sticky Glassmorphic Search Bar ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .glassmorphicBackground(RoundedCornerShape(24.dp), blurRadius = 16.dp)
            ) {
                DockedSearchBar(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = { active = false },
                    active = active,
                    onActiveChange = { active = it },
                    placeholder = { Text("Search movies & tv shows...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    colors = SearchBarDefaults.colors(
                        containerColor = Color.Transparent,
                        dividerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Dropdown content when active
                    if (query.isEmpty()) {
                        Text(
                            "Recent Searches",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        // We rely on the main UI state below for results, 
                        // but you could put instant autocomplete here.
                    }
                }
            }

            // ── Main Content Area ──
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is SearchUiState.Idle -> {
                        ZeroStateDiscovery()
                    }
                    is SearchUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is SearchUiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
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
                            Text(
                                text = "No results found for '$query'",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredResults, key = { it.id }) { show ->
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

        // ── Contextual Filters ──
        AnimatedVisibility(
            visible = active || query.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp) // Float just above the BottomNav
        ) {
            Row(
                modifier = Modifier
                    .glassmorphicBackground(RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All", "TV Shows", "Movies")
                filters.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { viewModel.onFilterChange(f) },
                        label = { Text(f) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        border = null
                    )
                }
            }
        }
    }

    if (selectedItemForAction != null) {
        val show = selectedItemForAction!!
        ModalBottomSheet(
            onDismissRequest = { selectedItemForAction = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = show.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = {
                        viewModel.addToWatchlist(show)
                        selectedItemForAction = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Watchlist")
                }
                
                Button(
                    onClick = {
                        viewModel.markAsWatched(show)
                        selectedItemForAction = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (show.mediaType == "movie") "Mark as Watched" else "Mark S1E1 as Watched")
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ZeroStateDiscovery() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(2) }) {
            Text(
                "Top Searches",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item(span = { GridItemSpan(2) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tags = listOf("Dune", "Shogun", "Fallout", "Breaking Bad", "The Bear")
                items(tags.size) { index ->
                    Box(
                        modifier = Modifier
                            .glassmorphicBackground(RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(tags[index], color = Color.White)
                    }
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Browse Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
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
                modifier = Modifier
                    .height(80.dp)
                    .background(
                        brush = Brush.linearGradient(colors),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
