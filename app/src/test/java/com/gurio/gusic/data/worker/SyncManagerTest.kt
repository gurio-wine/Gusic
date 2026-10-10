package com.gurio.gusic.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncManagerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns workManager
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList<WorkInfo>())
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun `startup and manual refresh entry points never enqueue scan work`() {
        val manager = SyncManager(context)

        manager.sync()
        manager.incrementalSync()
        manager.forceRefresh()
        manager.fullSync()
        manager.fullSync(deepScan = false)
        manager.rebuildDatabase()

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `start cancels legacy scan chains and schedules independent maintenance`() {
        SyncManager(context).start()

        verify { workManager.cancelUniqueWork(SyncWorker.WORK_NAME) }
        verify { workManager.cancelUniqueWork(SyncWorker.PERIODIC_MAINTENANCE_WORK_NAME) }
        verify {
            workManager.enqueueUniquePeriodicWork(
                MaintenanceWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                any()
            )
        }
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(
                SyncWorker.PERIODIC_MAINTENANCE_WORK_NAME,
                any(),
                any()
            )
        }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `start is idempotent`() {
        val manager = SyncManager(context)

        manager.start()
        manager.start()

        verify(exactly = 1) { workManager.cancelUniqueWork(SyncWorker.WORK_NAME) }
        verify(exactly = 1) { workManager.cancelUniqueWork(SyncWorker.PERIODIC_MAINTENANCE_WORK_NAME) }
        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                MaintenanceWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                any()
            )
        }
    }
}
