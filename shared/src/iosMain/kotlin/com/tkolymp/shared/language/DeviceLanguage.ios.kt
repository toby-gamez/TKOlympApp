package com.tkolymp.shared.language

import platform.Foundation.NSUserDefaults

actual fun getDeviceLanguageCode(): String {
    @Suppress("UNCHECKED_CAST")
    val langs = NSUserDefaults.standardUserDefaults
        .objectForKey("AppleLanguages") as? List<String> ?: return "cs"
    val first = langs.firstOrNull() ?: return "cs"
    return first.split("-").firstOrNull()?.split("_")?.firstOrNull() ?: "cs"
}
