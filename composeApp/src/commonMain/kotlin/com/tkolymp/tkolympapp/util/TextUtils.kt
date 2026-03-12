package com.tkolymp.tkolympapp.util

/**
 * Strips common Czech/Slovak/German/etc. diacritical marks for accent-insensitive search.
 * Replaces java.text.Normalizer usage in search filters.
 */
private val ACCENT_MAP = mapOf(
    'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'ã' to 'a', 'å' to 'a',
    'Á' to 'A', 'À' to 'A', 'Ä' to 'A', 'Â' to 'A', 'Ã' to 'A',
    'č' to 'c', 'ç' to 'c', 'Č' to 'C', 'Ç' to 'C',
    'ď' to 'd', 'Ď' to 'D',
    'é' to 'e', 'è' to 'e', 'ě' to 'e', 'ê' to 'e', 'ë' to 'e',
    'É' to 'E', 'È' to 'E', 'Ě' to 'E', 'Ê' to 'E', 'Ë' to 'E',
    'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
    'Í' to 'I', 'Ì' to 'I', 'Î' to 'I', 'Ï' to 'I',
    'ľ' to 'l', 'ĺ' to 'l', 'Ľ' to 'L', 'Ĺ' to 'L',
    'ň' to 'n', 'ñ' to 'n', 'Ň' to 'N', 'Ñ' to 'N',
    'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o', 'õ' to 'o',
    'Ó' to 'O', 'Ò' to 'O', 'Ö' to 'O', 'Ô' to 'O',
    'ř' to 'r', 'Ř' to 'R',
    'š' to 's', 'Š' to 'S',
    'ť' to 't', 'Ť' to 'T',
    'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u', 'ů' to 'u',
    'Ú' to 'U', 'Ù' to 'U', 'Ü' to 'U', 'Û' to 'U', 'Ů' to 'U',
    'ý' to 'y', 'ÿ' to 'y', 'Ý' to 'Y',
    'ž' to 'z', 'Ž' to 'Z',
    'ß' to 's'
)

fun stripAccents(s: String): String = buildString(s.length) {
    for (c in s) append(ACCENT_MAP[c] ?: c)
}

fun normalizeForSearch(s: String): String = stripAccents(s).lowercase()
