package dev.sequel.app.testing.synthetic

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Deterministic synthetic dataset generator engine for automated testing.
 *
 * Guarantees:
 * - 100% deterministic output for any given [SyntheticDatasetConfig.seed].
 * - Full referential integrity by default (episodes point to valid shows, reviews reference valid IDs).
 * - Explicit, isolated fault-injection when enabled via [FaultInjectionConfig].
 * - Realistic edge-case injection (leap days, midnight timestamps, anime specials, rewatches, multilingual reviews).
 */
class SyntheticDatasetGenerator(private val config: SyntheticDatasetConfig) {

    private val rng = Random(config.seed)
    private val injectedFaults = mutableListOf<InjectedFaultRecord>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val baseMovieTitles = listOf(
        "Oppenheimer", "Dune", "Blade Runner 2049", "Interstellar", "Past Lives",
        "The Matrix", "Inception", "Arrival", "Parasite", "Whiplash",
        "Spider-Man: Across the Spider-Verse", "Everything Everywhere All at Once",
        "Spirited Away", "Mad Max: Fury Road", "The Dark Knight", "Pulp Fiction",
        "Fight Club", "The Godfather", "Goodfellas", "Seven", "Alien",
        "The Thing", "Taxi Driver", "La La Land", "No Country for Old Men",
        "There Will Be Blood", "Drive", "Her", "The Social Network", "Zodiac"
    )

    private val baseShowTitles = listOf(
        "Severance", "Succession", "The Bear", "Chernobyl", "Dark",
        "Better Call Saul", "Breaking Bad", "Fargo", "True Detective", "Fleabag",
        "Twin Peaks", "The Wire", "The Sopranos", "Mindhunter", "Arcane",
        "Attack on Titan", "Frieren: Beyond Journey's End", "One Piece", "Vinland Saga", "Monster"
    )

    private val remakeTitles = listOf(
        Pair("Dune", Pair(1984, 2021)),
        Pair("Halloween", Pair(1978, 2018)),
        Pair("Ghost in the Shell", Pair(1995, 2017)),
        Pair("Total Recall", Pair(1990, 2012)),
        Pair("Solaris", Pair(1972, 2002))
    )

    private val multilingualReviews = listOf(
        "Cinema at its finest! 🍿🎬 ✨ 10/10 masterclass in tension.",
        "圧巻の映像美と音楽。間違いなく今年最高の一本。",
        "تحفة سينمائية تستحق المشاهدة بكل جدارة",
        "Una obra maestra inolvidable. Actuaciones de primer nivel.",
        "Un film captivant du début à la fin, avec une mise en scène impeccable.",
        "Großartiges Kino mit packender Atmosphäre und grandiosen Darstellern.",
        "Потрясающий фильм с невероятной атмосферой и актерской игрой.",
        "First half was breathtaking.\n\n\"Why did you do that?\" he whispered.\n\nSecond half fell apart.",
        "" // Intentional empty review text
    )

    fun generate(): SyntheticDataset {
        val movies = generateMovies()
        val shows = generateShows()
        val watchEvents = generateWatchEvents(movies, shows)
        val reviews = generateReviews(movies, shows)
        val watchlist = generateWatchlist(movies, shows)

        val report = SyntheticGenerationReport(
            seed = config.seed,
            config = config,
            totalMovies = movies.size,
            totalShows = shows.size,
            totalEpisodes = shows.sumOf { s -> s.seasons.sumOf { it.episodes.size } },
            totalWatchEvents = watchEvents.size,
            totalReviews = reviews.size,
            totalWatchlistItems = watchlist.size,
            injectedFaults = injectedFaults.toList(),
            reproductionSnippet = "val dataset = SyntheticDatasetGenerator(SyntheticDatasetConfig(seed = ${config.seed}L)).generate()"
        )

        return SyntheticDataset(
            seed = config.seed,
            config = config,
            movies = movies,
            shows = shows,
            watchEvents = watchEvents,
            reviews = reviews,
            watchlist = watchlist,
            injectedFaults = injectedFaults.toList(),
            report = report
        )
    }

