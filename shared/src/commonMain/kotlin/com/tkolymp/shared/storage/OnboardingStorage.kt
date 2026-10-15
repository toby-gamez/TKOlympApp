package com.tkolymp.shared.storage

expect class OnboardingStorage(platformContext: Any) {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingCompleted()
    suspend fun setUserRole(role: com.tkolymp.shared.models.UserRole)
    suspend fun getUserRole(): com.tkolymp.shared.models.UserRole?
    suspend fun hasSeenTutorial(): Boolean
    suspend fun setTutorialSeen()
    suspend fun hasAcceptedPrivacyConsent(): Boolean
    suspend fun setPrivacyConsentAccepted()
    /** Whether automatic anonymous error/crash reports may be sent. Defaults to true; chosen on the privacy consent screen. */
    suspend fun isErrorReportingEnabled(): Boolean
    suspend fun setErrorReportingEnabled(enabled: Boolean)
}
