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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.sequel.app.data.remote.tmdb.TmdbImageUtil
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable
import dev.sequel.app.presentation.components.spoilerShield

import android.annotation.SuppressLint

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
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

    val showId = (uiState as? DetailUiState.Success)?.show?.id
    val mediaType = (uiState as? DetailUiState.Success)?.show?.mediaType
    LaunchedEffect(showId, mediaType) {
        if (showId != null && mediaType != null) {
            reviewViewModel.loadReviews(showId, mediaType, null, null)
        }
    }

    // No fixed bottom bar — review input is inline in the scroll content
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is DetailUiState.Error -> {
                    dev.sequel.app.presentation.components.BeautifulErrorState(
                        error = state.error,
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = { viewModel.loadShowDetail() }
                    )
                }
                is DetailUiState.Success -> {
                    ShowDetailContent(
                        state = state,
                        communityState = communityState,
                        currentUserId = reviewViewModel.currentUserId,
                        onToggleWatched = { viewModel.toggleEpisodeWatched(it) },
                        onToggleMovieWatched = { viewModel.toggleMovieWatched(it) },
                        onToggleWatchlist = { viewModel.toggleWatchlist() },
                        onFetchSeason = { viewModel.fetchSeasonEpisodes(it) },
                        onSeasonClick = onSeasonClick,
                        onRecommendationClick = { id, type -> onShowClick?.invoke(id, type) },
                        onPostReview = { text, isSpoiler -> reviewViewModel.postReview(text, isSpoiler) },
                        onEditReview = { reviewId, text, isSpoiler -> reviewViewModel.editReview(reviewId, text, isSpoiler) },
                        onSubmitRating = { rating -> reviewViewModel.submitRating(rating) },
                        onDeleteReview = { reviewId -> reviewViewModel.deleteReview(reviewId) },
                        onRetryReviews = { 
                            showId?.let { sId -> 
                                mediaType?.let { mType -> 
                                    reviewViewModel.loadReviews(sId, mType, null, null) 
                                } 
                            } 
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Floating Glassmorphic Top Bar
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(
                        top = 16.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        start = 16.dp,
                        end = 16.dp
                    )
                    .glassmorphicBackground(RoundedCornerShape(32.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp).hapticClickable { onBackClick() })
                Spacer(Modifier.width(16.dp))
                if (uiState is DetailUiState.Success) {
                    val s = uiState as DetailUiState.Success
                    Text(s.show.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShowDetailContent(
    state: DetailUiState.Success,
    communityState: CommunityState,
    currentUserId: String?,
    onToggleWatched: (EpisodeUi) -> Unit,
    onToggleMovieWatched: (Boolean) -> Unit,
    onToggleWatchlist: () -> Unit,
    onFetchSeason: (Int) -> Unit,
    onSeasonClick: (Int, Int) -> Unit,
    onRecommendationClick: (Int, String) -> Unit,
    onPostReview: (String, Boolean) -> Unit,
    onEditReview: (String, String, Boolean) -> Unit,
    onSubmitRating: (Int) -> Unit,
    onDeleteReview: (String) -> Unit,
    onRetryReviews: () -> Unit,
    modifier: Modifier = Modifier
) {
    val show = state.show
    var showReviewInput by remember { mutableStateOf(false) }
    var editingReviewId by remember { mutableStateOf<String?>(null) }
    var editingReviewText by remember { mutableStateOf("") }
    var editingReviewSpoiler by remember { mutableStateOf(false) }

    var showRatingDialog by remember { mutableStateOf(false) }
    var showNotWatchedDialog by remember { mutableStateOf(false) }

    val isFullyWatched = remember(show.mediaType, state.isMovieWatched, state.seasons, state.watchedEpisodeKeys) {
        if (show.mediaType == "movie") {
            state.isMovieWatched
        } else {
            // For TV, consider it fully watched if all aired episodes in the last season are watched, or just > 0 watched
            // The instruction says "mark the movie or show as watched". Let's check if they have watched anything.
            val totalWatched = state.seasons.sumOf { it.watchedCount }
            totalWatched > 0
        }
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp
    )) {
        // ── Hero Backdrop & Poster & Metadata ──
        item {
            Box(Modifier.fillMaxWidth()) {
                // Blurred backdrop for atmosphere
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(TmdbImageUtil.backdropUrl(show.backdropPath ?: show.posterPath)).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop, 
                    modifier = Modifier.matchParentSize().background(Color(0xFF0F1115))
                )
                Box(Modifier.matchParentSize().background(Color(0xFF0F1115).copy(alpha = 0.6f)))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(0.8f), MaterialTheme.colorScheme.background), startY = 150f
                )))
                
                // Centered Poster with space around it
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(TmdbImageUtil.posterUrl(show.posterPath)).crossfade(true).build(),
                        contentDescription = "${show.title} poster",
                        contentScale = ContentScale.Fit, 
                        modifier = Modifier
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(show.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(String.format("%.1f", show.voteAverage), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        show.status?.let { Text("  •  $it", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(0.7f)) }
                    }
                    
                    // ── Metadata Text ──
                    val metaLine1 = buildList {
                        show.firstAirDate?.take(4)?.let { add(it) }
                        show.contentRating?.let { add(it) }
                        if (show.mediaType == "movie") {
                            show.runtime?.let { runtime ->
                                val hours = runtime / 60
                                val mins = runtime % 60
                                add(if (hours > 0) "${hours}h ${mins}m" else "${mins}m")
                            }
                        }
                    }.joinToString("  •  ")

                    val metaLine2 = if (show.mediaType == "tv") {
                        buildList {
                            show.numberOfSeasons?.let { add("$it Seasons") }
                            show.numberOfEpisodes?.let { add("$it Episodes") }
                            show.episodeRuntime?.let { add("~${it}m") }
                        }.joinToString("  •  ")
                    } else null

                    val metaLine3 = show.genresDisplay

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
                    ) {
                        if (metaLine1.isNotEmpty()) {
                            Text(metaLine1, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(0.9f), fontWeight = FontWeight.Bold)
                        }
                        if (!metaLine3.isNullOrEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(metaLine3, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
                        }
                        if (metaLine2?.isNotEmpty() == true) {
                            Spacer(Modifier.height(4.dp))
                            Text(metaLine2, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary.copy(0.9f), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Action Buttons: + Watchlist | Mark Watched (grouped) ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Watchlist button
                Box(
                    Modifier.weight(1f).height(56.dp)
                        .glassmorphicBackground(RoundedCornerShape(28.dp),
                            surfaceTint = if (state.isInWatchlist) MaterialTheme.colorScheme.primary.copy(0.3f) else Color(0xCC1A1D24))
                        .hapticClickable { onToggleWatchlist() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.isInWatchlist) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null,
                            tint = if (state.isInWatchlist) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isInWatchlist) "In Watchlist" else "+ Watchlist",
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Mark Watched button (movie only — for TV, episodes have individual toggles)
                if (show.mediaType == "movie") {
                    Box(
                        Modifier.weight(1f).height(56.dp)
                            .glassmorphicBackground(RoundedCornerShape(28.dp),
                                surfaceTint = if (state.isMovieWatched) MaterialTheme.colorScheme.primary.copy(0.8f) else Color(0xCC1A1D24))
                            .hapticClickable { 
                                onToggleMovieWatched(!state.isMovieWatched)
                                if (!state.isMovieWatched && state.userRating == null) {
                                    showRatingDialog = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (state.isMovieWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline, null,
                                tint = Color.White, modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isMovieWatched) "Watched" else "Mark Watched",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // ── Your Rating Block ──
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Rating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .glassmorphicBackground(
                            RoundedCornerShape(20.dp),
                            surfaceTint = if (state.userRating != null) MaterialTheme.colorScheme.primary.copy(0.2f) else Color(0xCC1A1D24),
                            borderColor = if (state.userRating != null) MaterialTheme.colorScheme.primary.copy(0.5f) else Color.White.copy(0.1f)
                        )
                        .hapticClickable { 
                            if (!isFullyWatched) {
                                showNotWatchedDialog = true
                            } else {
                                showRatingDialog = true 
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.userRating != null) Icons.Filled.Star else Icons.Outlined.Star, "Rate", tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.userRating != null) "${state.userRating}/10" else "Rate", 
                            style = MaterialTheme.typography.labelLarge, 
                            fontWeight = FontWeight.Black, 
                            color = if (state.userRating != null) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            }
        }

        // ── Synopsis ──
        if (show.overview.isNotBlank()) {
            item {
                Text(show.overview, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.8f), modifier = Modifier.padding(24.dp, 8.dp))
            }
        }

        // ── Drop-Off Insight ──
        if (state.dropOffInsight != null) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp, 12.dp)
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

        // ── TV: Seasons & Episodes ──
        if (show.mediaType == "tv" && state.seasons.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Seasons & Episodes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp, 8.dp))
            }
            state.seasons.forEach { season ->
                item(key = "season_${season.seasonNumber}") {
                    SeasonHeader(season = season, onClick = { onSeasonClick(show.id, season.seasonNumber) })
                }
            }
        }

        // ── Recommendations / More Like This ──
        if (state.recommendations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(32.dp))
                Text("More Like This", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp, 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recommendations, key = { it.id }) { rec ->
                        Column(Modifier.width(120.dp).hapticClickable { onRecommendationClick(rec.id, rec.mediaType) }) {
                            AsyncImage(model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(TmdbImageUtil.posterUrl(rec.posterPath)).crossfade(true).build(), contentDescription = rec.title,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
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

        // ── Reviews Section (inline, no fixed bottom bar) ──
        item {
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = { 
                        if (!isFullyWatched) {
                            showNotWatchedDialog = true
                        } else {
                            if (showReviewInput) {
                                showReviewInput = false
                                editingReviewId = null
                            } else {
                                showReviewInput = true
                                editingReviewText = ""
                                editingReviewSpoiler = false
                                editingReviewId = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(if (showReviewInput) Icons.Filled.Close else Icons.Filled.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showReviewInput) "Cancel" else "Add Review", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Inline Review Input (toggled by "Add Review" button or Edit)
        if (showReviewInput) {
            item {
                Box(Modifier.padding(24.dp, 12.dp)) {
                    ReviewInputBar(
                        initialText = editingReviewText,
                        initialSpoiler = editingReviewSpoiler,
                        onPostReview = { text, isSpoiler ->
                            if (editingReviewId != null) {
                                onEditReview(editingReviewId!!, text, isSpoiler)
                            } else {
                                onPostReview(text, isSpoiler)
                            }
                            showReviewInput = false
                            editingReviewId = null
                        }
                    )
                }
            }
        }
        // Review Cards
        when (communityState) {
            is CommunityState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is CommunityState.Error -> item {
                Box(Modifier.padding(horizontal = 24.dp)) {
                    dev.sequel.app.presentation.components.BeautifulErrorState(
                        error = (communityState as CommunityState.Error).error,
                        onRetry = onRetryReviews,
                        isCard = true
                    )
                }
            }
            is CommunityState.Success -> {
                if (communityState.reviews.isEmpty() && !showReviewInput) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp, 8.dp).glassmorphicBackground(RoundedCornerShape(16.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Be the first to share your thoughts!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                        }
                    }
                } else {
                    items(communityState.reviews.size, key = { communityState.reviews[it].id ?: it }) { index ->
                        val review = communityState.reviews[index]
                        val isFullyWatched = if (show.mediaType == "movie") {
                            state.isMovieWatched
                        } else {
                            if (review.seasonNum != null && review.episodeNum != null) {
                                state.watchedEpisodeKeys.contains("S${review.seasonNum}E${review.episodeNum}")
                            } else if (review.seasonNum != null) {
                                val season = state.seasons.find { it.seasonNumber == review.seasonNum }
                                if (season != null && season.episodeCount > 0) {
                                    season.watchedCount >= season.episodeCount
                                } else false
                            } else {
                                // Default to false for show-level reviews to prevent finale spoilers
                                false
                            }
                        }
                        ReviewCard(
                            review = review,
                            isWatched = isFullyWatched,
                            isMyReview = review.userId == currentUserId || review.userId == "you",
                            currentUserRating = if (review.userId == currentUserId || review.userId == "you") state.userRating else null,
                            onEdit = {
                                editingReviewId = review.id
                                editingReviewText = review.reviewText ?: ""
                                editingReviewSpoiler = review.isSpoiler
                                showReviewInput = true
                            },
                            onDelete = { review.id?.let { onDeleteReview(it) } }
                        )
                    }
                }
            }
        }
    }

    if (showNotWatchedDialog) {
        AlertDialog(
            onDismissRequest = { showNotWatchedDialog = false },
            title = { Text("Action Required") },
            text = { Text("Please mark the ${if (show.mediaType == "movie") "movie" else "show"} as watched before you can review or rate it.") },
            confirmButton = {
                Button(onClick = { showNotWatchedDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }

    if (showRatingDialog) {
        RatingDialog(
            currentRating = state.userRating,
            onDismiss = { showRatingDialog = false },
            onSubmit = { rating ->
                onSubmitRating(rating)
                showRatingDialog = false
            }
        )
    }
}



@Composable
private fun SeasonHeader(season: SeasonUi, onClick: () -> Unit) {
    val watchedCount = season.watchedCount
    val totalCount = season.episodeCount
    val progress = if (totalCount > 0) watchedCount.toFloat() / totalCount else 0f

    Column(Modifier.padding(24.dp, 8.dp)) {
        Box(Modifier.fillMaxWidth().glassmorphicBackground(RoundedCornerShape(16.dp)).hapticClickable { 
            onClick()
        }) {
            Column {
                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(season.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("$watchedCount / $totalCount watched", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "View Season", tint = MaterialTheme.colorScheme.onSurface)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(0.1f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(episode: EpisodeUi, onToggleWatched: (EpisodeUi) -> Unit, onSkip: ((EpisodeUi) -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggleWatched(episode) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = TmdbImageUtil.stillUrl(episode.stillPath), contentDescription = "Episode ${episode.episodeNumber}",
            contentScale = ContentScale.Crop, modifier = Modifier.size(100.dp, 56.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("E${episode.episodeNumber}  ${episode.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episode.runtime?.let { Text("${it}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
        }
        // Skip button
        if (onSkip != null && !episode.isWatched) {
            Box(Modifier.size(36.dp).hapticClickable { onSkip(episode) }, contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    if (episode.isSkipped) "Unskip" else "Skip",
                    tint = if (episode.isSkipped) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        // Watch toggle
        Box(Modifier.size(48.dp).hapticClickable { onToggleWatched(episode) }, contentAlignment = Alignment.Center) {
            val icon = when {
                episode.isWatched -> Icons.Filled.CheckCircle
                episode.isSkipped -> Icons.Outlined.CheckCircleOutline
                else -> Icons.Outlined.CheckCircleOutline
            }
            val tint = when {
                episode.isWatched -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
            }
            Icon(
                icon,
                if (episode.isWatched) "Unwatch" else "Watch",
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun ReviewCard(
    review: dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto, 
    isWatched: Boolean,
    isMyReview: Boolean,
    currentUserRating: Int?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(24.dp, 6.dp).glassmorphicBackground(RoundedCornerShape(16.dp))) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, "User", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isMyReview) "You" else "Community Member", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMyReview && currentUserRating != null) {
                            Icon(Icons.Filled.Star, "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("$currentUserRating/10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.8f), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (review.isSpoiler) Text("SPOILER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
                if (isMyReview && review.id != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Edit, "Edit review", tint = MaterialTheme.colorScheme.onSurface.copy(0.7f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Delete, "Delete review", tint = MaterialTheme.colorScheme.error.copy(0.7f), modifier = Modifier.size(18.dp))
                        }
                    }
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
fun ReviewInputBar(
    initialText: String = "",
    initialSpoiler: Boolean = false,
    onPostReview: (String, Boolean) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var isSpoiler by remember(initialSpoiler) { mutableStateOf(initialSpoiler) }

    Box(Modifier.fillMaxWidth().glassmorphicBackground(RoundedCornerShape(20.dp)).padding(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
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
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .hapticClickable {
                            onPostReview(text, isSpoiler)
                            text = ""; isSpoiler = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun RatingDialog(currentRating: Int?, onDismiss: () -> Unit, onSubmit: (Int) -> Unit) {
    var selectedRating by remember { mutableIntStateOf(currentRating ?: 0) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .glassmorphicBackground(
                    RoundedCornerShape(32.dp), 
                    surfaceTint = Color(0xCC1A1D24), 
                    borderColor = Color.White.copy(0.1f)
                )
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Emoji Feedback
                val ratingEmoji = when(selectedRating) {
                    0 -> "🤔"
                    in 1..2 -> "🗑️"
                    in 3..4 -> "🥱"
                    in 5..6 -> "😐"
                    in 7..8 -> "🤩"
                    in 9..10 -> "🤯"
                    else -> "🤔"
                }
                
                Text(
                    text = ratingEmoji,
                    style = MaterialTheme.typography.displayMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Large Number
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (selectedRating > 0) "$selectedRating" else "-",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = if (selectedRating > 0) MaterialTheme.colorScheme.primary else Color.White.copy(0.3f)
                    )
                    Text(
                        text = " / 10",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(0.3f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                val ratingText = when(selectedRating) {
                    0 -> "Unrated"
                    in 1..2 -> "Terrible"
                    in 3..4 -> "Poor"
                    in 5..6 -> "Average"
                    in 7..8 -> "Great"
                    in 9..10 -> "Masterpiece"
                    else -> ""
                }
                
                Text(
                    text = ratingText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // The Segmented Rating Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 1..10) {
                        val isSelected = selectedRating == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.65f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.1f))
                                .clickable {
                                    if (isSelected) selectedRating = 0 else selectedRating = i
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = i.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
                            )
                        }
                    }
                }
                
                if (selectedRating > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { selectedRating = 0 }) {
                        Text("Clear Rating", color = MaterialTheme.colorScheme.error)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { onSubmit(selectedRating) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Submit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