    private fun generateMovies(): List<SyntheticMovie> {
        val movies = mutableListOf<SyntheticMovie>()
        var idCounter = 1000

        // 1. Remakes / duplicate titles if requested
        if (config.edgeCases.includeDuplicateTitlesAndRemakes) {
            remakeTitles.forEach { (title, years) ->
                val (year1, year2) = years
                val id1 = idCounter++
                val id2 = idCounter++
                movies.add(
                    SyntheticMovie(
                        id = id1,
                        title = title,
                        year = year1,
                        tmdbId = id1 * 10,
                        imdbId = "tt${id1}00",
                        overview = "Original classic $title released in $year1.",
                        runtime = 115,
                        isRemake = true
                    )
                )
                movies.add(
                    SyntheticMovie(
                        id = id2,
                        title = title,
                        year = year2,
                        tmdbId = id2 * 10,
                        imdbId = "tt${id2}00",
                        overview = "Modern remake of $title released in $year2.",
                        runtime = 145,
                        isRemake = true
                    )
                )
                injectedFaults.add(
                    InjectedFaultRecord(
                        faultType = "DUPLICATE_TITLE_REMAKE",
                        entityType = "Movie",
                        identifier = title,
                        description = "Generated two distinct movies sharing identical title '$title' for years $year1 and $year2"
                    )
                )
            }
        }

        // 2. Standard movies
        while (movies.size < config.movieCount) {
            val idx = movies.size % baseMovieTitles.size
            val baseTitle = baseMovieTitles[idx]
            val title = if (movies.size >= baseMovieTitles.size) "$baseTitle Part ${movies.size / baseMovieTitles.size + 1}" else baseTitle
            val id = idCounter++

            var year: Int? = 1970 + rng.nextInt(54)
            var tmdbId: Int? = id * 10
            var imdbId: String? = "tt${id}00"
            var overview = "A compelling film exploring the depths of human nature in $title."
            var posterPath: String? = "/poster_$id.jpg"
            val runtime: Int? = 90 + rng.nextInt(90)
            var isAmbiguous = false

            // Apply fault injection
            if (config.faults.missingYearRate > 0.0 && rng.nextDouble() < config.faults.missingYearRate) {
                year = null
                injectedFaults.add(InjectedFaultRecord("MISSING_YEAR", "Movie", title, "Year removed by fault injection"))
            }

            if (config.faults.missingIdRate > 0.0 && rng.nextDouble() < config.faults.missingIdRate) {
                tmdbId = null
                imdbId = null
                injectedFaults.add(InjectedFaultRecord("MISSING_ID", "Movie", title, "External IDs set to null by fault injection"))
            }

            if (config.faults.conflictingIdRate > 0.0 && rng.nextDouble() < config.faults.conflictingIdRate) {
                tmdbId = 999999
                injectedFaults.add(InjectedFaultRecord("CONFLICTING_ID", "Movie", title, "Conflicting TMDB ID 999999 injected"))
            }

            if (config.faults.missingMetadataRate > 0.0 && rng.nextDouble() < config.faults.missingMetadataRate) {
                overview = ""
                posterPath = null
                injectedFaults.add(InjectedFaultRecord("MISSING_METADATA", "Movie", title, "Overview and poster cleared"))
            }

            if (config.faults.ambiguousMatchRate > 0.0 && rng.nextDouble() < config.faults.ambiguousMatchRate) {
                isAmbiguous = true
                injectedFaults.add(InjectedFaultRecord("AMBIGUOUS_MATCH", "Movie", title, "Marked as ambiguous candidate"))
            }

            movies.add(
                SyntheticMovie(
                    id = id,
                    title = title,
                    year = year,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    overview = overview,
                    posterPath = posterPath,
                    runtime = runtime,
                    isAmbiguous = isAmbiguous
                )
            )
        }

        return movies
    }

