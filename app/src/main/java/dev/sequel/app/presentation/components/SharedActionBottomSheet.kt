package dev.sequel.app.presentation.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import dev.sequel.app.presentation.state.BottomSheetUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedActionBottomSheet(
    sheetState: SheetState,
    bottomSheetState: BottomSheetUiState,
    isProcessingAction: Boolean,
    onDismissRequest: () -> Unit,
    onToggleWatchlist: ((String) -> Unit) -> Unit,
    onToggleWatched: ((String) -> Unit) -> Unit,
    onSkip: ((String) -> Unit) -> Unit = {},
    onShowDetailClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val show = bottomSheetState.show ?: return

    val dismissWithAnimation: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                show.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = "Go to ${show.title} details"
                    }
                    .hapticClickable {
                        onShowDetailClick()
                        dismissWithAnimation()
                    }
            )
            
            if (bottomSheetState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Button(
                    onClick = {
                        onToggleWatchlist { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            dismissWithAnimation()
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !isProcessingAction
                ) {
                    val icon = if (bottomSheetState.inWatchlist) Icons.Filled.CheckCircle else Icons.Filled.Add
                    val text = if (bottomSheetState.inWatchlist) "Remove from Watchlist" else "Add to Watchlist"
                    Icon(icon, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text)
                }

                if (bottomSheetState.isCompleted) {
                    Button(
                        onClick = {},
                        Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        Icon(Icons.Filled.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Completed")
                    }
                } else {
                    Button(
                        onClick = {
                            onToggleWatched { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                dismissWithAnimation()
                            }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = !isProcessingAction
                    ) {
                        val icon = if (bottomSheetState.isWatched) Icons.Filled.CheckCircle else Icons.Filled.Add
                        val text = if (show.mediaType == "movie") {
                            if (bottomSheetState.isWatched) "Remove from Watched" else "Mark as Watched"
                        } else {
                            bottomSheetState.nextEpisodeString ?: "Mark as Watched"
                        }
                        Icon(icon, null)
                        Spacer(Modifier.width(8.dp))
                        Text(text)
                    }

                    if (show.mediaType == "tv") {
                        val skipText = bottomSheetState.nextEpisodeString?.replace("Mark ", "Skip ")?.replace(" as Watched", "") ?: "Skip Episode"
                        OutlinedButton(
                            onClick = {
                                onSkip { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    dismissWithAnimation()
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = !isProcessingAction
                        ) {
                            Icon(Icons.Outlined.ArrowForward, null)
                            Spacer(Modifier.width(8.dp))
                            Text(skipText)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
