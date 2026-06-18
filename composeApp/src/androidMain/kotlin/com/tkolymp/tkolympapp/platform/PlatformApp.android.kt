package com.tkolymp.tkolympapp.platform

import androidx.core.content.pm.PackageInfoCompat

// Populated by MainActivity before first composition.
internal var appVersionName: String? = null
internal var appVersionCode: Long? = null

actual fun getAppVersion(): Pair<String?, Long?> = Pair(appVersionName, appVersionCode)
