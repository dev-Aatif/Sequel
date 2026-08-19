package dev.sequel.app.presentation.navigation

/**
 * Sealed class defining all navigation routes in the app.
 * Each screen is a route with an optional argument pattern.
 */
sealed class Screen(val route: String) {

    // ── Auth ──────────────────────────────────────────────────────
    data object Login : Screen("login")

    // ── Main (Bottom Nav) ─────────────────────────────────────────
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Watchlist : Screen("watchlist")
    data object Profile : Screen("profile")

    // ── Detail ────────────────────────────────────────────────────
    data object ShowDetail : Screen("show_detail/{showId}/{mediaType}") {
        fun createRoute(showId: Int, mediaType: String) = "show_detail/$showId/$mediaType"
    }

    data object SeasonDetail : Screen("season_detail/{showId}/{seasonNumber}") {
        fun createRoute(showId: Int, seasonNumber: Int) = "season_detail/$showId/$seasonNumber"
    }

    // ── Import ────────────────────────────────────────────────────
    data object TvTimeImport : Screen("tvtime_import")
    
    // ── Settings ──────────────────────────────────────────────────
    data object Settings : Screen("settings")
}
