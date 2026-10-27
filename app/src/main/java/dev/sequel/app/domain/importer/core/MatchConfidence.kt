package dev.sequel.app.domain.importer.core

enum class MatchConfidence(val score: Float, val description: String) {
    EXACT_ID(1.0f, "Matched definitively via provider ID (TMDB/IMDB)"),
    EXACT_TITLE_YEAR(0.95f, "Exact match on title and release year"),
    LOCAL_CACHE_MATCH(0.90f, "Matched against existing local Room library"),
    FUZZY_MATCH(0.75f, "Fuzzy match on normalized title"),
    AMBIGUOUS(0.50f, "Multiple potential candidate matches found"),
    UNRESOLVED(0.0f, "No remote or local match identified")
}

enum class ConflictResolutionPolicy(val displayName: String, val description: String) {
    KEEP_EXISTING(
        "Keep Existing",
        "Preserve existing local library entries. Skip conflicting incoming records."
    ),
    OVERWRITE_LOCAL(
        "Overwrite Local",
        "Replace local watch states, ratings, and reviews with imported values."
    ),
    LATEST_TIMESTAMP(
        "Latest Timestamp",
        "Keep whichever record has the newer timestamp."
    ),
    MERGE_ADDITIVE(
        "Merge Additive",
        "Add missing items without modifying existing watches or ratings."
    )
}
