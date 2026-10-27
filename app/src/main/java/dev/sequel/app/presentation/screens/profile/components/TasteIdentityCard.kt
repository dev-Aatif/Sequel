package dev.sequel.app.presentation.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import dev.sequel.app.domain.model.ControversialTake
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.presentation.util.TestTags

/**
 * Cyberpunk luxury card showcasing the user's algorithmic Cinephile DNA.
 *
 * Renders:
 * - Handle and Archetype ("Neon Auteurist", "The Cynical Visionary")
 * - Primary Obsession ("Cyberpunk & Sci-Fi") with watch hours
 * - Top Auteur ("Denis Villeneuve") with titles watched
 * - Controversial Hot Takes with Red Pill vs TMDB critic divergence
 * - Share DNA action button
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasteIdentityCard(
    identity: TasteIdentity,
    onShareClick: (TasteIdentity) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBackground = Color(0xFF161920)
    val neonPurple = Color(0xFFA855F7)
    val neonIndigo = Color(0xFF6366F1)
    val neonPink = Color(0xFFEC4899)

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            neonPurple.copy(alpha = 0.8f),
            neonIndigo.copy(alpha = 0.4f),
            neonPink.copy(alpha = 0.3f)
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.CARD_TASTE_IDENTITY)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(20.dp)
            ),
        color = cardBackground,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // ── Top Header: Badge + Handle + Archetype ───────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(neonPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = neonPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "CINEPHILE DNA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = neonPurple,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = identity.handle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Archetype Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(neonPurple.copy(alpha = 0.15f))
                        .border(
                            width = 1.dp,
                            color = neonPurple.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        modifier = Modifier.testTag(TestTags.TEXT_TASTE_ARCHETYPE),
                        text = identity.archetype,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE9D5FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Metrics Grid (Primary Obsession, Top Auteur, Hours Logged) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Obsession
                TasteMetricBox(
                    modifier = Modifier.weight(1.2f),
                    label = "PRIMARY OBSESSION",
                    value = identity.primaryObsession,
                    subtitle = "${identity.primaryObsessionHours}h logged",
                    icon = Icons.Default.Movie,
                    accentColor = neonPurple,
                    valueTestTag = TestTags.TEXT_TASTE_PRIMARY_OBSESSION
                )

                // Top Auteur
                TasteMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "TOP AUTEUR",
                    value = identity.topAuteur,
                    subtitle = if (identity.topAuteurWatchedCount > 0) "${identity.topAuteurWatchedCount} logged" else "Diverse",
                    icon = Icons.Default.Person,
                    accentColor = neonIndigo,
                    valueTestTag = TestTags.TEXT_TASTE_TOP_AUTEUR
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Total Hours Logged Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E232E))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TOTAL WATCH TIME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA1A1AA),
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    modifier = Modifier.testTag(TestTags.TEXT_TASTE_HOURS),
                    text = "${identity.totalHoursLogged} hrs logged",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            // ── Controversial Hot Takes Section (if present) ───────────────
            if (identity.controversialTakes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "CONTROVERSIAL HOT TAKES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF97316),
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    identity.controversialTakes.take(3).forEach { take ->
                        HotTakeItem(take = take)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Share DNA Button ──────────────────────────────────────────
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.BTN_SHARE_TASTE_CARD),
                onClick = { onShareClick(identity) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = neonPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share DNA",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TasteMetricBox(
    label: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    valueTestTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E232E))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA1A1AA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                modifier = Modifier.testTag(valueTestTag),
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HotTakeItem(take: ControversialTake) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF191D26))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = take.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (take.isHotTake) "Defended vs Consensus" else "Rejected Consensus Hype",
                style = MaterialTheme.typography.labelSmall,
                color = if (take.isHotTake) Color(0xFF34D399) else Color(0xFFF87171),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // User Rating
            Text(
                text = "💊 ${take.userRating.toInt()}/10",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFA855F7)
            )

            // Critic Rating
            Text(
                text = "⭐ ${"%.1f".format(take.criticRating)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFA1A1AA)
            )
        }
    }
}
