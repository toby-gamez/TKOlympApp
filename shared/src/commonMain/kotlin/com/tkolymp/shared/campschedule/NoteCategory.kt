package com.tkolymp.shared.campschedule

/** What kind of full-width note row this is, purely for picking a display icon. */
enum class NoteCategory { MEAL, SLEEP, OTHER }

private val mealKeywords = listOf("snídaně", "oběd", "večeře")
private val sleepKeywords = listOf("večerka")

/** Categorizes a note's text by keyword (e.g. "Oběd" -> MEAL, "Večerka..." -> SLEEP). */
fun categorizeNote(text: String?): NoteCategory {
    val normalized = text?.lowercase() ?: return NoteCategory.OTHER
    return when {
        mealKeywords.any { normalized.contains(it) } -> NoteCategory.MEAL
        sleepKeywords.any { normalized.contains(it) } -> NoteCategory.SLEEP
        else -> NoteCategory.OTHER
    }
}
