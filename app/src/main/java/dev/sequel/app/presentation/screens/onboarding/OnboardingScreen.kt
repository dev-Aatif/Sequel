package dev.sequel.app.presentation.screens.onboarding

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable
import dev.sequel.app.presentation.theme.SequelAccent
import dev.sequel.app.presentation.theme.SequelAccentDark
import dev.sequel.app.presentation.theme.SequelBackground
import dev.sequel.app.presentation.theme.SequelOnBackground
import dev.sequel.app.presentation.theme.SequelOnSurfaceMuted
import dev.sequel.app.presentation.theme.SequelSurface
import dev.sequel.app.presentation.theme.SequelSurfaceVariant
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Custom interactive luxury onboarding screen.
 * Follows micro-engineering principles:
 * - Subtle ambient depth & obsidian/violet luxury color palette
 * - Gesture-based interactive demonstration widgets per slide
 * - Animated expanding glowing pill indicators
 * - Tactile haptic feedback on all micro-interactions
 * - Smooth spring & tween curves
 */
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is OnboardingEvent.NavigateToLogin -> onFinishOnboarding()
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    // Outer Obsidian Background with Ambient Glow
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SequelBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Ambient soft nebula glow at top-center
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .size(360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SequelAccent.copy(alpha = 0.14f),
                            SequelAccentDark.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top Bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Editorial brand watermark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SequelAccent)
                    )
                    Text(
                        text = "S E Q U E L",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = SequelOnBackground.copy(alpha = 0.85f)
                    )
                }

                // Skip Button (fades out on the last page)
                AnimatedVisibility(
                    visible = pagerState.currentPage < 2,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .background(SequelSurface.copy(alpha = 0.5f))
                            .hapticClickable {
                                viewModel.completeOnboarding()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = SequelOnSurfaceMuted
                        )
                    }
                }
            }

            // ── Horizontal Pager ─────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingCurationSlide(
                        selectedIndex = uiState.selectedPreviewCardIndex,
                        onSelectCard = { index ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.selectPreviewCard(index)
                        }
                    )
                    1 -> OnboardingReflectionSlide(
                        selectedRating = uiState.selectedRating,
                        onSelectRating = { rating ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.selectRating(rating)
                        }
                    )
                    2 -> OnboardingSanctuarySlide()
                }
            }

            // ── Bottom Controls ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Glowing Pill Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 32.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
                            label = "indicatorWidth"
                        )
                        val color = if (isSelected) SequelAccent else Color.White.copy(alpha = 0.2f)

                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }

                // Primary CTA Button with Gradient and Micro-Press Scale
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "ctaScale"
                )

                val isLastPage = pagerState.currentPage == 2

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .scale(scale)
                        .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = SequelAccent.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(SequelAccent, SequelAccentDark)
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .hapticClickable(interactionSource = interactionSource) {
                            if (isLastPage) {
                                viewModel.completeOnboarding()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isLastPage) "Get Started" else "Continue",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Icon(
                            imageVector = if (isLastPage) Icons.Default.AutoAwesome else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Secondary Sign In link on last page
                AnimatedVisibility(
                    visible = isLastPage,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(150))
                ) {
                    TextButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.completeOnboarding()
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Already have an account? Sign In",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = SequelOnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

// ── Slide 01: Curation ────────────────────────────────────────────────────────

@Composable
private fun OnboardingCurationSlide(
    selectedIndex: Int,
    onSelectCard: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Chapter Badge
        LuxuryChapterBadge(chapter = "CHAPTER 01", subtitle = "CURATION")

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Curate Your Cinema",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = SequelOnBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A private, distraction-free sanctuary to track your favorite films and series across every streaming platform.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = SequelOnSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Interactive Stack of 3 Miniature Glass Cards
        val cards = listOf(
            Triple("Oppenheimer", "Christopher Nolan · 2023", "9.3"),
            Triple("Dune: Part Two", "Denis Villeneuve · 2024", "9.5"),
            Triple("Severance", "Dan Erickson · Series S2", "9.0")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            cards.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val borderColor = if (isSelected) SequelAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f)
                val backgroundColor = if (isSelected) SequelSurfaceVariant.copy(alpha = 0.85f) else SequelSurface.copy(alpha = 0.5f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(backgroundColor)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .clickable { onSelectCard(index) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SequelAccent.copy(alpha = if (isSelected) 0.25f else 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (index == 2) Icons.Default.Tv else Icons.Default.Movie,
                                contentDescription = null,
                                tint = if (isSelected) SequelAccent else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = item.first,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SequelOnBackground
                            )
                            Text(
                                text = item.second,
                                style = MaterialTheme.typography.bodySmall,
                                color = SequelOnSurfaceMuted
                            )
                        }
                    }

                    // Score pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2E7D32).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = item.third,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Slide 02: Reflection ──────────────────────────────────────────────────────

@Composable
private fun OnboardingReflectionSlide(
    selectedRating: Float,
    onSelectRating: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LuxuryChapterBadge(chapter = "CHAPTER 02", subtitle = "REFLECTION")

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Intimate Ratings & Insights",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = SequelOnBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Score every episode with precision. Capture your honest reflections and watch your cinematic journey evolve without algorithm noise.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = SequelOnSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Interactive Live Rating Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphicBackground(RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Succession · S4 E3 'Connor's Wedding'",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    color = SequelAccent
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rating Label animated
                val ratingLabel = when {
                    selectedRating >= 9.8f -> "Masterpiece"
                    selectedRating >= 9.0f -> "Superb"
                    selectedRating >= 8.5f -> "Brilliant"
                    else -> "Great"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "%.1f".format(selectedRating),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "· $ratingLabel",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = SequelOnSurfaceMuted
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Interactive selector pills
                val ratings = listOf(8.0f, 8.5f, 9.0f, 9.5f, 10.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ratings.forEach { rating ->
                        val isSelected = (selectedRating - rating) in -0.1f..0.1f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SequelAccent else SequelSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectRating(rating) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "%.1f".format(rating),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) Color.White else SequelOnSurfaceMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Micro metrics footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text(text = "142 Episodes", style = MaterialTheme.typography.labelSmall, color = SequelOnSurfaceMuted)
                    Text(text = "•", style = MaterialTheme.typography.labelSmall, color = SequelOnSurfaceMuted)
                    Text(text = "9.1 Avg Rating", style = MaterialTheme.typography.labelSmall, color = SequelOnSurfaceMuted)
                    Text(text = "•", style = MaterialTheme.typography.labelSmall, color = SequelOnSurfaceMuted)
                    Text(text = "Private Notes", style = MaterialTheme.typography.labelSmall, color = SequelOnSurfaceMuted)
                }
            }
        }
    }
}

// ── Slide 03: Sanctuary & Cloud Sync ──────────────────────────────────────────

@Composable
private fun OnboardingSanctuarySlide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LuxuryChapterBadge(chapter = "CHAPTER 03", subtitle = "SANCTUARY")

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Cloud Sync & Total Privacy",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = SequelOnBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Seamless real-time synchronization backed by robust cloud infrastructure. Your watchlist belongs solely to you — zero ads, zero trackers, pure cinema.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = SequelOnSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Luxury Security & Cloud Vault Widget
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphicBackground(RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pulse Animation Icon
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(SequelAccent.copy(alpha = 0.2f))
                            .border(1.dp, SequelAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = SequelAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "REALTIME CLOUD SYNC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = SequelAccent
                        )
                        Text(
                            text = "Encrypted Supabase Infrastructure",
                            style = MaterialTheme.typography.bodySmall,
                            color = SequelOnSurfaceMuted
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Pillars
                val pillars = listOf(
                    "Instant multi-device cloud synchronization",
                    "Resilient offline-first local database",
                    "No advertisements, telemetry, or user tracking"
                )

                pillars.forEach { pillar ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = pillar,
                            style = MaterialTheme.typography.bodySmall,
                            color = SequelOnBackground.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

// ── Shared UI Utilities ───────────────────────────────────────────────────────

@Composable
private fun LuxuryChapterBadge(
    chapter: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SequelAccent.copy(alpha = 0.12f))
            .border(1.dp, SequelAccent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(SequelAccent)
            )
            Text(
                text = "$chapter · $subtitle",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = SequelAccent
            )
        }
    }
}
