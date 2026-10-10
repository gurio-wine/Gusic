package com.gurio.gusic.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class FileExplorerDirectoryMergeTest {

    @Test
    fun `merge preserves filesystem directories and unknown counts`() {
        val firstAlbum = RawDirectoryEntry(
            file = File("/storage/emulated/0/Music/First Album"),
            directAudioCount = -1,
            totalAudioCount = -1,
            canonicalPath = "/storage/emulated/0/Music/First Album"
        )
        val secondAlbum = RawDirectoryEntry(
            file = File("/storage/emulated/0/Music/Second Album"),
            directAudioCount = -1,
            totalAudioCount = -1,
            canonicalPath = "/storage/emulated/0/Music/Second Album"
        )

        val merged = mergeDirectoryEntryLists(
            filesystemEntries = listOf(firstAlbum, secondAlbum),
            additionalFilesystemEntries = emptyList()
        )

        assertEquals(
            listOf(
                "/storage/emulated/0/Music/First Album",
                "/storage/emulated/0/Music/Second Album"
            ),
            merged.map { it.canonicalPath }
        )
        assertEquals(listOf(-1, -1), merged.map { it.directAudioCount })
        assertEquals(listOf(-1, -1), merged.map { it.totalAudioCount })
    }
}
