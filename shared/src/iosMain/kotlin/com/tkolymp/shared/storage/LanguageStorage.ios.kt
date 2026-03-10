package com.tkolymp.shared.storage

import platform.Foundation.NSUserDefaults

actual class LanguageStorage actual constructor(platformContext: Any) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun getLanguageCode(): String? =
        defaults.stringForKey("language_code")

    actual suspend fun saveLanguageCode(code: String) {
        defaults.setObject(code, "language_code")
        defaults.synchronize()
    }
}
