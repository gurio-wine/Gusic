package com.gurio.gusic.data.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceTypeTest {
    @Test
    fun `MediaStore content URI remains local`() {
        assertEquals(
            SourceType.LOCAL,
            SourceType.fromContentUri("content://media/external/audio/media/481"),
        )
    }

    @Test
    fun `standalone document URI is classified as SAF`() {
        assertEquals(
            SourceType.SAF,
            SourceType.fromContentUri("content://com.example.documents/document/primary%3AMusic%2Ftrack.mp3"),
        )
    }

    @Test
    fun `tree document URI is classified as SAF`() {
        assertEquals(
            SourceType.SAF,
            SourceType.fromContentUri(
                "content://com.example.documents/tree/primary%3AMusic/document/primary%3AMusic%2Ftrack.mp3",
            ),
        )
    }
}
