package com.gurio.gusic.presentation.screens

/** Setup permission prompts that explain optional features must never block setup completion. */
enum class SetupPermissionGate {
    NOTIFICATIONS
}

/** Returns the optional permission gate presented by a setup page, if any. */
fun setupPermissionGateForPage(page: SetupPage): SetupPermissionGate? = when (page) {
    SetupPage.NotificationsPermission -> SetupPermissionGate.NOTIFICATIONS
    else -> null
}

/** Returns whether setup may proceed past the given optional permission gate. */
@Suppress("UNUSED_PARAMETER")
fun setupPermissionGateSatisfied(
    gate: SetupPermissionGate?,
    granted: Boolean
): Boolean = when (gate) {
    null, SetupPermissionGate.NOTIFICATIONS -> true
}
