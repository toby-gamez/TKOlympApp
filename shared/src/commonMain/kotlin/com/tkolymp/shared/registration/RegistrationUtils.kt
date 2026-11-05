package com.tkolymp.shared.registration

import com.tkolymp.shared.utils.asJsonArrayOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Return a JsonArray containing only registrations that belong to `myPersonId` or any of `myCoupleIds`.
 * A registration element is expected to be a JsonObject possibly containing `person` or `couple` objects
 * with `id` fields (strings).
 */
fun filterOwnedRegistrations(registrations: JsonArray?, myPersonId: String?, myCoupleIds: List<String>): JsonArray {
    if (registrations == null) return buildJsonArray { }
    return buildJsonArray {
        registrations.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            // Skip child registrations (individual dancer entries of a couple registration)
            val parentRegId = obj["parentRegistrationId"]?.jsonPrimitive?.contentOrNull
            if (!parentRegId.isNullOrBlank()) return@forEach
            val personId = obj["person"]?.let { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull } ?: obj["personId"]?.jsonPrimitive?.contentOrNull
            if (!personId.isNullOrBlank() && !myPersonId.isNullOrBlank() && personId == myPersonId) {
                add(obj)
                return@forEach
            }
            val coupleId = obj["couple"]?.let { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull } ?: obj["coupleId"]?.jsonPrimitive?.contentOrNull
            if (!coupleId.isNullOrBlank() && myCoupleIds.contains(coupleId)) {
                add(obj)
                return@forEach
            }
        }
    }
}

/**
 * Returns the set of person IDs that already have a registration in [registrations].
 */
fun computeRegisteredPersonIds(registrations: JsonArray): Set<String> {
    val set = mutableSetOf<String>()
    registrations.forEach { el ->
        val obj = el as? JsonObject ?: return@forEach
        val pid = obj["person"]?.let { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
        if (!pid.isNullOrBlank()) set.add(pid)
    }
    return set
}

/**
 * Returns the set of couple IDs that already have a registration in [registrations].
 */
fun computeRegisteredCoupleIds(registrations: JsonArray): Set<String> {
    val set = mutableSetOf<String>()
    registrations.forEach { el ->
        val obj = el as? JsonObject ?: return@forEach
        val cid = obj["couple"]?.let { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
        if (!cid.isNullOrBlank()) set.add(cid)
    }
    return set
}

/**
 * Picks a sensible initial registrant: prefers self ([myPersonId]) if not already registered,
 * otherwise the first unregistered couple from [myCoupleIds].
 * Returns a pair of (personId?, coupleId?).
 */
fun computeInitialRegistrant(
    myPersonId: String?,
    myCoupleIds: List<String>,
    registeredPersonIds: Set<String>,
    registeredCoupleIds: Set<String>
): Pair<String?, String?> {
    val firstPerson = if (!myPersonId.isNullOrBlank() && !registeredPersonIds.contains(myPersonId)) myPersonId else null
    val firstUnregCouple = myCoupleIds.firstOrNull { !registeredCoupleIds.contains(it) }
    return Pair(firstPerson, if (firstPerson == null) firstUnregCouple else null)
}

/**
 * For a given [regId], look up the lesson demands stored inside that registration and
 * return a list of initial lesson counts aligned with [trainers].
 * Each element in the result corresponds to the trainer at the same index in [trainers].
 */
fun computeInitCountsFor(ownedRegistrations: JsonArray, trainers: JsonArray, regId: String): List<Int> {
    val reg = ownedRegistrations.firstOrNull {
        (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == regId
    } as? JsonObject
    val demands = reg?.get("eventLessonDemandsByRegistrationIdList").asJsonArrayOrNull()
    return trainers.map { t ->
        val tId = (t as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull
        val found = demands?.firstOrNull {
            (it as? JsonObject)?.get("trainerId")?.jsonPrimitive?.intOrNull == tId
        } as? JsonObject
        found?.get("lessonCount")?.jsonPrimitive?.intOrNull ?: 0
    }
}

/** A trainer with a lesson limit of 0 has nothing to pick, so it is hidden from the lesson selection. */
fun isTrainerUnavailable(trainer: JsonElement): Boolean =
    (trainer as? JsonObject)?.get("lessonsRemaining")?.jsonPrimitive?.intOrNull == 0

/** Only camps and reservations let the registrant pick trainers and lesson counts. */
fun supportsLessonSelection(eventType: String?): Boolean =
    eventType?.trim()?.lowercase().let { it == "camp" || it == "rezervation" }

/**
 * Whether the edit-registration screen has anything to change: a note (when [enableNotes]) or,
 * for camps/reservations, lesson counts. Lessons are editable when some trainer still has free
 * lessons, or when the registration already holds lessons with a trainer (they can be lowered).
 * Evaluated per owned registration — true when at least one of them is editable.
 */
fun computeCanEditRegistration(
    eventType: String?,
    enableNotes: Boolean,
    trainers: JsonArray,
    ownedRegistrations: JsonArray
): Boolean {
    if (ownedRegistrations.isEmpty()) return false
    if (enableNotes) return true
    if (!supportsLessonSelection(eventType)) return false
    return ownedRegistrations.any { reg ->
        val regId = (reg as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull ?: return@any false
        val mine = computeInitCountsFor(ownedRegistrations, trainers, regId)
        trainers.withIndex().any { (i, t) -> !isTrainerUnavailable(t) || (mine.getOrNull(i) ?: 0) > 0 }
    }
}

/**
 * Whether the user still has an account (themselves or a couple) that is not registered yet.
 * Mirrors the candidates offered by the Register screen. If the person id has not loaded yet the
 * answer is unknown, so the option stays available.
 */
fun computeCanRegisterAnother(
    myPersonId: String?,
    myCoupleIds: List<String>,
    registrations: JsonArray
): Boolean {
    if (myPersonId.isNullOrBlank()) return true
    val registeredPersonIds = computeRegisteredPersonIds(registrations)
    val registeredCoupleIds = computeRegisteredCoupleIds(registrations)
    return myPersonId !in registeredPersonIds || myCoupleIds.any { it !in registeredCoupleIds }
}
