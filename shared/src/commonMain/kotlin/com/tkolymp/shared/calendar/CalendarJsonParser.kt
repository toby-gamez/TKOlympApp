package com.tkolymp.shared.calendar

import com.tkolymp.shared.event.Cohort
import com.tkolymp.shared.event.Couple
import com.tkolymp.shared.event.Event
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.Location
import com.tkolymp.shared.event.Person
import com.tkolymp.shared.event.Registration
import com.tkolymp.shared.event.SimpleName
import com.tkolymp.shared.event.TargetCohort
import com.tkolymp.shared.json.AppJson
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses a calendar week JSON blob (as written by OfflineSyncManager) into
 * a date-keyed map of EventInstance lists.
 *
 * Returns an empty map on any parse failure so callers degrade gracefully.
 */
fun parseCalendarJson(raw: String): Map<String, List<EventInstance>> = try {
    val root = AppJson.parseToJsonElement(raw).jsonObject
    val result = mutableMapOf<String, MutableList<EventInstance>>()
    root.entries.forEach { (date, elem) ->
        val list = mutableListOf<EventInstance>()
        elem.jsonArray.forEach { item ->
            val obj = item.jsonObject
            val id = obj["id"]?.jsonPrimitive?.longOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val isCancelled = obj["isCancelled"]?.jsonPrimitive?.booleanOrNull ?: false
            val since     = obj["since"]?.jsonPrimitive?.contentOrNull
            val until     = obj["until"]?.jsonPrimitive?.contentOrNull
            val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
            val eventId   = obj["eventId"]?.jsonPrimitive?.longOrNull
            val eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull
            val eventType = obj["eventType"]?.jsonPrimitive?.contentOrNull
            val locationText = obj["locationText"]?.jsonPrimitive?.contentOrNull
            val trainers = obj["trainers"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

            val targetCohorts = obj["targetCohorts"]?.jsonArray?.mapNotNull { it2 ->
                val o = it2.jsonObject
                val cohortId = o["cohortId"]?.jsonPrimitive?.longOrNull
                    ?: o["cohortId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                val cohort = o["cohort"]?.jsonObject?.let { c ->
                    Cohort(
                        c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                        c["name"]?.jsonPrimitive?.contentOrNull,
                        c["colorRgb"]?.jsonPrimitive?.contentOrNull
                    )
                }
                TargetCohort(cohortId, cohort)
            } ?: emptyList()

            val registrations = obj["eventRegistrationsList"]?.jsonArray?.mapNotNull { regEl ->
                val o = regEl.jsonObject
                val rid = o["id"]?.jsonPrimitive?.longOrNull
                    ?: o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                val person = o["person"]?.jsonObject?.let { p ->
                    Person(
                        p["id"]?.jsonPrimitive?.longOrNull ?: p["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                        p["name"]?.jsonPrimitive?.contentOrNull,
                        p["firstName"]?.jsonPrimitive?.contentOrNull,
                        p["lastName"]?.jsonPrimitive?.contentOrNull
                    )
                }
                val couple = o["couple"]?.jsonObject?.let { c ->
                    Couple(
                        c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                        c["man"]?.jsonObject?.let { m -> SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) },
                        c["woman"]?.jsonObject?.let { w -> SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                    )
                }
                Registration(rid, person, couple)
            } ?: emptyList()

            val location = obj["location"]?.jsonObject?.let { l ->
                Location(
                    l["id"]?.jsonPrimitive?.longOrNull ?: l["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    l["name"]?.jsonPrimitive?.contentOrNull
                )
            }

            val isRegistrationOpen = obj["isRegistrationOpen"]?.jsonPrimitive?.booleanOrNull ?: false
            val event = Event(eventId, eventName, null, eventType, locationText,
                isRegistrationOpen, false, false, trainers, targetCohorts, registrations, location)
            list += EventInstance(id, isCancelled, since, until, updatedAt, event)
        }
        result[date] = list
    }
    result
} catch (_: Exception) { emptyMap() }
