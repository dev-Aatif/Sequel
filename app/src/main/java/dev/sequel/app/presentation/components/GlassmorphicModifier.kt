package dev.sequel.app.presentation.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a glassmorphic background effect.
 * - Uses a premium semi-transparent dark tint
 * - Adds a subtle semi-transparent white border
 * - Does not use Modifier.blur to prevent child blurring and UI flickering
 */
@Composable
fun Modifier.glassmorphicBackground(
    shape: Shape = RoundedCornerShape(16.dp),
    blurRadius: Dp = 16.dp, // Kept for compatibility with existing calls, but unused
    surfaceTint: Color = Color(0xCC1A1D24), // 80% opacity Surface color
    borderColor: Color = Color(0x1AFFFFFF), // 10% opacity White
    borderWidth: Dp = 1.dp
): Modifier = composed {
    this
        .clip(shape)
        .background(surfaceTint)
        .border(width = borderWidth, color = borderColor, shape = shape)
}

/**
 * A clickable modifier that triggers haptic feedback (vibration) on click.
 */
@Composable
fun Modifier.hapticClickable(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: androidx.compose.foundation.Indication? = androidx.compose.foundation.LocalIndication.current,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    this.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onClick()
        }
    )
}
