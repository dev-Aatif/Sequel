package dev.sequel.app.presentation.screens.login

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.sequel.app.domain.error.AppError
import dev.sequel.app.presentation.components.GoogleIcon
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

/**
 * Minimalistic luxury Login & Sign-Up screen.
 * Follows micro-engineering design principles:
 * - Obsidian backdrop with ambient radial luminescence
 * - Segmented luxury pill mode switcher (Sign In vs Create Account)
 * - Prominent "Continue with Google" action featuring pixel-perfect vector brand mark
 * - Tactile haptic feedback on all tap and toggle events
 * - Clear, non-intrusive error/success status banners
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    val view = LocalView.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LoginEvent.NavigateToHome -> onLoginSuccess()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SequelBackground)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the glass card
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp)
                .size(380.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SequelAccent.copy(alpha = 0.12f),
                            SequelAccentDark.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Editorial Brand Header ───────────────────────────────
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

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (uiState.isSignUpMode) "Begin Your Archive" else "Welcome Back",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = SequelOnBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (uiState.isSignUpMode)
                    "Create your private sanctuary to track cinema and television."
                else
                    "Sign in to synchronize your watchlist and ratings.",
                style = MaterialTheme.typography.bodySmall,
                color = SequelOnSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Glassmorphic Form Container ──────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassmorphicBackground(RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Segmented Mode Switcher (Sign In vs Create Account) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SequelSurface.copy(alpha = 0.7f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isSignIn = !uiState.isSignUpMode

                    // Sign In Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSignIn) SequelSurfaceVariant else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSignIn) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .hapticClickable {
                                viewModel.setSignUpMode(false)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSignIn) SequelOnBackground else SequelOnSurfaceMuted
                        )
                    }

                    // Create Account Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isSignIn) SequelSurfaceVariant else Color.Transparent)
                            .border(
                                1.dp,
                                if (!isSignIn) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .hapticClickable {
                                viewModel.setSignUpMode(true)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Account",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (!isSignIn) SequelOnBackground else SequelOnSurfaceMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Google Sign-In Action Button ──────────────────────
                val googleInteractionSource = remember { MutableInteractionSource() }
                val isGooglePressed by googleInteractionSource.collectIsPressedAsState()
                val googleScale by animateFloatAsState(
                    targetValue = if (isGooglePressed) 0.98f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "googleScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .scale(googleScale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                        .hapticClickable(
                            interactionSource = googleInteractionSource,
                            enabled = !uiState.isLoading
                        ) {
                            viewModel.signInWithGoogle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoogleIcon(size = 20.dp)
                        Text(
                            text = if (uiState.isSignUpMode) "Sign up with Google" else "Continue with Google",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Subtle Divider ───────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.08f)
                    )
                    Text(
                        text = "or continue with email",
                        style = MaterialTheme.typography.labelSmall,
                        color = SequelOnSurfaceMuted.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.08f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Email Input Field ────────────────────────────────
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.error != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SequelAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = SequelAccent,
                        unfocusedLabelColor = SequelOnSurfaceMuted,
                        focusedTextColor = SequelOnBackground,
                        unfocusedTextColor = SequelOnBackground
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ── Password Input Field ─────────────────────────────
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.error != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SequelAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = SequelAccent,
                        unfocusedLabelColor = SequelOnSurfaceMuted,
                        focusedTextColor = SequelOnBackground,
                        unfocusedTextColor = SequelOnBackground
                    ),
                    visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (uiState.isSignUpMode) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (!uiState.isSignUpMode) viewModel.submit() }
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.togglePasswordVisibility()
                        }) {
                            Icon(
                                imageVector = if (uiState.isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (uiState.isPasswordVisible) "Hide password" else "Show password",
                                tint = SequelOnSurfaceMuted
                            )
                        }
                    }
                )

                // ── Confirm Password Field (Sign Up Mode) ────────────
                AnimatedVisibility(
                    visible = uiState.isSignUpMode,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = uiState.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = { Text("Confirm password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = uiState.error != null,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SequelAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = SequelAccent,
                                unfocusedLabelColor = SequelOnSurfaceMuted,
                                focusedTextColor = SequelOnBackground,
                                unfocusedTextColor = SequelOnBackground
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.submit() }
                            )
                        )
                    }
                }

                // ── Forgot Password Link (Sign In Mode) ──────────────
                if (!uiState.isSignUpMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showForgotPasswordDialog = true
                            }
                        ) {
                            Text(
                                "Forgot password?",
                                style = MaterialTheme.typography.bodySmall,
                                color = SequelAccent
                            )
                        }
                    }
                }

                // ── Error / Success Message Banner ───────────────────
                val currentError = uiState.error
                AnimatedVisibility(
                    visible = currentError != null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    if (currentError != null) {
                        val isSuccess = currentError is AppError.SuccessMessage
                        val containerColor = if (isSuccess)
                            Color(0xFF2E7D32).copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        val borderColor = if (isSuccess) Color(0xFF4CAF50).copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        val contentColor = if (isSuccess) Color(0xFF81C784) else MaterialTheme.colorScheme.error
                        val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline

                        Card(
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = currentError.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Primary Action Button ────────────────────────────
                val submitInteractionSource = remember { MutableInteractionSource() }
                val isSubmitPressed by submitInteractionSource.collectIsPressedAsState()
                val submitScale by animateFloatAsState(
                    targetValue = if (isSubmitPressed) 0.98f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "submitScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(submitScale)
                        .shadow(16.dp, RoundedCornerShape(14.dp), spotColor = SequelAccent.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(SequelAccent, SequelAccentDark)
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .hapticClickable(
                            interactionSource = submitInteractionSource,
                            enabled = !uiState.isLoading
                        ) {
                            viewModel.submit()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .semantics { contentDescription = "Loading" },
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (uiState.isSignUpMode) "Create Account" else "Sign In",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Footer Disclaimer ────────────────────────────────────
            Text(
                text = "By continuing, you agree to Sequel's Terms of Service and Privacy Policy.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = SequelOnSurfaceMuted.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }

    // ── Password Reset Dialog ────────────────────────────────────────
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showForgotPasswordDialog = false
                forgotPasswordEmail = ""
            },
            containerColor = SequelSurface,
            title = {
                Text(
                    "Reset Password",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SequelOnBackground
                )
            },
            text = {
                Column {
                    Text(
                        "Enter your account email to receive a secure password reset link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SequelOnSurfaceMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = forgotPasswordEmail,
                        onValueChange = { forgotPasswordEmail = it },
                        label = { Text("Email address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SequelAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = SequelOnBackground,
                            unfocusedTextColor = SequelOnBackground
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.resetPassword(forgotPasswordEmail)
                        showForgotPasswordDialog = false
                        forgotPasswordEmail = ""
                    },
                    enabled = forgotPasswordEmail.isNotBlank()
                ) {
                    Text("Send Link", color = SequelAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showForgotPasswordDialog = false
                        forgotPasswordEmail = ""
                    }
                ) {
                    Text("Cancel", color = SequelOnSurfaceMuted)
                }
            }
        )
    }
}
