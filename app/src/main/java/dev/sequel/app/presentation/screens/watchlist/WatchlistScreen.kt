package dev.sequel.app.presentation.screens.watchlist

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.sequel.app.data.remote.tmdb.TmdbImageUtil
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val upNextItems by viewModel.upNextItems.collectAsState()
    val planToWatchItems by viewModel.planToWatchItems.collectAsState()
    val watchedTvItems by viewModel.watchedTvItems.collectAsState()
    val watchedMovieItems by viewModel.watchedMovieItems.collectAsState()
    
    val activeTab by viewModel.currentTab.collectAsState()
    var watchedSubTab by remember { mutableStateOf("Shows") } // "Shows" or "Movies"
    
    var selectedItemForAction by remember { mutableStateOf<Any?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val view = LocalView.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 3-Tab Glassmorphic Segmented Control ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .glassmorphicBackground(RoundedCornerShape(32.dp))
                .padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Up Next", "Watchlist", "Watched").forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .hapticClickable { viewModel.setTab(tab) }
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

        // ── Main Content ──
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                "Up Next" -> {
                    if (upNextItems.isEmpty()) {
                        EmptyTabState(Icons.Default.Tv, "Nothing up next", "Start tracking shows to see your next episodes here", "Explore Trending", Icons.Default.Explore) {
                            onNavigateHome()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(upNextItems, key = { "${it.showId}_${it.nextEpisodeId}" }) { item ->
                                UpNextGlassmorphicRow(
                                    item = item,
                                    onClick = { onShowClick(item.showId, item.mediaType) },
                                    onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); selectedItemForAction = item },
                                    onMarkWatched = { viewModel.markAsWatched(item) }
                                )
                            }
                        }
                    }
                }
                "Watchlist" -> {
                    if (planToWatchItems.isEmpty()) {
                        EmptyTabState(Icons.Outlined.BookmarkAdd, "Your watchlist is empty", "Bookmark any show to add it here", "Search Shows", Icons.Default.Search) {
                            onNavigateSearch()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(planToWatchItems, key = { it.tmdbId }) { item ->
                                PlanToWatchGlassmorphicRow(
                                    item = item,
                                    onClick = { onShowClick(item.tmdbId, item.mediaType.name.lowercase()) },
                                    onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); selectedItemForAction = item }
                                )
                            }
                        }
                    }
                }
                "Watched" -> {
                    Column(Modifier.fillMaxSize()) {
                        // Shows / Movies sub-toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("Shows" to Icons.Default.Tv, "Movies" to Icons.Default.Movie).forEach { (label, icon) ->
                                val isSelected = watchedSubTab == label
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { watchedSubTab = label },
                                    label = { Text(label) },
                                    leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White
                                    ),
                                    border = null
                                )
                            }
                        }

                        val displayItems = if (watchedSubTab == "Shows") watchedTvItems else watchedMovieItems

                        if (displayItems.isEmpty()) {
                            EmptyTabState(
                                icon = if (watchedSubTab == "Shows") Icons.Default.Tv else Icons.Default.Movie,
                                title = "No ${watchedSubTab.lowercase()} watched yet",
                                subtitle = "Mark episodes or movies as watched to see them here",
                                ctaLabel = "Explore", ctaIcon = Icons.Default.Explore
                            ) {
                                onNavigateHome()
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(displayItems, key = { it.showId }) { item ->
                                    WatchedGlassmorphicRow(
                                        item = item,
                                        onClick = { onShowClick(item.showId, item.mediaType) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Bottom Sheet Actions ──
    if (selectedItemForAction != null) {
        ModalBottomSheet(onDismissRequest = { selectedItemForAction = null }, sheetState = sheetState) {
            val title = when (selectedItemForAction) {
                is UpNextItem -> (selectedItemForAction as UpNextItem).title
                is dev.sequel.app.data.local.entity.WatchlistEntity -> (selectedItemForAction as dev.sequel.app.data.local.entity.WatchlistEntity).title
                else -> ""
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (selectedItemForAction is dev.sequel.app.data.local.entity.WatchlistEntity) {
                    Button(
                        onClick = { 
                            viewModel.removeFromWatchlist((selectedItemForAction as dev.sequel.app.data.local.entity.WatchlistEntity).tmdbId)
                            selectedItemForAction = null 
                        }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Icon(Icons.Outlined.BookmarkBorder, null); Spacer(Modifier.width(8.dp)); Text("Remove from Watchlist")
                    }
                } else {
                    Button(onClick = { selectedItemForAction = null }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.VisibilityOff, null); Spacer(Modifier.width(8.dp)); Text("Dismiss")
                    }
                }
                Button(onClick = { selectedItemForAction = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text("Share")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Empty Tab State ──
@Composable
fun EmptyTabState(icon: ImageVector, title: String, subtitle: String, ctaLabel: String, ctaIcon: ImageVector, onCtaClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .glassmorphicBackground(RoundedCornerShape(32.dp), blurRadius = 24.dp)
                .padding(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.7f), textAlign = TextAlign.Center, lineHeight = 20.sp)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Icon(ctaIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(ctaLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── Up Next Row ──
@Composable
fun UpNextGlassmorphicRow(item: UpNextItem, onClick: () -> Unit, onLongClick: () -> Unit, onMarkWatched: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().glassmorphicBackground(RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() }) }
    ) {
        Column {
            Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = TmdbImageUtil.posterUrl(item.posterPath), contentDescription = "${item.title} poster",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = 16.dp))
                )
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    if (item.nextEpisodeName != null) {
                        Text(item.nextEpisodeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else if (item.mediaType == "movie") {
                        Text("Movie", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                }
                Box(Modifier.padding(end = 16.dp).size(48.dp).glassmorphicBackground(RoundedCornerShape(24.dp)).hapticClickable { onMarkWatched() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CheckCircleOutline, "Mark watched", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            // Progress bar for TV shows
            if (item.mediaType == "tv" && item.seasonNumber != null && item.episodeNumber != null) {
                val rough = ((item.seasonNumber - 1) * 10 + (item.episodeNumber - 1)).toFloat() / (item.seasonNumber * 10).coerceAtLeast(1)
                val animatedProgress by animateFloatAsState(rough.coerceIn(0.01f, 1f), tween(600), label = "progress")
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(0.1f))) {
                    Box(Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(RoundedCornerShape(bottomStart = 16.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

// ── Watchlist Row ──
@Composable
fun PlanToWatchGlassmorphicRow(item: dev.sequel.app.data.local.entity.WatchlistEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(140.dp).glassmorphicBackground(RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() }) }
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = TmdbImageUtil.posterUrl(item.posterPath), contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(if (item.mediaType.name.lowercase() == "movie") "Movie" else "TV Show", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

// ── Watched Row (with status tag) ──
@Composable
fun WatchedGlassmorphicRow(item: WatchedItem, onClick: () -> Unit) {
    val statusColor = when (item.statusTag) {
        "Completed" -> Color(0xFF10B981)
        "Up to Date" -> Color(0xFF3B82F6)
        "In Progress" -> Color(0xFFF59E0B)
        "Watched" -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.onSurface.copy(0.6f)
    }

    Box(
        modifier = Modifier.fillMaxWidth().glassmorphicBackground(RoundedCornerShape(16.dp)).hapticClickable { onClick() }
    ) {
        Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = TmdbImageUtil.posterUrl(item.posterPath), contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                // Status tag chip
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(item.statusTag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusColor)
                }
                Spacer(Modifier.height(6.dp))
                if (item.mediaType == "tv" && item.totalEpisodes != null) {
                    Text("${item.episodesWatched} / ${item.totalEpisodes} episodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
            // Watched checkmark
            Icon(Icons.Filled.CheckCircle, "Watched", tint = statusColor, modifier = Modifier.padding(end = 16.dp).size(28.dp))
        }
    }
}
