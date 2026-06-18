package com.tkolymp.tkolympapp.platform

/** Returns (versionName, versionCode) for the About screen. Returns nulls when unavailable. */
expect fun getAppVersion(): Pair<String?, Long?>
