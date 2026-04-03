package com.tkolymp.shared.registration

import com.tkolymp.shared.utils.asJsonArrayOrNull
import kotlinx.serialization.json.JsonArray
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
