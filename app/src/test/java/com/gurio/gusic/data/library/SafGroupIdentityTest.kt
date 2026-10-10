package com.gurio.gusic.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SafGroupIdentityTest {
    @Test
    fun `album tracks with normalized same metadata share group identity`() {
        assertEquals(
            SafGroupIdentity.albumNativeId("Blue Notes", "The Trio", "doc-1"),
            SafGroupIdentity.albumNativeId(" blue notes ", "THE TRIO", "doc-2"),
        )
    }

    @Test
    fun `artists with normalized same metadata share group identity`() {
        assertEquals(
            SafGroupIdentity.artistNativeId("The Trio", "doc-1"),
            SafGroupIdentity.artistNativeId(" the trio ", "doc-2"),
        )
    }

    @Test
    fun `group key segments avoid delimiter collisions`() {
        assertNotEquals(
            SafGroupIdentity.albumNativeId("a:b", "c", "doc"),
            SafGroupIdentity.albumNativeId("a", "b:c", "doc"),
        )
    }

    @Test
    fun `missing grouping metadata falls back to exact document id`() {
        assertNotEquals(
            SafGroupIdentity.albumNativeId(null, "Artist", "doc-1"),
            SafGroupIdentity.albumNativeId(null, "Artist", "doc-2"),
        )
    }
}
