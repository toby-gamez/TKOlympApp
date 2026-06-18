package com.tkolymp.shared.announcements

import com.tkolymp.shared.viewmodels.DataResult

interface IAnnouncementService {
    suspend fun getAnnouncements(sticky: Boolean): DataResult<List<Announcement>>
    suspend fun getAnnouncementById(id: Long, forceRefresh: Boolean = false): DataResult<Announcement>
}
