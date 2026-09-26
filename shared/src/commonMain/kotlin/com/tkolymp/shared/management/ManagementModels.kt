package com.tkolymp.shared.management

import kotlinx.serialization.Serializable

/**
 * What the logged-in user may manage. Trainers manage their own events and announcements,
 * administrators manage everything in the club. The server enforces the same rules through
 * row-level security, so this only decides which actions the UI offers.
 */
@Serializable
data class UserPermissions(
    val userId: String? = null,
    val personIds: Set<String> = emptySet(),
    val isTrainer: Boolean = false,
    val isAdmin: Boolean = false,
) {
    val canManage: Boolean get() = isTrainer || isAdmin

    /** An event is "own" when one of the user's persons trains or manages it. */
    fun canEditEvent(trainerPersonIds: Collection<String>, managerPersonIds: Collection<String>): Boolean {
        if (isAdmin) return true
        if (!isTrainer) return false
        return trainerPersonIds.any { it in personIds } || managerPersonIds.any { it in personIds }
    }

    fun canEditAnnouncement(authorId: String?): Boolean {
        if (isAdmin) return true
        if (!isTrainer) return false
        return authorId != null && authorId == userId
    }

    companion object {
        val NONE = UserPermissions()
    }
}

/** Mirrors the backend `EventType` enum. */
enum class ManagedEventType { LESSON, GROUP, CAMP, RESERVATION, HOLIDAY }

/** Mirrors the backend `AnnouncementStatus` enum (SCHEDULED is not offered in the app). */
enum class ManagedAnnouncementStatus { PUBLISHED, DRAFT, ARCHIVED }

@Serializable
data class ManagementOption(val id: String, val name: String, val colorRgb: String? = null)

@Serializable
data class ManagementOptions(
    val trainers: List<ManagementOption> = emptyList(),
    val cohorts: List<ManagementOption> = emptyList(),
    val locations: List<ManagementOption> = emptyList(),
)

/**
 * Editable fields of a single event instance. [sinceIso]/[untilIso] are ISO-8601 instants.
 * [trainerPersonIds] and [cohortIds] can only be set when creating: the backend exposes no
 * safe per-field mutation for them on existing events.
 */
data class EventDraft(
    val name: String = "",
    val type: ManagedEventType = ManagedEventType.LESSON,
    val sinceIso: String = "",
    val untilIso: String = "",
    val locationId: String? = null,
    val locationText: String = "",
    val capacity: Int? = null,
    val isVisible: Boolean = true,
    val isPublic: Boolean = false,
    val isLocked: Boolean = false,
    val enableNotes: Boolean = false,
    val isCancelled: Boolean = false,
    val summary: String = "",
    val description: String = "",
    val trainerPersonIds: List<String> = emptyList(),
    val cohortIds: List<String> = emptyList(),
)

data class AnnouncementDraft(
    val title: String = "",
    val body: String = "",
    val isSticky: Boolean = false,
    val status: ManagedAnnouncementStatus = ManagedAnnouncementStatus.PUBLISHED,
    val cohortIds: List<String> = emptyList(),
)
