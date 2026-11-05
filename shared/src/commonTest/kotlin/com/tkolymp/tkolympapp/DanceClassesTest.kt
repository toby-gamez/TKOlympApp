package com.tkolymp.tkolympapp

import com.tkolymp.shared.competitions.CompetitionCategory
import com.tkolymp.shared.competitions.CstsProgress
import com.tkolymp.shared.competitions.deriveDanceClasses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DanceClassesTest {
    private fun progress(discipline: String?, cls: String?) = CstsProgress(
        points = null, finals = null, competitorName = null,
        category = CompetitionCategory(1, null, null, discipline, null, null, cls, null, null)
    )

    @Test fun takesHighestClassPerDiscipline() {
        val result = deriveDanceClasses(
            listOf(progress("Standard", "C"), progress("Standard", "B"), progress("Latin", "D"), progress("Latin", "C"))
        )
        assertEquals("B/C", result?.label)
    }

    @Test fun missingDisciplineIsDash() {
        assertEquals("–/D", deriveDanceClasses(listOf(progress("Latin", "d")))?.label)
        assertEquals("A/–", deriveDanceClasses(listOf(progress("STT", "A")))?.label)
    }

    @Test fun nullWhenNothingUsable() {
        assertNull(deriveDanceClasses(emptyList()))
        assertNull(deriveDanceClasses(listOf(progress("Standard", "X"), progress("Ten dance", "A"), progress(null, "B"))))
    }
}
