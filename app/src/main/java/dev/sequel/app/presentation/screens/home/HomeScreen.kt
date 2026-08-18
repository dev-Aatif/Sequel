package dev.sequel.app.presentation.screens.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.presentation.components.ShowCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pagedShows = viewModel.pagedShows.collectAsLazyPagingItems()
    var selectedItemForAction by remember { mutableStateOf<ShowEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val view = LocalView.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        val currentType by viewModel.mediaType.collectAsState()
        val tabs = listOf("tv" to "TV Shows", "movie" to "Movies")
        val selectedTabIndex = tabs.indexOfFirst { it.first == currentType }.coerceAtLeast(0)

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { viewModel.setMediaType(tab.first) },
                    text = { Text(tab.second, style = MaterialTheme.typography.titleMedium) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (pagedShows.loadState.refresh is LoadState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (pagedShows.loadState.refresh is LoadState.Error) {
                val error = (pagedShows.loadState.refresh as LoadState.Error).error
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error.localizedMessage ?: "Failed to load",
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { pagedShows.retry() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Retry")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = pagedShows.itemCount,
                        key = pagedShows.itemKey { it.id },
                        contentType = pagedShows.itemContentType { "ShowCard" }
                    ) { index ->
                        val show = pagedShows[index]
                        if (show != null) {
                            ShowCard(
                                show = show,
                                onClick = { onShowClick(show.id, show.mediaType) },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    selectedItemForAction = show
                                }
                            )
                        }
                    }

                    if (pagedShows.loadState.append is LoadState.Loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (pagedShows.loadState.append is LoadState.Error) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(onClick = { pagedShows.retry() }) {
                                    Text("Retry")
                                }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = selectedItemForAction?.title ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = { selectedItemForAction = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Watchlist") // Currently unused, placeholder
                }
                
                Button(
                    onClick = { selectedItemForAction = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hide")
                }
                
                Button(
                    onClick = { selectedItemForAction = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
