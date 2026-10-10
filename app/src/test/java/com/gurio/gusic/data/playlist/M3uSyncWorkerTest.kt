package com.gurio.gusic.data.playlist

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class M3uSyncWorkerTest {
    @Test
    fun `legacy retirer cancels exact automatic M3U unique work names`() {
        val workManager = mockk<WorkManager>(relaxed = true)

        LegacyM3uSyncRetirer(workManager).cancelQueuedWork()

        verify(exactly = 1) {
            workManager.cancelUniqueWork("automatic_m3u_sync_periodic")
        }
        verify(exactly = 1) {
            workManager.cancelUniqueWork("automatic_m3u_sync_immediate")
        }
    }

    @Test
    fun `stale worker succeeds without running retired folder synchronization`() = runTest {
        val worker = M3uSyncWorker(
            appContext = mockk<Context>(relaxed = true),
            workerParams = mockk<WorkerParameters>(relaxed = true),
        )

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }
}
