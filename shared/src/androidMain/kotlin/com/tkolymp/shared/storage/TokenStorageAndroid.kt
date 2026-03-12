package com.tkolymp.shared.storage

import android.content.Context
import eu.anifantakis.lib.ksafe.KSafe

actual class TokenStorage actual constructor(platformContext: Any) {
    private val context = platformContext as Context
    private val ksafe = KSafe(context, fileName = "tokenstore")

    actual suspend fun saveToken(token: String) {
        ksafe.put("jwt", token)
    }

    actual suspend fun getToken(): String? {
        val value = ksafe.get("jwt", "")
        return value.takeIf { it.isNotEmpty() }
    }

    actual suspend fun clear() {
        ksafe.delete("jwt")
    }
}
