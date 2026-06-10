package com.tkolymp.shared.storage

import android.content.Context
import com.tkolymp.shared.models.UserRole

actual class OnboardingStorage actual constructor(platformContext: Any) {
    private val prefs = (platformContext as Context)
        .getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

    actual suspend fun hasSeenOnboarding(): Boolean =
        prefs.getBoolean("onboarding_completed", false)

    actual suspend fun setOnboardingCompleted() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    actual suspend fun setUserRole(role: UserRole) {
        prefs.edit().putString("user_role", role.name).apply()
    }

    actual suspend fun getUserRole(): UserRole? {
        val name = prefs.getString("user_role", null) ?: return null
        return try {
            UserRole.valueOf(name)
        } catch (_: Exception) {
            null
        }
    }

    actual suspend fun hasSeenTutorial(): Boolean =
        prefs.getBoolean("tutorial_seen", false)

    actual suspend fun setTutorialSeen() {
        prefs.edit().putBoolean("tutorial_seen", true).apply()
    }
}
