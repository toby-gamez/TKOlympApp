package com.tkolymp.shared.storage

import android.content.Context

actual class OnboardingStorage actual constructor(platformContext: Any) {
    private val prefs = (platformContext as Context)
        .getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

    actual suspend fun hasSeenOnboarding(): Boolean =
        prefs.getBoolean("onboarding_completed", false)

    actual suspend fun setOnboardingCompleted() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }
}
