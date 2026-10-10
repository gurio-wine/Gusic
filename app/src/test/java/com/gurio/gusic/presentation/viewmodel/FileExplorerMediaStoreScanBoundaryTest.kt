package com.gurio.gusic.presentation.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import com.gurio.gusic.data.preferences.UserPreferencesRepository
import com.gurio.gusic.utils.StorageInfo
import com.gurio.gusic.utils.StorageType
import com.gurio.gusic.utils.StorageUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileExplorerMediaStoreScanBoundaryTest {

    @Test
    fun `priming a filesystem root lists child directories with unknown counts`() = runTest {
        val root = Files.createTempDirectory("file-explorer-scan-boundary").toFile()
        val child = File(root, "Visible album").apply { mkdir() }
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()

        try {
            every { context.contentResolver } returns contentResolver
            every { preferences.allowedDirectoriesFlow } returns flowOf(emptySet())
            every { preferences.blockedDirectoriesFlow } returns flowOf(emptySet())

            mockkObject(StorageUtils)
            every { StorageUtils.getAvailableStorages(context) } returns listOf(
                StorageInfo(root, "Test storage", StorageType.INTERNAL, false)
            )

            val holder = FileExplorerStateHolder(
                userPreferencesRepository = preferences,
                scope = backgroundScope,
                context = context,
                initialRoot = root
            )

            holder.primeExplorerRoot()?.join()
            val visibleEntries = holder.currentDirectoryChildren.first { it.isNotEmpty() }

            assertEquals(listOf(child.absolutePath), visibleEntries.map { it.canonicalPath })
            assertEquals(-1, visibleEntries.single().directAudioCount)
            assertEquals(-1, visibleEntries.single().totalAudioCount)
            verify(exactly = 0) {
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        } finally {
            unmockkObject(StorageUtils)
            root.deleteRecursively()
        }
    }

    @Test
    fun `priming an empty filesystem root does not enumerate the whole external audio collection`() = runTest {
        val root = Files.createTempDirectory("file-explorer-scan-boundary").toFile()
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()

        try {
            every { context.contentResolver } returns contentResolver
            every { preferences.allowedDirectoriesFlow } returns flowOf(emptySet())
            every { preferences.blockedDirectoriesFlow } returns flowOf(emptySet())

            mockkObject(StorageUtils)
            every { StorageUtils.getAvailableStorages(context) } returns listOf(
                StorageInfo(
                    path = root,
                    displayName = "Test storage",
                    storageType = StorageType.INTERNAL,
                    isRemovable = false
                )
            )

            val holder = FileExplorerStateHolder(
                userPreferencesRepository = preferences,
                scope = backgroundScope,
                context = context,
                initialRoot = root
            )

            holder.primeExplorerRoot()?.join()

            verify(exactly = 0) {
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        } finally {
            unmockkObject(StorageUtils)
            root.deleteRecursively()
        }
    }
}
