package com.tkolymp.shared.sync

object OfflineKeys {
    const val PAYMENTS = "offline_payments"
    const val PAYMENTS_PERSON_PREFIX = "offline_payments_person_"
    fun paymentsForPerson(personId: String) = "$PAYMENTS_PERSON_PREFIX$personId"

    const val CAL_PREFIX = "offline_cal_"
    fun calendarWeek(bucket: CalendarBucket, weekStart: String) =
        "${CAL_PREFIX}${bucket.name}_$weekStart"
    const val CAL_CAMPS_ALL = "offline_cal_CAMPS_all"

    const val EVENT_PREFIX = "offline_event_"
    fun eventDetail(id: Long) = "$EVENT_PREFIX$id"

    const val ANN_STICKY = "offline_ann_list_sticky"
    const val ANN_NONSTICKY = "offline_ann_list_nonsticky"
    const val ANN_BODY_PREFIX = "offline_ann_body_"
    fun announcementBody(id: Long) = "$ANN_BODY_PREFIX$id"

    const val PEOPLE = "offline_people"

    const val CLUB = "offline_club"
    const val CLUB_COHORTS = "offline_club_cohorts"
    const val CLUB_BASIC = "offline_club_basic"

    const val SCOREBOARD_PREFIX = "offline_scoreboard_"
    fun scoreboard(since: String, until: String) = "${SCOREBOARD_PREFIX}${since}_$until"

    const val ATTENDANCE_PREFIX = "offline_attendance_"
    fun attendance(personId: String) = "$ATTENDANCE_PREFIX$personId"

    const val COMPETITIONS = "offline_competitions"

    const val META_LAST_SYNC = "offline_meta_last_sync"

    /**
     * Bump this whenever the shape of any offline blob changes in a breaking way.
     * OfflineSyncManager.migrateIfNeeded() wipes all "offline_*" keys and re-saves the new
     * version so stale blobs are never deserialized with the wrong structure.
     */
    const val SCHEMA_VERSION = 1
    const val META_SCHEMA_VERSION = "offline_meta_schema_version"

    const val DISMISSED_CANCELLED = "dismissed_cancelled_replacements"
}
