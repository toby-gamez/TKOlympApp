package com.tkolymp.shared.storage

import platform.Foundation.NSUserDefaults

actual class AnnouncementBadgeStorage actual constructor(platformContext: Any) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getLastSeenTimestamp(): String? =
        defaults.stringForKey("announcement_last_seen_ts")

    actual fun setLastSeenTimestamp(ts: String) {
        defaults.setObject(ts, "announcement_last_seen_ts")
        defaults.synchronize()
    }
}
