package dev.sequel.app.domain.usecase

import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.domain.model.ControversialTake
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.domain.model.TasteWatchRecord
import dev.sequel.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Algorithmic generator that synthesizes a cinephile's Taste Identity Card from local Room DB history.
 *
 * Evaluates:
 * 1. Primary Obsession: Top genre by total hours watched.
 * 2. Top Auteur / Director: Aggregated from watched shows/movies where watch count is highest.
 * 3. Controversial Hot Takes: Titles where user rating diverges from TMDB critic consensus:
 *    - User Loved: user rating >= 8.5 (or >= 8.0) while TMDB vote average <= 6.5.
 *    - Slop Called Out: user rating <= 5.0 while TMDB vote average >= 7.5.
 */
class GenerateTasteIdentityUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val showDao: ShowDao,
    private val reviewDao: ReviewDao
) {

    operator fun invoke(): Flow<TasteIdentity?> = combine(
        showDao.observeWatchedMovies(),
        showDao.observeWatchedTvShows(),
        reviewDao.observeUserReviews()
    ) { movies, tvShows, reviews ->
        val reviewsMap = reviews.associateBy { it.mediaId to it.mediaType.lowercase() }

        val movieRecords = movies.map { movie ->
            val userReview = reviewsMap[movie.id to "movie"]
            TasteWatchRecord(
                mediaId = movie.id,
                mediaType = "movie",
                title = movie.title,
                genres = parseGenres(movie.genresDisplay, movie.genreIds),
                runtimeMinutes = movie.runtime ?: 100,
                watchCount = 1,
                director = resolveDirector(movie.title),
                tmdbRating = movie.voteAverage,
                userRating = userReview?.rating?.toFloat(),
                posterPath = movie.posterPath
            )
        }

        val tvRecords = tvShows.map { tuple ->
            val show = tuple.show
            val userReview = reviewsMap[show.id to "tv"]
            val epRuntime = show.episodeRuntime ?: 45
            TasteWatchRecord(
                mediaId = show.id,
                mediaType = "tv",
                title = show.title,
                genres = parseGenres(show.genresDisplay, show.genreIds),
                runtimeMinutes = tuple.watchedCount * epRuntime,
                watchCount = tuple.watchedCount,
                director = resolveDirector(show.title),
                tmdbRating = show.voteAverage,
                userRating = userReview?.rating?.toFloat(),
                posterPath = show.posterPath
            )
        }

        val allRecords = movieRecords + tvRecords
        if (allRecords.isEmpty()) {
            null
        } else {
            calculate(
                email = authRepository.currentUserEmail,
                records = allRecords
            )
        }
    }

    /**
     * Pure calculation engine for Taste Identity synthesis.
     * Takes deterministic mock or live watch records and returns the analyzed [TasteIdentity].
     */
    fun calculate(
        email: String?,
        records: List<TasteWatchRecord>
    ): TasteIdentity {
        val handle = when {
            !email.isNullOrBlank() -> "@${email.substringBefore('@').trim()}"
            else -> "@cinephile"
        }

        if (records.isEmpty()) {
            return TasteIdentity(
                handle = handle,
                archetype = "The Aspiring Cinephile",
                primaryObsession = "Undiscovered",
                primaryObsessionHours = 0,
                topAuteur = "Eclectic",
                topAuteurWatchedCount = 0,
                totalHoursLogged = 0,
                controversialTakes = emptyList()
            )
        }

        // 1. Primary Obsession by runtime minutes
        val genreMinutes = mutableMapOf<String, Int>()
        var totalMinutesLogged = 0

        for (record in records) {
            val recordMinutes = if (record.runtimeMinutes > 0) {
                record.runtimeMinutes
            } else {
                if (record.mediaType.equals("movie", ignoreCase = true)) 100 else 45 * record.watchCount
            }
            totalMinutesLogged += recordMinutes

            for (genre in record.genres) {
                val trimmed = genre.trim()
                if (trimmed.isNotBlank()) {
                    genreMinutes[trimmed] = (genreMinutes[trimmed] ?: 0) + recordMinutes
                }
            }
        }

        val topGenreEntry = genreMinutes.maxByOrNull { it.value }
        val rawTopGenre = topGenreEntry?.key
        val primaryObsession = rawTopGenre?.let { formatObsession(it) } ?: "Undiscovered"
        val primaryObsessionHours = (topGenreEntry?.value ?: 0) / 60
        val totalHoursLogged = totalMinutesLogged / 60

        // 2. Top Director / Showrunner by watched count
        val directorCounts = mutableMapOf<String, Int>()
        for (record in records) {
            val auteur = record.director?.trim() ?: resolveDirector(record.title)
            if (!auteur.isNullOrBlank()) {
                directorCounts[auteur] = (directorCounts[auteur] ?: 0) + record.watchCount
            }
        }

        val topDirectorEntry = directorCounts.maxByOrNull { it.value }
        val topAuteur = topDirectorEntry?.key ?: "Eclectic"
        val topAuteurWatchedCount = topDirectorEntry?.value ?: 0

        // 3. Controversial Hot Takes
        val controversialTakes = records.mapNotNull { record ->
            val uRating = record.userRating
            val cRating = record.tmdbRating
            if (uRating != null && cRating > 0.0) {
                val isLoved = (uRating >= 8.5f || (uRating >= 8.0f && cRating <= 6.5)) && cRating <= 6.5
                val isDisliked = uRating <= 5.0f && cRating >= 7.5
                if (isLoved || isDisliked) {
                    ControversialTake(
                        mediaId = record.mediaId,
                        mediaType = record.mediaType,
                        title = record.title,
                        posterPath = record.posterPath,
                        userRating = uRating,
                        criticRating = cRating,
                        isHotTake = isLoved
                    )
                } else null
            } else null
        }.sortedByDescending { it.discrepancy }

        // 4. Cinephile Archetype
        val archetype = determineArchetype(rawTopGenre, controversialTakes.size)

        return TasteIdentity(
            handle = handle,
            archetype = archetype,
            primaryObsession = primaryObsession,
            primaryObsessionHours = primaryObsessionHours,
            topAuteur = topAuteur,
            topAuteurWatchedCount = topAuteurWatchedCount,
            totalHoursLogged = totalHoursLogged,
            controversialTakes = controversialTakes
        )
    }

    private fun determineArchetype(topGenre: String?, hotTakesCount: Int): String {
        return when {
            hotTakesCount >= 2 -> "The Cynical Visionary"
            topGenre == null -> "The Aspiring Cinephile"
            topGenre.contains("Sci-Fi", ignoreCase = true) || topGenre.contains("Science Fiction", ignoreCase = true) -> "Neon Auteurist"
            topGenre.contains("Crime", ignoreCase = true) || topGenre.contains("Noir", ignoreCase = true) || topGenre.contains("Mystery", ignoreCase = true) -> "The Noir Realist"
            topGenre.contains("Horror", ignoreCase = true) -> "The Midnight Hedonist"
            topGenre.contains("Drama", ignoreCase = true) || topGenre.contains("History", ignoreCase = true) -> "The Prestige Purist"
            topGenre.contains("Animation", ignoreCase = true) || topGenre.contains("Fantasy", ignoreCase = true) -> "The Worldbuilder"
            topGenre.contains("Action", ignoreCase = true) -> "The Adrenaline Purist"
            else -> "The Eclectic Cinephile"
        }
    }

    private fun formatObsession(genre: String): String = when (genre.lowercase().trim()) {
        "science fiction", "sci-fi & fantasy", "sci-fi" -> "Cyberpunk & Sci-Fi"
        "crime" -> "Noir & Crime Thrillers"
        "drama" -> "Prestige Drama"
        "thriller", "mystery" -> "Psychological Thrillers"
        "horror" -> "Psychological Horror"
        "action", "action & adventure" -> "High-Octane Action"
        "animation" -> "Anime & Animation"
        "comedy" -> "Satire & Dark Comedy"
        "fantasy" -> "Mythic & Dark Fantasy"
        "history", "war", "war & politics" -> "Historical Warfare & Politics"
        "documentary" -> "Investigative Cinema"
        else -> genre
    }

    private fun parseGenres(genresDisplay: String?, genreIds: String?): List<String> {
        if (!genresDisplay.isNullOrBlank()) {
            return genresDisplay.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        if (!genreIds.isNullOrBlank()) {
            val cleaned = genreIds.removePrefix("[").removeSuffix("]").trim()
            if (cleaned.isNotBlank()) {
                return cleaned.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .mapNotNull { TMDB_GENRES[it] }
            }
        }
        return emptyList()
    }

    private fun resolveDirector(title: String): String? {
        val normalized = title.trim().lowercase()
        return KNOWN_DIRECTORS[normalized]
    }

    companion object {
        private val TMDB_GENRES = mapOf(
            28 to "Action",
            12 to "Adventure",
            16 to "Animation",
            35 to "Comedy",
            80 to "Crime",
            99 to "Documentary",
            18 to "Drama",
            10751 to "Family",
            14 to "Fantasy",
            36 to "History",
            27 to "Horror",
            10402 to "Music",
            9648 to "Mystery",
            10749 to "Romance",
            878 to "Science Fiction",
            10770 to "TV Movie",
            53 to "Thriller",
            10752 to "War",
            37 to "Western",
            10759 to "Action & Adventure",
            10762 to "Kids",
            10763 to "News",
            10764 to "Reality",
            10765 to "Sci-Fi & Fantasy",
            10766 to "Soap",
            10767 to "Talk",
            10768 to "War & Politics"
        )

        private val KNOWN_DIRECTORS: Map<String, String> = mapOf(
            "blade runner 2049" to "Denis Villeneuve",
            "dune" to "Denis Villeneuve",
            "dune: part two" to "Denis Villeneuve",
            "arrival" to "Denis Villeneuve",
            "sicario" to "Denis Villeneuve",
            "prisoners" to "Denis Villeneuve",
            "oppenheimer" to "Christopher Nolan",
            "interstellar" to "Christopher Nolan",
            "inception" to "Christopher Nolan",
            "the dark knight" to "Christopher Nolan",
            "tenet" to "Christopher Nolan",
            "memento" to "Christopher Nolan",
            "dunkirk" to "Christopher Nolan",
            "fight club" to "David Fincher",
            "se7en" to "David Fincher",
            "the social network" to "David Fincher",
            "zodiac" to "David Fincher",
            "gone girl" to "David Fincher",
            "pulp fiction" to "Quentin Tarantino",
            "inglourious basterds" to "Quentin Tarantino",
            "django unchained" to "Quentin Tarantino",
            "kill bill: vol. 1" to "Quentin Tarantino",
            "once upon a time in hollywood" to "Quentin Tarantino",
            "goodfellas" to "Martin Scorsese",
            "taxi driver" to "Martin Scorsese",
            "the wolf of wall street" to "Martin Scorsese",
            "the departed" to "Martin Scorsese",
            "2001: a space odyssey" to "Stanley Kubrick",
            "the shining" to "Stanley Kubrick",
            "a clockwork orange" to "Stanley Kubrick",
            "alien" to "Ridley Scott",
            "blade runner" to "Ridley Scott",
            "gladiator" to "Ridley Scott",
            "twin peaks" to "David Lynch",
            "mulholland drive" to "David Lynch",
            "blue velvet" to "David Lynch",
            "breaking bad" to "Vince Gilligan",
            "better call saul" to "Vince Gilligan",
            "succession" to "Jesse Armstrong",
            "severance" to "Ben Stiller",
            "the sopranos" to "David Chase",
            "the wire" to "David Simon",
            "mad men" to "Matthew Weiner",
            "parasite" to "Bong Joon-ho",
            "memories of murder" to "Bong Joon-ho",
            "spirited away" to "Hayao Miyazaki",
            "princess mononoke" to "Hayao Miyazaki"
        )
    }
}
