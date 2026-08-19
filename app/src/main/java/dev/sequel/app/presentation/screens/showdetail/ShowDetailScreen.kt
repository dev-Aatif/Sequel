package dev.sequel.app.presentation.screens.showdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.sequel.app.presentation.components.spoilerShield
import androidx.compose.material.icons.automirrored.filled.Send

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/**
 * Show detail screen — full show/movie info, seasons, episodes, watch tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    onSeasonClick: (showId: Int, seasonNumber: Int) -> Unit,
    onBackClick: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
    reviewViewModel: dev.sequel.app.presentation.screens.showdetail.ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val communityState by reviewViewModel.communityState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(uiState) {
        if (uiState is DetailUiState.Success) {
            val show = (uiState as DetailUiState.Success).show
            reviewViewModel.loadReviews(show.id, null, null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState is DetailUiState.Success) {
                        Text(
                            (uiState as DetailUiState.Success).show.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (uiState is DetailUiState.Success) {
                WatercoolerInputBar(
                    onPostReview = { text, vibe, isSpoiler ->
                        reviewViewModel.postReview(text, vibe, isSpoiler)
                    }
                )
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadShowDetail() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            is DetailUiState.Success -> {
                ShowDetailContent(
                    state = state,
                    communityState = communityState,
                    onToggleWatched = { episode -> viewModel.toggleEpisodeWatched(episode) },
                    onToggleMovieWatched = { isWatched -> viewModel.toggleMovieWatched(isWatched) },
                    modifier = Modifier.padding(innerPadding)
                )
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
    modifier: Modifier = Modifier
) {
    val show = state.show

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // ── Backdrop Header ──────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                AsyncImage(
                    model = TmdbImageUtil.backdropUrl(show.backdropPath),
                    contentDescription = "${show.title} backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient scrim for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                ),
                                startY = 100f
                            )
                        )
                )
                // Title + Rating overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", show.voteAverage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        show.status?.let { status ->
                            Text(
                                text = "  •  $status",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // ── Synopsis ─────────────────────────────────────────────
        item {
            if (show.overview.isNotBlank()) {
                Text(
                    text = show.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
        
        // ── Drop-Off Insight ─────────────────────────────────────
        if (state.dropOffInsight != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF422B00)), // Amber-ish dark background
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Insight",
                            tint = Color(0xFFFFB300) // Amber tint
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Pro Insight",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.dropOffInsight,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // ── Media Specific Content ───────────────────────────────
        if (show.mediaType == "movie") {
            item {
                Button(
                    onClick = { onToggleMovieWatched(state.isMovieWatched) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = if (state.isMovieWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = if (state.isMovieWatched) "Watched" else "Mark Watched",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else {
            if (state.seasons.isNotEmpty()) {
                item {
                    Text(
                        text = "Seasons & Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                state.seasons.forEach { season ->
                    item(key = "season_header_${season.seasonNumber}") {
                        SeasonHeader(season = season, onToggleWatched = onToggleWatched)
                    }
                }
            }
        }

        // ── Community Watercooler ────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Community Watercooler",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        when (communityState) {
            is CommunityState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is CommunityState.Error -> {
                item {
                    Text(
                        text = "Failed to load community reviews.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is CommunityState.Success -> {
                if (communityState.reviews.isEmpty()) {
                    item {
                        Text(
                            text = "Be the first to share your thoughts!",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    items(
                        count = communityState.reviews.size,
                        key = { index -> communityState.reviews[index].id ?: index }
                    ) { index ->
                        val review = communityState.reviews[index]
                        ReviewCard(
                            review = review,
                            isWatched = state.isMovieWatched || state.seasons.all { season -> season.episodes.all { it.isWatched } }
                        )
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom input bar
        }
    }
}

@Composable
private fun SeasonHeader(season: SeasonUi, onToggleWatched: (EpisodeUi) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val watchedCount = season.episodes.count { it.isWatched }
    val totalCount = season.episodes.size

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$watchedCount / $totalCount watched",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                season.episodes.forEach { episode ->
                    EpisodeRow(episode = episode, onToggleWatched = onToggleWatched)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeUi,
    onToggleWatched: (EpisodeUi) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleWatched(episode) }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode thumbnail
        AsyncImage(
            model = TmdbImageUtil.stillUrl(episode.stillPath),
            contentDescription = "Episode ${episode.episodeNumber}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 80.dp, height = 45.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Episode info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "E${episode.episodeNumber}  ${episode.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            episode.runtime?.let { runtime ->
                Text(
                    text = "${runtime}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // Watch toggle icon
        IconButton(
            onClick = { onToggleWatched(episode) }
        ) {
            Icon(
                imageVector = if (episode.isWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                contentDescription = if (episode.isWatched) "Mark as unwatched" else "Mark as watched",
                tint = if (episode.isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun ReviewCard(review: dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto, isWatched: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (review.vibeEmoji != null) {
                    Text(text = review.vibeEmoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "User", // Real app would join with users table
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (review.isSpoiler) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SPOILER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            if (!review.reviewText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = review.reviewText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.spoilerShield(
                        isSpoiler = review.isSpoiler,
                        isWatched = isWatched
                    )
                )
            }
        }
    }
}

@Composable
fun WatercoolerInputBar(onPostReview: (String, String, Boolean) -> Unit) {
    var text by androidx.compose.runtime.remember { mutableStateOf("") }
    var isSpoiler by androidx.compose.runtime.remember { mutableStateOf(false) }
    var selectedVibe by androidx.compose.runtime.remember { mutableStateOf("🔥") }

    val vibes = listOf("🔥", "🤯", "😭", "🐌", "🥱", "😍")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        // Vibe Picker
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            items(vibes) { vibe ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (selectedVibe == vibe) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { selectedVibe = vibe },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = vibe, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("What did you think?") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Spoiler", style = MaterialTheme.typography.labelSmall)
                androidx.compose.material3.Switch(
                    checked = isSpoiler,
                    onCheckedChange = { isSpoiler = it },
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onPostReview(text, selectedVibe, isSpoiler)
                    text = ""
                    isSpoiler = false
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
