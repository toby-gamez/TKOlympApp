package com.tkolymp.shared.storage

import android.content.Context

actual class LanguageStorage actual constructor(platformContext: Any) {
    private val prefs = (platformContext as Context)
        .getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

    actual suspend fun getLanguageCode(): String? =
        prefs.getString("language_code", null)

    actual suspend fun saveLanguageCode(code: String) {
        prefs.edit().putString("language_code", code).apply()
    }
}
