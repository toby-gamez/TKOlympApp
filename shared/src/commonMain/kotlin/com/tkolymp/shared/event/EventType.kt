package com.tkolymp.shared.event

enum class EventType(val rawValue: String) {
    LESSON("lesson"),
    GROUP("group"),
    CAMP("CAMP"),
    PERSONAL("PERSONAL"),
    OTHER("");

    companion object {
        fun fromString(raw: String?): EventType =
            raw?.let { s -> entries.firstOrNull { it.rawValue.equals(s, ignoreCase = true) } }
                ?: OTHER
    }
}

fun String?.toEventType(): EventType = EventType.fromString(this)
