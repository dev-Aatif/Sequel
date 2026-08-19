package dev.sequel.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * A custom modifier that applies a heavy blur effect if the content is a spoiler
 * and the user hasn't watched the media yet. The user can tap to reveal the content.
 */
fun Modifier.spoilerShield(isSpoiler: Boolean, isWatched: Boolean): Modifier = composed {
    // If it's not a spoiler, or the user already watched it, don't blur.
    if (!isSpoiler || isWatched) {
        return@composed this
    }

    var isRevealed by remember { mutableStateOf(false) }

    if (isRevealed) {
        this
    } else {
        this
            .blur(radius = 16.dp)
            .clickable { isRevealed = true }
    }
}
