package com.tkolymp.shared.storage

import platform.Foundation.NSUserDefaults
import com.tkolymp.shared.models.UserRole

actual class OnboardingStorage actual constructor(platformContext: Any) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun hasSeenOnboarding(): Boolean =
        defaults.boolForKey("onboarding_completed")

    actual suspend fun setOnboardingCompleted() {
        defaults.setBool(true, "onboarding_completed")
        defaults.synchronize()
    }

    actual suspend fun setUserRole(role: UserRole) {
        defaults.setObject(role.name, "user_role")
        defaults.synchronize()
    }

    actual suspend fun getUserRole(): UserRole? {
        val name = defaults.stringForKey("user_role") ?: return null
        return try {
            UserRole.valueOf(name)
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun hasSeenTutorial(): Boolean =
        defaults.boolForKey("tutorial_seen")

    actual suspend fun setTutorialSeen() {
        defaults.setBool(true, "tutorial_seen")
        defaults.synchronize()
    }
}
