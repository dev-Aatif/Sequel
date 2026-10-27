package dev.sequel.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sequel.app.domain.model.RedPillRatingUtil
import dev.sequel.app.presentation.util.TestTags

/**
 * Bottom sheet modal providing Sequel's signature 0.5–10.0 Red Pill rating experience.
 *
 * Scoped strictly to Movies or TV Seasons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingModalBottomSheet(
    initialRating: Float?,
    title: String,
    seasonNumber: Int? = null,
    onDismissRequest: () -> Unit,
    onSaveRating: (Float) -> Unit,
    onClearRating: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ratingState by remember(initialRating) {
        mutableFloatStateOf(initialRating ?: 8.0f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(TestTags.SHEET_RATING),
        containerColor = Color(0xFF161920),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF52525B)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val scopeTitle = if (seasonNumber != null) {
                "Rate Season $seasonNumber"
            } else {
                "Rate Title"
            }

            Text(
                text = scopeTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Red Pill Rating Slider ────────────────────────────────
            RedPillRatingSlider(
                rating = ratingState,
                onRatingChange = { ratingState = it }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Action Buttons: Save & Clear ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (initialRating != null) {
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TestTags.BTN_CLEAR_RATING),
                        onClick = {
                            onClearRating()
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFF87171)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFFF87171).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    modifier = Modifier
                        .weight(if (initialRating != null) 2f else 1f)
                        .testTag(TestTags.BTN_SAVE_RATING),
                    onClick = {
                        val snapped = RedPillRatingUtil.clampAndSnapRating(ratingState)
                        onSaveRating(snapped)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF43F5E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save Rating",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
