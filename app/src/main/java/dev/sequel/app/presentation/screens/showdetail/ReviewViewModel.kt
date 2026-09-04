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

sealed class CommunityState {
    data object Loading : CommunityState()
    data class Success(val reviews: List<SupabaseReviewDto>) : CommunityState()
    data class Error(val message: String) : CommunityState()
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
    private var currentSeasonNum: Int? = null
    private var currentEpisodeNum: Int? = null

    val currentUserId: String? = supabaseAuthService.currentUserId

    fun loadReviews(mediaId: Int, seasonNum: Int?, episodeNum: Int?) {
        currentMediaId = mediaId
        currentSeasonNum = seasonNum
        currentEpisodeNum = episodeNum
        
        viewModelScope.launch {
            _communityState.value = CommunityState.Loading
            try {
                // Fetch community reviews from Supabase directly for the watercooler
                val reviews = supabaseSyncService.fetchReviewsForMedia(
                    mediaId = mediaId,
                    seasonNum = seasonNum,
                    episodeNum = episodeNum
                )
                // Sort by newest first
                _communityState.value = CommunityState.Success(reviews.sortedByDescending { it.createdAt })
            } catch (e: Exception) {
                // If Supabase fails (e.g. not logged in), show empty state instead of error
                _communityState.value = CommunityState.Success(emptyList())
            }
        }
    }

    fun postReview(text: String, rating: Int?, isSpoiler: Boolean) {
        if (text.isBlank() && rating == null) return
        
        viewModelScope.launch {
            val entity = ReviewEntity(
                mediaId = currentMediaId,
                seasonNum = currentSeasonNum,
                episodeNum = currentEpisodeNum,
                reviewText = text.ifBlank { null },
                rating = rating,
                isSpoiler = isSpoiler,
                syncStatus = SyncStatus.PENDING
            )
            reviewDao.insertReview(entity)
            syncManager.syncWatchedEpisodesNow() // Forces WorkManager to run sync which includes reviews
            
            // Optimistically add review to the list so user sees it immediately
            val currentState = _communityState.value
            if (currentState is CommunityState.Success) {
                val optimisticReview = SupabaseReviewDto(
                    id = null,
                    userId = "you",
                    mediaId = currentMediaId,
                    seasonNum = currentSeasonNum,
                    episodeNum = currentEpisodeNum,
                    reviewText = text.ifBlank { null },
                    vibeEmoji = rating?.toString(),
                    isSpoiler = isSpoiler,
                    createdAt = System.currentTimeMillis().toString()
                )
                _communityState.value = CommunityState.Success(
                    listOf(optimisticReview) + currentState.reviews
                )
            }
        }
    }

    fun deleteReview(reviewId: String) {
        viewModelScope.launch {
            try {
                // Attempt to delete from cloud
                supabaseSyncService.deleteReview(reviewId)
            } catch (e: Exception) {
                // Ignore network errors, local delete will still happen
            }
            // Delete local cache
            reviewDao.deleteReviewForMedia(currentMediaId, currentSeasonNum, currentEpisodeNum)

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
