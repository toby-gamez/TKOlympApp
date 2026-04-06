package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
import com.tkolymp.shared.storage.CalendarPreferenceStorage
import com.tkolymp.shared.storage.OnboardingStorage

class OnboardingViewModel(
    private val onboardingStorage: OnboardingStorage = ServiceLocator.onboardingStorage,
    private val calendarPreferenceStorage: CalendarPreferenceStorage = ServiceLocator.calendarPreferenceStorage
) : ViewModel() {
    suspend fun hasSeenOnboarding(): Boolean =
        onboardingStorage.hasSeenOnboarding()

    suspend fun completeOnboarding() {
        onboardingStorage.setOnboardingCompleted()
        // Seed the default notification rule exactly once on first run
        try {
            ServiceLocator.notificationService.initializeIfNeeded()
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun setPreferTimeline(value: Boolean) {
        calendarPreferenceStorage.setPreferTimeline(value)
    }

    suspend fun getPreferTimeline(): Boolean =
        calendarPreferenceStorage.getPreferTimeline()
}
