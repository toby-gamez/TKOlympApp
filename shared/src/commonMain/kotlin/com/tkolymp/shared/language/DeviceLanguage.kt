package com.tkolymp.shared.language

/**
 * Returns the BCP-47 language code of the device's current locale (e.g. "cs", "de", "en").
 */
expect fun getDeviceLanguageCode(): String
