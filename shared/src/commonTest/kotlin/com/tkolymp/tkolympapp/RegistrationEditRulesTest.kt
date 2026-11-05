package com.tkolymp.tkolympapp

import com.tkolymp.shared.registration.computeCanEditRegistration
import com.tkolymp.shared.registration.computeCanRegisterAnother
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun trainer(id: Int, remaining: Int?): JsonObject = buildJsonObject {
    put("id", id)
    if (remaining != null) put("lessonsRemaining", remaining)
}

private fun personReg(regId: String, personId: String, lessons: Map<Int, Int> = emptyMap()): JsonObject = buildJsonObject {
    put("id", regId)
    put("person", buildJsonObject { put("id", personId) })
    put("eventLessonDemandsByRegistrationIdList", buildJsonArray {
        lessons.forEach { (t, c) -> add(buildJsonObject { put("trainerId", t); put("lessonCount", c) }) }
    })
}

private fun coupleReg(regId: String, coupleId: String): JsonObject = buildJsonObject {
    put("id", regId)
    put("couple", buildJsonObject { put("id", coupleId) })
}

class RegistrationEditRulesTest {
    private val allFull = JsonArray(listOf(trainer(1, 0), trainer(2, 0)))

    @Test
    fun `edit hidden when no lessons held and every trainer is at limit`() {
        assertFalse(computeCanEditRegistration("camp", false, allFull, JsonArray(listOf(personReg("r1", "p1")))))
    }

    @Test
    fun `edit stays when notes are enabled`() {
        assertTrue(computeCanEditRegistration("camp", true, allFull, JsonArray(listOf(personReg("r1", "p1")))))
    }

    @Test
    fun `edit stays when registration holds lessons with a full trainer`() {
        assertTrue(computeCanEditRegistration("camp", false, allFull, JsonArray(listOf(personReg("r1", "p1", mapOf(1 to 2))))))
    }

    @Test
    fun `edit stays when a trainer is unlimited or has free lessons`() {
        val trainers = JsonArray(listOf(trainer(1, 0), trainer(2, null)))
        assertTrue(computeCanEditRegistration("rezervation", false, trainers, JsonArray(listOf(personReg("r1", "p1")))))
    }

    @Test
    fun `edit stays if any owned registration is editable`() {
        val owned = JsonArray(listOf(personReg("r1", "p1"), personReg("r2", "p2", mapOf(2 to 1))))
        assertTrue(computeCanEditRegistration("camp", false, allFull, owned))
    }

    @Test
    fun `edit hidden for event types without lesson selection or notes`() {
        val free = JsonArray(listOf(trainer(1, 5)))
        assertFalse(computeCanEditRegistration("other", false, free, JsonArray(listOf(personReg("r1", "p1")))))
    }

    @Test
    fun `register another false when self and all couples registered`() {
        val regs = JsonArray(listOf(personReg("r1", "p1"), coupleReg("r2", "c1")))
        assertFalse(computeCanRegisterAnother("p1", listOf("c1"), regs))
    }

    @Test
    fun `register another true when a couple is left`() {
        assertTrue(computeCanRegisterAnother("p1", listOf("c1", "c2"), JsonArray(listOf(personReg("r1", "p1"), coupleReg("r2", "c1")))))
    }

    @Test
    fun `register another stays available while person id is unknown`() {
        assertTrue(computeCanRegisterAnother(null, emptyList(), JsonArray(emptyList())))
    }
}
