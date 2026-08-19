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
    private val syncManager: SyncManager
) : ViewModel() {

    private val _communityState = MutableStateFlow<CommunityState>(CommunityState.Loading)
    val communityState: StateFlow<CommunityState> = _communityState.asStateFlow()

    private var currentMediaId: Int = 0
    private var currentSeasonNum: Int? = null
    private var currentEpisodeNum: Int? = null

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
                _communityState.value = CommunityState.Error(e.message ?: "Failed to load community reviews")
            }
        }
    }

    fun postReview(text: String, vibeEmoji: String, isSpoiler: Boolean) {
        viewModelScope.launch {
            val entity = ReviewEntity(
                mediaId = currentMediaId,
                seasonNum = currentSeasonNum,
                episodeNum = currentEpisodeNum,
                reviewText = text,
                vibeEmoji = vibeEmoji,
                isSpoiler = isSpoiler,
                syncStatus = SyncStatus.PENDING
            )
            reviewDao.insertReview(entity)
            syncManager.syncWatchedEpisodesNow() // Forces WorkManager to run sync which includes reviews
            
            // Reload the watercooler to show our newly posted review once it syncs
            // (In a real app we might optimistically insert it into the list)
        }
    }
}
