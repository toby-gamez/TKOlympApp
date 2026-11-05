package com.tkolymp.shared.competitions

/** Highest dance class a person has in the two ČSTS disciplines; null when they have none there. */
data class DanceClasses(val standard: String?, val latin: String?) {
    /** `STT/LAT` pair such as `B/C`; a missing discipline is written as `–`. */
    val label: String get() = "${standard ?: "–"}/${latin ?: "–"}"
}

// Lowest to highest. Classes outside this list are ignored rather than guessed.
private val CLASS_ORDER = listOf("H", "D", "C", "B", "A", "M")

private fun disciplineOf(raw: String?): Char? {
    val d = raw?.trim()?.lowercase() ?: return null
    return when {
        d.startsWith("stt") || d.startsWith("stand") -> 'S'
        d.startsWith("lat") -> 'L'
        else -> null
    }
}

/** Best class per discipline over every category the person has progress in; null if there is none. */
fun deriveDanceClasses(progress: List<CstsProgress>): DanceClasses? {
    fun best(discipline: Char): String? = progress
        .filter { disciplineOf(it.category?.discipline) == discipline }
        .mapNotNull { it.category?.competitorClass?.trim()?.uppercase()?.takeIf { c -> c in CLASS_ORDER } }
        .maxByOrNull { CLASS_ORDER.indexOf(it) }
    val standard = best('S')
    val latin = best('L')
    return if (standard == null && latin == null) null else DanceClasses(standard, latin)
}
