package com.tkolymp.shared.sync

import com.tkolymp.shared.Logger
import com.tkolymp.shared.announcements.IAnnouncementService
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.network.NetworkMonitor
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

enum class CalendarBucket { MINE, ALL, CAMPS }

class OfflineSyncManager(
    private val eventService: IEventService,
    private val announcementService: IAnnouncementService,
    private val peopleService: PeopleService,
    private val offlineDataStorage: OfflineDataStorage,
    private val networkMonitor: NetworkMonitor
) {
    private val metaLastSyncKey = "offline_meta_last_sync"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        Logger.d("OfflineSyncManager", "syncAll: starting")
        if (!networkMonitor.isConnected()) {
            Logger.d("OfflineSyncManager", "syncAll: network not available, skipping")
            return@withContext
        }

        try {
            syncCalendarBuckets()
            syncAnnouncements()
            syncPeople()
            saveLastSyncTime(kotlin.time.Clock.System.now().toString())
            Logger.d("OfflineSyncManager", "syncAll: completed successfully")
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "syncAll: failed: ${ex.message}")
            // swallow; best-effort sync
        }
    }

    private suspend fun syncCalendarBuckets() {
        val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val buckets = listOf(CalendarBucket.MINE, CalendarBucket.ALL, CalendarBucket.CAMPS)

        for (bucket in buckets) {
            Logger.d("OfflineSyncManager", "syncCalendarBuckets: bucket=${bucket.name}")
            for (weekOffset in -1..2) {
                Logger.d("OfflineSyncManager", "syncCalendarBuckets: bucket=${bucket.name} weekOffset=${weekOffset}")
                val weekStart = today.plus(weekOffset * 7, DateTimeUnit.DAY)
                val endDay = weekStart.plus(6, DateTimeUnit.DAY)
                val startIso = "${weekStart}" + "T00:00:00Z"
                val endIso = "${endDay}" + "T23:59:59Z"

                val onlyMine = bucket == CalendarBucket.MINE
                val onlyType = if (bucket == CalendarBucket.CAMPS) "CAMP" else null

                // Fetch summary events for the range
                val grouped = try {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = onlyMine, first = 500, offset = 0, onlyType = onlyType, cacheNamespace = null)
                } catch (e: Exception) {
                    emptyMap<String, List<EventInstance>>()
                }

                // Build lightweight JSON summary
                val weekKey = "offline_cal_${bucket.name}_${weekStart}"
                val jo = buildJsonObject {
                    grouped.forEach { (date, list) ->
                        put(date, buildJsonArray {
                            list.forEach { inst ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(inst.id))
                                    put("isCancelled", JsonPrimitive(inst.isCancelled))
                                    put("since", JsonPrimitive(inst.since ?: ""))
                                    put("until", JsonPrimitive(inst.until ?: ""))
                                    put("updatedAt", JsonPrimitive(inst.updatedAt ?: ""))
                                    val ev = inst.event
                                    if (ev != null) {
                                        put("eventId", JsonPrimitive(ev.id ?: -1))
                                        put("eventName", JsonPrimitive(ev.name ?: ""))
                                        put("eventType", JsonPrimitive(ev.type ?: ""))
                                        put("locationText", JsonPrimitive(ev.locationText ?: ""))
                                        put("trainers", buildJsonArray { ev.eventTrainersList.forEach { add(JsonPrimitive(it)) } })
                                    }
                                })
                            }
                        })
                    }
                }

                try {
                    if (grouped.isNotEmpty()) {
                        offlineDataStorage.save(weekKey, json.encodeToString(JsonObject.serializer(), jo))
                        Logger.d("OfflineSyncManager", "saved weekKey=${weekKey}")
                    } else {
                        Logger.d("OfflineSyncManager", "skipping save for empty week=${weekKey}")
                    }
                } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save weekKey failed: ${ex.message}") }

                // Persist full event details for each event id in this week's data
                val allEventIds = grouped.values.flatten().mapNotNull { it.event?.id }.distinct()
                for (evId in allEventIds) {
                    try {
                        val full = eventService.fetchEventById(evId, forceRefresh = false)
                        if (full != null) {
                            offlineDataStorage.save("offline_event_${evId}", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), full))
                            Logger.d("OfflineSyncManager", "saved event detail offline_event_${evId}")
                        }
                    } catch (ex: Exception) {
                        Logger.d("OfflineSyncManager", "fetch/save event ${evId} failed: ${ex.message}")
                        // ignore individual event failures
                    }
                }
            }
        }
    }

    private suspend fun syncAnnouncements() {
        try {
            val sticky = announcementService.getAnnouncements(true)
            val non = announcementService.getAnnouncements(false)
            try {
                offlineDataStorage.save("offline_ann_list_sticky", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), sticky))
            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save sticky announcements failed: ${ex.message}") }
            try {
                offlineDataStorage.save("offline_ann_list_nonsticky", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), non))
            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save nonsticky announcements failed: ${ex.message}") }
            // Save bodies for top 10 non-sticky
            non.take(10).forEach { ann ->
                try {
                    ann.id?.toLongOrNull()?.let { id ->
                        offlineDataStorage.save("offline_ann_body_${id}", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject { put("id", JsonPrimitive(ann.id)); put("title", JsonPrimitive(ann.title ?: "")); put("body", JsonPrimitive(ann.body ?: "")); put("updatedAt", JsonPrimitive(ann.updatedAt ?: "")) }))
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private suspend fun syncPeople() {
        try {
            val people = peopleService.fetchPeople()
            // For simplicity save raw serialized list of minimal person info
            val peopleJson = buildJsonArray {
                people.forEach { p ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(p.id?.toString() ?: ""))
                        put("firstName", JsonPrimitive(p.firstName ?: ""))
                        put("lastName", JsonPrimitive(p.lastName ?: ""))
                        put("cohortMembershipsList", buildJsonArray {
                            p.cohortMembershipsList.forEach { cm ->
                                add(buildJsonObject {
                                    val c = cm.cohort
                                    put("cohort", buildJsonObject {
                                        put("id", JsonPrimitive(c?.id ?: ""))
                                        put("name", JsonPrimitive(c?.name ?: ""))
                                        put("colorRgb", JsonPrimitive(c?.colorRgb ?: ""))
                                        put("isVisible", JsonPrimitive(c?.isVisible?.toString() ?: "false"))
                                    })
                                    put("since", JsonPrimitive(cm.since ?: ""))
                                    put("until", JsonPrimitive(cm.until ?: ""))
                                })
                            }
                        })
                    })
                }
            }
            if (people.isNotEmpty()) {
                offlineDataStorage.save("offline_people", json.encodeToString(JsonArray.serializer(), peopleJson))
            } else Logger.d("OfflineSyncManager", "skipping save offline_people: empty")
        } catch (_: Exception) {}
    }

    suspend fun loadCalendarWeek(key: String): String? {
        return offlineDataStorage.load(key)
    }

    suspend fun loadEventDetail(id: Long): String? {
        return offlineDataStorage.load("offline_event_$id")
    }

    suspend fun loadAnnouncements(sticky: Boolean): String? {
        val k = if (sticky) "offline_ann_list_sticky" else "offline_ann_list_nonsticky"
        return offlineDataStorage.load(k)
    }

    suspend fun loadAnnouncementDetail(id: Long): String? {
        return offlineDataStorage.load("offline_ann_body_$id")
    }

    suspend fun loadPeople(): String? = offlineDataStorage.load("offline_people")

    suspend fun getLastSyncTime(): String? = offlineDataStorage.load(metaLastSyncKey)

    private suspend fun saveLastSyncTime(iso: String) = offlineDataStorage.save(metaLastSyncKey, iso)

    private suspend fun <T> withRetry(attempts: Int = 3, initialDelayMs: Long = 500, block: suspend () -> T): T {
        var lastEx: Exception? = null
        var delayMs = initialDelayMs
        repeat(attempts) { i ->
            try {
                return block()
            } catch (ex: Exception) {
                lastEx = ex
                Logger.d("OfflineSyncManager", "attempt ${i + 1} failed: ${ex.message}")
                try { kotlinx.coroutines.delay(delayMs) } catch (_: Exception) {}
                delayMs *= 2
            }
        }
        throw lastEx ?: Exception("unknown")
    }

    suspend fun downloadAll(onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }) = withContext(Dispatchers.IO) {
        if (!networkMonitor.isConnected()) throw Exception("Network unavailable")
        Logger.d("OfflineSyncManager", "downloadAll: starting")

        val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val buckets = listOf(CalendarBucket.MINE, CalendarBucket.ALL, CalendarBucket.CAMPS)
        val weekOffsets = (-4..4).toList()
        val totalCalendar = buckets.size * weekOffsets.size
        var calDone = 0
        val collectedEventIds = mutableSetOf<Long>()

        for (bucket in buckets) {
            for (weekOffset in weekOffsets) {
                onProgress("calendar", ++calDone, totalCalendar)
                val weekStart = today.plus(weekOffset * 7, DateTimeUnit.DAY)
                val endDay = weekStart.plus(6, DateTimeUnit.DAY)
                val startIso = "${weekStart}T00:00:00Z"
                val endIso = "${endDay}T23:59:59Z"
                val onlyMine = bucket == CalendarBucket.MINE
                val onlyType = if (bucket == CalendarBucket.CAMPS) "CAMP" else null
                val grouped = try { withRetry { eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = onlyMine, first = 500, offset = 0, onlyType = onlyType, cacheNamespace = null) } } catch (ex: Exception) { emptyMap<String, List<EventInstance>>() }
                if (grouped.isNotEmpty()) {
                    val jo = buildJsonObject {
                        grouped.forEach { (date, list) ->
                            put(date, buildJsonArray {
                                list.forEach { inst ->
                                    add(buildJsonObject {
                                        put("id", JsonPrimitive(inst.id))
                                        put("isCancelled", JsonPrimitive(inst.isCancelled))
                                        put("since", JsonPrimitive(inst.since ?: ""))
                                        put("until", JsonPrimitive(inst.until ?: ""))
                                        put("updatedAt", JsonPrimitive(inst.updatedAt ?: ""))
                                        val ev = inst.event
                                        if (ev != null) {
                                            put("eventId", JsonPrimitive(ev.id ?: -1))
                                            put("eventName", JsonPrimitive(ev.name ?: ""))
                                            put("eventType", JsonPrimitive(ev.type ?: ""))
                                            put("locationText", JsonPrimitive(ev.locationText ?: ""))
                                            put("trainers", buildJsonArray { ev.eventTrainersList.forEach { add(JsonPrimitive(it)) } })
                                        }
                                    })
                                }
                            })
                        }
                    }
                    try { offlineDataStorage.save("offline_cal_${bucket.name}_${weekStart}", json.encodeToString(JsonObject.serializer(), jo)) } catch (_: Exception) {}
                    collectedEventIds += grouped.values.flatten().mapNotNull { it.event?.id }
                }
            }
        }

        // Announcements
        onProgress("announcements", 0, 1)
        try {
            val sticky = withRetry { announcementService.getAnnouncements(true) }
            val non = withRetry { announcementService.getAnnouncements(false) }
            if (sticky.isNotEmpty()) offlineDataStorage.save("offline_ann_list_sticky", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), sticky))
            if (non.isNotEmpty()) offlineDataStorage.save("offline_ann_list_nonsticky", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), non))
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll announcements failed: ${ex.message}") }

        // People
        onProgress("people", 0, 1)
        try {
            val ppl = withRetry { peopleService.fetchPeople() }
            if (ppl.isNotEmpty()) {
                val peopleJson = buildJsonArray {
                    ppl.forEach { p ->
                        add(buildJsonObject {
                            put("id", JsonPrimitive(p.id?.toString() ?: ""))
                            put("firstName", JsonPrimitive(p.firstName ?: ""))
                            put("lastName", JsonPrimitive(p.lastName ?: ""))
                            put("cohortMembershipsList", buildJsonArray {
                                p.cohortMembershipsList.forEach { cm ->
                                    add(buildJsonObject {
                                        val c = cm.cohort
                                        put("cohort", buildJsonObject {
                                            put("id", JsonPrimitive(c?.id ?: ""))
                                            put("name", JsonPrimitive(c?.name ?: ""))
                                            put("colorRgb", JsonPrimitive(c?.colorRgb ?: ""))
                                            put("isVisible", JsonPrimitive(c?.isVisible?.toString() ?: "false"))
                                        })
                                        put("since", JsonPrimitive(cm.since ?: ""))
                                        put("until", JsonPrimitive(cm.until ?: ""))
                                    })
                                }
                            })
                        })
                    }
                }
                offlineDataStorage.save("offline_people", json.encodeToString(JsonArray.serializer(), peopleJson))
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll people failed: ${ex.message}") }

        // Scoreboard (global)
        onProgress("scoreboard", 0, 1)
        try {
            val since = today.plus(-365, DateTimeUnit.DAY).toString()
            val until = today.plus(365, DateTimeUnit.DAY).toString()
            val board = withRetry { peopleService.fetchScoreboard(null, since, until) }
            if (board.isNotEmpty()) {
                val boardJson = buildJsonArray {
                    board.forEach { b ->
                        add(buildJsonObject {
                            put("ranking", JsonPrimitive(b.ranking ?: -1))
                            put("personId", JsonPrimitive(b.personId ?: ""))
                            put("personFirstName", JsonPrimitive(b.personFirstName ?: ""))
                            put("personLastName", JsonPrimitive(b.personLastName ?: ""))
                            put("totalScore", JsonPrimitive(b.totalScore ?: 0.0))
                            put("lessonTotalScore", JsonPrimitive(b.lessonTotalScore ?: 0.0))
                            put("groupTotalScore", JsonPrimitive(b.groupTotalScore ?: 0.0))
                            put("eventTotalScore", JsonPrimitive(b.eventTotalScore ?: 0.0))
                            put("manualTotalScore", JsonPrimitive(b.manualTotalScore ?: 0.0))
                        })
                    }
                }
                offlineDataStorage.save("offline_scoreboard_${since}_$until", json.encodeToString(JsonArray.serializer(), boardJson))
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll scoreboard failed: ${ex.message}") }

        // Event details
        val evList = collectedEventIds.toList()
        val totalEv = evList.size
        var evDone = 0
        for (evId in evList) {
            onProgress("events", ++evDone, totalEv)
            try {
                val full = withRetry { eventService.fetchEventById(evId, forceRefresh = false) }
                if (full != null) offlineDataStorage.save("offline_event_${evId}", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), full))
            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll event ${evId} failed: ${ex.message}") }
        }

        saveLastSyncTime(kotlin.time.Clock.System.now().toString())
        Logger.d("OfflineSyncManager", "downloadAll: completed")
    }
}
