package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.storage.OnboardingStorage

class OnboardingViewModel(
    private val onboardingStorage: OnboardingStorage = ServiceLocator.onboardingStorage
) {
    suspend fun hasSeenOnboarding(): Boolean =
        onboardingStorage.hasSeenOnboarding()

    suspend fun completeOnboarding() {
        onboardingStorage.setOnboardingCompleted()
    }
}
