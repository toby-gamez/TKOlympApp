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
import com.tkolymp.shared.utils.DateRangeConstants
import kotlinx.coroutines.async

enum class CalendarBucket { MINE, ALL, CAMPS }

class OfflineSyncManager(
    private val eventService: IEventService,
    private val announcementService: IAnnouncementService,
    private val peopleService: PeopleService,
    private val offlineDataStorage: OfflineDataStorage,
    private val networkMonitor: NetworkMonitor,
    private val userService: com.tkolymp.shared.user.UserService,
    private val notificationService: com.tkolymp.shared.notification.NotificationService,
    private val clubService: com.tkolymp.shared.club.ClubService,
    private val paymentService: com.tkolymp.shared.payments.PaymentService
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
            // Sync payments
            try { syncPayments() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "syncPayments failed: ${ex.message}") }
            syncCalendarBuckets()
            syncAnnouncements()
            syncPeople()
            // Ensure basic club data is saved for offline UIs that read `offline_club`.
            try { syncClub() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "syncClub failed: ${ex.message}") }
            saveLastSyncTime(kotlin.time.Clock.System.now().toString())
            Logger.d("OfflineSyncManager", "syncAll: completed successfully")
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "syncAll: failed: ${ex.message}")
            // swallow; best-effort sync
        }
    }

    private suspend fun syncPayments() {
        try {
            val list = try { paymentService.fetchPaymentDebtors() } catch (_: Exception) { emptyList() }
            if (list.isNotEmpty()) {
                // Build lightweight JSON array
                val arr = buildJsonArray {
                    list.forEach { itItem ->
                        val jo = buildJsonObject {
                            put("id", JsonPrimitive(itItem.id ?: ""))
                            put("isUnpaid", JsonPrimitive(itItem.isUnpaid?.toString() ?: ""))
                            val price = itItem.price
                            put("price", buildJsonObject { put("amount", JsonPrimitive(price?.amount?.toString() ?: "")); put("currency", JsonPrimitive(price?.currency ?: "")) })
                            put("paymentId", JsonPrimitive(itItem.paymentId ?: ""))
                            put("personId", JsonPrimitive(itItem.personId ?: ""))
                            val p = itItem.payment
                            put("payment", buildJsonObject { put("id", JsonPrimitive(p?.id ?: "")); put("variableSymbol", JsonPrimitive(p?.variableSymbol ?: "")); put("specificSymbol", JsonPrimitive(p?.specificSymbol ?: "")); put("dueAt", JsonPrimitive(p?.dueAt ?: "")); put("status", JsonPrimitive(p?.status ?: "")) })
                            val person = itItem.person
                            put("person", buildJsonObject { put("id", JsonPrimitive(person?.id ?: "")); put("firstName", JsonPrimitive(person?.firstName ?: "")); put("lastName", JsonPrimitive(person?.lastName ?: "")) })
                        }
                        add(jo)
                    }
                }

                try { offlineDataStorage.save("offline_payments", json.encodeToString(JsonArray.serializer(), arr)) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_payments failed: ${ex.message}") }

                // Save per-person lists for quick lookup
                val grouped = list.groupBy { it.personId ?: "" }
                grouped.forEach { (pid, items) ->
                    if (pid.isNotBlank()) {
                        val perArr = buildJsonArray {
                            items.forEach { itItem -> add(Json.parseToJsonElement(itItem.raw?.toString() ?: JsonObject(emptyMap()).toString())) }
                        }
                        try { offlineDataStorage.save("offline_payments_person_$pid", json.encodeToString(JsonArray.serializer(), perArr)) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_payments_person_${pid} failed: ${ex.message}") }
                    }
                }
            }
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "syncPayments exception: ${ex.message}")
        }
    }

    private suspend fun syncClub() {
        try {
            val cd = try { clubService.fetchClubData() } catch (_: Exception) { null }
            if (cd != null) {
                try {
                    val rawEl = cd.raw
                    when (rawEl) {
                        is kotlinx.serialization.json.JsonObject -> {
                            val dataObj = rawEl["data"] as? kotlinx.serialization.json.JsonObject ?: rawEl
                            // Extract cohortsList and save separately
                            val cohortsEl = (dataObj["getCurrentTenant"] as? kotlinx.serialization.json.JsonObject)?.get("cohortsList")
                            if (cohortsEl != null) {
                                try { offlineDataStorage.save("offline_club_cohorts", cohortsEl.toString()) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club_cohorts failed: ${ex.message}") }
                            }
                            // Save basic data (locations + trainers)
                            try {
                                val basics = buildJsonObject {
                                    put("tenantLocationsList", dataObj["tenantLocationsList"] ?: kotlinx.serialization.json.JsonArray(emptyList()))
                                    put("tenantTrainersList", dataObj["tenantTrainersList"] ?: kotlinx.serialization.json.JsonArray(emptyList()))
                                }
                                offlineDataStorage.save("offline_club_basic", basics.toString())
                            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club_basic failed: ${ex.message}") }
                        }
                        null -> { /* nothing to save */ }
                        else -> {
                            // Fallback: save the whole payload as basic
                            try { offlineDataStorage.save("offline_club_basic", rawEl.toString()) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club_basic failed: ${ex.message}") }
                        }
                    }
                } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club failed: ${ex.message}") }
            }
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "syncClub exception: ${ex.message}")
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
                    ann.id.toLongOrNull()?.let { id ->
                        offlineDataStorage.save("offline_ann_body_${id}", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject { put("id", JsonPrimitive(ann.id)); put("title", JsonPrimitive(ann.title ?: "")); put("body", JsonPrimitive(ann.body ?: "")); put("updatedAt", JsonPrimitive(ann.updatedAt ?: "")) }))
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private suspend fun syncPeople() {
        try {
            val people = peopleService.fetchPeople()
            // Save people with additional details (try fetching full person details to get isTrainer/birthDate)
            val peopleJson = buildJsonArray {
                // Fetch details in concurrent batches to avoid sequential N+1 latency
                val chunks = people.chunked(20)
                val detailsMap = mutableMapOf<String, com.tkolymp.shared.people.PersonDetails?>()
                for (chunk in chunks) {
                    val deferred = chunk.map { p -> kotlinx.coroutines.GlobalScope.async {
                        try { withRetry { peopleService.fetchPerson(p.id) } } catch (_: Exception) { null }
                    } }
                    val results = deferred.map { d -> try { d.await() } catch (_: Exception) { null } }
                    // map results back to ids (some may be null)
                    chunk.forEachIndexed { idx, p -> detailsMap[p.id] = results.getOrNull(idx) }
                }

                people.forEach { p ->
                    val details = detailsMap[p.id]
                    val birth = details?.birthDate ?: p.birthDate
                    val prefix = details?.prefixTitle ?: p.prefixTitle
                    val suffix = details?.suffixTitle ?: p.suffixTitle
                    val isTrainer = details?.isTrainer ?: false

                    add(buildJsonObject {
                        put("id", JsonPrimitive(p.id))
                        put("firstName", JsonPrimitive(p.firstName ?: ""))
                        put("lastName", JsonPrimitive(p.lastName ?: ""))
                        put("prefixTitle", JsonPrimitive(prefix ?: ""))
                        put("suffixTitle", JsonPrimitive(suffix ?: ""))
                        put("birthDate", JsonPrimitive(birth ?: ""))
                        put("isTrainer", JsonPrimitive(isTrainer.toString()))
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

    suspend fun loadClub(): String? = try { offlineDataStorage.load("offline_club") } catch (_: Exception) { null }

    suspend fun loadClubCohorts(): String? = try { offlineDataStorage.load("offline_club_cohorts") } catch (_: Exception) { null }

    suspend fun loadClubBasics(): String? = try { offlineDataStorage.load("offline_club_basic") } catch (_: Exception) { null }

    suspend fun getLastSyncTime(): String? = offlineDataStorage.load(metaLastSyncKey)

    // Debug / inspection helpers for payments
    suspend fun loadPayments(): String? = try { offlineDataStorage.load("offline_payments") } catch (_: Exception) { null }

    suspend fun loadPaymentsForPerson(personId: String): String? = try { offlineDataStorage.load("offline_payments_person_$personId") } catch (_: Exception) { null }

    suspend fun listOfflineKeys(): Set<String> = try { offlineDataStorage.allKeys() } catch (_: Exception) { emptySet() }

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
                try { kotlinx.coroutines.delay(delayMs) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
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

        // User data
        onProgress("user", 0, 1)
        try {
            val pid = try { withRetry { userService.fetchAndStorePersonId() } } catch (_: Exception) { null }
            try { withRetry { userService.fetchAndStoreActiveCouples() } } catch (_: Exception) {}
            if (!pid.isNullOrBlank()) {
                try { withRetry { userService.fetchAndStorePersonDetails(pid) } } catch (_: Exception) {}
            }
            // Ensure notification defaults exist
            try { notificationService.initializeIfNeeded() } catch (_: Exception) {}
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll user data failed: ${ex.message}") }

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
                    // Fetch details in concurrent batches to reduce round-trips
                    val chunks = ppl.chunked(20)
                    val detailsMap = mutableMapOf<String, com.tkolymp.shared.people.PersonDetails?>()
                    for (chunk in chunks) {
                        val deferred = chunk.map { p -> kotlinx.coroutines.GlobalScope.async {
                            try { withRetry { peopleService.fetchPerson(p.id) } } catch (_: Exception) { null }
                        } }
                        val results = deferred.map { d -> try { d.await() } catch (_: Exception) { null } }
                        chunk.forEachIndexed { idx, p -> detailsMap[p.id] = results.getOrNull(idx) }
                    }

                    ppl.forEach { p ->
                        val details = detailsMap[p.id]
                        val birth = details?.birthDate ?: p.birthDate
                        val prefix = details?.prefixTitle ?: p.prefixTitle
                        val suffix = details?.suffixTitle ?: p.suffixTitle
                        val isTrainer = details?.isTrainer ?: false

                        add(buildJsonObject {
                            put("id", JsonPrimitive(p.id))
                            put("firstName", JsonPrimitive(p.firstName ?: ""))
                            put("lastName", JsonPrimitive(p.lastName ?: ""))
                            put("prefixTitle", JsonPrimitive(prefix ?: ""))
                            put("suffixTitle", JsonPrimitive(suffix ?: ""))
                            put("birthDate", JsonPrimitive(birth ?: ""))
                            put("isTrainer", JsonPrimitive(isTrainer.toString()))
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
                val encoded = json.encodeToString(JsonArray.serializer(), peopleJson)
                try {
                    offlineDataStorage.save("offline_people", encoded)
                    Logger.d("OfflineSyncManager", "downloadAll: saved offline_people count=${ppl.size}")
                } catch (ex: Exception) {
                    Logger.d("OfflineSyncManager", "downloadAll: save offline_people failed: ${ex.message}")
                }
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll people failed: ${ex.message}") }

            // Payments
            onProgress("payments", 0, 1)
            try {
                try { syncPayments() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll payments failed: ${ex.message}") }
            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll payments outer failed: ${ex.message}") }

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
                val encodedBoard = json.encodeToString(JsonArray.serializer(), boardJson)
                try {
                    offlineDataStorage.save("offline_scoreboard_${since}_$until", encodedBoard)
                    Logger.d("OfflineSyncManager", "downloadAll: saved offline_scoreboard entries=${board.size}")
                } catch (ex: Exception) {
                    Logger.d("OfflineSyncManager", "downloadAll: save offline_scoreboard failed: ${ex.message}")
                }
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll scoreboard failed: ${ex.message}") }

        // Club data
        onProgress("club", 0, 1)
        try {
            val cd = try { withRetry { clubService.fetchClubData() } } catch (ex: Exception) { null }
            if (cd != null) {
                try {
                    val rawEl = cd.raw
                    when (rawEl) {
                        is kotlinx.serialization.json.JsonObject -> {
                            val dataObj = rawEl["data"] as? kotlinx.serialization.json.JsonObject ?: rawEl
                            val cohortsEl = (dataObj["getCurrentTenant"] as? kotlinx.serialization.json.JsonObject)?.get("cohortsList")
                            if (cohortsEl != null) {
                                try { offlineDataStorage.save("offline_club_cohorts", cohortsEl.toString()) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll: save offline_club_cohorts failed: ${ex.message}") }
                            }
                            try {
                                val basics = buildJsonObject {
                                    put("tenantLocationsList", dataObj["tenantLocationsList"] ?: kotlinx.serialization.json.JsonArray(emptyList()))
                                    put("tenantTrainersList", dataObj["tenantTrainersList"] ?: kotlinx.serialization.json.JsonArray(emptyList()))
                                }
                                offlineDataStorage.save("offline_club_basic", basics.toString())
                                Logger.d("OfflineSyncManager", "downloadAll: saved offline_club_basic")
                            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll: save offline_club_basic failed: ${ex.message}") }
                        }
                        null -> { /* nothing */ }
                        else -> {
                            try { offlineDataStorage.save("offline_club_basic", rawEl.toString()); Logger.d("OfflineSyncManager", "downloadAll: saved offline_club_basic (fallback)") } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll: save offline_club_basic failed: ${ex.message}") }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll club failed: ${ex.message}") }

        // Event details
        val evList = collectedEventIds.toList()
        val totalEv = evList.size
        var evDone = 0
        // Ensure we also download ALL CAMP events separately (beyond weekly buckets)
        try {
            val allCamps = try {
                withRetry {
                    eventService.fetchEventsGroupedByDay(
                        DateRangeConstants.FAR_PAST,
                        DateRangeConstants.FAR_FUTURE,
                        onlyMine = false,
                        first = 1000,
                        offset = 0,
                        onlyType = "CAMP",
                        cacheNamespace = null
                    )
                }
            } catch (ex: Exception) {
                emptyMap<String, List<EventInstance>>()
            }

            if (allCamps.isNotEmpty()) {
                val joAll = buildJsonObject {
                    allCamps.forEach { (d, list) ->
                        put(d, buildJsonArray {
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
                    offlineDataStorage.save("offline_cal_CAMPS_all", json.encodeToString(JsonObject.serializer(), joAll))
                } catch (ex: Exception) {
                    Logger.d("OfflineSyncManager", "save all camps failed: ${ex.message}")
                }

                collectedEventIds += allCamps.values.flatten().mapNotNull { it.event?.id }
            }
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "fetch all camps failed: ${ex.message}")
        }
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
