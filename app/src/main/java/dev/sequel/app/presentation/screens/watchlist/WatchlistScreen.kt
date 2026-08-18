package dev.sequel.app.presentation.screens.watchlist

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.sequel.app.data.remote.tmdb.TmdbImageUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onShowClick: (showId: Int, mediaType: String) -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val upNextItems by viewModel.upNextItems.collectAsState()
    var selectedItemForAction by remember { mutableStateOf<UpNextItem?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val view = LocalView.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Up Next",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(upNextItems, key = { "${it.showId}_${it.nextEpisodeId}" }) { item ->
                UpNextCard(
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

@Composable
private fun UpNextCard(
    item: UpNextItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkWatched: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (item.nextEpisodeName != null) {
                    Text(
                        text = item.nextEpisodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (item.mediaType == "movie") {
                    Text(
                        text = "Movie",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(onClick = onMarkWatched) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Mark as watched",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
