package dev.sequel.app.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.R
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@HiltWorker
class AirDateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val showDao: ShowDao,
    private val watchlistDao: WatchlistDao,
    private val tmdbApiService: TmdbApiService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "air_date_worker"
        const val NOTIFICATION_CHANNEL_ID = "air_date_channel"
    }

    override suspend fun doWork(): Result {
        try {
            // 1. Get all tracked TV shows (started shows + watchlist shows)
            val startedShows = showDao.getStartedTvShows().map { it.id }
            val watchlistShows = watchlistDao.getAllWatchlistTvShows().map { it.tmdbId }
            val allTrackedShowIds = (startedShows + watchlistShows).distinct()

            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 2. Fetch details for each and check next_episode_to_air
            for (showId in allTrackedShowIds) {
                try {
                    val detail = tmdbApiService.getTvShowDetail(showId)
                    val nextEpisode = detail.nextEpisodeToAir
                    if (nextEpisode != null && nextEpisode.airDate == todayStr) {
                        showNotification(
                            showName = detail.name,
                            episodeInfo = "S${nextEpisode.seasonNumber}E${nextEpisode.episodeNumber}: ${nextEpisode.name}"
                        )
                    }
                } catch (e: Exception) {
                    // Ignore individual show fetch errors
                }
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun showNotification(showName: String, episodeInfo: String) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "New Episode Air Dates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon
            .setContentTitle("New Episode Today: $showName")
            .setContentText(episodeInfo)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(showName.hashCode(), notification)
    }
}
