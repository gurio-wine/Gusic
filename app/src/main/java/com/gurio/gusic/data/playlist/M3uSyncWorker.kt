package com.gurio.gusic.data.playlist

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Compatibility tombstone for WorkManager requests queued by older app versions.
 * Automatic folder synchronization was retired; a stale request must never read or mutate playlists.
 */
@HiltWorker
class M3uSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Timber.i("Ignoring retired automatic M3U folder-sync request")
        return Result.success()
    }
}

/** Cancels automatic M3U folder-sync work left by older app versions. */
@Singleton
class LegacyM3uSyncRetirer @Inject constructor(
    private val workManager: WorkManager,
) {
    fun cancelQueuedWork() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "automatic_m3u_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "automatic_m3u_sync_immediate"
    }
}
