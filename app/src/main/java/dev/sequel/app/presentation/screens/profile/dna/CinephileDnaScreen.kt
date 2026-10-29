package dev.sequel.app.presentation.screens.profile.dna

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.presentation.screens.profile.components.TasteIdentityCard
import dev.sequel.app.presentation.util.CinephileDnaShareUtil
import dev.sequel.app.presentation.util.TestTags

/**
 * Dedicated sanctuary screen for the user's algorithmic Cinephile DNA.
 * Provides uncrowded layout, data authenticity explanations, and viral sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinephileDnaScreen(
    onBackClick: () -> Unit,
    onMediaClick: (showId: Int, mediaType: String) -> Unit = { _, _ -> },
    viewModel: CinephileDnaViewModel = hiltViewModel()
) {
    val identity by viewModel.tasteIdentity.collectAsState()
    val context = LocalContext.current
    var showDataInfoDialog by remember { mutableStateOf(false) }

    val neonPurple = Color(0xFFA855F7)
    val neonIndigo = Color(0xFF6366F1)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.SCREEN_CINEPHILE_DNA),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CINEPHILE DNA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        Text(
                            text = identity?.handle ?: "Taste Dossier",
                            style = MaterialTheme.typography.labelSmall,
                            color = neonPurple
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        modifier = Modifier.testTag(TestTags.BTN_DNA_DATA_INFO),
                        onClick = { showDataInfoDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Data Authenticity Info",
                            tint = Color(0xFFD8B4FE)
                        )
                    }

                    identity?.let { dna ->
                        IconButton(
                            onClick = { CinephileDnaShareUtil.shareCinephileDna(context, dna) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share DNA",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val currentIdentity = identity
            if (currentIdentity != null) {
                // 1. The Hero Dossier Card
                item {
                    TasteIdentityCard(
                        identity = currentIdentity,
                        onShareClick = { CinephileDnaShareUtil.shareCinephileDna(context, it) }
                    )
                }

                // 2. Data Transparency & Authenticity Explanation
                item {
                    DataAuthenticityCard(
                        identity = currentIdentity,
                        onLearnMoreClick = { showDataInfoDialog = true }
                    )
                }

                // 3. Viral CTA Share Card
                item {
                    ViralShareCtaCard(
                        identity = currentIdentity,
                        onShareClick = { CinephileDnaShareUtil.shareCinephileDna(context, currentIdentity) }
                    )
                }
            } else {
                // Empty / Undiscovered state
                item {
                    UndiscoveredDnaState(
                        onBackClick = onBackClick
                    )
                }
            }
        }
    }

    if (showDataInfoDialog) {
        DataAuthenticityDialog(
            onDismiss = { showDataInfoDialog = false }
        )
    }
}

@Composable
private fun DataAuthenticityCard(
    identity: TasteIdentity,
    onLearnMoreClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            ),
        color = Color(0xFF14171E),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "HOW YOUR DNA IS SYNTHESIZED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "Sequel evaluates your offline-first Room database to synthesize this profile in real-time. No black box, no third-party data tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA),
                lineHeight = 18.sp
            )

            BreakdownRow(
                icon = Icons.Default.Movie,
                title = "Primary Obsession (${identity.primaryObsessionHours}h logged)",
                description = "Aggregated from actual movie runtimes and logged TV episode durations in your library."
            )

            BreakdownRow(
                icon = Icons.Default.Person,
                title = "Top Auteur (${identity.topAuteur})",
                description = if (identity.topAuteurTitles.isNotEmpty()) {
                    "Matched from your watched titles: ${identity.topAuteurTitles.joinToString(", ")}."
                } else {
                    "Calculated by aggregating all logged works by directors and showrunners in your library."
                }
            )

            BreakdownRow(
                icon = Icons.Default.Lock,
                title = "100% Private & On-Device",
                description = "Your taste profile is generated entirely on your device from ${identity.totalTitlesWatched} recorded titles."
            )

            TextButton(
                onClick = onLearnMoreClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "View Technical Architecture ❯",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA855F7),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E232E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFA855F7),
                modifier = Modifier.size(14.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ViralShareCtaCard(
    identity: TasteIdentity,
    onShareClick: () -> Unit
) {
    val neonPurple = Color(0xFFA855F7)
    val neonIndigo = Color(0xFF6366F1)

    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(neonPurple, neonIndigo)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        color = Color(0xFF1B1F2A),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = neonPurple,
                modifier = Modifier.size(28.dp)
            )

            Text(
                text = "Challenge Your Fellow Cinephiles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Share your cinematic identity with fellow cinephiles and invite them to download Sequel on the Google Play Store to uncover their own DNA.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Button(
                onClick = onShareClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(gradientBrush, shape = RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
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
                    text = "Share Cinephile DNA",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun UndiscoveredDnaState(
    onBackClick: () -> Unit
) {
    val neonPurple = Color(0xFFA855F7)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = Color(0xFF161920),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(neonPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = neonPurple,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "CINEPHILE DNA UNDISCOVERED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "Your cinematic DNA is synthesized dynamically from your real watch history and personal ratings. Once you log movies or rate titles on the Red Pill scale, your archetype and auteur dossier will unlock here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = neonPurple),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Return to Profile",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DataAuthenticityDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag(TestTags.SHEET_DNA_DATA_INFO),
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Deterministic Taste Engine",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Sequel does not use placeholder data or algorithmic guessing. Your Cinephile DNA is computed directly from three local Room database tables:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD4D4D8)
                )

                Text(
                    text = "• watched_movies: Supplies title, runtime, TMDB vote average, and genre IDs.\n" +
                           "• watched_episodes: Aggregates TV watch counts multiplied by episode runtimes.\n" +
                           "• reviews: Compares your 1.0–10.0 user score with TMDB critic consensus to isolate hot takes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA1A1AA),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )

                Text(
                    text = "Every time you mark a title as watched or submit a review, this dossier recalibrates instantly on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF34D399)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7))
            ) {
                Text("Got It")
            }
        },
        containerColor = Color(0xFF1E232E)
    )
}
