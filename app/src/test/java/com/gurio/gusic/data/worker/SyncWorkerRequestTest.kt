package com.gurio.gusic.data.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncWorkerRequestTest {

    @Test
    fun `periodic maintenance targets dedicated top-level worker with no scan input`() {
        val request = MaintenanceWorker.periodicWork()

        assertEquals(MaintenanceWorker::class.java.name, request.workSpec.workerClassName)
        assertTrue(request.workSpec.input.keyValueMap.isEmpty())
        assertTrue(request.workSpec.constraints.requiresCharging())
        assertEquals("com.gurio.gusic.data.worker.MaintenanceWorker.Periodic", MaintenanceWorker.PERIODIC_WORK_NAME)
    }
}
