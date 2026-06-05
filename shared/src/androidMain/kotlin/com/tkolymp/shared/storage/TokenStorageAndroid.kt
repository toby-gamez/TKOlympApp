package com.tkolymp.shared.storage

import android.content.Context
import eu.anifantakis.lib.ksafe.KSafe

actual class TokenStorage actual constructor(platformContext: Any) : ITokenStorage {
    private val context = platformContext as Context
    private val ksafe = KSafe(context, fileName = "tokenstore")

    actual override suspend fun saveToken(token: String) {
        ksafe.put("jwt", token)
    }

    actual override suspend fun getToken(): String? {
        val value = ksafe.get("jwt", "")
        return value.takeIf { it.isNotEmpty() }
    }

    actual override suspend fun clear() {
        ksafe.delete("jwt")
    }
}
