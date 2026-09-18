package dev.sequel.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sequel.app.domain.error.AppError

@Composable
fun BeautifulErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    isCard: Boolean = false
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val icon: ImageVector = when (error) {
        is AppError.NoInternet -> Icons.Default.WifiOff
        is AppError.Timeout -> Icons.Default.Timer
        is AppError.ServerError -> Icons.Default.CloudOff
        is AppError.AuthFailed, is AppError.SessionExpired, is AppError.InvalidCredentials, is AppError.WeakPassword -> Icons.Default.Lock
        is AppError.NotFound -> Icons.Default.Search
        is AppError.SyncFailed -> Icons.Default.SyncProblem
        is AppError.DatabaseError -> Icons.Default.Storage
        is AppError.ParseError -> Icons.Default.BrokenImage
        is AppError.Validation -> Icons.Default.Warning
        is AppError.Unknown -> Icons.Default.ErrorOutline
    }

    val title: String = when (error) {
        is AppError.NoInternet -> "Connection Lost"
        is AppError.Timeout -> "Request Timeout"
        is AppError.ServerError -> "Server Error"
        is AppError.AuthFailed, is AppError.SessionExpired, is AppError.InvalidCredentials, is AppError.WeakPassword -> "Authentication Issue"
        is AppError.NotFound -> "Not Found"
        is AppError.SyncFailed -> "Sync Failed"
        is AppError.DatabaseError -> "Storage Error"
        is AppError.ParseError -> "Data Error"
        is AppError.Validation -> "Invalid Input"
        is AppError.Unknown -> "Oops!"
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isCard) {
                        Modifier
                            .padding(16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                            .glassmorphicBackground(RoundedCornerShape(24.dp))
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Error Icon",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Try Again",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
