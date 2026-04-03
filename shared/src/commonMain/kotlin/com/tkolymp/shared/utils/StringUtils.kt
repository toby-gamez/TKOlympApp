package com.tkolymp.shared.utils

import kotlin.math.roundToInt

fun stripTitles(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty() || cleaned.startsWith("(")) return cleaned

    val titleTokens = setOf(
        "Bc", "Bc.", "Mgr", "Mgr.", "Ing", "Ing.", "PhDr", "PhDr.", "RNDr", "RNDr.",
        "JUDr", "JUDr.", "PhD", "PhD.", "Dr", "Dr.", "Prof", "Prof.", "doc", "doc.",
        "MBA", "MVDr", "MVDr.", "MD", "MD."
    )

    val parts = cleaned.split(Regex("\\s+"))
    var start = 0
    var end = parts.size

    while (start < end && titleTokens.contains(parts[start].trimEnd(','))) start++
    while (end - 1 >= start && titleTokens.contains(parts[end - 1].trimEnd(','))) end--

    val core = parts.subList(start, end).joinToString(" ")
    return if (core.isBlank()) cleaned else core
}

fun Double.roundTo1dp(): Double = (this * 10.0).roundToInt() / 10.0
