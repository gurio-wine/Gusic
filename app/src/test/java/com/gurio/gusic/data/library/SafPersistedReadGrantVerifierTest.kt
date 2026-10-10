package com.gurio.gusic.data.library

import android.net.Uri
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SafPersistedReadGrantVerifierTest {
    @Test
    fun `read grant is accepted only after exact uri appears in persisted permissions`() {
        val uri = mockk<Uri>()
        val store = FakePermissionStore(
            persisted = listOf(PersistedUriReadPermission(uri, isReadPermission = true)),
        )

        assertTrue(SafPersistedReadGrantVerifier(store).ensureReadGrant(uri))
        assertTrue(store.takeCalled)
    }

    @Test
    fun `missing persisted permission fails even when acquisition was attempted`() {
        val uri = mockk<Uri>()
        val store = FakePermissionStore(persisted = emptyList())

        assertFalse(SafPersistedReadGrantVerifier(store).ensureReadGrant(uri))
        assertTrue(store.takeCalled)
    }

    @Test
    fun `write-only or different-uri permission does not satisfy read grant`() {
        val uri = mockk<Uri>()
        val otherUri = mockk<Uri>()
        val store = FakePermissionStore(
            persisted = listOf(
                PersistedUriReadPermission(uri, isReadPermission = false),
                PersistedUriReadPermission(otherUri, isReadPermission = true),
            ),
        )

        assertFalse(SafPersistedReadGrantVerifier(store).ensureReadGrant(uri))
    }

    private class FakePermissionStore(
        private val persisted: List<PersistedUriReadPermission>,
    ) : PersistedUriPermissionStore {
        var takeCalled: Boolean = false
            private set

        override fun takePersistableReadPermission(uri: Uri) {
            takeCalled = true
        }

        override fun persistedUriReadPermissions(): List<PersistedUriReadPermission> = persisted
    }
}
