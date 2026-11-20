package com.tkolymp.shared.sync

import com.tkolymp.shared.graphql.GraphQlOperations
import com.tkolymp.shared.Logger
import com.tkolymp.shared.announcements.IAnnouncementService
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.competitions.ICompetitionService
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.network.NetworkMonitor
import com.tkolymp.shared.people.CoupleDetail
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.storage.OfflineDataStorage
import com.tkolymp.shared.utils.DateRangeConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import com.tkolymp.shared.json.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import com.tkolymp.shared.sync.OfflineKeys
import com.tkolymp.shared.utils.AppConstants
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.viewmodels.DataResult

enum class CalendarBucket { MINE, ALL, CAMPS }

private const val EVENT_FETCH_CONCURRENCY = 12
private const val EVENT_SAVE_BATCH = 150

/** Steps of [OfflineSyncManager.downloadAll] in execution order; the ids are what `onProgress` reports as the stage. */
val OFFLINE_DOWNLOAD_STAGES = listOf("calendar", "user", "announcements", "people", "couples", "payments", "scoreboard", "club", "events", "competitions", "achievements")

class OfflineSyncManager(
    private val eventService: IEventService,
    private val announcementService: IAnnouncementService,
    private val peopleService: PeopleService,
    private val offlineDataStorage: OfflineDataStorage,
    private val networkMonitor: NetworkMonitor,
    private val userService: com.tkolymp.shared.user.UserService,
    private val notificationService: com.tkolymp.shared.notification.NotificationService,
    private val clubService: com.tkolymp.shared.club.ClubService,
    private val paymentService: com.tkolymp.shared.payments.PaymentService,
    private val competitionService: ICompetitionService,
    private val competitiveDataService: com.tkolymp.shared.competitivedata.ICompetitiveDataService? = null,
    // Lazy: the service reads ServiceLocator, which is only initialised after this manager is constructed.
    private val achievementServiceProvider: (() -> com.tkolymp.shared.achievements.AchievementService)? = null
) {
    

    suspend fun syncAll() = withContext(Dispatchers.Default) {
        Logger.d("OfflineSyncManager", "syncAll: starting")
        migrateIfNeeded()
        if (!networkMonitor.isConnected()) {
            Logger.d("OfflineSyncManager", "syncAll: network not available, skipping")
            return@withContext
        }

        // Debounce rapid successive syncs: skip if last sync was within 5 minutes
        try {
            val last = try { getLastSyncTime() } catch (_: Exception) { null }
            if (!last.isNullOrBlank()) {
                try {
                    val parsed = Instant.parse(last)
                    val now = Clock.System.now()
                    val elapsedSec = now.epochSeconds - parsed.epochSeconds
                    if (elapsedSec < AppConstants.SYNC_DEBOUNCE_SECONDS) {
                        Logger.d("OfflineSyncManager", "syncAll: skipped (last sync ${elapsedSec}s ago)")
                        return@withContext
                    }
                } catch (_: Exception) { /* ignore parse errors, continue with sync */ }
            }
        } catch (_: Exception) {}

        try {
            // Sync payments
            try { syncPayments() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "syncPayments failed: ${ex.message}") }
            syncCalendarBuckets()
            syncAnnouncements()
            syncPeople()
            try { syncCompetitions() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "syncCompetitions failed: ${ex.message}") }
            try { syncPastCompetitions() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "syncPastCompetitions failed: ${ex.message}") }
            try { syncFederationEvents() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "syncFederationEvents failed: ${ex.message}") }
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

                try { offlineDataStorage.save(OfflineKeys.PAYMENTS, AppJson.encodeToString(JsonArray.serializer(), arr)) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_payments failed: ${ex.message}") }

                // Save per-person lists for quick lookup
                val grouped = list.groupBy { it.personId ?: "" }
                val perPerson = HashMap<String, String>()
                grouped.forEach { (pid, items) ->
                    if (pid.isNotBlank()) {
                        val perArr = buildJsonArray {
                            items.forEach { itItem -> add(AppJson.parseToJsonElement(itItem.raw?.toString() ?: JsonObject(emptyMap()).toString())) }
                        }
                        perPerson[OfflineKeys.paymentsForPerson(pid)] = AppJson.encodeToString(JsonArray.serializer(), perArr)
                    }
                }
                try { offlineDataStorage.saveAll(perPerson) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_payments_person_* failed: ${ex.message}") }
            }
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "syncPayments exception: ${ex.message}")
        }
    }

    private suspend fun syncClub() {
        try {
            val cd = try { clubService.fetchClubData() } catch (_: Exception) { null }
            if (cd != null) persistClubData(cd.raw)
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "syncClub exception: ${ex.message}")
        }
    }

    /** Splits a club GraphQL payload into the cohorts and locations/trainers offline keys. Shared by [syncClub] and [downloadAll]. */
    private suspend fun persistClubData(rawEl: kotlinx.serialization.json.JsonElement?) {
        try {
            when (rawEl) {
                is kotlinx.serialization.json.JsonObject -> {
                    val dataObj = rawEl["data"] as? kotlinx.serialization.json.JsonObject ?: rawEl
                    val tenantObj = dataObj["tenant"] as? kotlinx.serialization.json.JsonObject
                    // Extract cohortsList and save separately
                    val cohortsEl = tenantObj?.get("cohortsList")
                    if (cohortsEl != null) {
                        try { offlineDataStorage.save(OfflineKeys.CLUB_COHORTS, cohortsEl.toString()) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club_cohorts failed: ${ex.message}") }
                    }
                    // Save basic data (locations + trainers)
                    try {
                        val basics = buildJsonObject {
                            put("tenantLocationsList", tenantObj?.get("tenantLocationsList") ?: kotlinx.serialization.json.JsonArray(emptyList()))
                            put("tenantTrainersList", tenantObj?.get("tenantTrainersList") ?: kotlinx.serialization.json.JsonArray(emptyList()))
                        }
                        offlineDataStorage.save(OfflineKeys.CLUB_BASIC, basics.toString())
                    } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club_basic failed: ${ex.message}") }
                }
                null -> { /* nothing to save */ }
                else -> {
                    // Fallback: save the whole payload as basic
                    try { offlineDataStorage.save(OfflineKeys.CLUB_BASIC, rawEl.toString()) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club_basic failed: ${ex.message}") }
                }
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save offline_club failed: ${ex.message}") }
    }

    private suspend fun syncCalendarBuckets() {
        val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val buckets = listOf(CalendarBucket.MINE, CalendarBucket.ALL, CalendarBucket.CAMPS)

        for (bucket in buckets) {
            Logger.d("OfflineSyncManager", "syncCalendarBuckets: bucket=${bucket.name}")
            for (weekOffset in -1..2) {
                Logger.d("OfflineSyncManager", "syncCalendarBuckets: bucket=${bucket.name} weekOffset=${weekOffset}")
                val rawWeekStart = today.plus(weekOffset * 7, DateTimeUnit.DAY)
                val weekStart = rawWeekStart.minus(rawWeekStart.dayOfWeek.ordinal, DateTimeUnit.DAY)
                val endDay = weekStart.plus(6, DateTimeUnit.DAY)
                val startIso = "${weekStart}" + "T00:00:00Z"
                val endIso = "${endDay}" + "T23:59:59Z"

                val onlyMine = bucket == CalendarBucket.MINE
                val onlyType = if (bucket == CalendarBucket.CAMPS) EventType.CAMP.rawValue else null

                // Fetch summary events for the range
                val grouped = try {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = onlyMine, first = AppConstants.FETCH_LIMIT_PERIOD, offset = 0, onlyType = onlyType, cacheNamespace = null)
                } catch (e: Exception) {
                    emptyMap<String, List<EventInstance>>()
                }

                // Build lightweight JSON summary
                val weekKey = OfflineKeys.calendarWeek(bucket, weekStart.toString())
                val jo = buildCalendarWeekJson(grouped)

                try {
                    if (grouped.isNotEmpty()) {
                        offlineDataStorage.save(weekKey, AppJson.encodeToString(JsonObject.serializer(), jo))
                        Logger.d("OfflineSyncManager", "saved weekKey=${weekKey}")
                    } else {
                        Logger.d("OfflineSyncManager", "skipping save for empty week=${weekKey}")
                    }
                } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save weekKey failed: ${ex.message}") }

                // Persist full event details for each event id in this week's data, fetched concurrently
                // in small batches (sequential one-by-one fetches were taking minutes for busy weeks).
                val allEventIds = grouped.values.flatten().mapNotNull { it.event?.id }.distinct()
                for (chunk in allEventIds.chunked(20)) {
                    val fetched = coroutineScope {
                        chunk.map { evId ->
                            async {
                                try {
                                    val full = eventService.fetchEventById(evId, forceRefresh = false)
                                    if (full != null) OfflineKeys.eventDetail(evId) to AppJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), full) else null
                                } catch (ex: Exception) {
                                    Logger.d("OfflineSyncManager", "fetch event ${evId} failed: ${ex.message}")
                                    // ignore individual event failures
                                    null
                                }
                            }
                        }.awaitAll()
                    }
                    try { offlineDataStorage.saveAll(fetched.filterNotNull().toMap()) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save event details failed: ${ex.message}") }
                }
            }
        }
    }

    /** Lightweight per-instance summary used in calendar week caches. Shared by [syncCalendarBuckets] and [downloadAll]. */
    private fun buildEventInstanceJson(inst: EventInstance): JsonObject = buildJsonObject {
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
            put("isRegistrationOpen", JsonPrimitive(ev.isRegistrationOpen))
        }
    }

    /** `{"<date>": [eventInstanceJson, ...]}` shape used for all calendar week/camps offline caches. */
    private fun buildCalendarWeekJson(grouped: Map<String, List<EventInstance>>): JsonObject = buildJsonObject {
        grouped.forEach { (date, list) ->
            put(date, buildJsonArray { list.forEach { add(buildEventInstanceJson(it)) } })
        }
    }

    /**
     * Fetches per-person details (trainer flag, birth date, titles) in concurrent batches and builds the
     * offline PEOPLE array. Shared by [syncPeople] and [downloadAll].
     */
    private suspend fun buildPeopleJson(people: List<com.tkolymp.shared.people.Person>, onChunkDone: (Int) -> Unit = {}): JsonArray = buildJsonArray {
        // Sliding window instead of fixed batches: a batch waits for its slowest request.
        val detailsMap = HashMap<String, com.tkolymp.shared.people.PersonDetails?>()
        val permits = kotlinx.coroutines.sync.Semaphore(EVENT_FETCH_CONCURRENCY)
        val lock = kotlinx.coroutines.sync.Mutex()
        coroutineScope {
            people.map { p ->
                async {
                    permits.withPermit {
                        val d = try { withRetry { peopleService.fetchPerson(p.id) } } catch (_: Exception) { null }
                        lock.withLock {
                            detailsMap[p.id] = d
                            onChunkDone(detailsMap.size)
                        }
                    }
                }
            }.awaitAll()
        }

        people.forEach { p ->
            val details = detailsMap[p.id]
            val birth = details?.birthDate ?: p.birthDate
            val prefix = details?.prefixTitle ?: p.prefixTitle
            val suffix = details?.suffixTitle ?: p.suffixTitle
            val isTrainer = details?.isTrainer ?: false
            val cohortMemberships = if (details != null) details.cohortMembershipsList else p.cohortMembershipsList

            add(buildJsonObject {
                put("id", JsonPrimitive(p.id))
                put("firstName", JsonPrimitive(p.firstName ?: ""))
                put("lastName", JsonPrimitive(p.lastName ?: ""))
                put("prefixTitle", JsonPrimitive(prefix ?: ""))
                put("suffixTitle", JsonPrimitive(suffix ?: ""))
                put("birthDate", JsonPrimitive(birth ?: ""))
                put("isTrainer", JsonPrimitive(isTrainer.toString()))
                // Fields only available from the per-person detail fetch (fetchPerson), not the
                // lightweight `people` list query. Achievements/diplomas are intentionally excluded
                // here — those are handled separately by AchievementService/AchievementStorage.
                put("cstsId", JsonPrimitive(details?.cstsId ?: ""))
                put("email", JsonPrimitive(details?.email ?: ""))
                put("gender", JsonPrimitive(details?.gender ?: ""))
                put("phone", JsonPrimitive(details?.phone ?: ""))
                put("wdsfId", JsonPrimitive(details?.wdsfId ?: ""))
                put("instagramUsername", JsonPrimitive(details?.instagramUsername ?: ""))
                put("tiktokUsername", JsonPrimitive(details?.tiktokUsername ?: ""))
                put("facebookUrl", JsonPrimitive(details?.facebookUrl ?: ""))
                put("websiteUrl", JsonPrimitive(details?.websiteUrl ?: ""))
                put("note", JsonPrimitive(details?.note ?: ""))
                put("activeCouplesList", buildJsonArray {
                    details?.activeCouplesList?.forEach { couple ->
                        add(buildJsonObject {
                            put("id", JsonPrimitive(couple.id ?: ""))
                            put("man", buildJsonObject {
                                put("firstName", JsonPrimitive(couple.man?.firstName ?: ""))
                                put("lastName", JsonPrimitive(couple.man?.lastName ?: ""))
                            })
                            put("woman", buildJsonObject {
                                put("firstName", JsonPrimitive(couple.woman?.firstName ?: ""))
                                put("lastName", JsonPrimitive(couple.woman?.lastName ?: ""))
                            })
                        })
                    }
                })
                put("allCouplesList", buildJsonArray {
                    details?.allCouplesList?.forEach { cp ->
                        add(buildJsonObject {
                            put("id", JsonPrimitive(cp.id ?: ""))
                            put("manId", JsonPrimitive(cp.manId ?: ""))
                            put("womanId", JsonPrimitive(cp.womanId ?: ""))
                            put("since", JsonPrimitive(cp.since ?: ""))
                            put("until", JsonPrimitive(cp.until ?: ""))
                            put("status", JsonPrimitive(cp.status ?: ""))
                            listOf("man" to cp.man, "woman" to cp.woman).forEach { (key, m) ->
                                if (m != null) put(key, buildJsonObject {
                                    put("firstName", JsonPrimitive(m.firstName ?: ""))
                                    put("lastName", JsonPrimitive(m.lastName ?: ""))
                                    m.imageUrl?.let { put("image", buildJsonObject { put("url", JsonPrimitive(it)) }) }
                                })
                            }
                        })
                    }
                })
                put("cstsProgressList", buildJsonArray {
                    details?.cstsProgressList?.forEach { progress ->
                        add(buildJsonObject {
                            put("points", JsonPrimitive(progress.points ?: ""))
                            put("finals", JsonPrimitive(progress.finals ?: -1))
                            put("competitorName", JsonPrimitive(progress.competitorName ?: ""))
                            val cat = progress.category
                            put("category", if (cat == null) kotlinx.serialization.json.JsonNull else buildJsonObject {
                                put("id", JsonPrimitive(cat.id ?: -1L))
                                put("name", JsonPrimitive(cat.name ?: ""))
                                put("series", JsonPrimitive(cat.series ?: ""))
                                put("discipline", JsonPrimitive(cat.discipline ?: ""))
                                put("ageGroup", JsonPrimitive(cat.ageGroup ?: ""))
                                put("genderGroup", JsonPrimitive(cat.genderGroup ?: ""))
                                put("class", JsonPrimitive(cat.competitorClass ?: ""))
                                put("competitorType", JsonPrimitive(cat.competitorType ?: ""))
                                put("baseDanceProgramId", JsonPrimitive(cat.baseDanceProgramId ?: -1L))
                            })
                        })
                    }
                })
                put("cohortMembershipsList", buildJsonArray {
                    cohortMemberships.forEach { cm ->
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

    private suspend fun syncAnnouncements() {
        try {
            val sticky = when (val r = announcementService.getAnnouncements(true)) { is DataResult.Success -> r.data; else -> emptyList() }
            val non = when (val r = announcementService.getAnnouncements(false)) { is DataResult.Success -> r.data; else -> emptyList() }
            try {
                offlineDataStorage.save(OfflineKeys.ANN_STICKY, AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), sticky))
            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save sticky announcements failed: ${ex.message}") }
            try {
                offlineDataStorage.save(OfflineKeys.ANN_NONSTICKY, AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), non))
            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "save nonsticky announcements failed: ${ex.message}") }
            // Save bodies for every announcement (sticky + non-sticky) so the detail screen can
            // load any of them offline, not just the handful most recently synced.
            val bodies = HashMap<String, String>()
            (sticky + non).forEach { ann ->
                try {
                    ann.id.toLongOrNull()?.let { id ->
                        bodies[OfflineKeys.announcementBody(id)] = AppJson.encodeToString(com.tkolymp.shared.announcements.Announcement.serializer(), ann)
                    }
                } catch (_: Exception) {}
            }
            try { offlineDataStorage.saveAll(bodies) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private suspend fun syncPeople() {
        try {
            val people = peopleService.fetchPeople()
            // Save people with additional details (try fetching full person details to get isTrainer/birthDate)
            val peopleJson = buildPeopleJson(people)
            if (people.isNotEmpty()) {
                offlineDataStorage.save(OfflineKeys.PEOPLE, AppJson.encodeToString(JsonArray.serializer(), peopleJson))
                persistCouples(peopleJson)
            } else Logger.d("OfflineSyncManager", "skipping save offline_people: empty")
        } catch (_: Exception) {}
    }

    suspend fun loadCalendarWeek(key: String): String? {
        return offlineDataStorage.load(key)
    }

    suspend fun loadEventDetail(id: Long): String? {
        return offlineDataStorage.load(OfflineKeys.eventDetail(id))
    }

    suspend fun loadAnnouncements(sticky: Boolean): String? {
        val k = if (sticky) OfflineKeys.ANN_STICKY else OfflineKeys.ANN_NONSTICKY
        return offlineDataStorage.load(k)
    }

    suspend fun loadAnnouncementDetail(id: Long): String? {
        return offlineDataStorage.load(OfflineKeys.announcementBody(id))
    }

    suspend fun loadPeople(): String? = offlineDataStorage.load(OfflineKeys.PEOPLE)

    suspend fun loadCouple(id: String): CoupleDetail? = try {
        offlineDataStorage.load(OfflineKeys.couple(id))?.let { AppJson.decodeFromString(CoupleDetail.serializer(), it) }
    } catch (_: Exception) { null }

    /** Fetches and saves every couple (active and past) referenced by the synced people, so the couple screen opens offline. */
    internal suspend fun persistCouples(peopleJson: JsonArray, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        try {
            val ids = peopleJson.flatMap { p ->
                val obj = p as? JsonObject ?: return@flatMap emptyList()
                listOf("activeCouplesList", "allCouplesList").flatMap { key ->
                    (obj[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull?.takeIf { id -> id.isNotBlank() } }
                }
            }.distinct()
            if (ids.isEmpty()) return
            val permits = kotlinx.coroutines.sync.Semaphore(EVENT_FETCH_CONCURRENCY)
            val lock = kotlinx.coroutines.sync.Mutex()
            var finished = 0
            onProgress(0, ids.size)
            val entries = coroutineScope {
                ids.map { id ->
                    async {
                        permits.withPermit {
                            val entry = try {
                                withRetry { peopleService.fetchCouple(id) }?.let { OfflineKeys.couple(id) to AppJson.encodeToString(CoupleDetail.serializer(), it) }
                            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "couple $id failed: ${ex.message}"); null }
                            lock.withLock { onProgress(++finished, ids.size) }
                            entry
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
            if (entries.isNotEmpty()) offlineDataStorage.saveAll(entries)
            Logger.d("OfflineSyncManager", "persistCouples: saved ${entries.size}/${ids.size}")
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "persistCouples failed: ${ex.message}") }
    }

    suspend fun loadClub(): String? = try { offlineDataStorage.load(OfflineKeys.CLUB) } catch (_: Exception) { null }

    suspend fun loadClubCohorts(): String? = try { offlineDataStorage.load(OfflineKeys.CLUB_COHORTS) } catch (_: Exception) { null }

    suspend fun loadClubBasics(): String? = try { offlineDataStorage.load(OfflineKeys.CLUB_BASIC) } catch (_: Exception) { null }

    suspend fun getLastSyncTime(): String? = offlineDataStorage.load(OfflineKeys.META_LAST_SYNC)

    // Debug / inspection helpers for payments
    suspend fun loadPayments(): String? = try { offlineDataStorage.load(OfflineKeys.PAYMENTS) } catch (_: Exception) { null }

    suspend fun loadPaymentsForPerson(personId: String): String? = try { offlineDataStorage.load(OfflineKeys.paymentsForPerson(personId)) } catch (_: Exception) { null }

    suspend fun listOfflineKeys(): Set<String> = try { offlineDataStorage.allKeys() } catch (_: Exception) { emptySet() }

    private suspend fun saveLastSyncTime(iso: String) = offlineDataStorage.save(OfflineKeys.META_LAST_SYNC, iso)

    internal suspend fun migrateIfNeeded() {
        val stored = try { offlineDataStorage.load(OfflineKeys.META_SCHEMA_VERSION) } catch (_: Exception) { null }
        if (stored?.toIntOrNull() == OfflineKeys.SCHEMA_VERSION) return
        Logger.d("OfflineSyncManager", "schema mismatch (stored=$stored, current=${OfflineKeys.SCHEMA_VERSION}), clearing cached data")
        try { offlineDataStorage.deleteByPrefix("offline_") } catch (_: Exception) {}
        try { offlineDataStorage.save(OfflineKeys.META_SCHEMA_VERSION, OfflineKeys.SCHEMA_VERSION.toString()) } catch (_: Exception) {}
    }

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

    suspend fun downloadAll(report: (String, Int, Int) -> Unit = { _, _, _ -> }) = withContext(Dispatchers.Default) {
        val startMs = Clock.System.now().toEpochMilliseconds()
        var lastStage = ""
        val onProgress: (String, Int, Int) -> Unit = { stage, done, total ->
            if (stage != lastStage) {
                lastStage = stage
                Logger.d("OfflineSyncManager", "downloadAll: stage=$stage started at +${(Clock.System.now().toEpochMilliseconds() - startMs) / 1000}s")
            }
            report(stage, done, total)
        }
        migrateIfNeeded()
        if (!networkMonitor.isConnected()) throw Exception("Network unavailable")
        Logger.d("OfflineSyncManager", "downloadAll: starting")

        val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val buckets = listOf(CalendarBucket.MINE, CalendarBucket.ALL, CalendarBucket.CAMPS)
        val weekOffsets = (-4..4).toList()
        val totalCalendar = buckets.size * weekOffsets.size
        var calDone = 0
        val collectedEventIds = mutableSetOf<Long>()

        // All bucket/week requests run concurrently (each takes ~2 s, 27 sequential ones took about a minute).
        onProgress("calendar", 0, totalCalendar)
        val calPermits = kotlinx.coroutines.sync.Semaphore(EVENT_FETCH_CONCURRENCY)
        val calLock = kotlinx.coroutines.sync.Mutex()
        val calEntries = HashMap<String, String>()
        coroutineScope {
            buckets.flatMap { bucket -> weekOffsets.map { bucket to it } }.map { (bucket, weekOffset) ->
                async {
                    calPermits.withPermit {
                        val rawWeekStart = today.plus(weekOffset * 7, DateTimeUnit.DAY)
                        val weekStart = rawWeekStart.minus(rawWeekStart.dayOfWeek.ordinal, DateTimeUnit.DAY)
                        val endDay = weekStart.plus(6, DateTimeUnit.DAY)
                        val startIso = "${weekStart}T00:00:00Z"
                        val endIso = "${endDay}T23:59:59Z"
                        val onlyMine = bucket == CalendarBucket.MINE
                        val onlyType = if (bucket == CalendarBucket.CAMPS) EventType.CAMP.rawValue else null
                        val grouped = try { withRetry { eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = onlyMine, first = AppConstants.FETCH_LIMIT_PERIOD, offset = 0, onlyType = onlyType, cacheNamespace = null) } } catch (ex: Exception) { emptyMap<String, List<EventInstance>>() }
                        val encoded = if (grouped.isNotEmpty()) AppJson.encodeToString(JsonObject.serializer(), buildCalendarWeekJson(grouped)) else null
                        calLock.withLock {
                            if (encoded != null) {
                                calEntries[OfflineKeys.calendarWeek(bucket, weekStart.toString())] = encoded
                                collectedEventIds += grouped.values.flatten().mapNotNull { it.event?.id }
                            }
                            onProgress("calendar", ++calDone, totalCalendar)
                        }
                    }
                }
            }.awaitAll()
        }
        try { offlineDataStorage.saveAll(calEntries) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll save calendar weeks failed: ${ex.message}") }

        // User data
        onProgress("user", 0, 1)
        try {
            val pid = try { withRetry { userService.fetchAndStorePersonId() } } catch (_: Exception) { null }
            coroutineScope {
                launch { try { withRetry { userService.fetchAndStoreActiveCouples() } } catch (_: Exception) {} }
                if (!pid.isNullOrBlank()) {
                    launch { try { withRetry { userService.fetchAndStorePersonDetails(pid) } } catch (_: Exception) {} }
                }
            }
            // Ensure notification defaults exist. Pass stored user role so parents don't get default rules.
            try {
                val role = try { com.tkolymp.shared.ServiceLocator.onboardingStorage.getUserRole() } catch (_: Exception) { null }
                notificationService.initializeIfNeeded(role)
            } catch (_: Exception) {}
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll user data failed: ${ex.message}") }
        onProgress("user", 1, 1)

        // Announcements
        onProgress("announcements", 0, 1)
        try {
            // getAnnouncements reports failures as DataResult.Error instead of throwing; unwrap so withRetry retries and the failure is logged.
            suspend fun fetchAnnouncements(sticky: Boolean) = withRetry {
                when (val r = announcementService.getAnnouncements(sticky)) {
                    is DataResult.Success -> r.data
                    is DataResult.Error -> throw Exception("announcements(sticky=$sticky): ${r.error.message}")
                }
            }
            val (sticky, non) = coroutineScope {
                val st = async { fetchAnnouncements(true) }
                val nn = async { fetchAnnouncements(false) }
                st.await() to nn.await()
            }
            Logger.d("OfflineSyncManager", "downloadAll: announcements sticky=${sticky.size} nonSticky=${non.size}")
            if (sticky.isNotEmpty()) offlineDataStorage.save(OfflineKeys.ANN_STICKY, AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), sticky))
            if (non.isNotEmpty()) offlineDataStorage.save(OfflineKeys.ANN_NONSTICKY, AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), non))
            // Save individual bodies too so the detail screen loads offline for any announcement.
            val bodies = HashMap<String, String>()
            (sticky + non).forEach { ann ->
                try {
                    ann.id.toLongOrNull()?.let { id ->
                        bodies[OfflineKeys.announcementBody(id)] = AppJson.encodeToString(com.tkolymp.shared.announcements.Announcement.serializer(), ann)
                    }
                } catch (_: Exception) {}
            }
            offlineDataStorage.saveAll(bodies)
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll announcements failed: ${ex.message}") }
        onProgress("announcements", 1, 1)

        // People
        var syncedPeopleJson: JsonArray? = null
        onProgress("people", 0, 1)
        try {
            val ppl = withRetry { peopleService.fetchPeople() }
            if (ppl.isNotEmpty()) {
                onProgress("people", 0, ppl.size)
                val peopleJson = buildPeopleJson(ppl) { done -> onProgress("people", done, ppl.size) }
                val encoded = AppJson.encodeToString(JsonArray.serializer(), peopleJson)
                try {
                    offlineDataStorage.save(OfflineKeys.PEOPLE, encoded)
                    Logger.d("OfflineSyncManager", "downloadAll: saved offline_people count=${ppl.size}")
                } catch (ex: Exception) {
                    Logger.d("OfflineSyncManager", "downloadAll: save offline_people failed: ${ex.message}")
                }
                syncedPeopleJson = peopleJson
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll people failed: ${ex.message}") }
        onProgress("people", 1, 1)

        // Couples (ids come from the people just synced, or from the stored copy when that fetch failed)
        onProgress("couples", 0, 1)
        try {
            val source = syncedPeopleJson
                ?: try { offlineDataStorage.load(OfflineKeys.PEOPLE)?.let { AppJson.parseToJsonElement(it) as? JsonArray } } catch (_: Exception) { null }
            if (source != null) persistCouples(source) { done, total -> onProgress("couples", done, total) }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll couples failed: ${ex.message}") }
        onProgress("couples", 1, 1)

            // Payments
            onProgress("payments", 0, 1)
            try {
                try { syncPayments() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll payments failed: ${ex.message}") }
            } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll payments outer failed: ${ex.message}") }
        onProgress("payments", 1, 1)

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
                val encodedBoard = AppJson.encodeToString(JsonArray.serializer(), boardJson)
                try {
                    offlineDataStorage.save(OfflineKeys.scoreboard(since, until), encodedBoard)
                    Logger.d("OfflineSyncManager", "downloadAll: saved offline_scoreboard entries=${board.size}")
                } catch (ex: Exception) {
                    Logger.d("OfflineSyncManager", "downloadAll: save offline_scoreboard failed: ${ex.message}")
                }
            }
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll scoreboard failed: ${ex.message}") }
        onProgress("scoreboard", 1, 1)

        // Club data
        onProgress("club", 0, 1)
        try {
            val cd = try { withRetry { clubService.fetchClubData() } } catch (ex: Exception) { null }
            if (cd != null) persistClubData(cd.raw)
        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll club failed: ${ex.message}") }
        onProgress("club", 1, 1)

        // Event details
        // Ensure we also download ALL CAMP events separately (beyond weekly buckets)
        try {
            val allCamps = try {
                withRetry {
                    eventService.fetchEventsGroupedByDay(
                        DateRangeConstants.FAR_PAST,
                        DateRangeConstants.FAR_FUTURE,
                        onlyMine = false,
                        first = AppConstants.FETCH_LIMIT_FULL,
                        offset = 0,
                        onlyType = EventType.CAMP.rawValue,
                        cacheNamespace = null
                    )
                }
            } catch (ex: Exception) {
                emptyMap<String, List<EventInstance>>()
            }

            if (allCamps.isNotEmpty()) {
                val joAll = buildCalendarWeekJson(allCamps)

                try {
                    offlineDataStorage.save(OfflineKeys.CAL_CAMPS_ALL, AppJson.encodeToString(JsonObject.serializer(), joAll))
                } catch (ex: Exception) {
                    Logger.d("OfflineSyncManager", "save all camps failed: ${ex.message}")
                }

                collectedEventIds += allCamps.values.flatten().mapNotNull { it.event?.id }
            }
        } catch (ex: Exception) {
            Logger.d("OfflineSyncManager", "fetch all camps failed: ${ex.message}")
        }
        // Sliding window of EVENT_FETCH_CONCURRENCY requests (a fixed batch waits for its slowest request),
        // progress reported per event and results persisted in batches via saveAll.
        val allEventIds = collectedEventIds.toList() // after the camps above, so camp details are included too
        val totalAll = allEventIds.size
        onProgress("events", 0, totalAll)
        val permits = kotlinx.coroutines.sync.Semaphore(EVENT_FETCH_CONCURRENCY)
        val lock = kotlinx.coroutines.sync.Mutex()
        val saveLock = kotlinx.coroutines.sync.Mutex()
        val pending = HashMap<String, String>()
        var finished = 0
        coroutineScope {
            allEventIds.map { evId ->
                async {
                    // Persisting runs outside the permit and the bookkeeping lock so a slow write (the storage
                    // index grows with every batch) never stalls the downloads.
                    val batch = permits.withPermit {
                        val entry = try {
                            val full = withRetry { eventService.fetchEventById(evId, forceRefresh = false) }
                            if (full != null) OfflineKeys.eventDetail(evId) to AppJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), full) else null
                        } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll event ${evId} failed: ${ex.message}"); null }
                        lock.withLock {
                            if (entry != null) pending[entry.first] = entry.second
                            finished++
                            onProgress("events", finished, totalAll)
                            if (pending.size >= EVENT_SAVE_BATCH || finished == totalAll) pending.toMap().also { pending.clear() } else null
                        }
                    }
                    if (batch != null) saveLock.withLock {
                        val t0 = Clock.System.now().toEpochMilliseconds()
                        try { offlineDataStorage.saveAll(batch) } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll save event details failed: ${ex.message}") }
                        Logger.d("OfflineSyncManager", "downloadAll: saved ${batch.size} event details in ${Clock.System.now().toEpochMilliseconds() - t0}ms")
                    }
                }
            }.awaitAll()
        }

        // Competitions
        onProgress("competitions", 0, 1)
        coroutineScope {
            launch { try { syncCompetitions() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll competitions failed: ${ex.message}") } }
            launch { try { syncPastCompetitions() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll pastCompetitions failed: ${ex.message}") } }
            launch { try { syncFederationEvents() } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll federationEvents failed: ${ex.message}") } }
        }
        onProgress("competitions", 1, 1)

        // Achievements (context snapshot for diplomas/progress; earned badges are persisted when the screen opens)
        onProgress("achievements", 0, 1)
        try { achievementServiceProvider?.invoke()?.refreshOfflineContext() } catch (ex: kotlinx.coroutines.CancellationException) { throw ex } catch (ex: Exception) { Logger.d("OfflineSyncManager", "downloadAll achievements failed: ${ex.message}") }
        onProgress("achievements", 1, 1)

        saveLastSyncTime(kotlin.time.Clock.System.now().toString())
        Logger.d("OfflineSyncManager", "downloadAll: completed")
    }

    private suspend fun syncCompetitions() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val list = competitionService.getUpcomingCompetitions(
            pSince = today.toString(),
            pUntil = today.plus(21, DateTimeUnit.DAY).toString()
        )
        if (list.isNotEmpty()) {
            val json = AppJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Competition.serializer()),
                list
            )
            offlineDataStorage.save(OfflineKeys.COMPETITIONS, json)
            Logger.d("OfflineSyncManager", "syncCompetitions: saved ${list.size} entries")
        }
    }

    suspend fun loadCompetitions(): List<Competition> = try {
        val json = offlineDataStorage.load(OfflineKeys.COMPETITIONS) ?: return emptyList()
        AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Competition.serializer()), json)
    } catch (_: Exception) { emptyList() }

    // Fetching through the service saves the lists offline; this is what the Overview "nearest
    // competition" card reads (this year and the next, as findNearestUpcomingEvent does).
    private suspend fun syncFederationEvents() {
        val service = competitiveDataService ?: return
        val year = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        for (y in year..year + 1) service.getEvents("csts", y, forceRefresh = true)
    }

    private suspend fun syncPastCompetitions() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val list = competitionService.getPastCompetitions(
            pSince = today.minus(90, DateTimeUnit.DAY).toString(),
            pUntil = today.toString()
        )
        if (list.isNotEmpty()) {
            val json = AppJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Competition.serializer()),
                list
            )
            offlineDataStorage.save(OfflineKeys.COMPETITIONS_PAST, json)
            Logger.d("OfflineSyncManager", "syncPastCompetitions: saved ${list.size} entries")
        }
    }

    suspend fun loadPastCompetitions(): List<Competition> = try {
        val json = offlineDataStorage.load(OfflineKeys.COMPETITIONS_PAST) ?: return emptyList()
        AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Competition.serializer()), json)
    } catch (_: Exception) { emptyList() }
}
