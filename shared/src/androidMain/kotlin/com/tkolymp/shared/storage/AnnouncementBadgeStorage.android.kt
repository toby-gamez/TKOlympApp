package com.tkolymp.shared.storage

import android.content.Context

actual class AnnouncementBadgeStorage actual constructor(platformContext: Any) {
    private val prefs = (platformContext as Context)
        .getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

    actual fun getLastSeenTimestamp(): String? =
        prefs.getString("announcement_last_seen_ts", null)

    actual fun setLastSeenTimestamp(ts: String) {
        prefs.edit().putString("announcement_last_seen_ts", ts).apply()
    }
}
