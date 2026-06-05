package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
import com.tkolymp.shared.storage.ICalendarPreferenceStorage
import com.tkolymp.shared.storage.OnboardingStorage
import com.tkolymp.shared.models.UserRole

class OnboardingViewModel(
    private val onboardingStorage: OnboardingStorage = ServiceLocator.onboardingStorage,
    private val calendarPreferenceStorage: ICalendarPreferenceStorage = ServiceLocator.calendarPreferenceStorage
) : ViewModel() {
    suspend fun hasSeenOnboarding(): Boolean =
        onboardingStorage.hasSeenOnboarding()

    suspend fun completeOnboarding() {
        onboardingStorage.setOnboardingCompleted()
        // Seed the default notification rule exactly once on first run
        try {
            val role = onboardingStorage.getUserRole()
            ServiceLocator.notificationService.initializeIfNeeded(role)
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun setPreferTimeline(value: Boolean) {
        calendarPreferenceStorage.setPreferTimeline(value)
    }

    suspend fun getPreferTimeline(): Boolean =
        calendarPreferenceStorage.getPreferTimeline()

    suspend fun setUserRole(role: UserRole) {
        onboardingStorage.setUserRole(role)
    }

    suspend fun getUserRole(): UserRole? =
        onboardingStorage.getUserRole()
}
