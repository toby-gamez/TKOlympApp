package com.tkolymp.shared.registration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
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
