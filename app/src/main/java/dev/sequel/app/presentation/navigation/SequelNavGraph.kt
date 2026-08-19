package dev.sequel.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.sequel.app.presentation.screens.home.HomeScreen
import dev.sequel.app.presentation.screens.login.LoginScreen
import dev.sequel.app.presentation.screens.profile.ProfileScreen
import dev.sequel.app.presentation.screens.search.SearchScreen
import dev.sequel.app.presentation.screens.showdetail.ShowDetailScreen
import dev.sequel.app.presentation.screens.seasondetail.SeasonDetailScreen
import dev.sequel.app.presentation.screens.tvtimeimport.TvTimeImportScreen
import dev.sequel.app.presentation.screens.watchlist.WatchlistScreen

/**
 * Main navigation graph for the app.
 */
@Composable
fun SequelNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            )
        }
    ) {
        // ── Auth ──────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Main tabs ─────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onShowClick = { showId, mediaType ->
                    navController.navigate(Screen.ShowDetail.createRoute(showId, mediaType))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onShowClick = { showId, mediaType ->
                    navController.navigate(Screen.ShowDetail.createRoute(showId, mediaType))
                }
            )
        }

        composable(Screen.Watchlist.route) {
            WatchlistScreen(
                onShowClick = { showId, mediaType ->
                    navController.navigate(Screen.ShowDetail.createRoute(showId, mediaType))
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onImportClick = {
                    navController.navigate(Screen.TvTimeImport.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // ── Detail screens ────────────────────────────────────────
        composable(
            route = Screen.ShowDetail.route,
            arguments = listOf(
                navArgument("showId") { type = NavType.IntType },
                navArgument("mediaType") { type = NavType.StringType }
            )
        ) {
            ShowDetailScreen(
                onSeasonClick = { showId, seasonNumber ->
                    navController.navigate(Screen.SeasonDetail.createRoute(showId, seasonNumber))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SeasonDetail.route,
            arguments = listOf(
                navArgument("showId") { type = NavType.IntType },
                navArgument("seasonNumber") { type = NavType.IntType }
            )
        ) {
            SeasonDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Import ────────────────────────────────────────────────
        composable(Screen.TvTimeImport.route) {
            TvTimeImportScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Settings ──────────────────────────────────────────────
        composable(Screen.Settings.route) {
            dev.sequel.app.presentation.screens.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAuth = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
