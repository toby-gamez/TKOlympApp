package com.tkolymp.shared.storage

import platform.Foundation.NSUserDefaults

actual class OnboardingStorage actual constructor(platformContext: Any) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun hasSeenOnboarding(): Boolean =
        defaults.boolForKey("onboarding_completed")

    actual suspend fun setOnboardingCompleted() {
        defaults.setBool(true, "onboarding_completed")
        defaults.synchronize()
    }
}
