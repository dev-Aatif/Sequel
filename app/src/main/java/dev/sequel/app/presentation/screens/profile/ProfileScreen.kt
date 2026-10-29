package dev.sequel.app.presentation.screens.profile

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.sequel.app.data.local.entity.RecentActivityItem
import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.data.local.entity.ProfileReviewItem
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.remote.tmdb.TmdbImageUtil
import dev.sequel.app.presentation.components.glassmorphicBackground
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import dev.sequel.app.presentation.util.TestTags

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import dev.sequel.app.presentation.screens.profile.components.CinephileDnaBanner

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToCinephileDna: () -> Unit = {},
    onMediaClick: (showId: Int, mediaType: String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickerSlot by viewModel.pickerSlot.collectAsState()
    val pickerQuery by viewModel.pickerQuery.collectAsState()
    val pickerSearching by viewModel.pickerSearching.collectAsState()
    val pickerResults by viewModel.pickerResults.collectAsState()
    val context = LocalContext.current

    var selectedFilledSlot by remember { mutableStateOf<Pair<Int, MountRushmoreEntity>?>(null) }

    Scaffold(
        modifier = Modifier.testTag(TestTags.SCREEN_PROFILE),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, 
                end = 16.dp, 
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp, 
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                ProfileHeader(
                    email = uiState.email,
                    onSettingsClick = onNavigateToSettings
                )
            }

            item {
                MountRushmoreSection(
                    slots = uiState.mountRushmore,
                    onEmptySlotClick = { slotIndex ->
                        viewModel.openPickerForSlot(slotIndex)
                    },
                    onFilledSlotClick = { slotIndex, item ->
                        selectedFilledSlot = slotIndex to item
                    }
                )
            }
            
            item {
                ProfileStatsGrid(
                    watchTime = uiState.watchTimeFormatted,
                    episodes = uiState.totalEpisodesWatched,
                    movies = uiState.totalMoviesWatched
                )
            }

            item {
                CinephileDnaBanner(
                    identity = uiState.tasteIdentity,
                    onClick = onNavigateToCinephileDna
                )
            }

            item {
                ProfileTabSwitcher(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) }
                )
            }

            if (uiState.selectedTab == ProfileTab.WATCH_HISTORY) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watch History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        var sortMenuExpanded by remember { mutableStateOf(false) }

                        Box {
                            IconButton(
                                modifier = Modifier.testTag(TestTags.BTN_SORT_HISTORY),
                                onClick = { sortMenuExpanded = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort watch history",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(TestTags.SORT_OPTION_DATE_DESC),
                                    text = { Text("Recently Logged") },
                                    onClick = {
                                        viewModel.setSortOrder(HistorySortOrder.DATE_DESC)
                                        sortMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Schedule, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(TestTags.SORT_OPTION_DATE_ASC),
                                    text = { Text("Oldest Logged") },
                                    onClick = {
                                        viewModel.setSortOrder(HistorySortOrder.DATE_ASC)
                                        sortMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.History, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(TestTags.SORT_OPTION_RATING_DESC),
                                    text = { Text("Highest Rated") },
                                    onClick = {
                                        viewModel.setSortOrder(HistorySortOrder.RATING_DESC)
                                        sortMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                                    }
                                )
                                DropdownMenuItem(
                                    modifier = Modifier.testTag(TestTags.SORT_OPTION_RUNTIME_DESC),
                                    text = { Text("Runtime (Longest)") },
                                    onClick = {
                                        viewModel.setSortOrder(HistorySortOrder.RUNTIME_DESC)
                                        sortMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Timer, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            modifier = Modifier.testTag(TestTags.FILTER_HISTORY_ALL),
                            selected = uiState.historyFilter.mediaType == HistoryMediaTypeFilter.ALL,
                            onClick = { viewModel.setMediaTypeFilter(HistoryMediaTypeFilter.ALL) },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            modifier = Modifier.testTag(TestTags.FILTER_HISTORY_MOVIES),
                            selected = uiState.historyFilter.mediaType == HistoryMediaTypeFilter.MOVIE,
                            onClick = { viewModel.setMediaTypeFilter(HistoryMediaTypeFilter.MOVIE) },
                            label = { Text("Movies") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            modifier = Modifier.testTag(TestTags.FILTER_HISTORY_SHOWS),
                            selected = uiState.historyFilter.mediaType == HistoryMediaTypeFilter.TV,
                            onClick = { viewModel.setMediaTypeFilter(HistoryMediaTypeFilter.TV) },
                            label = { Text("TV Shows") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                if (uiState.watchHistoryItems.isEmpty()) {
                    item {
                        ZeroWatchHistoryCard(
                            onImportClick = onNavigateToImport,
                            onDiscoverClick = onNavigateToSearch
                        )
                    }
                } else {
                    items(uiState.watchHistoryItems, key = { "${it.mediaType.name}_${it.historyId}" }) { activity ->
                        RecentActivityCard(
                            item = activity,
                            onClick = {
                                onMediaClick(activity.showId, activity.mediaType.name)
                            }
                        )
                    }
                }
            } else {
                // ProfileTab.REVIEWS
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Reviews",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "${uiState.userReviews.size} ${if (uiState.userReviews.size == 1) "Review" else "Reviews"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            modifier = Modifier.testTag(TestTags.FILTER_REVIEWS_ALL),
                            selected = uiState.reviewFilter == ReviewMediaTypeFilter.ALL,
                            onClick = { viewModel.setReviewFilter(ReviewMediaTypeFilter.ALL) },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            modifier = Modifier.testTag(TestTags.FILTER_REVIEWS_MOVIES),
                            selected = uiState.reviewFilter == ReviewMediaTypeFilter.MOVIE,
                            onClick = { viewModel.setReviewFilter(ReviewMediaTypeFilter.MOVIE) },
                            label = { Text("Movies") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            modifier = Modifier.testTag(TestTags.FILTER_REVIEWS_SHOWS),
                            selected = uiState.reviewFilter == ReviewMediaTypeFilter.TV,
                            onClick = { viewModel.setReviewFilter(ReviewMediaTypeFilter.TV) },
                            label = { Text("TV Shows") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                if (uiState.userReviews.isEmpty()) {
                    item {
                        ZeroReviewsCard(
                            onDiscoverClick = onNavigateToSearch
                        )
                    }
                } else {
                    items(uiState.userReviews, key = { it.id }) { review ->
                        ProfileReviewCard(
                            review = review,
                            onClick = {
                                onMediaClick(review.mediaId, review.mediaType)
                            }
                        )
                    }
                }
            }
        }

        // Mount Rushmore Options Dialog for filled slots
        selectedFilledSlot?.let { (slotIndex, entity) ->
            AlertDialog(
                modifier = Modifier.testTag(TestTags.DIALOG_RUSH_OPTIONS),
                onDismissRequest = { selectedFilledSlot = null },
                title = {
                    Text(
                        text = entity.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    val roman = listOf("I", "II", "III", "IV").getOrElse(slotIndex) { "${slotIndex + 1}" }
                    Text(text = "Manage Slot $roman pinned favorite")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val item = entity
                            selectedFilledSlot = null
                            onMediaClick(item.mediaId, item.mediaType)
                        }
                    ) {
                        Text("View Details")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                selectedFilledSlot = null
                                viewModel.clearSlot(slotIndex)
                            }
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                selectedFilledSlot = null
                                viewModel.openPickerForSlot(slotIndex)
                            }
                        ) {
                            Text("Replace")
                        }
                    }
                }
            )
        }

        // Mount Rushmore Search & Picker Bottom Sheet
        if (pickerSlot != null) {
            val slot = pickerSlot!!
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.closePicker() },
                sheetState = sheetState,
                modifier = Modifier.testTag(TestTags.SHEET_RUSH_PICKER),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                MountRushmorePickerContent(
                    slotIndex = slot,
                    query = pickerQuery,
                    isSearching = pickerSearching,
                    results = pickerResults,
                    onQueryChange = { viewModel.onPickerQueryChanged(it) },
                    onSelectShow = { show ->
                        viewModel.selectSlotMedia(slot, show)
                    },
                    onDismiss = { viewModel.closePicker() }
                )
            }
        }
    }
}

@Composable
fun ProfileHeader(
    email: String?,
    onSettingsClick: () -> Unit
) {
    val displayName = email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User"
    val monogram = displayName.take(2).uppercase()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Monogram Badge
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email ?: "Loading...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        FilledTonalIconButton(
            modifier = Modifier.testTag(TestTags.BTN_SETTINGS),
            onClick = onSettingsClick
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
            )
        }
    }
}

