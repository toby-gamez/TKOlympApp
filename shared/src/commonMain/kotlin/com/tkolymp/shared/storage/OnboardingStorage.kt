package com.tkolymp.shared.storage

expect class OnboardingStorage(platformContext: Any) {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingCompleted()
}
