package com.gurio.gusic.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Compatibility tombstone for work persisted by prior app versions. It deliberately performs no
 * library synchronization: in particular it never opens MediaStore, queries ContentResolver, or
 * deletes database rows based on a device-wide scan.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val WORK_NAME = "com.gurio.gusic.data.worker.SyncWorker"
        const val PERIODIC_MAINTENANCE_WORK_NAME =
            "com.gurio.gusic.data.worker.SyncWorker.PeriodicMaintenance"
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PHASE = "progress_phase"
        const val OUTPUT_TOTAL_SONGS = "output_total_songs"
    }
}
