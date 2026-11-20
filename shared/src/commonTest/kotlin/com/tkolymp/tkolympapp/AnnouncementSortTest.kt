package com.tkolymp.tkolympapp

import com.tkolymp.shared.announcements.Announcement
import com.tkolymp.shared.announcements.sortedNewestFirst
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementSortTest {
    @Test
    fun `sorts by createdAt descending even when an old notice was updated recently`() {
        val old = Announcement(id = "1", createdAt = "2011-03-01T10:00:00Z", updatedAt = "2026-11-01T00:00:00Z")
        val mid = Announcement(id = "2", createdAt = "2020-05-01T10:00:00Z", updatedAt = "2020-05-01T10:00:00Z")
        val new = Announcement(id = "3", createdAt = "2026-10-01T10:00:00Z", updatedAt = "2026-10-01T10:00:00Z")
        assertEquals(listOf("3", "2", "1"), listOf(old, mid, new).sortedNewestFirst().map { it.id })
    }

    @Test
    fun `updatedAt breaks createdAt ties and missing dates sort last`() {
        val a = Announcement(id = "a", createdAt = "2024-01-01T00:00:00Z", updatedAt = "2024-01-02T00:00:00Z")
        val b = Announcement(id = "b", createdAt = "2024-01-01T00:00:00Z", updatedAt = "2024-02-01T00:00:00Z")
        val none = Announcement(id = "n")
        assertEquals(listOf("b", "a", "n"), listOf(none, a, b).sortedNewestFirst().map { it.id })
    }
}
