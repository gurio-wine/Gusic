package com.gurio.gusic.data.worker

import android.content.Context
import android.content.ContentResolver
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    @Test
    fun legacySyncWorkSucceedsWithoutAccessingContentResolver() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val guardedContext = object : ContextWrapper(appContext) {
            override fun getContentResolver(): ContentResolver =
                error("Legacy SyncWorker must not access ContentResolver")
        }

        val result = TestListenableWorkerBuilder<SyncWorker>(guardedContext)
            .build()
            .doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }
}
