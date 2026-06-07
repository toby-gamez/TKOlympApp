package com.tkolymp.shared.event

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Safely returns the first trainer name trimmed, or empty string when missing.
 */
fun Event?.firstTrainerOrEmpty(): String = this?.eventTrainersList?.firstOrNull()?.trim() ?: ""

/** Parses a single registration JsonObject into a [Registration]. Returns null for malformed input. */
fun parseRegistrationFromJson(o: JsonObject): Registration? {
    val ridPrim = o["id"]?.jsonPrimitive
    val rid = ridPrim?.longOrNull ?: ridPrim?.contentOrNull?.toLongOrNull()

    val personObj = o["person"] as? JsonObject
    val person = personObj?.let { p ->
        val pidPrim = p["id"]?.jsonPrimitive
        val pid = pidPrim?.longOrNull ?: pidPrim?.contentOrNull?.toLongOrNull()
        Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
    }

    val coupleObj = o["couple"] as? JsonObject
    val couple = coupleObj?.let { c ->
        val cidPrim = c["id"]?.jsonPrimitive
        val cid = cidPrim?.longOrNull ?: cidPrim?.contentOrNull?.toLongOrNull()
        val man = (c["man"] as? JsonObject)?.let { m -> SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
        val woman = (c["woman"] as? JsonObject)?.let { w -> SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
        Couple(cid, man, woman)
    }

    return Registration(rid, person, couple)
}

/** Parses all registration objects in a [JsonArray] using [parseRegistrationFromJson]. */
fun parseRegistrationsFromJson(arr: JsonArray): List<Registration> =
    arr.mapNotNull { (it as? JsonObject)?.let(::parseRegistrationFromJson) }
