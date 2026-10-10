package com.gurio.gusic.data.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryItemIdentitySafetyTest {

    @Test
    fun `failed regrant preserves old locator grant state and missing tombstone`() {
        val existing = LibraryItemIdentityEntity(
            itemType = LibraryItemIdentityEntity.TYPE_SONG,
            sourceNamespace = "saf:provider",
            nativeId = "opaque-42",
            entityId = 100L,
            locatorUri = "content://provider/tree/old/document/opaque-42",
            hasPersistedReadGrant = true,
            isMissing = true,
            lastSeenAt = 7L,
        )

        assertEquals(existing, LibraryItemGrantPolicy.afterGrantFailure(existing))
    }

    @Test
    fun `failed first grant does not create an identity mapping`() {
        assertNull(LibraryItemGrantPolicy.afterGrantFailure(null))
    }

    @Test
    fun `confirmed grant updates locator and clears missing tombstone`() {
        val existing = LibraryItemIdentityEntity(
            itemType = LibraryItemIdentityEntity.TYPE_SONG,
            sourceNamespace = "saf:provider",
            nativeId = "opaque-42",
            entityId = 100L,
            locatorUri = "content://provider/tree/old/document/opaque-42",
            hasPersistedReadGrant = false,
            isMissing = true,
            lastSeenAt = 7L,
        )

        val updated = checkNotNull(
            LibraryItemGrantPolicy.afterSuccessfulGrant(
                existing,
                locatorUri = "content://provider/document/opaque-42",
                lastSeenAt = 99L,
            ),
        )

        assertEquals("content://provider/document/opaque-42", updated.locatorUri)
        assertEquals(true, updated.hasPersistedReadGrant)
        assertEquals(false, updated.isMissing)
        assertEquals(99L, updated.lastSeenAt)
    }

    @Test
    fun `new identity ID skips occupied IDs from every library entity kind`() {
        assertEquals(
            13L,
            LibraryItemIdAllocator.nextAvailableId(
                currentHighWater = 10L,
                occupiedIds = setOf(-500L, 11L, 12L),
            ),
        )
    }

    @Test
    fun `ID allocator fails rather than wrapping after Long max value`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            LibraryItemIdAllocator.nextAvailableId(
                currentHighWater = Long.MAX_VALUE,
                occupiedIds = emptySet(),
            )
        }
    }
}

