package com.tkolymp.tkolympapp

import com.tkolymp.shared.update.UpdateChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test fun parsesTagWithV() = assertEquals(listOf(2, 2, 1), UpdateChecker.parseVersion("v2.2.1"))
    @Test fun parsesPlainVersion() = assertEquals(listOf(2, 2), UpdateChecker.parseVersion("2.2"))
    @Test fun rejectsGarbage() = assertNull(UpdateChecker.parseVersion("latest"))
    @Test fun newerIsGreater() = assertTrue(UpdateChecker.compareVersions(listOf(2, 2, 1), listOf(2, 2)) > 0)
    @Test fun missingPartsEqualZero() = assertEquals(0, UpdateChecker.compareVersions(listOf(2, 2), listOf(2, 2, 0)))
}
