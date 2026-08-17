package dev.sequel.app.data.remote.tmdb

/**
 * TMDB image URL builder.
 * TMDB stores only relative paths (e.g. "/abc123.jpg");
 * this constructs full CDN URLs.
 */
object TmdbImageUtil {

    private const val BASE_URL = "https://image.tmdb.org/t/p/"

    /** Poster sizes commonly used in TMDB. */
    enum class PosterSize(val path: String) {
        W92("w92"),
        W154("w154"),
        W185("w185"),
        W342("w342"),
        W500("w500"),
        W780("w780"),
        ORIGINAL("original")
    }

    /** Backdrop sizes. */
    enum class BackdropSize(val path: String) {
        W300("w300"),
        W780("w780"),
        W1280("w1280"),
        ORIGINAL("original")
    }

    /** Still (episode thumbnail) sizes. */
    enum class StillSize(val path: String) {
        W92("w92"),
        W185("w185"),
        W300("w300"),
        ORIGINAL("original")
    }

    fun posterUrl(path: String?, size: PosterSize = PosterSize.W342): String? {
        return path?.let { "$BASE_URL${size.path}$it" }
    }

    fun backdropUrl(path: String?, size: BackdropSize = BackdropSize.W780): String? {
        return path?.let { "$BASE_URL${size.path}$it" }
    }

    fun stillUrl(path: String?, size: StillSize = StillSize.W300): String? {
        return path?.let { "$BASE_URL${size.path}$it" }
    }
}
