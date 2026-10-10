package com.gurio.gusic.presentation.screens

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetupPermissionPolicyTest {
    @Test
    fun `notification setup page is optional and denied permission still advances`() {
        val gate = setupPermissionGateForPage(SetupPage.NotificationsPermission)

        assertNotNull(gate)
        assertTrue(setupPermissionGateSatisfied(gate, granted = false))
    }

    @Test
    fun `non-permission setup pages have no permission gate`() {
        val gate = setupPermissionGateForPage(SetupPage.Welcome)

        assertTrue(setupPermissionGateSatisfied(gate, granted = false))
    }
}
