package dev.sequel.app.presentation.screens.showdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
    viewModel: DetailViewModel = hiltViewModel(),
    reviewViewModel: dev.sequel.app.presentation.screens.showdetail.ReviewViewModel = hiltViewModel()
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
                    WatercoolerInputBar(
                        onPostReview = { text, vibe, isSpoiler ->
                            reviewViewModel.postReview(text, vibe, isSpoiler)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is DetailUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ── Floating Glassmorphic Top Bar ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .glassmorphicBackground(RoundedCornerShape(32.dp), blurRadius = 12.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(28.dp)
                            .hapticClickable { onBackClick() }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    if (uiState is DetailUiState.Success) {
                        Text(
                            text = (uiState as DetailUiState.Success).show.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
    modifier: Modifier = Modifier
) {
    val show = state.show

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 120.dp) // Leave space for watercooler
    ) {
        // ── Backdrop Hero ──────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                AsyncImage(
                    model = TmdbImageUtil.backdropUrl(show.backdropPath),
                    contentDescription = "${show.title} backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Premium Vertical Gradient Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background
                                ),
                                startY = 300f
                            )
                        )
                )
                // Title + Rating overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format("%.1f", show.voteAverage),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        show.status?.let { status ->
                            Text(
                                text = "  •  $status",
                                style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
        
        // ── Drop-Off Insight ─────────────────────────────────────
        if (state.dropOffInsight != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .glassmorphicBackground(RoundedCornerShape(16.dp), surfaceTint = Color(0x66FFB300), borderColor = Color(0x33FFB300))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFFB300).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Insight",
                                tint = Color(0xFFFFB300)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Pro Insight",
                                style = MaterialTheme.typography.labelMedium,
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

        // ── Media Specific Content (Movies vs Shows) ─────────────
        if (show.mediaType == "movie") {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .height(64.dp)
                        .glassmorphicBackground(
                            shape = RoundedCornerShape(32.dp),
                            surfaceTint = if (state.isMovieWatched) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color(0xCC1A1D24)
                        )
                        .hapticClickable { onToggleMovieWatched(state.isMovieWatched) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.isMovieWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (state.isMovieWatched) "Watched" else "Mark Watched",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            if (state.seasons.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Seasons & Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
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
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Community Watercooler",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
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
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            is CommunityState.Error -> {
                item {
                    Text(
                        text = "Failed to load community reviews.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is CommunityState.Success -> {
                if (communityState.reviews.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .glassmorphicBackground(RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Be the first to share your thoughts!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
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
    }
}

@Composable
private fun SeasonHeader(season: SeasonUi, onToggleWatched: (EpisodeUi) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val watchedCount = season.episodes.count { it.isWatched }
    val totalCount = season.episodes.size
    val progress = if (totalCount > 0) watchedCount.toFloat() / totalCount else 0f

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphicBackground(RoundedCornerShape(16.dp))
                .hapticClickable { expanded = !expanded }
        ) {
            Column {
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$watchedCount / $totalCount watched",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Thin progress bar at bottom of season header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
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
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleWatched(episode) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode thumbnail
        AsyncImage(
            model = TmdbImageUtil.stillUrl(episode.stillPath),
            contentDescription = "Episode ${episode.episodeNumber}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 100.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Episode info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "E${episode.episodeNumber}  ${episode.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            episode.runtime?.let { runtime ->
                Text(
                    text = "${runtime}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Watch toggle icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .hapticClickable { onToggleWatched(episode) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (episode.isWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                contentDescription = if (episode.isWatched) "Mark as unwatched" else "Mark as watched",
                tint = if (episode.isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun ReviewCard(review: dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto, isWatched: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .glassmorphicBackground(RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (review.vibeEmoji != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = review.vibeEmoji, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = "Community Member", // Real app would join with users table
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (review.isSpoiler) {
                        Text(
                            text = "CONTAINS SPOILERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (!review.reviewText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = review.reviewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
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
    var text by remember { mutableStateOf("") }
    var isSpoiler by remember { mutableStateOf(false) }
    var selectedVibe by remember { mutableStateOf("🔥") }

    val vibes = listOf("🔥", "🤯", "😭", "🐌", "🥱", "😍")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphicBackground(RoundedCornerShape(32.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Vibe Picker
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                items(vibes) { vibe ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selectedVibe == vibe) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .hapticClickable { selectedVibe = vibe },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = vibe, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Share your thoughts...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                // Spoiler Toggle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isSpoiler) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .hapticClickable { isSpoiler = !isSpoiler },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        color = if (isSpoiler) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Send Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .hapticClickable {
                            onPostReview(text, selectedVibe, isSpoiler)
                            text = ""
                            isSpoiler = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
