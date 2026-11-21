package com.tkolymp.tkolympapp

import com.tkolymp.shared.language.CompetitionStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class CompetitionStringsTest {
    private val strings = CompetitionStrings()

    @Test
    fun formatType_translatesAgeGroupRegardlessOfCase() {
        assertEquals("Děti II", strings.formatType("juvenile ii"))
        assertEquals("Děti II STT", strings.formatType("Juvenile II Standard"))
        assertEquals("Dospělí", strings.formatType("ADULT"))
    }

    @Test
    fun term_translatesAgeGroupWithNumeral() {
        assertEquals("Děti II", strings.term("JUVENILE_II"))
        assertEquals("Děti II", strings.term("juvenile ii"))
        assertEquals("Dospělí", strings.term("ADULT"))
    }
}
