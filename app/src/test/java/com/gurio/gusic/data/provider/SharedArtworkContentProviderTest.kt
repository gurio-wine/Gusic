package com.gurio.gusic.data.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedArtworkContentProviderTest {

    @Test
    fun artworkReadAccess_allowsProviderProcess() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 1001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
            )
        ).isTrue()
    }

    @Test
    fun artworkReadAccess_allowsExplicitUriGrant() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 2001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
        ).isTrue()
    }

    @Test
    fun artworkReadAccess_rejectsUntrustedExternalCaller() {
        assertThat(
            SharedArtworkContentProvider.hasArtworkReadAccess(
                callingUid = 2001,
                providerUid = 1001,
                uriPermissionResult = android.content.pm.PackageManager.PERMISSION_DENIED,
            )
        ).isFalse()
    }

    @Test
    fun buildSongUri_usesDedicatedArtworkAuthority() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.gurio.gusic",
            songId = 42L
        )

        assertThat(uri).isEqualTo("content://com.gurio.gusic.artwork/song/42")
    }

    @Test
    fun buildSongUri_preservesCacheBustToken() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.gurio.gusic",
            songId = 42L,
            cacheBustToken = "1234"
        )

        assertThat(uri)
            .isEqualTo("content://com.gurio.gusic.artwork/song/42?t=1234")
    }

    @Test
    fun parseSongId_rejectsOtherAuthorities() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://example.com.artwork/song/42",
            packageName = "com.gurio.gusic"
        )

        assertThat(songId).isNull()
    }

    @Test
    fun parseSongId_readsSharedArtworkSongUri() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://com.gurio.gusic.artwork/song/42",
            packageName = "com.gurio.gusic"
        )

        assertThat(songId).isEqualTo(42L)
    }
}
