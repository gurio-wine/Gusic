package com.gurio.gusic.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber

/**
 * Keeps the existing UI-facing sync status and entry points while retiring automatic
 * device-wide catalog scans. User-selected SAF imports remain handled by their own flow.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)
    private val sharingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)
    @Volatile
    private var currentSyncWorkId: UUID? = null

    val isSyncing: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING } ||
                    workInfos.any {
                        it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0
                    }
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    /** Emits once for a failed legacy foreground sync when no run remains active. */
    val syncFailed: Flow<Unit> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                latestForegroundSyncWork(workInfos)?.state == WorkInfo.State.FAILED
            }
            .distinctUntilChanged()
            .filter { it }
            .map { }
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 0
            )

    /**
     * Cancels scan-capable work persisted by older app versions and schedules only
     * database-persisted maintenance. Idempotent across repeated application startup.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
        workManager.cancelUniqueWork(SyncWorker.PERIODIC_MAINTENANCE_WORK_NAME)
        workManager.enqueueUniquePeriodicWork(
            MaintenanceWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            MaintenanceWorker.periodicWork()
        )
    }

    /** Retained for existing callers; automatic local catalog sync is disabled. */
    fun sync() {
        Timber.tag(TAG).i("Automatic MediaStore catalog sync is disabled")
    }

    /** Catalog refreshes are disabled; import selected files through SAF instead. */
    fun incrementalSync() {
        Timber.tag(TAG).i("Local catalog refresh is disabled; import selected files through SAF")
    }

    /** Device-wide full scans are disabled. */
    fun fullSync(deepScan: Boolean = true) {
        Timber.tag(TAG).i("Full MediaStore scan is disabled")
    }

    /** Rebuilding the database from the device catalog is disabled. */
    fun rebuildDatabase() {
        Timber.tag(TAG).i("MediaStore-backed database rebuild is disabled")
    }

    /** Catalog refreshes are disabled; import selected files through SAF instead. */
    fun forceRefresh() {
        Timber.tag(TAG).i("Local catalog refresh is disabled; import selected files through SAF")
    }

    /** Detailed status retained for UI consumers of already persisted worker records. */
    val syncProgress: Flow<SyncProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                val runningWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val succeededWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                val enqueuedWork = workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }

                when {
                    runningWork != null -> {
                        val current = runningWork.progress.getInt(SyncWorker.PROGRESS_CURRENT, 0)
                        val total = runningWork.progress.getInt(SyncWorker.PROGRESS_TOTAL, 0)
                        val phaseOrdinal = runningWork.progress.getInt(SyncWorker.PROGRESS_PHASE, 0)
                        val phase = try {
                            SyncProgress.SyncPhase.entries[phaseOrdinal]
                        } catch (e: IndexOutOfBoundsException) {
                            SyncProgress.SyncPhase.IDLE
                        }
                        SyncProgress(
                            isRunning = true,
                            currentCount = current,
                            totalCount = total,
                            isCompleted = false,
                            phase = phase
                        )
                    }
                    succeededWork != null -> {
                        val total = readCompletedSongCount(succeededWork)
                        SyncProgress(
                            isRunning = false,
                            currentCount = total,
                            totalCount = total,
                            isCompleted = true,
                            phase = SyncProgress.SyncPhase.COMPLETING
                        )
                    }
                    enqueuedWork != null && enqueuedWork.runAttemptCount == 0 ->
                        SyncProgress(isRunning = true, isCompleted = false, phase = SyncProgress.SyncPhase.IDLE)
                    else -> SyncProgress()
                }
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    val isFetchingChanges: Flow<Boolean> = syncProgress
        .map { progress -> progress.isRunning && progress.phase in CHANGE_PHASES }
        .distinctUntilChanged()
        .shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    val isPerformingMaintenance: Flow<Boolean> = syncProgress
        .map { progress -> progress.isRunning && progress.phase in MAINTENANCE_PHASES }
        .distinctUntilChanged()
        .shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    private fun readCompletedSongCount(workInfo: WorkInfo): Int {
        val intValue = workInfo.outputData.getInt(SyncWorker.OUTPUT_TOTAL_SONGS, -1)
        if (intValue >= 0) return intValue

        return workInfo.outputData
            .getLong(SyncWorker.OUTPUT_TOTAL_SONGS, 0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun latestForegroundSyncWork(workInfos: List<WorkInfo>): WorkInfo? {
        val activeWork = workInfos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        if (activeWork != null) {
            currentSyncWorkId = activeWork.id
            return activeWork
        }
        return currentSyncWorkId?.let { id -> workInfos.firstOrNull { it.id == id } }
    }

    companion object {
        private const val TAG = "SyncManager"

        private val CHANGE_PHASES = setOf(
            SyncProgress.SyncPhase.IDLE,
            SyncProgress.SyncPhase.FETCHING_MEDIASTORE,
            SyncProgress.SyncPhase.PROCESSING_FILES,
            SyncProgress.SyncPhase.SAVING_TO_DATABASE
        )

        private val MAINTENANCE_PHASES = setOf(
            SyncProgress.SyncPhase.SCANNING_LRC,
            SyncProgress.SyncPhase.CLEANING_CACHE,
            SyncProgress.SyncPhase.SYNCING_CLOUD,
            SyncProgress.SyncPhase.COMPLETING
        )
    }
}

/** Data class representing the status of legacy sync work for UI compatibility. */
data class SyncProgress(
    val isRunning: Boolean = false,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val isCompleted: Boolean = false,
    val phase: SyncPhase = SyncPhase.IDLE
) {
    enum class SyncPhase {
        IDLE,
        FETCHING_MEDIASTORE,
        PROCESSING_FILES,
        SAVING_TO_DATABASE,
        SCANNING_LRC,
        CLEANING_CACHE,
        SYNCING_CLOUD,
        COMPLETING
    }

    val progress: Float
        get() = if (totalCount > 0) currentCount.toFloat() / totalCount else 0f

    val hasProgress: Boolean
        get() = totalCount > 0
}
