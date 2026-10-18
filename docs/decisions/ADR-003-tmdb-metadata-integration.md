# ADR-003: TMDB API v3 as Authoritative Media Metadata Provider

## Status
Accepted

## Date
2026-08-25

## Context
A tracking application requires comprehensive, global, and up-to-date metadata for movies and TV shows, including:
- Show/movie title, synopsis, release/air dates, runtime, genres, content ratings.
- Structured season-by-season and episode-by-episode lists.
- Poster, backdrop, and still image assets across multiple resolutions.
- Trending and discovery endpoints for exploration.

Building and maintaining our own media catalog is infeasible. We evaluated third-party metadata providers.

## Decision
Use **The Movie Database (TMDB) API v3** as the single authoritative provider for media metadata:

1. **Retrofit Client**: Consume TMDB REST endpoints using Retrofit and OkHttp with Kotlinx Serialization.
2. **Key Injection**: Inject the TMDB API key dynamically via `local.properties` into `BuildConfig.TMDB_API_KEY` and append via an OkHttp `Interceptor` (`TmdbApiKeyInterceptor`).
3. **Optimized Image URLs**: Standardize image resolution loading using `TmdbImageUtil` (e.g. `w185` for poster thumbnails, `w500` for cards, `w780` or `original` for immersive backdrops) to optimize mobile data consumption.
4. **Append to Response**: Utilize TMDB's `append_to_response=content_ratings` query parameter on show detail requests to fetch ratings in a single round-trip.

## Alternatives Considered

### TVMaze API
- **Pros**: Free and open for TV shows with high accuracy for episode air times.
- **Cons**: Lacks movie metadata entirely. Would require a separate provider (such as OMDb) for movies, doubling integration complexity and creating inconsistent ID spaces.
- **Rejected**: Fragmented ID schemas between TV and movies would complicate the local database design.

### OMDb API / IMDb Scraping
- **Pros**: High brand recognition.
- **Cons**: Severe rate limits on free tier, lack of structured episode-level breakdown, and legal restrictions on scraping IMDb directly.
- **Rejected**: Inadequate episode-level granularity and commercial licensing friction.

## Consequences

### Positive
- Unified catalog for both movies and TV shows under a single numerical ID space (`tmdb_id`).
- High-quality CDN images distributed globally.
- Rich discovery and trending feeds updated daily/weekly.

### Negative / Trade-offs
- Subject to TMDB rate limits (approx. 40 requests per 10-second window); requires intelligent client-side caching in Room to avoid spamming the API.
- TMDB attribution requirement must be maintained in application settings / about screen.
