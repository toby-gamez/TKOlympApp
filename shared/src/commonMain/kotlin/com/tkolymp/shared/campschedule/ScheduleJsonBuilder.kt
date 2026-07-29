package com.tkolymp.shared.campschedule

import com.tkolymp.shared.json.AppJson

// Captures the whole run of group numbers after LAT/STT (e.g. "STT 2 a 3" → "2 a 3"),
// not just the first digit, so a block covering multiple groups keeps every number.
private val blockRegex = Regex("(LAT|STT)\\s*(\\d+(?:\\s*a\\s*\\d+)*)")
private val digitRegex = Regex("\\d+")

/**
 * Builds the schedule JSON for one camp day.
 *
 * [cells] holds only body rows (no header row): each row's first element is the
 * time-column text, followed either by one value per [columns] (a normal lesson
 * row) or by a single value (a merged full-width row, e.g. "Oběd").
 */
fun buildJson(day: String, columns: List<String>, cells: List<List<String?>>): String {
    // Column headers become entries' map keys, so they must be unique — OCR very
    // commonly returns blank or duplicate header text, which would otherwise silently
    // collapse multiple columns' data into one key (last write wins).
    val uniqueColumns = dedupeColumns(columns)
    val schedule = cells.map { row -> rowToEntry(row, uniqueColumns) }
    return AppJson.encodeToString(ScheduleDay.serializer(), ScheduleDay(day = day, columns = uniqueColumns, schedule = schedule))
}

private fun dedupeColumns(columns: List<String>): List<String> {
    val seenCount = mutableMapOf<String, Int>()
    return columns.mapIndexed { index, raw ->
        val base = raw.trim().ifEmpty { "Sloupec ${index + 1}" }
        val count = seenCount.getOrDefault(base, 0)
        seenCount[base] = count + 1
        if (count == 0) base else "$base ($count)"
    }
}

fun parseScheduleDay(json: String): ScheduleDay =
    AppJson.decodeFromString(ScheduleDay.serializer(), json)

private fun rowToEntry(row: List<String?>, columns: List<String>): ScheduleEntry {
    val time = row.getOrNull(0)?.trim().orEmpty()
    val bodyCells = row.drop(1)
    return if (bodyCells.size != columns.size) {
        ScheduleEntry.Note(time = time, text = bodyCells.getOrNull(0))
    } else {
        val block = bodyCells.firstNotNullOfOrNull { text ->
            text?.let { blockRegex.find(it) }?.let { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }
        }
        val entries = columns.indices.associate { i -> columns[i] to bodyCells[i] }
        ScheduleEntry.Lesson(time = time, block = block, entries = entries)
    }
}

/** Every group number mentioned in a block string, e.g. "STT 2 a 3" -> {2, 3}. */
fun blockGroupNumbers(block: String): Set<Int> =
    digitRegex.findAll(block).map { it.value.toInt() }.toSet()

/** Every distinct group number mentioned across the day's lesson blocks, sorted. */
fun availableGroupNumbers(day: ScheduleDay): List<Int> =
    day.schedule.filterIsInstance<ScheduleEntry.Lesson>()
        .mapNotNull { it.block }
        .flatMap { blockGroupNumbers(it) }
        .distinct()
        .sorted()

data class NameOccurrence(val time: String, val column: String, val block: String?)

/**
 * Searches every [ScheduleEntry.Lesson] block in [day] for [name] appearing as an
 * entry value (case-insensitive, trimmed), returning every (time, column) hit.
 */
fun findNameOccurrences(day: ScheduleDay, name: String): List<NameOccurrence> {
    val target = name.trim()
    return day.schedule.filterIsInstance<ScheduleEntry.Lesson>().flatMap { lesson ->
        lesson.entries
            .filter { (_, value) -> value != null && value.trim().equals(target, ignoreCase = true) }
            .map { (column, _) -> NameOccurrence(time = lesson.time, column = column, block = lesson.block) }
    }
}
