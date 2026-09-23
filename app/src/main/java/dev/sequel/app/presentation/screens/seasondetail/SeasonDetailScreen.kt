package dev.sequel.app.presentation.screens.seasondetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dev.sequel.app.presentation.screens.showdetail.EpisodeRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonDetailScreen(
    viewModel: SeasonDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val title = if (uiState is SeasonDetailUiState.Success) {
                        (uiState as SeasonDetailUiState.Success).season.name
                    } else {
                        "Season Detail"
                    }
                    Text(title) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState is SeasonDetailUiState.Success) {
                        val season = (uiState as SeasonDetailUiState.Success).season
                        val allWatched = season.episodes.all { it.isWatched }
                        IconButton(onClick = {
                            if (allWatched) {
                                viewModel.unwatchSeason(season.seasonNumber)
                            } else {
                                val unwatched = season.episodes.filter { !it.isWatched }
                                viewModel.markSeasonWatched(unwatched)
                            }
                        }) {
                            Icon(
                                imageVector = if (allWatched) Icons.Default.DoneAll else Icons.Default.Check,
                                contentDescription = "Mark Season Watched"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is SeasonDetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is SeasonDetailUiState.Error -> {
                    dev.sequel.app.presentation.components.BeautifulErrorState(
                        error = state.error,
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = { viewModel.retry() }
                    )
                }
                is SeasonDetailUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    items(state.season.episodes, key = { it.id }) { episode ->
                            EpisodeRow(
                                episode = episode,
                                onToggleWatched = { viewModel.toggleEpisodeWatched(episode) },
                                onSkip = { viewModel.skipEpisode(episode) }
                            )
                        }
                    }
                }
            }
        }
    }
}
