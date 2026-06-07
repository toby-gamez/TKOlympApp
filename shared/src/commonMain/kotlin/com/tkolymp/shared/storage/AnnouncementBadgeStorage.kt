package com.tkolymp.shared.storage

expect class AnnouncementBadgeStorage(platformContext: Any) {
    fun getLastSeenTimestamp(): String?
    fun setLastSeenTimestamp(ts: String)
}
