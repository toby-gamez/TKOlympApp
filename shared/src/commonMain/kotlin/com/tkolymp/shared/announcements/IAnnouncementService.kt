package com.tkolymp.shared.announcements

interface IAnnouncementService {
    suspend fun getAnnouncements(sticky: Boolean): List<Announcement>
    suspend fun getAnnouncementById(id: Long): Announcement?
}
