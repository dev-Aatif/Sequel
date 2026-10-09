package dev.sequel.app.presentation.screens.showdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.entity.ReviewEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.remote.supabase.SupabaseSyncService
import dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto
import dev.sequel.app.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import dev.sequel.app.domain.error.AppError

sealed class CommunityState {
    data object Loading : CommunityState()
    data class Success(val reviews: List<SupabaseReviewDto>) : CommunityState()
    data class Error(val error: AppError) : CommunityState()
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val supabaseSyncService: SupabaseSyncService,
    private val reviewDao: ReviewDao,
    private val syncManager: SyncManager,
    private val supabaseAuthService: dev.sequel.app.data.remote.supabase.SupabaseAuthService
) : ViewModel() {

    private val _communityState = MutableStateFlow<CommunityState>(CommunityState.Loading)
    val communityState: StateFlow<CommunityState> = _communityState.asStateFlow()

    private var currentMediaId: Int = 0
    private var currentMediaType: String = "tv"
    private var currentSeasonNum: Int? = null
    private var currentEpisodeNum: Int? = null

    val currentUserId: String?
        get() = supabaseAuthService.currentUserId

    fun loadReviews(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?) {
        currentMediaId = mediaId
        currentMediaType = mediaType
        currentSeasonNum = seasonNum
        currentEpisodeNum = episodeNum
        
        viewModelScope.launch {
            _communityState.value = CommunityState.Loading
            try {
                // Fetch community reviews from Supabase directly for the watercooler
                val reviews = supabaseSyncService.fetchReviewsForMedia(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    seasonNum = seasonNum,
                    episodeNum = episodeNum
                )
                // Filter to only text reviews (exclude rating-only entries)
                val textReviews = reviews.filter { !it.reviewText.isNullOrBlank() }
                // Deduplicate by id
                val deduped = textReviews.distinctBy { it.id ?: "${it.userId}_${it.mediaId}_${it.createdAt}" }
                // Sort by newest first
                _communityState.value = CommunityState.Success(deduped.sortedByDescending { it.createdAt })
            } catch (e: Exception) {
                // If Supabase fails (e.g. not logged in), show empty state instead of error
                _communityState.value = CommunityState.Success(emptyList())
            }
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    /**
     * Submit a rating (1-10) for the current media. This is completely separate from reviews.
     * Rating is stored as a rating-only row in the reviews table.
     */
    fun submitRating(rating: Int) {
        if (rating == 0) {
            deleteRating()
            return
        }
        viewModelScope.launch {
            reviewDao.upsertRating(currentMediaId, currentMediaType, currentSeasonNum, currentEpisodeNum, rating)
            
            syncJob?.cancel()
            syncJob = launch {
                kotlinx.coroutines.delay(400)
                syncManager.syncReviewsNow(currentMediaId)
            }
        }
    }

    /**
     * Delete the user's rating for the current media.
     */
    fun deleteRating() {
        viewModelScope.launch {
            val localReview = reviewDao.getReviewForMediaAndEpisode(currentMediaId, currentMediaType, currentSeasonNum, currentEpisodeNum)
            var needsSync = false
            if (localReview != null) {
                if (localReview.reviewText.isNullOrBlank()) {
                    reviewDao.deleteReviewById(localReview.id)
                    val supabaseId = localReview.supabaseId
                    if (supabaseId != null) {
                        try {
                            supabaseSyncService.deleteReview(supabaseId)
                        } catch (e: Exception) {}
                    } else {
                        currentUserId?.let { uid ->
                            try {
                                supabaseSyncService.deleteReviewForMedia(uid, currentMediaId, currentMediaType, currentSeasonNum, currentEpisodeNum)
                            } catch (e: Exception) {}
                        }
                    }
                } else {
                    reviewDao.deleteRating(currentMediaId, currentMediaType, currentSeasonNum, currentEpisodeNum)
                    needsSync = true
                }
            }
            if (needsSync) {
                syncJob?.cancel()
                syncJob = launch {
                    kotlinx.coroutines.delay(400)
                    syncManager.syncReviewsNow(currentMediaId)
                }
            }
        }
    }

    /**
     * Post a new text review. Multiple reviews per show are allowed.
     * This does NOT affect the rating.
     */
    fun postReview(text: String, isSpoiler: Boolean) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            reviewDao.upsertReviewText(currentMediaId, currentMediaType, currentSeasonNum, currentEpisodeNum, text, isSpoiler)
            syncManager.syncReviewsNow(currentMediaId)
            
            // Optimistically add review to the list so user sees it immediately
            val currentState = _communityState.value
            if (currentState is CommunityState.Success) {
                val optimisticReview = SupabaseReviewDto(
                    id = null,
                    userId = "you",
                    mediaId = currentMediaId,
                    mediaType = currentMediaType,
                    seasonNum = currentSeasonNum,
                    episodeNum = currentEpisodeNum,
                    reviewText = text,
                    rating = null,
                    isSpoiler = isSpoiler,
                    createdAt = System.currentTimeMillis().toString()
                )
                _communityState.value = CommunityState.Success(
                    listOf(optimisticReview) + currentState.reviews
                )
            }
        }
    }

    /**
     * Edit an existing review's text.
     */
    fun editReview(reviewId: String, newText: String, isSpoiler: Boolean) {
        viewModelScope.launch {
            // Try local first (by supabase_id)
            val local = reviewDao.getReviewBySupabaseId(reviewId)
            if (local != null) {
                reviewDao.updateReviewText(local.id, newText, isSpoiler)
                syncManager.syncReviewsNow(currentMediaId)
            }
            
            // Optimistically update UI
            val currentState = _communityState.value
            if (currentState is CommunityState.Success) {
                _communityState.value = CommunityState.Success(
                    currentState.reviews.map {
                        if (it.id == reviewId) it.copy(reviewText = newText, isSpoiler = isSpoiler) else it
                    }
                )
            }
        }
    }

    fun deleteReview(reviewId: String) {
        viewModelScope.launch {
            val localReview = reviewDao.getReviewBySupabaseId(reviewId)
            var needsSync = false
            if (localReview != null) {
                if (localReview.rating == null) {
                    reviewDao.deleteReviewById(localReview.id)
                    try {
                        supabaseSyncService.deleteReview(reviewId)
                    } catch (e: Exception) {}
                } else {
                    reviewDao.updateReviewText(localReview.id, "", false)
                    needsSync = true
                }
            } else {
                // Fallback if local not found, just delete remotely
                try {
                    supabaseSyncService.deleteReview(reviewId)
                } catch (e: Exception) {}
            }
            if (needsSync) {
                syncJob?.cancel()
                syncJob = launch {
                    kotlinx.coroutines.delay(400)
                    syncManager.syncReviewsNow(currentMediaId)
                }
            }

            // Optimistically update UI
            val currentState = _communityState.value
            if (currentState is CommunityState.Success) {
                _communityState.value = CommunityState.Success(
                    currentState.reviews.filter { it.id != reviewId }
                )
            }
        }
    }
}
