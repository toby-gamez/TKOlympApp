package com.tkolymp.shared.storage

import android.content.Context

actual class UserStorage actual constructor(platformContext: Any) {
    private val ctx = platformContext as Context
    private val prefs by lazy { ctx.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }

    actual suspend fun savePersonId(personId: String) {
        prefs.edit().putString("person_id", personId).apply()
    }

    actual suspend fun getPersonId(): String? = prefs.getString("person_id", null)

    actual suspend fun saveCoupleIds(coupleIds: List<String>) {
        prefs.edit().putStringSet("couple_ids", coupleIds.toSet()).apply()
    }

    actual suspend fun getCoupleIds(): List<String> = prefs.getStringSet("couple_ids", emptySet())?.toList() ?: emptyList()

    actual suspend fun saveCurrentUserJson(json: String) {
        prefs.edit().putString("current_user_json", json).apply()
    }

    actual suspend fun getCurrentUserJson(): String? = prefs.getString("current_user_json", null)

    actual suspend fun savePersonDetailsJson(json: String) {
        prefs.edit().putString("person_details_json", json).apply()
    }

    actual suspend fun getPersonDetailsJson(): String? = prefs.getString("person_details_json", null)

    actual suspend fun clear() {
        prefs.edit().clear().apply()
    }
}
