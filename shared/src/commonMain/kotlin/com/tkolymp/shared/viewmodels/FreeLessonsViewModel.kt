package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.storage.OfflineDataStorage
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
import kotlin.time.Clock
import com.tkolymp.shared.language.AppStrings

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

            val (mineMap, allMap) = coroutineScope {
                val mineDeferred = async(Dispatchers.Default) {
                    try {
                        eventService.fetchEventsGroupedByDay(startIso, endIso, true, 200, 0, null, cacheNamespace = "free_lessons_mine_")
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        Logger.d(TAG, "fetchMine failed: ${e.message}")
                        emptyMap()
                    }
                }
                val allDeferred = async(Dispatchers.Default) {
                    try {
                        eventService.fetchEventsGroupedByDay(startIso, endIso, false, 200, 0, null, cacheNamespace = "free_lessons_all_")
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        Logger.d(TAG, "fetchAll failed: ${e.message}")
                        emptyMap()
                    }
                }
                Pair(mineDeferred.await(), allDeferred.await())
            }

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

            // Free slots from all events pool — only lessons, not cancelled, no registrations or open registration
            val freeSlots: List<EventInstance> = allMap.values.flatten()
                .filter { !it.isCancelled }
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
        val conflictName = conflictingEvent?.event?.name

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
                val prevName = nearestMine.event?.name ?: AppStrings.current.freeLessons.trainingFallback
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
}
