package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.storage.OfflineDataStorage
import com.tkolymp.shared.sync.CalendarBucket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

private const val TAG = "FreeLessonsViewModel"
private const val DISMISSED_STORAGE_KEY = "dismissed_cancelled_replacements"

data class FreeLessonResult(
    val instance: EventInstance,
    val score: Int,
    val hasConflict: Boolean,
    val conflictName: String?,
    val dayDistance: Int,
    val sameLocation: Boolean,
    val tip: String?
)

data class FreeLessonsState(
    val cancelledMineInstances: List<EventInstance> = emptyList(),
    val replacementResults: Map<String, List<FreeLessonResult>> = emptyMap(),
    val bestFinds: List<FreeLessonResult> = emptyList(),
    val otherFinds: List<FreeLessonResult> = emptyList(),
    val hasCancelledToShow: Boolean = false,
    val dismissedInstanceIds: Set<String> = emptySet(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class FreeLessonsViewModel(
    private val eventService: IEventService = ServiceLocator.eventService,
    private val cache: CacheService = ServiceLocator.cacheService,
    private val offlineDataStorage: OfflineDataStorage = ServiceLocator.offlineDataStorage
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(FreeLessonsState())
    val state: StateFlow<FreeLessonsState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)

        val dismissedIds = loadDismissedIds()

        try {
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(tz)
            val twoWeeksLater = today.plus(14, DateTimeUnit.DAY)
            val startIso = today.toString() + "T00:00:00Z"
            val endIso = twoWeeksLater.toString() + "T23:59:59Z"

            val (mineResult, allResult) = coroutineScope {
                val mineDeferred = async(Dispatchers.Default) {
                    try {
                        fetchGroupedEvents(startIso, endIso, onlyMine = true, first = 500, offset = 0, onlyType = null, cacheNamespace = "free_lessons_mine_")
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        Logger.d(TAG, "fetchMine failed: ${e.message}")
                        Pair(emptyMap<String, List<EventInstance>>(), true)
                    }
                }
                val allDeferred = async(Dispatchers.Default) {
                    try {
                        fetchGroupedEvents(startIso, endIso, onlyMine = false, first = 500, offset = 0, onlyType = null, cacheNamespace = "free_lessons_all_")
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        Logger.d(TAG, "fetchAll failed: ${e.message}")
                        Pair(emptyMap<String, List<EventInstance>>(), true)
                    }
                }
                Pair(mineDeferred.await(), allDeferred.await())
            }
            val mineMap = mineResult.first
            val allMap = allResult.first

            // Flatten mine events
            val myAllInstances = mineMap.values.flatten()

            // Cancelled mine instances (not dismissed) — only lessons
            val cancelledMine = myAllInstances
                .filter { it.isCancelled }
                .filter { it.event?.type?.equals("lesson", ignoreCase = true) == true }
                .filter { it.id.toString() !in dismissedIds }

            // My training instances (not cancelled) for conflict/scoring
            val myTrainingByDay: Map<String, List<EventInstance>> = mineMap.mapValues { (_, list) ->
                list.filter { !it.isCancelled }
            }

            // Collect all instances from ALL bucket first — including cancelled ones.
            // Build a set of cancelled IDs: if the same instance appears in multiple cached weeks
            // with different isCancelled values (stale vs. fresh), the cancelled flag wins.
            val allInstances = allMap.values.flatten()
            val cancelledAllIds = allInstances.filter { it.isCancelled }.map { it.id }.toSet()

            // Free slots from all events pool — only lessons, not cancelled, 0 registrations, open registration.
            val freeSlots: List<EventInstance> = allInstances
                .filter { it.id !in cancelledAllIds }
                .filter { isFuture(it.since) }
                .filter { it.event?.type?.equals("lesson", ignoreCase = true) == true }
                .filter {
                    it.event?.eventRegistrationsList?.isEmpty() == true &&
                    it.event?.isRegistrationOpen == true
                }

            // Score each free slot
            val scoredResults: List<FreeLessonResult> = freeSlots.map { inst ->
                scoreInstance(inst, myTrainingByDay, today.toString())
            }

            // Replacements for cancelled lessons
            val replacementResults: Map<String, List<FreeLessonResult>> = cancelledMine.associate { cancelled ->
                val cancelledTrainer = cancelled.event.firstTrainerOrEmpty()
                val cancelledWeekStart = weekStartOf(cancelled.since)
                val replacements = scoredResults.filter { result ->
                    val inst = result.instance
                    val instTrainer = inst.event.firstTrainerOrEmpty()
                    val instWeekStart = weekStartOf(inst.since)
                    instTrainer == cancelledTrainer &&
                            instWeekStart == cancelledWeekStart &&
                            isFuture(inst.since)
                }.sortedByDescending { it.score }
                cancelled.id.toString() to replacements
            }

            // Best finds: top 5 without conflict
            val topN = 5
            val bestFindsRaw = scoredResults
                .filter { !it.hasConflict }
                .sortedByDescending { it.score }
                .take(topN)

            val bestFinds = bestFindsRaw.mapIndexed { index, result ->
                val tip = if (index == 0) {
                    // Tip #5: highest score overall — skip isBestSection fallback so we can give "Toto je nejlepší volba"
                    computeTip(result, myTrainingByDay, isBestSection = false) ?: AppStrings.current.freeLessons.bestChoiceFallback
                } else {
                    computeTip(result, myTrainingByDay, isBestSection = true)
                }
                result.copy(tip = tip)
            }

            val bestSet = bestFinds.map { it.instance.id }.toSet()

            val otherFinds = scoredResults
                .filter { it.instance.id !in bestSet }
                .sortedByDescending { it.score }
                .map { it.copy(tip = computeTip(it, myTrainingByDay, isBestSection = false)) }

            val hasCancelledToShow = cancelledMine.isNotEmpty()

            _state.value = FreeLessonsState(
                cancelledMineInstances = cancelledMine,
                replacementResults = replacementResults,
                bestFinds = bestFinds,
                otherFinds = otherFinds,
                hasCancelledToShow = hasCancelledToShow,
                dismissedInstanceIds = dismissedIds,
                isLoading = false,
                error = null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.d(TAG, "load failed: ${e.message}")
            _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Chyba při načítání")
        }
    }

    suspend fun dismissCancelled(instanceId: String) {
        val newDismissed = _state.value.dismissedInstanceIds + instanceId
        saveDismissedIds(newDismissed)
        val newCancelled = _state.value.cancelledMineInstances.filter { it.id.toString() != instanceId }
        val newReplacements = _state.value.replacementResults - instanceId
        _state.value = _state.value.copy(
            cancelledMineInstances = newCancelled,
            replacementResults = newReplacements,
            hasCancelledToShow = newCancelled.isNotEmpty(),
            dismissedInstanceIds = newDismissed
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun scoreInstance(
        inst: EventInstance,
        myTrainingByDay: Map<String, List<EventInstance>>,
        todayStr: String
    ): FreeLessonResult {
        val instDate = inst.since?.substringBefore("T") ?: return FreeLessonResult(inst, 0, false, null, 0, false, null)

        val dayDistance = try {
            val today = kotlinx.datetime.LocalDate.parse(todayStr)
            val instDay = kotlinx.datetime.LocalDate.parse(instDate)
            (instDay.toEpochDays() - today.toEpochDays()).toInt().coerceAtLeast(0)
        } catch (_: Exception) { 0 }

        val myEventsToday = myTrainingByDay[instDate] ?: emptyList()

        // Conflict check
        val conflictingEvent = myEventsToday.firstOrNull { mine ->
            overlaps(mine.since, mine.until, inst.since, inst.until)
        }
        val hasConflict = conflictingEvent != null
        val conflictName = conflictingEvent?.event?.name?.takeIf { it.isNotBlank() }

        // Same location bonus
        val instLocation = locationOf(inst)
        val sameLocation = myEventsToday.any { mine ->
            val mineLoc = locationOf(mine)
            instLocation.isNotBlank() && instLocation == mineLoc
        }

        // Scoring
        var score = 100
        score -= (dayDistance * 5).coerceAtMost(70)
        if (myEventsToday.isEmpty()) score += 30
        if (sameLocation) score += 20

        return FreeLessonResult(
            instance = inst,
            score = score,
            hasConflict = hasConflict,
            conflictName = conflictName,
            dayDistance = dayDistance,
            sameLocation = sameLocation,
            tip = null // computed separately
        )
    }

    private fun computeTip(
        result: FreeLessonResult,
        myTrainingByDay: Map<String, List<EventInstance>>,
        isBestSection: Boolean
    ): String? {
        val inst = result.instance
        val instDate = inst.since?.substringBefore("T") ?: return null
        val myEventsToday = myTrainingByDay[instDate] ?: emptyList()

        // 1. Conflict
        if (result.hasConflict) {
            val name = result.conflictName ?: AppStrings.current.freeLessons.trainingFallback
            return AppStrings.current.freeLessons.conflictTip.replace("{0}", name)
        }

        // Find nearest adjacent (non-overlapping) my event and the gap in minutes
        // gapMinutes(a, b) = (b - a) in minutes, positive means b is after a
        val nearest: Pair<EventInstance, Int>? = myEventsToday
            .mapNotNull { mine ->
                val gapAfterMine = gapMinutes(mine.until, inst.since)  // mine ends before inst starts
                val gapAfterInst = gapMinutes(inst.until, mine.since)  // inst ends before mine starts
                when {
                    gapAfterMine >= 0 -> Pair(mine, gapAfterMine)
                    gapAfterInst >= 0 -> Pair(mine, gapAfterInst)
                    else -> null  // overlap (conflict) — skip
                }
            }
            .minByOrNull { it.second }

        if (nearest != null) {
            val (nearestMine, gap) = nearest
            val instLoc = locationOf(inst)
            val mineLoc = locationOf(nearestMine)
            val sameHall = instLoc.isNotBlank() && mineLoc.isNotBlank() && instLoc == mineLoc

            if (!sameHall && gap < 15) {
                // 3. Different hall, not enough time
                return AppStrings.current.freeLessons.differentHallNoTime
            }
            if (!sameHall && gap >= 15) {
                // 4. Different hall, enough time
                return AppStrings.current.freeLessons.differentHallHasTime
            }
            if (sameHall && gap > 45) {
                // 2. Same hall, long wait — include previous training name
                val prevName = nearestMine.event?.name?.takeIf { it.isNotBlank() } ?: AppStrings.current.freeLessons.trainingFallback
                return AppStrings.current.freeLessons.sameHallLongWait.replace("{0}", prevName)
            }
            // Same hall, gap OK → no tip
        }

        // 5. Best score overall — we'd need global context; skip here (handled at call site if needed)
        // 6. In best section
        if (isBestSection) {
            return AppStrings.current.freeLessons.alsoGoodChoice
        }

        return null
    }

    // --- helpers ---

    private fun overlaps(s1: String?, e1: String?, s2: String?, e2: String?): Boolean {
        val start1 = s1?.let { parseEpoch(it) } ?: return false
        val end1 = e1?.let { parseEpoch(it) } ?: return false
        val start2 = s2?.let { parseEpoch(it) } ?: return false
        val end2 = e2?.let { parseEpoch(it) } ?: return false
        return start1 < end2 && start2 < end1
    }

    private fun gapMinutes(end: String?, start: String?): Int {
        val e = end?.let { parseEpoch(it) } ?: return Int.MAX_VALUE
        val s = start?.let { parseEpoch(it) } ?: return Int.MAX_VALUE
        return ((s - e) / 60_000L).toInt()
    }

    private fun parseEpoch(iso: String): Long {
        return try {
            kotlinx.datetime.Instant.parse(iso).toEpochMilliseconds()
        } catch (_: Exception) { 0L }
    }

    private fun locationOf(inst: EventInstance): String {
        return inst.event?.locationText?.takeIf { !it.isNullOrBlank() }
            ?: inst.event?.location?.name?.takeIf { !it.isNullOrBlank() }
            ?: ""
    }

    private fun weekStartOf(isoDateTime: String?): String? {
        val date = isoDateTime?.substringBefore("T") ?: return null
        return try {
            val ld = kotlinx.datetime.LocalDate.parse(date)
            val dayOfWeek = ld.dayOfWeek.isoDayNumber // 1=Mon, 7=Sun
            ld.plus(-(dayOfWeek - 1), DateTimeUnit.DAY).toString()
        } catch (_: Exception) { null }
    }

    private fun isFuture(isoDateTime: String?): Boolean {
        val epoch = isoDateTime?.let { parseEpoch(it) } ?: return false
        return epoch > Clock.System.now().toEpochMilliseconds()
    }

    private suspend fun loadDismissedIds(): Set<String> {
        return try {
            val raw = withContext(Dispatchers.Default) { offlineDataStorage.load(DISMISSED_STORAGE_KEY) }
            raw?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
    }

    private suspend fun saveDismissedIds(ids: Set<String>) {
        try {
            withContext(Dispatchers.Default) { offlineDataStorage.save(DISMISSED_STORAGE_KEY, ids.joinToString(",")) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    // Fetch helper — returns (data, isOffline). isOffline=true when falling back to stored data.
    private suspend fun fetchGroupedEvents(
        startIso: String,
        endIso: String,
        onlyMine: Boolean,
        first: Int = 500,
        offset: Int = 0,
        onlyType: String? = null,
        cacheNamespace: String? = null
    ): Pair<Map<String, List<EventInstance>>, Boolean> {
        return try {
            val res = eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine, first, offset, onlyType, cacheNamespace = cacheNamespace)
            if (res.isNotEmpty()) Pair(res, false)
            else Pair(loadGroupedFromOffline(startIso, endIso, if (onlyMine) CalendarBucket.MINE else CalendarBucket.ALL), true)
        } catch (ex: Exception) {
            Logger.d(TAG, "fetchGroupedEvents failed: ${ex.message}")
            Pair(loadGroupedFromOffline(startIso, endIso, if (onlyMine) CalendarBucket.MINE else CalendarBucket.ALL), true)
        }
    }

    // Reconstruct grouped events map from offline calendar summaries and offline event details
    private suspend fun loadGroupedFromOffline(startIso: String, endIso: String, bucket: CalendarBucket): Map<String, List<EventInstance>> {
        val startDate = startIso.substringBefore("T")
        val endDate = endIso.substringBefore("T")
        val sd = try { kotlinx.datetime.LocalDate.parse(startDate) } catch (_: Exception) { return emptyMap() }
        val ed = try { kotlinx.datetime.LocalDate.parse(endDate) } catch (_: Exception) { return emptyMap() }

        val weekStarts = mutableSetOf<String>()
        var d = sd
        while (d <= ed) {
            val ws = weekStartOf(d.toString() + "T00:00:00Z")
            if (ws != null) weekStarts.add(ws)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        Logger.d(TAG, "loadGroupedFromOffline: computed weekStarts=$weekStarts for range $startDate..$endDate")

        val result = mutableMapOf<String, MutableList<EventInstance>>();

        // Prefer direct week keys, but also scan all stored keys for matches if keys are present under different formats
        val candidateKeys = mutableSetOf<String>()
        // direct keys from computed weekStarts
        weekStarts.forEach { ws -> candidateKeys.add("offline_cal_${bucket.name}_$ws") }

        // add any matching keys from storage (robustness for different week formats)
        try {
            val all = offlineDataStorage.allKeys()
            all.forEach { k ->
                if (k.startsWith("offline_cal_${bucket.name}_")) candidateKeys.add(k)
            }
            if (candidateKeys.isEmpty()) Logger.d(TAG, "loadGroupedFromOffline: no offline_cal keys for ${bucket.name}")
            else Logger.d(TAG, "loadGroupedFromOffline: candidateKeys=${candidateKeys}")
        } catch (_: Exception) { }

        for (key in candidateKeys) {
            val raw = try { offlineDataStorage.load(key) } catch (_: Exception) { null }
            if (raw.isNullOrBlank()) continue
            try {
                // Use Calendar-like parsing to ensure the same shape as CalendarViewModel
                val parsed = parseCalendarJson(raw)
                val enriched = enrichParsedWithEventDetails(parsed)
                enriched.forEach { (date, list) ->
                    val target = result.getOrPut(date) { mutableListOf() }
                    val existingIds = target.map { it.id }.toHashSet()
                    target.addAll(list.filter { it.id !in existingIds })
                }
            } catch (e: Exception) { Logger.d(TAG, "loadGroupedFromOffline: parse failed for key=${key}: ${e.message}") }
        }

        // Filter to only dates within the requested range to avoid leaking past/future events
        return result.filter { (date, _) -> date >= startDate && date <= endDate }
    }

    // --- Diagnostics helpers (call from debugger / temporary UI) ---
    suspend fun debugListOfflineKeys(): Set<String> = try {
        try { offlineDataStorage.allKeys() } catch (_: Exception) { emptySet() }
    } catch (_: Exception) { emptySet() }

    suspend fun debugOfflineCalendarSummary(): Map<String, Int> = try {
        val all = try { offlineDataStorage.allKeys() } catch (_: Exception) { emptySet<String>() }
        val calKeys = all.filter { it.startsWith("offline_cal_") }
        val map = mutableMapOf<String, Int>()
        calKeys.forEach { k ->
            try {
                val raw = offlineDataStorage.load(k)
                if (!raw.isNullOrBlank()) {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    var count = 0
                    obj.forEach { (_, arrEl) ->
                        count += try { arrEl.jsonArray.size } catch (_: Exception) { 0 }
                    }
                    map[k] = count
                } else map[k] = 0
            } catch (ex: Exception) {
                map[k] = -1
            }
        }
        map
    } catch (_: Exception) { emptyMap() }

    suspend fun debugLoadKey(key: String): String? = try { offlineDataStorage.load(key) } catch (_: Exception) { null }

    private fun parseCalendarJson(raw: String): Map<String, List<EventInstance>> {
        return try {
            val parsedJson = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            val result = mutableMapOf<String, MutableList<EventInstance>>()
            parsedJson.entries.forEach { (date, elem) ->
                val arr = elem.jsonArray
                val list = mutableListOf<EventInstance>()
                arr.forEach { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.longOrNull ?: obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val isCancelled = obj["isCancelled"]?.jsonPrimitive?.booleanOrNull ?: false
                    val since = obj["since"]?.jsonPrimitive?.contentOrNull
                    val until = obj["until"]?.jsonPrimitive?.contentOrNull
                    val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
                    val eventId = obj["eventId"]?.jsonPrimitive?.longOrNull
                    val eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull
                    val eventType = obj["eventType"]?.jsonPrimitive?.contentOrNull
                    val locationText = obj["locationText"]?.jsonPrimitive?.contentOrNull
                    val trainers = (obj["trainers"]?.jsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                    val targetCohorts = (obj["targetCohorts"]?.jsonArray)?.mapNotNull { it2 ->
                        val o = it2.jsonObject
                        val cohortId = o["cohortId"]?.jsonPrimitive?.longOrNull ?: o["cohortId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val cohortObj = o["cohort"]?.jsonObject
                        val cohort = cohortObj?.let { c ->
                            val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            com.tkolymp.shared.event.Cohort(cid, c["name"]?.jsonPrimitive?.contentOrNull, c["colorRgb"]?.jsonPrimitive?.contentOrNull)
                        }
                        com.tkolymp.shared.event.TargetCohort(cohortId, cohort)
                    } ?: emptyList()

                    val registrations = (obj["eventRegistrationsList"]?.jsonArray)?.mapNotNull { regEl ->
                        val o = regEl.jsonObject
                        val rid = o["id"]?.jsonPrimitive?.longOrNull ?: o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

                        val personObj = o["person"]?.jsonObject
                        val person = personObj?.let { p ->
                            val pid = p["id"]?.jsonPrimitive?.longOrNull ?: p["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            com.tkolymp.shared.event.Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
                        }

                        val coupleObj = o["couple"]?.jsonObject
                        val couple = coupleObj?.let { c ->
                            val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            val manObj = c["man"]?.jsonObject
                            val womanObj = c["woman"]?.jsonObject
                            val man = manObj?.let { m -> com.tkolymp.shared.event.SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
                            val woman = womanObj?.let { w -> com.tkolymp.shared.event.SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                            com.tkolymp.shared.event.Couple(cid, man, woman)
                        }
                        com.tkolymp.shared.event.Registration(rid, person, couple)
                    } ?: emptyList()

                    val locationObj = obj["location"]?.jsonObject
                    val location = locationObj?.let { l ->
                        val lid = l["id"]?.jsonPrimitive?.longOrNull ?: l["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        com.tkolymp.shared.event.Location(lid, l["name"]?.jsonPrimitive?.contentOrNull)
                    }

                    val isRegistrationOpen = obj["isRegistrationOpen"]?.jsonPrimitive?.booleanOrNull ?: false
                    val event = com.tkolymp.shared.event.Event(eventId, eventName, null, eventType, locationText, isRegistrationOpen, false, false, trainers, targetCohorts, registrations, location)
                    list += com.tkolymp.shared.event.EventInstance(id, isCancelled, since, until, updatedAt, event)
                }
                result[date] = list
            }
            result
        } catch (e: Exception) { emptyMap() }
    }

    private suspend fun enrichParsedWithEventDetails(parsed: Map<String, List<EventInstance>>): Map<String, List<EventInstance>> {
        val jsonLib = kotlinx.serialization.json.Json
        val result = mutableMapOf<String, MutableList<EventInstance>>()
        for ((date, list) in parsed) {
            val newList = mutableListOf<EventInstance>()
            for (inst in list) {
                val ev = inst.event
                if (ev?.id != null) {
                    try {
                        val raw = ServiceLocator.offlineSyncManager.loadEventDetail(ev.id)
                        if (!raw.isNullOrBlank()) {
                            val obj = jsonLib.parseToJsonElement(raw).jsonObject
                            val trainers = (obj["eventTrainersList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
                                ?: (obj["eventTrainersList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                            val targetCohorts = (obj["eventTargetCohortsList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { item ->
                                val o = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                val cohortId = o["cohortId"]?.jsonPrimitive?.longOrNull ?: o["cohortId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                val cohortObj = o["cohort"] as? kotlinx.serialization.json.JsonObject
                                val cohort = cohortObj?.let { c ->
                                    val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    com.tkolymp.shared.event.Cohort(cid, c["name"]?.jsonPrimitive?.contentOrNull, c["colorRgb"]?.jsonPrimitive?.contentOrNull)
                                }
                                com.tkolymp.shared.event.TargetCohort(cohortId, cohort)
                            } ?: emptyList()

                            val regArr = when {
                                obj["eventRegistrationsList"] is kotlinx.serialization.json.JsonArray -> obj["eventRegistrationsList"] as kotlinx.serialization.json.JsonArray
                                obj["eventRegistrations"] is kotlinx.serialization.json.JsonObject -> (obj["eventRegistrations"] as kotlinx.serialization.json.JsonObject)["nodes"] as? kotlinx.serialization.json.JsonArray
                                else -> null
                            }
                            val registrations = regArr?.mapNotNull { item ->
                                val o = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                val rid = o["id"]?.jsonPrimitive?.longOrNull ?: o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                val personObj = o["person"] as? kotlinx.serialization.json.JsonObject
                                val person = personObj?.let { p ->
                                    val pid = p["id"]?.jsonPrimitive?.longOrNull ?: p["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    com.tkolymp.shared.event.Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
                                }
                                val coupleObj = o["couple"] as? kotlinx.serialization.json.JsonObject
                                val couple = coupleObj?.let { c ->
                                    val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    val manObj = c["man"] as? kotlinx.serialization.json.JsonObject
                                    val womanObj = c["woman"] as? kotlinx.serialization.json.JsonObject
                                    val man = manObj?.let { m -> com.tkolymp.shared.event.SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
                                    val woman = womanObj?.let { w -> com.tkolymp.shared.event.SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                                    com.tkolymp.shared.event.Couple(cid, man, woman)
                                }
                                com.tkolymp.shared.event.Registration(rid, person, couple)
                            } ?: emptyList()

                            val locationObj = obj["location"] as? kotlinx.serialization.json.JsonObject
                            val location = locationObj?.let { l ->
                                val lid = l["id"]?.jsonPrimitive?.longOrNull ?: l["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                com.tkolymp.shared.event.Location(lid, l["name"]?.jsonPrimitive?.contentOrNull)
                            }

                            // isLocked is always present in offline_event_* (was always in eventByIdQuery).
                            // isRegistrationOpen: if locked → false, else use stored value, else fall back to
                            // calendar summary value (NOT true — avoids marking cancelled lessons as open).
                            val isLocked = obj["isLocked"]?.jsonPrimitive?.booleanOrNull ?: false
                            val isRegistrationOpen = if (isLocked) false else (obj["isRegistrationOpen"]?.jsonPrimitive?.booleanOrNull ?: ev.isRegistrationOpen)

                            // Cross-check isCancelled from event detail's eventInstancesList.
                            // eventByIdQuery always fetches eventInstancesList { id isCancelled }.
                            // If the event detail was refreshed after a cancellation (e.g. user opened the
                            // event screen), it will have isCancelled=true even when the calendar summary
                            // cache still says false. OR the two values so that either source wins.
                            val isCancelledFromDetail = (obj["eventInstancesList"] as? kotlinx.serialization.json.JsonArray)
                                ?.any { item ->
                                    val o = item as? kotlinx.serialization.json.JsonObject ?: return@any false
                                    val itemId = o["id"]?.jsonPrimitive?.longOrNull
                                        ?: o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    itemId == inst.id && (o["isCancelled"]?.jsonPrimitive?.booleanOrNull == true)
                                } ?: false
                            val isCancelledFinal = inst.isCancelled || isCancelledFromDetail

                            val enrichedEvent = com.tkolymp.shared.event.Event(ev.id, obj["name"]?.jsonPrimitive?.contentOrNull ?: ev.name, obj["description"]?.jsonPrimitive?.contentOrNull, obj["type"]?.jsonPrimitive?.contentOrNull ?: ev.type, obj["locationText"]?.jsonPrimitive?.contentOrNull ?: ev.locationText, isRegistrationOpen, obj["isVisible"]?.jsonPrimitive?.booleanOrNull ?: ev.isVisible, obj["isPublic"]?.jsonPrimitive?.booleanOrNull ?: ev.isPublic, trainers, targetCohorts.ifEmpty { ev.eventTargetCohortsList }, registrations.ifEmpty { ev.eventRegistrationsList }, location ?: ev.location)
                            newList += com.tkolymp.shared.event.EventInstance(inst.id, isCancelledFinal, inst.since, inst.until, inst.updatedAt, enrichedEvent)
                            continue
                        }
                    } catch (_: Exception) {}
                }
                newList += inst
            }
            result[date] = newList
        }
        return result
    }
}
