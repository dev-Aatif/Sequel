package dev.sequel.app.presentation.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.presentation.components.hapticClickable
import dev.sequel.app.presentation.util.TestTags

/**
 * Compact, luxury cyberpunk entry banner for the Cinephile DNA feature.
 * Replaces the heavy, clustered card in the profile feed and navigates to the dedicated sanctuary screen.
 */
@Composable
fun CinephileDnaBanner(
    identity: TasteIdentity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBackground = Color(0xFF161920)
    val neonPurple = Color(0xFFA855F7)
    val neonIndigo = Color(0xFF6366F1)
    val neonPink = Color(0xFFEC4899)

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            neonPurple.copy(alpha = 0.7f),
            neonIndigo.copy(alpha = 0.4f),
            neonPink.copy(alpha = 0.3f)
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.BANNER_CINEPHILE_DNA)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(18.dp)
            )
            .hapticClickable { onClick() },
        color = cardBackground,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Glowing Icon Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(neonPurple.copy(alpha = 0.18f))
                        .border(
                            width = 1.dp,
                            color = neonPurple.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Cinephile DNA",
                        tint = neonPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CINEPHILE DNA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = neonPurple,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (identity != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(neonIndigo.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC7D2FE)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = identity?.archetype ?: "Synthesize Your Taste Identity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (identity != null) {
                            "${identity.primaryObsession} • ${identity.totalHoursLogged}h logged"
                        } else {
                            "Track & rate movies to reveal your cinephile archetype"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA1A1AA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // CTA Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(neonPurple.copy(alpha = 0.2f))
                    .border(
                        width = 1.dp,
                        color = neonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "View Dossier",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE9D5FF)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFFE9D5FF),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
