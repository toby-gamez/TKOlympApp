package com.tkolymp.tkolympapp

import com.tkolymp.shared.event.Event

fun translateEventType(type: String?): String? = com.tkolymp.shared.utils.translateEventType(type)

fun formatTimes(since: String?, until: String?): String = com.tkolymp.shared.utils.formatTimes(since, until)

fun formatTimesWithDate(since: String?, until: String?): String = com.tkolymp.shared.utils.formatTimesWithDate(since, until)

fun parseToLocal(s: String?) = com.tkolymp.shared.utils.parseToLocal(s)

fun formatTimesWithDateAlways(since: String?, until: String?): String = com.tkolymp.shared.utils.formatTimesWithDateAlways(since, until)

fun durationMinutes(since: String?, until: String?): String? = com.tkolymp.shared.utils.durationMinutes(since, until)

fun formatHtmlContent(html: String?): String = com.tkolymp.shared.utils.formatHtmlContent(html)

fun participantsForEvent(event: Event?) = com.tkolymp.shared.utils.participantsForEvent(event)