    private fun generateShows(): List<SyntheticShow> {
        val shows = mutableListOf<SyntheticShow>()
        var showIdCounter = 5000
        var episodeIdCounter = 20000

        for (i in 0 until config.showCount) {
            val idx = i % baseShowTitles.size
            val baseTitle = baseShowTitles[idx]
            val title = if (i >= baseShowTitles.size) "$baseTitle (Season ${i / baseShowTitles.size + 1})" else baseTitle
            val showId = showIdCounter++

            val isAnime = config.edgeCases.includeAnimeAndSpecials && (i % 5 == 0 || title.contains("One Piece") || title.contains("Attack on Titan") || title.contains("Frieren"))
            var tmdbId: Int? = showId * 10
            var imdbId: String? = "tt${showId}00"
            val tvdbId: Int? = showId * 20
            val year = 2000 + rng.nextInt(24)

            if (config.faults.missingIdRate > 0.0 && rng.nextDouble() < config.faults.missingIdRate) {
                tmdbId = null
                imdbId = null
            }

            val seasons = mutableListOf<SyntheticSeason>()
            val numSeasons = rng.nextInt(config.seasonsPerShow.first, config.seasonsPerShow.last + 1)

            // Special Season 0 for anime or specials
            if (config.edgeCases.includeAnimeAndSpecials && (isAnime || rng.nextDouble() < 0.4)) {
                val specialEpisodes = listOf(
                    SyntheticEpisode(
                        showId = showId,
                        episodeId = episodeIdCounter++,
                        seasonNumber = 0,
                        episodeNumber = 1,
                        title = "Special OVA: Origins",
                        tmdbEpisodeId = episodeIdCounter * 10,
                        isSpecial = true
                    ),
                    SyntheticEpisode(
                        showId = showId,
                        episodeId = episodeIdCounter++,
                        seasonNumber = 0,
                        episodeNumber = 2,
                        title = "Special Behind The Scenes",
                        tmdbEpisodeId = episodeIdCounter * 10,
                        isSpecial = true
                    )
                )
                seasons.add(
                    SyntheticSeason(
                        showId = showId,
                        seasonNumber = 0,
                        isSpecial = true,
                        episodes = specialEpisodes
                    )
                )
                injectedFaults.add(InjectedFaultRecord("ANIME_SPECIALS", "Show", title, "Added Season 0 specials"))
            }

            // Regular Seasons
            for (s in 1..numSeasons) {
                val isSplit = config.edgeCases.includeIrregularNumbering && s == numSeasons && rng.nextBoolean()
                val numEpisodes = rng.nextInt(config.episodesPerSeason.first, config.episodesPerSeason.last + 1)
                val episodes = mutableListOf<SyntheticEpisode>()

                for (e in 1..numEpisodes) {
                    val epId = episodeIdCounter++
                    val epNumber = if (isAnime && s == 1 && e == numEpisodes) 1054 else e
                    val fractionalEp = if (config.edgeCases.includeIrregularNumbering && e == 5) 5.5f else null

                    episodes.add(
                        SyntheticEpisode(
                            showId = showId,
                            episodeId = epId,
                            seasonNumber = s,
                            episodeNumber = epNumber,
                            title = if (fractionalEp != null) "Episode $fractionalEp (Recap)" else "Episode $epNumber",
                            tmdbEpisodeId = epId * 10,
                            isSpecial = false,
                            fractionalEpisodeNumber = fractionalEp
                        )
                    )
                }

                seasons.add(
                    SyntheticSeason(
                        showId = showId,
                        seasonNumber = s,
                        isSpecial = false,
                        isSplitSeason = isSplit,
                        splitPart = if (isSplit) 1 else null,
                        episodes = episodes
                    )
                )
            }

            shows.add(
                SyntheticShow(
                    id = showId,
                    title = title,
                    year = year,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    tvdbId = tvdbId,
                    isAnime = isAnime,
                    seasons = seasons
                )
            )
        }

        return shows
    }

