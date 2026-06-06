package com.tkolymp.shared.storage

import android.content.Context
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

actual class UserStorage actual constructor(platformContext: Any) : IUserStorage {
    private val context = platformContext as Context
    private val ksafe = KSafe(context, fileName = "userstore")

    actual override suspend fun savePersonId(personId: String) {
        ksafe.put("person_id", personId)
    }

    actual override suspend fun getPersonId(): String? {
        val value = ksafe.get("person_id", "")
        return value.takeIf { it.isNotEmpty() }
    }

    actual override suspend fun saveCstsId(cstsId: String) {
        ksafe.put("csts_id", cstsId)
    }

    actual override suspend fun getCstsId(): String? {
        val value = ksafe.get("csts_id", "")
        return value.takeIf { it.isNotEmpty() }
    }

    actual override suspend fun saveCoupleIds(coupleIds: List<String>) {
        ksafe.put("couple_ids", Json.encodeToString(ListSerializer(String.serializer()), coupleIds))
    }

    actual override suspend fun getCoupleIds(): List<String> {
        val value = ksafe.get("couple_ids", "")
        if (value.isEmpty()) return emptyList()
        return try {
            Json.decodeFromString(ListSerializer(String.serializer()), value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    actual override suspend fun saveCurrentUserJson(json: String) {
        ksafe.put("current_user_json", json)
    }

    actual override suspend fun getCurrentUserJson(): String? {
        val value = ksafe.get("current_user_json", "")
        return value.takeIf { it.isNotEmpty() }
    }

    actual override suspend fun savePersonDetailsJson(json: String) {
        ksafe.put("person_details_json", json)
    }

    actual override suspend fun getPersonDetailsJson(): String? {
        val value = ksafe.get("person_details_json", "")
        return value.takeIf { it.isNotEmpty() }
    }

    actual override suspend fun clear() {
        ksafe.delete("person_id")
        ksafe.delete("csts_id")
        ksafe.delete("couple_ids")
        ksafe.delete("current_user_json")
        ksafe.delete("person_details_json")
    }
}
