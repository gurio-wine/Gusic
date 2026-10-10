package com.gurio.gusic.data.library

import com.gurio.gusic.utils.MediaStorePermissionHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafMediaStoreFallbackTest {
    @Test
    fun positiveSafSongIdNeverFallsBackToMediaStore() {
        assertThat(
            MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest(
                "content://provider.example/document/audio%2Ftrack-42",
            ),
        ).isFalse()
    }
}