@Composable
fun ProfileStatsGrid(
    watchTime: String,
    episodes: Int,
    movies: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1 (Hero Stat - Full Width)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "TOTAL TIME TRACKED",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = watchTime,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Row 2 (Split Stat - 50/50)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "EPISODES",
                value = episodes.toString(),
                icon = Icons.Default.Tv,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "MOVIES",
                value = movies.toString(),
                icon = Icons.Default.Movie,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ZeroWatchHistoryCard(
    onImportClick: () -> Unit,
    onDiscoverClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, 
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome to Sequel!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start tracking your favorite shows to build your stats, or import your history from TV Time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDiscoverClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Discover Shows & Movies", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onImportClick,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import from TV Time", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RecentActivityCard(
    item: RecentActivityItem,
    onClick: () -> Unit
) {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        item.watchedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = TmdbImageUtil.posterUrl(item.posterPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(52.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.showTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeText = if (item.mediaType.name.equals("movie", ignoreCase = true)) {
                        "Movie"
                    } else {
                        "S${item.seasonNumber} · E${item.episodeNumber}"
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (item.userRating != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${if (item.userRating % 1f == 0f) item.userRating.toInt().toString() else "%.1f".format(item.userRating)}/10",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Mount Rushmore (Top 4 Pinned Favorites) ──────────────────────────────────

@Composable
fun MountRushmoreSection(
    slots: List<MountRushmoreSlotUi>,
    onEmptySlotClick: (slotIndex: Int) -> Unit,
    onFilledSlotClick: (slotIndex: Int, item: MountRushmoreEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val romanNumerals = listOf("I", "II", "III", "IV")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.SECTION_MOUNT_RUSHMORE),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFFD700)
                )
                Text(
                    text = "MOUNT RUSHMORE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "TOP 4 FAVORITES",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val displaySlots = (0..3).map { index ->
                slots.find { it.slotIndex == index } ?: MountRushmoreSlotUi(index)
            }

            displaySlots.forEach { slot ->
                val roman = romanNumerals.getOrElse(slot.slotIndex) { "${slot.slotIndex + 1}" }
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (slot.isFilled && slot.item != null) {
                        FilledRushmoreSlotCard(
                            slotIndex = slot.slotIndex,
                            romanNumeral = roman,
                            item = slot.item,
                            onClick = { onFilledSlotClick(slot.slotIndex, slot.item) }
                        )
                    } else {
                        EmptyRushmoreSlotCard(
                            slotIndex = slot.slotIndex,
                            romanNumeral = roman,
                            onClick = { onEmptySlotClick(slot.slotIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyRushmoreSlotCard(
    slotIndex: Int,
    romanNumeral: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .testTag(TestTags.rushSlotEmpty(slotIndex))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .dashedBorder(
                strokeWidth = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                cornerRadius = 12.dp
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = romanNumeral,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Pin to slot $slotIndex",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Pin",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilledRushmoreSlotCard(
    slotIndex: Int,
    romanNumeral: String,
    item: MountRushmoreEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .testTag(TestTags.rushSlotFilled(slotIndex))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.5.dp,
                color = Color(0xFFFFD700).copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = TmdbImageUtil.posterUrl(item.posterPath, TmdbImageUtil.PosterSize.W342),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = romanNumeral,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = Color(0xFFFFD700)
            )
        }

        if (item.rating != null && item.rating > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "★%.1f", item.rating),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MountRushmorePickerContent(
    slotIndex: Int,
    query: String,
    isSearching: Boolean,
    results: List<ShowEntity>,
    onQueryChange: (String) -> Unit,
    onSelectShow: (ShowEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val romanNumerals = listOf("I", "II", "III", "IV")
    val roman = romanNumerals.getOrElse(slotIndex) { "${slotIndex + 1}" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Pin to Slot $roman",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose a movie or TV show for your Mount Rushmore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search title...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.INPUT_SEARCH),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "Type a title to search..." else "No results found for \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { "${it.id}_${it.mediaType}" }) { show ->
                    Surface(
                        onClick = { onSelectShow(show) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = TmdbImageUtil.posterUrl(show.posterPath, TmdbImageUtil.PosterSize.W154),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = show.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val year = show.firstAirDate?.take(4) ?: ""
                                val type = show.mediaType.uppercase()
                                val rating = if (show.voteAverage > 0.0) " • ★ ${String.format(java.util.Locale.US, "%.1f", show.voteAverage)}" else ""
                                Text(
                                    text = "$type • $year$rating",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Profile Tabs & Review Components ──────────────────────────────────────────

@Composable
fun ProfileTabSwitcher(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glassmorphicBackground(RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val tabs = listOf(
            ProfileTab.WATCH_HISTORY to ("Watch History" to TestTags.TAB_WATCH_HISTORY),
            ProfileTab.REVIEWS to ("My Reviews" to TestTags.TAB_MY_REVIEWS)
        )
        tabs.forEach { (tab, labelAndTag) ->
            val isSelected = selectedTab == tab
            val (label, tag) = labelAndTag
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
                    .then(
                        if (isSelected) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .clickable { onTabSelected(tab) }
                    .testTag(tag)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProfileReviewCard(
    review: ProfileReviewItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        review.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.CARD_PROFILE_REVIEW),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = TmdbImageUtil.posterUrl(review.posterPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(52.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = review.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        review.year?.let { y ->
                            Text(
                                text = y,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val mediaBadge = if (review.mediaType.equals("movie", ignoreCase = true)) {
                            "Movie"
                        } else if (review.seasonNum != null && review.episodeNum != null) {
                            "S${review.seasonNum} · E${review.episodeNum}"
                        } else {
                            "TV Series"
                        }

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = mediaBadge,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips Row: Red Pill Rating + Emotion Chip + Spoiler Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Red Pill Rating Badge
                        if (review.rating != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(Color(0xFFE11D48).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFFE11D48).copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "💊 ${if (review.rating % 1f == 0f) review.rating.toInt().toString() else "%.1f".format(review.rating)}/10",
                                    modifier = Modifier.testTag(TestTags.BADGE_RED_PILL),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFB7185)
                                )
                            }
                        }

                        // Primary Emotion Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${review.emotionEmoji} ${review.displayEmotion}",
                                modifier = Modifier.testTag(TestTags.CHIP_EMOTION),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Spoiler Tag if applicable
                        if (review.isSpoiler) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SPOILER",
                                    modifier = Modifier.testTag(TestTags.TAG_SPOILER),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Review text snippet
            if (!review.reviewText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = review.reviewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.TEXT_REVIEW_SNIPPET)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Row: Relative time + Reactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔥 ${review.reactionCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ZeroReviewsCard(
    onDiscoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Reviews Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Share your hot takes and Red Pill scores on films and shows you've experienced.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDiscoverClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Explore Titles")
            }
        }
    }
}

fun Modifier.dashedBorder(
    strokeWidth: androidx.compose.ui.unit.Dp,
    color: Color,
    cornerRadius: androidx.compose.ui.unit.Dp
): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
    )
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )
}
