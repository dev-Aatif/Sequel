package dev.sequel.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sequel.app.domain.model.RedPillRatingUtil
import dev.sequel.app.presentation.util.TestTags

enum class PillFillState {
    EMPTY,
    HALF,
    FULL
}

/**
 * Sequel Signature 0.5–10.0 Red Pill Rating Slider.
 *
 * Renders:
 * - Numerical badge & live verdict tag (e.g. "💊 7.5 / 10.0 • Certified Banger")
 * - Status phrase & cultural vibe translation
 * - Visual row of 10 Red Pill icons (Full, Half, Empty)
 * - Drag and tap gesture support with 0.5 snap increments and haptic detents
 */
@Composable
fun RedPillRatingSlider(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentSnapped = remember(rating) { RedPillRatingUtil.clampAndSnapRating(rating) }
    val tier = remember(currentSnapped) { RedPillRatingUtil.getRatingTier(currentSnapped) }

    var rowWidthPx by remember { mutableFloatStateOf(0f) }

    fun updateFromPosition(x: Float) {
        if (rowWidthPx <= 0f) return
        val fraction = (x / rowWidthPx).coerceIn(0f, 1f)
        // Map 0..1 to 0.5..10.0
        val target = 0.5f + fraction * 9.5f
        val snapped = RedPillRatingUtil.clampAndSnapRating(target)
        if (snapped != currentSnapped) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRatingChange(snapped)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.SLIDER_RED_PILL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Numerical Score Badge with Live Glow ──────────────────────
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(tier.glowColor.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = tier.glowColor.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(percent = 50)
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💊",
                    fontSize = 18.sp
                )
                Text(
                    modifier = Modifier.testTag(TestTags.TEXT_RATING_SCORE),
                    text = "${RedPillRatingUtil.formatScore(currentSnapped)} / 10.0",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "• ${tier.verdictLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = tier.glowColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Status Phrase & Vibe Translation ─────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = tier.icon,
                    fontSize = 20.sp
                )
                Text(
                    modifier = Modifier.testTag(TestTags.TEXT_RATING_STATUS),
                    text = tier.statusPhrase,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                modifier = Modifier.testTag(TestTags.TEXT_RATING_VIBE),
                text = tier.vibeTranslation,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 10 Pill Icons Row with Tap & Drag Interaction ───────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updateFromPosition(offset.x)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> updateFromPosition(offset.x) },
                        onHorizontalDrag = { change, _ ->
                            updateFromPosition(change.position.x)
                        }
                    )
                }
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (slotIndex in 0 until 10) {
                    val fullThreshold = (slotIndex + 1).toFloat()
                    val halfThreshold = fullThreshold - 0.5f

                    val fillState = when {
                        currentSnapped >= fullThreshold -> PillFillState.FULL
                        currentSnapped >= halfThreshold -> PillFillState.HALF
                        else -> PillFillState.EMPTY
                    }

                    RedPillIcon(
                        fillState = fillState,
                        slotNumber = slotIndex + 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Continuous Snap Slider Slider Control ─────────────────────
        Slider(
            value = currentSnapped,
            onValueChange = { newVal ->
                val snapped = RedPillRatingUtil.clampAndSnapRating(newVal)
                if (snapped != currentSnapped) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRatingChange(snapped)
                }
            },
            valueRange = 0.5f..10.0f,
            steps = 18, // 19 values from 0.5 to 10.0 with 0.5 step = 18 interior steps
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFF43F5E),
                activeTrackColor = Color(0xFFF43F5E),
                inactiveTrackColor = Color(0xFF27272A),
                activeTickColor = Color(0xFFFDA4AF).copy(alpha = 0.4f),
                inactiveTickColor = Color(0xFF3F3F46).copy(alpha = 0.4f)
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * Renders a single Red Pill capsule icon in FULL, HALF, or EMPTY fill states.
 */
@Composable
fun RedPillIcon(
    fillState: PillFillState,
    slotNumber: Int,
    modifier: Modifier = Modifier
) {
    val pillRedGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFB7185), // Light crimson
            Color(0xFFF43F5E), // Neon Red
            Color(0xFFBE123C)  // Deep Red
        )
    )

    val emptyBackground = Color(0xFF222630)
    val emptyBorder = Color(0x33F43F5E)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            val width = size.width
            val height = size.height
            val cornerRadius = CornerRadius(width / 2f, width / 2f)

            val pillPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset.Zero, Size(width, height)),
                        cornerRadius = cornerRadius
                    )
                )
            }

            when (fillState) {
                PillFillState.EMPTY -> {
                    // Empty capsule outline and fill
                    drawRoundRect(
                        color = emptyBackground,
                        size = size,
                        cornerRadius = cornerRadius
                    )
                    drawRoundRect(
                        color = emptyBorder,
                        size = size,
                        cornerRadius = cornerRadius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                    // Mid-pill divider seam
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(0f, height / 2f),
                        end = Offset(width, height / 2f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                PillFillState.HALF -> {
                    // Empty background
                    drawRoundRect(
                        color = emptyBackground,
                        size = size,
                        cornerRadius = cornerRadius
                    )
                    // Clip to left/bottom half and draw red gradient
                    clipPath(pillPath) {
                        drawRect(
                            brush = pillRedGradient,
                            topLeft = Offset(0f, height / 2f),
                            size = Size(width, height / 2f)
                        )
                    }
                    // Mid divider
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(0f, height / 2f),
                        end = Offset(width, height / 2f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawRoundRect(
                        color = Color(0xFFF43F5E).copy(alpha = 0.7f),
                        size = size,
                        cornerRadius = cornerRadius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }

                PillFillState.FULL -> {
                    // Fully filled red pill
                    drawRoundRect(
                        brush = pillRedGradient,
                        size = size,
                        cornerRadius = cornerRadius
                    )
                    // Mid divider seam
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(0f, height / 2f),
                        end = Offset(width, height / 2f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawRoundRect(
                        color = Color(0xFFFDA4AF).copy(alpha = 0.8f),
                        size = size,
                        cornerRadius = cornerRadius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = slotNumber.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (fillState != PillFillState.EMPTY) Color(0xFFFDA4AF) else Color(0xFF71717A),
            fontWeight = if (fillState != PillFillState.EMPTY) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}
