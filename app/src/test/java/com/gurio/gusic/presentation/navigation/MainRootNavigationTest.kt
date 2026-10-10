package com.gurio.gusic.presentation.navigation

import com.gurio.gusic.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainRootNavigationTest {
    @Test
    fun `main root tabs expose recommend discover and mine labels in order`() {
        assertEquals(
            listOf(Screen.Home, Screen.LxMusic, Screen.Library),
            mainRootTabSpecs.map { it.screen },
        )
        assertEquals(
            listOf(R.string.tab_home, R.string.tab_online, R.string.tab_library),
            mainRootTabSpecs.map { it.labelResId },
        )
    }
}
