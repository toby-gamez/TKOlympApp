package com.tkolymp.shared.language

import platform.Foundation.NSLocale

actual fun getDeviceLanguageCode(): String =
    NSLocale.currentLocale.languageCode ?: "cs"
