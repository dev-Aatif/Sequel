package dev.sequel.app.presentation.screens.watchlist

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkAdd
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
                val tabs = listOf("Up Next", "Watchlist")
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
                    if (upNextItems.isEmpty()) {
                        EmptyTabState(
                            icon = Icons.Default.Tv,
                            title = "Nothing up next",
                            subtitle = "Start tracking shows to see your next episodes here",
                            ctaLabel = "Explore Trending",
                            ctaIcon = Icons.Default.Explore,
                            onCtaClick = { /* Handled by parent navigation */ }
                        )
                    } else {
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
                }
                "Watchlist" -> {
                    if (planToWatchItems.isEmpty()) {
                        EmptyTabState(
                            icon = Icons.Outlined.BookmarkAdd,
                            title = "Your watchlist is empty",
                            subtitle = "Long-press any show to add it here",
                            ctaLabel = "Search Shows",
                            ctaIcon = Icons.Default.Search,
                            onCtaClick = { /* Handled by parent navigation */ }
                        )
                    } else {
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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

// ── Engaging empty state with icon + text + CTA button ──
@Composable
fun EmptyTabState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ctaLabel: String,
    ctaIcon: ImageVector,
    onCtaClick: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Icon(ctaIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(ctaLabel, fontWeight = FontWeight.Bold)
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
            .glassmorphicBackground(RoundedCornerShape(16.dp), blurRadius = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = TmdbImageUtil.posterUrl(item.posterPath),
                    contentDescription = "${item.title} poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 0.dp))
                )
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    if (item.nextEpisodeName != null) {
                        Text(item.nextEpisodeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else if (item.mediaType == "movie") {
                        Text("Movie", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Box(
                    Modifier.padding(end = 16.dp).size(48.dp).glassmorphicBackground(RoundedCornerShape(24.dp)).hapticClickable { onMarkWatched() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.CheckCircleOutline, "Mark as watched", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }

            // ── Progress bar for active TV shows ──
            if (item.mediaType == "tv" && item.seasonNumber != null && item.episodeNumber != null) {
                val episodesWatched = ((item.seasonNumber - 1) * 10) + (item.episodeNumber - 1) // Rough estimate
                val totalEstimate = item.seasonNumber * 10 // Rough estimate
                val progress = if (totalEstimate > 0) (episodesWatched.toFloat() / totalEstimate).coerceIn(0.01f, 1f) else 0.01f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(600),
                    label = "progress_anim"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = if (animatedProgress >= 0.99f) 16.dp else 0.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
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
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = TmdbImageUtil.posterUrl(item.posterPath),
                contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(if (item.mediaType.name.lowercase() == "movie") "Movie" else "TV Show", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
