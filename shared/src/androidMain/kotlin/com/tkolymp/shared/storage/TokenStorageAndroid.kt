package com.tkolymp.shared.storage

import android.content.Context

actual class TokenStorage actual constructor(platformContext: Any) {
    private val context = platformContext as Context
    private val prefs = context.getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

    actual suspend fun saveToken(token: String) {
        prefs.edit().putString("jwt", token).apply()
    }

    actual suspend fun getToken(): String? {
        return prefs.getString("jwt", null)
    }

    actual suspend fun clear() {
        prefs.edit().remove("jwt").apply()
    }
}
