package com.tkolymp.shared.tutorial

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TutorialStep(val route: String, val key: String)

object TutorialManager {
    val steps = listOf(
        // Overview sections
        TutorialStep("overview", "overviewUpcoming"),
        TutorialStep("overview", "overviewBoard"),
        TutorialStep("overview", "overviewCamps"),
        TutorialStep("overview", "overviewBirthdays"),
        TutorialStep("overview", "overviewStats"),
        // Calendar sections
        TutorialStep("calendar", "calendarMine"),
        TutorialStep("calendar", "calendarAll"),
        TutorialStep("calendar", "calendarFilter"),
        // Board sections
        TutorialStep("board", "boardList"),
        TutorialStep("board", "boardSearch"),
        // Events sections
        TutorialStep("events", "eventsPlanned"),
        TutorialStep("events", "eventsPast"),
        // Other (closing step)
        TutorialStep("other", "other"),
    )

    val stepCount = steps.size

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    fun start() { _currentStep.value = 0; _isActive.value = true }
    fun next() { val n = _currentStep.value + 1; if (n >= stepCount) skip() else _currentStep.value = n }
    fun previous() { val p = _currentStep.value - 1; if (p >= 0) _currentStep.value = p }
    fun skip() { _isActive.value = false; _currentStep.value = 0 }
}
