package com.tkolymp.tkolympapp

import com.tkolymp.shared.people.CouplePeriod
import com.tkolymp.shared.people.parseCouplePeriod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CouplePeriodTest {
    private fun parse(json: String) = parseCouplePeriod(Json.parseToJsonElement(json) as JsonObject)

    @Test
    fun `parses names, image and dates and turns blanks into null`() {
        val cp = parse("""{"id":"7","manId":"1","womanId":"2","since":"2024-12-17T00:00:00","until":"","status":"ACTIVE",
            "man":{"firstName":"Adam","lastName":"Badura","image":{"url":"/f/a.jpg"}},"woman":{"firstName":"","lastName":""}}""")
        assertEquals("7", cp.id)
        assertNull(cp.until)
        assertEquals("Adam", cp.man?.firstName)
        assertEquals("https://tkolymp.cz/f/a.jpg", cp.man?.imageUrl)
        assertNull(cp.woman, "a member without any name is dropped")
    }

    @Test
    fun `an ACTIVE status or a missing end date without status counts as active`() {
        assertTrue(CouplePeriod("1", null, null, null, null, "ACTIVE").isActive)
        assertTrue(CouplePeriod("1", null, null, null, null, null).isActive)
        assertFalse(CouplePeriod("1", null, null, null, "2024-01-01", null).isActive)
        assertFalse(CouplePeriod("1", null, null, null, null, "EXPIRED").isActive)
    }
}
