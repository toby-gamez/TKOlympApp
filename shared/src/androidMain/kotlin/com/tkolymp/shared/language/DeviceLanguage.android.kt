package com.tkolymp.shared.language

import java.util.Locale

actual fun getDeviceLanguageCode(): String = Locale.getDefault().language