    private fun generateWatchEvents(movies: List<SyntheticMovie>, shows: List<SyntheticShow>): List<SyntheticWatchEvent> {
        val events = mutableListOf<SyntheticWatchEvent>()
        val baseTimestamp = 1672531200000L // 2023-01-01 00:00:00 UTC

        // 1. Movie Watch Events
        movies.take(config.watchEventCount / 2).forEachIndexed { index, movie ->
            var timestamp = baseTimestamp + (index * 86400000L * 3)
            var dateStr = dateFormat.format(timestamp)

            // Inject Leap Day or midnight timestamps
            if (config.edgeCases.includeLeapDaysAndMidnightTimestamps && index == 0) {
                timestamp = 1709215200000L // 2024-02-29 14:00:00 UTC
                dateStr = "2024-02-29"
                injectedFaults.add(InjectedFaultRecord("LEAP_DAY_TIMESTAMP", "WatchEvent", movie.title, "Anchored to 2024-02-29 Leap Day"))
            } else if (config.edgeCases.includeLeapDaysAndMidnightTimestamps && index == 1) {
                dateStr = dateFormat.format(timestamp)
                injectedFaults.add(InjectedFaultRecord("MIDNIGHT_TIMESTAMP", "WatchEvent", movie.title, "Exact midnight boundary timestamp"))
            }

            events.add(
                SyntheticWatchEvent(
                    eventId = "ev_movie_${movie.id}_1",
                    mediaType = "movie",
                    showOrMovieId = movie.id,
                    title = movie.title,
                    year = movie.year,
                    tmdbId = movie.tmdbId,
                    imdbId = movie.imdbId,
                    watchedAt = timestamp,
                    watchDate = dateStr,
                    isRewatch = false
                )
            )

            // Legitimate rewatch event (+ 180 days)
            if (config.edgeCases.includeLegitimateRewatches && index % 3 == 0) {
                val rewatchTimestamp = timestamp + (180L * 86400000L)
                val rewatchDateStr = dateFormat.format(rewatchTimestamp)
                events.add(
                    SyntheticWatchEvent(
                        eventId = "ev_movie_${movie.id}_2",
                        mediaType = "movie",
                        showOrMovieId = movie.id,
                        title = movie.title,
                        year = movie.year,
                        tmdbId = movie.tmdbId,
                        imdbId = movie.imdbId,
                        watchedAt = rewatchTimestamp,
                        watchDate = rewatchDateStr,
                        isRewatch = true
                    )
                )
                injectedFaults.add(InjectedFaultRecord("LEGITIMATE_REWATCH", "WatchEvent", movie.title, "Added second viewing 180 days later"))
            }
        }

        // 2. TV Episode Watch Events
        shows.take(config.watchEventCount / 4).forEachIndexed { sIdx, show ->
            val regularSeasons = show.seasons.filter { !it.isSpecial }
            if (regularSeasons.isNotEmpty()) {
                val season = regularSeasons.first()
                val episodesToWatch = if (config.edgeCases.includePartialSeasons) {
                    season.episodes.filterIndexed { idx, _ -> idx % 2 == 0 } // Watch episodes 1, 3, 5...
                } else {
                    season.episodes.take(5)
                }

                // Out of order watch history
                val orderedEpisodes = if (config.edgeCases.includeOutOfOrderWatches) {
                    injectedFaults.add(InjectedFaultRecord("OUT_OF_ORDER_WATCH", "Show", show.title, "Episodes watched out of chronological sequence"))
                    episodesToWatch.reversed()
                } else {
                    episodesToWatch
                }

                orderedEpisodes.forEachIndexed { eIdx, ep ->
                    val timestamp = baseTimestamp + ((sIdx * 10 + eIdx) * 86400000L)
                    val dateStr = dateFormat.format(timestamp)
                    events.add(
                        SyntheticWatchEvent(
                            eventId = "ev_ep_${ep.episodeId}",
                            mediaType = "tv",
                            showOrMovieId = show.id,
                            title = show.title,
                            year = show.year,
                            seasonNumber = ep.seasonNumber,
                            episodeNumber = ep.episodeNumber,
                            tmdbId = show.tmdbId,
                            imdbId = show.imdbId,
                            watchedAt = timestamp,
                            watchDate = dateStr,
                            isRewatch = false
                        )
                    )
                }

                // Show-level watched with partial episode history
                if (config.edgeCases.includeShowLevelWatchedWithPartialEpisodes && sIdx == 0) {
                    events.add(
                        SyntheticWatchEvent(
                            eventId = "ev_show_level_${show.id}",
                            mediaType = "tv",
                            showOrMovieId = show.id,
                            title = show.title,
                            year = show.year,
                            seasonNumber = null,
                            episodeNumber = null,
                            tmdbId = show.tmdbId,
                            imdbId = show.imdbId,
                            watchedAt = baseTimestamp + 100000000L,
                            watchDate = dateFormat.format(baseTimestamp + 100000000L),
                            isRewatch = false,
                            isShowLevelOnly = true
                        )
                    )
                    injectedFaults.add(InjectedFaultRecord("SHOW_LEVEL_WATCHED_PARTIAL", "Show", show.title, "Explicit show-level watch with incomplete episode history"))
                }
            }
        }

        return events
    }

