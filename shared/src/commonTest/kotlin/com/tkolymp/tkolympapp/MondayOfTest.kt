package com.tkolymp.tkolympapp

import com.tkolymp.shared.viewmodels.mondayOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MondayOfTest {
    @Test
    fun mondayStaysMonday() = assertEquals(LocalDate(2026, 11, 16), mondayOf(LocalDate(2026, 11, 16)))

    @Test
    fun midWeekMapsBackToMonday() = assertEquals(LocalDate(2026, 11, 16), mondayOf(LocalDate(2026, 11, 18)))

    @Test
    fun sundayBelongsToTheWeekThatStartedSixDaysEarlier() = assertEquals(LocalDate(2026, 11, 16), mondayOf(LocalDate(2026, 11, 22)))

    @Test
    fun crossesMonthAndYearBoundaries() {
        assertEquals(LocalDate(2026, 12, 28), mondayOf(LocalDate(2027, 1, 1)))
        assertEquals(LocalDate(2026, 11, 30), mondayOf(LocalDate(2026, 12, 2)))
    }
}
