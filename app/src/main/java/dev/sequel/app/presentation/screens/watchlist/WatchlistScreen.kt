package dev.sequel.app.presentation.screens.watchlist

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.sequel.app.data.remote.tmdb.TmdbImageUtil
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val upNextItems by viewModel.upNextItems.collectAsState()
    val planToWatchItems by viewModel.planToWatchItems.collectAsState()
    
    // For this redesign, we'll map currentTab to our custom Segmented Tabs
    // Let's keep local state for the tab if we are adding "Lists" which might not be in ViewModel
    var activeTab by remember { mutableStateOf("Up Next") }
    
    var selectedItemForAction by remember { mutableStateOf<Any?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val view = LocalView.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Glassmorphic Segmented Tabs ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .glassmorphicBackground(RoundedCornerShape(32.dp), blurRadius = 12.dp)
                .padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val tabs = listOf("Up Next", "Watchlist", "Lists")
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .hapticClickable { activeTab = tab }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // ── Main Content Area ──
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                "Up Next" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(upNextItems, key = { "${it.showId}_${it.nextEpisodeId}" }) { item ->
                            UpNextGlassmorphicRow(
                                item = item,
                                onClick = { onShowClick(item.showId, item.mediaType) },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    selectedItemForAction = item
                                },
                                onMarkWatched = { viewModel.markAsWatched(item) }
                            )
                        }
                    }
                }
                "Watchlist" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(planToWatchItems, key = { it.tmdbId }) { item ->
                            PlanToWatchGlassmorphicRow(
                                item = item,
                                onClick = { onShowClick(item.tmdbId, item.mediaType.name.lowercase()) },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    selectedItemForAction = item
                                }
                            )
                        }
                    }
                }
                "Lists" -> {
                    MyListsStackedGrid()
                }
            }
        }
    }

    if (selectedItemForAction != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedItemForAction = null },
            sheetState = sheetState
        ) {
            val title = when (selectedItemForAction) {
                is UpNextItem -> (selectedItemForAction as UpNextItem).title
                is dev.sequel.app.data.local.entity.WatchlistEntity -> (selectedItemForAction as dev.sequel.app.data.local.entity.WatchlistEntity).title
                else -> ""
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Button(onClick = { selectedItemForAction = null }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hide from list")
                }
                
                Button(onClick = { selectedItemForAction = null }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun UpNextGlassmorphicRow(
    item: UpNextItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkWatched: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .glassmorphicBackground(RoundedCornerShape(16.dp), blurRadius = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2:3 Aspect Ratio Poster
            AsyncImage(
                model = TmdbImageUtil.posterUrl(item.posterPath),
                contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (item.nextEpisodeName != null) {
                    Text(
                        text = item.nextEpisodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (item.mediaType == "movie") {
                    Text(
                        text = "Movie",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Right Action
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(48.dp)
                    .glassmorphicBackground(RoundedCornerShape(24.dp))
                    .hapticClickable { onMarkWatched() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Mark as watched",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun PlanToWatchGlassmorphicRow(
    item: dev.sequel.app.data.local.entity.WatchlistEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .glassmorphicBackground(RoundedCornerShape(16.dp), blurRadius = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2:3 Aspect Ratio Poster
            AsyncImage(
                model = TmdbImageUtil.posterUrl(item.posterPath),
                contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (item.mediaType.name.lowercase() == "movie") "Movie" else "TV Show",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun MyListsStackedGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val dummyLists = listOf("Favorites", "Summer 2026", "Action Packed", "To Watch with Family")
        items(dummyLists.size) { index ->
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Stacked Image Representation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.85f)
                            .align(Alignment.TopEnd)
                            .glassmorphicBackground(RoundedCornerShape(12.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.92f)
                            .align(Alignment.Center)
                            .padding(end = 8.dp, top = 8.dp)
                            .glassmorphicBackground(RoundedCornerShape(12.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.9f)
                            .align(Alignment.BottomStart)
                            .glassmorphicBackground(RoundedCornerShape(12.dp), surfaceTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        // Normally an AsyncImage goes here for the cover
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dummyLists[index],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(index + 1) * 4} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
