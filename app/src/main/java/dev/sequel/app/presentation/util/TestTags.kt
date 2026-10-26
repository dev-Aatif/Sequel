package dev.sequel.app.presentation.util

/**
 * Centralized semantic keys for Compose UI testing across Sequel.
 * Used with Modifier.testTag() to decouple automated UI tests from display text, icons, and styling changes.
 */
object TestTags {
    // ── Screens ──────────────────────────────────────────────────────────
    const val SCREEN_HOME = "screen_home"
    const val SCREEN_PROFILE = "screen_profile"
    const val SCREEN_DETAIL = "screen_detail"
    const val SCREEN_SEARCH = "screen_search"
    const val SCREEN_WATCHLIST = "screen_watchlist"

    // ── Buttons ──────────────────────────────────────────────────────────
    const val BTN_ADD_WATCHLIST = "btn_add_watchlist"
    const val BTN_RATE_PILL = "btn_rate_pill"
    const val BTN_SETTINGS = "btn_settings"
    const val BTN_FILTER_MOVIES = "btn_filter_movies"
    const val BTN_FILTER_SHOWS = "btn_filter_shows"
    const val BTN_SORT_HISTORY = "btn_sort_history"

    // ── Watch History Filters ─────────────────────────────────────────────
    const val FILTER_HISTORY_ALL = "filter_history_all"
    const val FILTER_HISTORY_MOVIES = "filter_history_movies"
    const val FILTER_HISTORY_SHOWS = "filter_history_shows"
    const val SORT_OPTION_DATE_DESC = "sort_option_date_desc"
    const val SORT_OPTION_DATE_ASC = "sort_option_date_asc"
    const val SORT_OPTION_RATING_DESC = "sort_option_rating_desc"
    const val SORT_OPTION_RUNTIME_DESC = "sort_option_runtime_desc"

    // ── Elements ─────────────────────────────────────────────────────────
    const val INPUT_SEARCH = "input_search"
    const val CARD_MEDIA_POSTER = "card_media_poster"
    const val SHEET_REVIEW_DRAWER = "sheet_review_drawer"

    // ── Mount Rushmore (Top 4) ───────────────────────────────────────────
    const val SECTION_MOUNT_RUSHMORE = "section_mount_rushmore"
    const val RUSH_SLOT_EMPTY_0 = "rush_slot_empty_0"
    const val RUSH_SLOT_EMPTY_1 = "rush_slot_empty_1"
    const val RUSH_SLOT_EMPTY_2 = "rush_slot_empty_2"
    const val RUSH_SLOT_EMPTY_3 = "rush_slot_empty_3"

    const val RUSH_SLOT_FILLED_0 = "rush_slot_filled_0"
    const val RUSH_SLOT_FILLED_1 = "rush_slot_filled_1"
    const val RUSH_SLOT_FILLED_2 = "rush_slot_filled_2"
    const val RUSH_SLOT_FILLED_3 = "rush_slot_filled_3"

    const val SHEET_RUSH_PICKER = "sheet_rush_picker"
    const val DIALOG_RUSH_OPTIONS = "dialog_rush_options"

    fun rushSlotEmpty(slot: Int) = "rush_slot_empty_$slot"
    fun rushSlotFilled(slot: Int) = "rush_slot_filled_$slot"
}
