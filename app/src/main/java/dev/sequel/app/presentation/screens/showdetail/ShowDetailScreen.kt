package dev.sequel.app.presentation.screens.showdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.sequel.app.data.remote.tmdb.TmdbImageUtil
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable
import dev.sequel.app.presentation.components.spoilerShield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    onSeasonClick: (showId: Int, seasonNumber: Int) -> Unit,
    onBackClick: () -> Unit,
    onShowClick: ((showId: Int, mediaType: String) -> Unit)? = null,
    viewModel: DetailViewModel = hiltViewModel(),
    reviewViewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val communityState by reviewViewModel.communityState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is DetailUiState.Success) {
            val show = (uiState as DetailUiState.Success).show
            reviewViewModel.loadReviews(show.id, null, null)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (uiState is DetailUiState.Success) {
                Box(modifier = Modifier.padding(16.dp)) {
                    ReviewInputBar(
                        onPostReview = { text, rating, isSpoiler ->
                            reviewViewModel.postReview(text, rating, isSpoiler)
                        }
                    )
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is DetailUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadShowDetail() }) { Text("Retry") }
                        }
                    }
                }
                is DetailUiState.Success -> {
                    ShowDetailContent(
                        state = state,
                        communityState = communityState,
                        onToggleWatched = { viewModel.toggleEpisodeWatched(it) },
                        onToggleMovieWatched = { viewModel.toggleMovieWatched(it) },
                        onToggleWatchlist = { viewModel.toggleWatchlist() },
                        onRecommendationClick = { id, type -> onShowClick?.invoke(id, type) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Floating Glassmorphic Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .glassmorphicBackground(RoundedCornerShape(32.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp).hapticClickable { onBackClick() }
                )
                Spacer(Modifier.width(16.dp))
                if (uiState is DetailUiState.Success) {
                    Text(
                        (uiState as DetailUiState.Success).show.title,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // Watchlist bookmark
                    val inWatchlist = (uiState as DetailUiState.Success).isInWatchlist
                    Icon(
                        if (inWatchlist) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        "Watchlist",
                        tint = if (inWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp).hapticClickable { viewModel.toggleWatchlist() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowDetailContent(
    state: DetailUiState.Success,
    communityState: CommunityState,
    onToggleWatched: (EpisodeUi) -> Unit,
    onToggleMovieWatched: (Boolean) -> Unit,
    onToggleWatchlist: () -> Unit,
    onRecommendationClick: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val show = state.show

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp)) {
        // Hero Backdrop
        item {
            Box(Modifier.fillMaxWidth().height(400.dp)) {
                AsyncImage(
                    model = TmdbImageUtil.backdropUrl(show.backdropPath),
                    contentDescription = "${show.title} backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(0.5f), MaterialTheme.colorScheme.background),
                            startY = 300f
                        )
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    Text(show.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(String.format("%.1f", show.voteAverage), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        show.status?.let {
                            Text("  •  $it", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(0.7f))
                        }
                    }
                }
            }
        }

        // Synopsis
        if (show.overview.isNotBlank()) {
            item {
                Text(show.overview, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(0.8f), modifier = Modifier.padding(24.dp, 12.dp))
            }
        }

        // Drop-Off Insight
        if (state.dropOffInsight != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp, 12.dp)
                        .glassmorphicBackground(RoundedCornerShape(16.dp), surfaceTint = Color(0x66FFB300), borderColor = Color(0x33FFB300))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(Color(0xFFFFB300).copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Info, "Insight", tint = Color(0xFFFFB300))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Pro Insight", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                            Text(state.dropOffInsight, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }
            }
        }

        // Movie: Watch toggle
        if (show.mediaType == "movie") {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp).height(64.dp)
                        .glassmorphicBackground(RoundedCornerShape(32.dp), surfaceTint = if (state.isMovieWatched) MaterialTheme.colorScheme.primary.copy(0.8f) else Color(0xCC1A1D24))
                        .hapticClickable { onToggleMovieWatched(state.isMovieWatched) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.isMovieWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(if (state.isMovieWatched) "Watched" else "Mark Watched", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        } else {
            // TV: Seasons & Episodes
            if (state.seasons.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text("Seasons & Episodes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp, 8.dp))
                }
                state.seasons.forEach { season ->
                    item(key = "season_${season.seasonNumber}") {
                        SeasonHeader(season = season, onToggleWatched = onToggleWatched)
                    }
                }
            }
        }

        // Recommendations / More Like This
        if (state.recommendations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(32.dp))
                Text("More Like This", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp, 8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.recommendations, key = { it.id }) { rec ->
                        Column(
                            modifier = Modifier.width(120.dp).hapticClickable { onRecommendationClick(rec.id, rec.mediaType) }
                        ) {
                            AsyncImage(
                                model = TmdbImageUtil.posterUrl(rec.posterPath),
                                contentDescription = rec.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(rec.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(String.format("%.1f", rec.voteAverage), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                        }
                    }
                }
            }
        }

        // Community Reviews
        item {
            Spacer(Modifier.height(32.dp))
            Text("Community Reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp, 8.dp))
        }
        when (communityState) {
            is CommunityState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is CommunityState.Error -> item {
                Text("Failed to load reviews.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
            }
            is CommunityState.Success -> {
                if (communityState.reviews.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp, 8.dp).glassmorphicBackground(RoundedCornerShape(16.dp)).padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Be the first to share your thoughts!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                        }
                    }
                } else {
                    items(communityState.reviews.size, key = { communityState.reviews[it].id ?: it }) { index ->
                        ReviewCard(communityState.reviews[index], state.isMovieWatched || state.seasons.all { s -> s.episodes.all { it.isWatched } })
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonHeader(season: SeasonUi, onToggleWatched: (EpisodeUi) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val watchedCount = season.episodes.count { it.isWatched }
    val totalCount = season.episodes.size
    val progress = if (totalCount > 0) watchedCount.toFloat() / totalCount else 0f

    Column(Modifier.padding(24.dp, 8.dp)) {
        Box(Modifier.fillMaxWidth().glassmorphicBackground(RoundedCornerShape(16.dp)).hapticClickable { expanded = !expanded }) {
            Column {
                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(season.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("$watchedCount / $totalCount watched", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.onSurface)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(0.1f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(top = 8.dp)) {
                season.episodes.forEach { EpisodeRow(it, onToggleWatched) }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeUi, onToggleWatched: (EpisodeUi) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggleWatched(episode) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = TmdbImageUtil.stillUrl(episode.stillPath), 
            contentDescription = "Episode ${episode.episodeNumber}",
            contentScale = ContentScale.Crop, 
            modifier = Modifier.size(100.dp, 56.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("E${episode.episodeNumber}  ${episode.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episode.runtime?.let { Text("${it}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
        }
        Box(Modifier.size(48.dp).hapticClickable { onToggleWatched(episode) }, contentAlignment = Alignment.Center) {
            Icon(
                if (episode.isWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                if (episode.isWatched) "Unwatch" else "Watch",
                tint = if (episode.isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun ReviewCard(review: dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto, isWatched: Boolean) {
    Box(Modifier.fillMaxWidth().padding(24.dp, 6.dp).glassmorphicBackground(RoundedCornerShape(16.dp))) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, "User", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Community Member", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (review.isSpoiler) Text("SPOILER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            if (!review.reviewText.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(review.reviewText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
                    modifier = Modifier.spoilerShield(review.isSpoiler, isWatched))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewInputBar(onPostReview: (String, Int?, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var isSpoiler by remember { mutableStateOf(false) }
    var selectedRating by remember { mutableIntStateOf(0) } // 0 means no rating

    Box(Modifier.fillMaxWidth().glassmorphicBackground(RoundedCornerShape(32.dp)).padding(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            // Rating Row (1-10)
            Text("Rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                (1..10).forEach { rating ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(if (selectedRating == rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.08f))
                            .hapticClickable { selectedRating = if (selectedRating == rating) 0 else rating },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$rating", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = if (selectedRating == rating) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    text, { text = it },
                    placeholder = { Text("Share your thoughts...", color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(0.05f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(0.05f),
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(8.dp))
                // Spoiler toggle
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (isSpoiler) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.onSurface.copy(0.05f))
                        .hapticClickable { isSpoiler = !isSpoiler },
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                        color = if (isSpoiler) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(8.dp))
                // Send
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .hapticClickable { onPostReview(text, if (selectedRating > 0) selectedRating else null, isSpoiler); text = ""; isSpoiler = false; selectedRating = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
