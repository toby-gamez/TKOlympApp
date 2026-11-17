package com.tkolymp.shared.language

data class AnnouncementStrings(
    val announcements: String,
    val noAnnouncementsToShow: String,
    val errorLoadingAnnouncements: String,
    val announcement: String,
    val noAnnouncementToShow: String,
    val newBadge: String,
    /** Contains one `%d` for the number of days. */
    val daysAgo: String,
)
