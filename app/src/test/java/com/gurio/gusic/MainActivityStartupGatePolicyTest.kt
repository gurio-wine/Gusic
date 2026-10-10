package com.gurio.gusic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainActivityStartupGatePolicyTest {

    @Test
    fun completedSetupDoesNotRequireMediaOrNotificationPermissions() {
        assertThat(
            shouldShowStartupSetup(
                isSetupComplete = true,
                isBenchmarkMode = false
            )
        ).isFalse()
    }

    @Test
    fun incompleteSetupStillShowsSetup() {
        assertThat(
            shouldShowStartupSetup(
                isSetupComplete = false,
                isBenchmarkMode = false
            )
        ).isTrue()
    }

    @Test
    fun benchmarkModeSkipsSetup() {
        assertThat(
            shouldShowStartupSetup(
                isSetupComplete = false,
                isBenchmarkMode = true
            )
        ).isFalse()
    }
}
