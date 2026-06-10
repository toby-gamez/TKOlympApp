package com.tkolymp.shared.storage

expect class OnboardingStorage(platformContext: Any) {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingCompleted()
    suspend fun setUserRole(role: com.tkolymp.shared.models.UserRole)
    suspend fun getUserRole(): com.tkolymp.shared.models.UserRole?
    suspend fun hasSeenTutorial(): Boolean
    suspend fun setTutorialSeen()
}