    private fun generateReviews(movies: List<SyntheticMovie>, shows: List<SyntheticShow>): List<SyntheticReview> {
        val reviews = mutableListOf<SyntheticReview>()
        var count = 0

        // Movie reviews
        for (m in movies) {
            if (count >= config.reviewCount) break

            var rating: Float? = if (config.edgeCases.includeRatingScaleVariance && count % 4 == 0) {
                // Minimum or maximum
                if (count % 8 == 0) 0.5f else 10.0f
            } else if (config.edgeCases.includeRatingScaleVariance && count % 3 == 0) {
                // Fractional step
                7.5f
            } else {
                ((rng.nextInt(10) + 1)).toFloat()
            }

            var scale = 10.0f
            if (config.edgeCases.includeRatingScaleVariance && count % 5 == 0) {
                rating = ((rng.nextInt(5) + 1) * 0.5f) + 2.5f // 2.5..5.0
                scale = 5.0f
            }

            var reviewText: String? = if (config.edgeCases.includeMultilingualAndEmojiReviews) {
                multilingualReviews[count % multilingualReviews.size]
            } else {
                "Captivating cinematic storytelling with outstanding cinematography for ${m.title}."
            }

            // Fault injection
            if (config.faults.invalidNumericRate > 0.0 && rng.nextDouble() < config.faults.invalidNumericRate) {
                rating = 99.0f
                injectedFaults.add(InjectedFaultRecord("INVALID_NUMERIC_RATING", "Review", m.title, "Rating set to 99.0 out of range"))
            }

            reviews.add(
                SyntheticReview(
                    mediaId = m.id,
                    mediaType = "movie",
                    title = m.title,
                    rating = rating,
                    originalRatingScale = scale,
                    reviewText = reviewText,
                    isSpoiler = count % 6 == 0,
                    updatedAt = 1680000000000L + (count * 1000000L)
                )
            )
            count++
        }

        // Show / Season reviews
        for (s in shows) {
            if (count >= config.reviewCount) break
            reviews.add(
                SyntheticReview(
                    mediaId = s.id,
                    mediaType = "tv",
                    title = s.title,
                    seasonNumber = 1,
                    rating = 8.5f,
                    originalRatingScale = 10.0f,
                    reviewText = "Season 1 is a masterclass in pacing and writing for ${s.title}.",
                    isSpoiler = false,
                    updatedAt = 1680000000000L + (count * 1000000L)
                )
            )
            count++
        }

        return reviews
    }

    private fun generateWatchlist(movies: List<SyntheticMovie>, shows: List<SyntheticShow>): List<SyntheticWatchlistItem> {
        val items = mutableListOf<SyntheticWatchlistItem>()
        val combined = (movies.map { "movie" to it.id } + shows.map { "tv" to it.id }).shuffled(rng)

        for (i in 0 until config.watchlistCount.coerceAtMost(combined.size)) {
            val (type, id) = combined[i]
            val title = if (type == "movie") {
                movies.first { it.id == id }.title
            } else {
                shows.first { it.id == id }.title
            }
            val year = if (type == "movie") {
                movies.first { it.id == id }.year
            } else {
                shows.first { it.id == id }.year
            }
            val tmdbId = if (type == "movie") {
                movies.first { it.id == id }.tmdbId
            } else {
                shows.first { it.id == id }.tmdbId
            }

            items.add(
                SyntheticWatchlistItem(
                    tmdbId = tmdbId,
                    mediaType = type,
                    title = title,
                    year = year,
                    addedAt = 1690000000000L + (i * 86400000L)
                )
            )
        }

        return items
    }

    companion object {
        fun generate(config: SyntheticDatasetConfig = SyntheticDatasetConfig()): SyntheticDataset {
            return SyntheticDatasetGenerator(config).generate()
        }
    }
}
