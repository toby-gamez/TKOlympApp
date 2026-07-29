package com.tkolymp.shared.campschedule

private val diacriticsMap = mapOf(
    'á' to 'a', 'č' to 'c', 'ď' to 'd', 'é' to 'e', 'ě' to 'e', 'í' to 'i', 'ĺ' to 'l', 'ľ' to 'l',
    'ň' to 'n', 'ó' to 'o', 'ô' to 'o', 'ř' to 'r', 'ŕ' to 'r', 'š' to 's', 'ť' to 't', 'ú' to 'u',
    'ů' to 'u', 'ý' to 'y', 'ž' to 'z', 'ä' to 'a', 'ö' to 'o', 'ü' to 'u',
    'Á' to 'A', 'Č' to 'C', 'Ď' to 'D', 'É' to 'E', 'Ě' to 'E', 'Í' to 'I', 'Ĺ' to 'L', 'Ľ' to 'L',
    'Ň' to 'N', 'Ó' to 'O', 'Ô' to 'O', 'Ř' to 'R', 'Ŕ' to 'R', 'Š' to 'S', 'Ť' to 'T', 'Ú' to 'U',
    'Ů' to 'U', 'Ý' to 'Y', 'Ž' to 'Z', 'Ä' to 'A', 'Ö' to 'O', 'Ü' to 'U'
)

/**
 * Strips Czech/Slovak/German diacritics (e.g. "Křížan" -> "Krizan") so a name typed
 * without them — a missing accent, an awkward keyboard, a simple typo — still matches.
 * Only ever used as a fallback after an exact match fails, never in place of it, so a
 * correctly-typed name is never weakened by a coincidental diacritic-folded collision.
 */
fun foldDiacritics(s: String): String = s.map { diacriticsMap[it] ?: it }.joinToString("")

/** Case-insensitive equality, falling back to a diacritic-folded comparison (see [foldDiacritics]). */
fun namesMatch(a: String, b: String): Boolean =
    a.equals(b, ignoreCase = true) || foldDiacritics(a).equals(foldDiacritics(b), ignoreCase = true)
