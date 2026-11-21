package com.tkolymp.tkolympapp

import com.tkolymp.shared.event.parseTrainerNamesFromJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainerNamesParserTest {
    private fun parse(s: String) = parseTrainerNamesFromJson(Json.parseToJsonElement(s).jsonArray)

    @Test fun readsPersonName() =
        assertEquals(listOf("Jan Novák"), parse("""[{"id":1,"person":{"id":2,"name":"Jan Novák"}}]"""))

    @Test fun acceptsFlatNameAndStrings() =
        assertEquals(listOf("A", "B"), parse("""[{"name":"A"},"B"]"""))

    @Test fun dropsBlankAndMissing() =
        assertEquals(emptyList(), parse("""[{"id":1},{"person":{"name":" "}}]"""))

    @Test fun nullArrayIsEmpty() = assertEquals(emptyList(), parseTrainerNamesFromJson(null))
}
