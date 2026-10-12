package com.gurio.gusic.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gurio.gusic.data.database.MusicDao
import com.gurio.gusic.data.database.toSong
import com.gurio.gusic.data.preferences.UserPreferencesRepository
import com.gurio.gusic.data.repository.LyricsRepository
import com.gurio.gusic.utils.AlbumArtCacheManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Performs maintenance against records already stored in the app database.
 * This worker deliberately has no MediaStore or ContentResolver dependency.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val lyricsRepository: LyricsRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (userPreferencesRepository.autoScanLrcFilesFlow.first()) {
                val songIds = musicDao.getAllMediaStoreSongIds()
                val total = songIds.size
                var completed = 0
                songIds.chunked(LRC_BATCH_SIZE).forEach { batch ->
                    val songs = musicDao.getSongsByIdsListSimple(batch).map { it.toSong() }
                    lyricsRepository.scanAndAssignLocalLrcFiles(songs) { current, _ ->
                        setProgress(workDataOf(PROGRESS_CURRENT to completed + current, PROGRESS_TOTAL to total))
                    }
                    completed += batch.size
                }
            }

            setProgress(workDataOf(PROGRESS_PHASE to PHASE_CLEANING_CACHE))
            AlbumArtCacheManager.cleanOrphanedCacheFiles(
                applicationContext,
                musicDao.getAllSongIds().toSet()
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Database maintenance failed")
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "com.gurio.gusic.data.worker.MaintenanceWorker.Periodic"
        private const val TAG = "MaintenanceWorker"
        private const val LRC_BATCH_SIZE = 1_000
        private const val PROGRESS_CURRENT = SyncWorker.PROGRESS_CURRENT
        private const val PROGRESS_TOTAL = SyncWorker.PROGRESS_TOTAL
        private const val PROGRESS_PHASE = SyncWorker.PROGRESS_PHASE
        private val PHASE_CLEANING_CACHE = SyncProgress.SyncPhase.CLEANING_CACHE.ordinal

        fun periodicWork(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true)
                .build()
            return PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        }
    }
}
