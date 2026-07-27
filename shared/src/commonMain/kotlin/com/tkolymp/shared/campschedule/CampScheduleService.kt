package com.tkolymp.shared.campschedule

import com.tkolymp.shared.Logger
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.coroutines.CancellationException

/**
 * Local-only persistence for camp schedule days: the transcribed JSON per day, the
 * user's chosen group number per day, and the shared reminder-minutes preference for
 * the camp. Nothing here is synced or uploaded anywhere.
 */
class CampScheduleService(
    private val offlineDataStorage: OfflineDataStorage
) {
    private companion object {
        const val TAG = "CampScheduleService"
    }

    private fun dayKey(eventId: Long, dayIndex: Int) = "camp_schedule_${eventId}_day_$dayIndex"
    private fun groupNumberKey(eventId: Long, dayIndex: Int) = "camp_schedule_groupnum_${eventId}_day_$dayIndex"
    private fun reminderMinutesKey(eventId: Long) = "camp_schedule_reminder_minutes_$eventId"
    private fun myNameOverrideKey(eventId: Long) = "camp_schedule_myname_$eventId"
    fun reminderIdsKey(eventId: Long, dayIndex: Int) = "camp_schedule_reminder_ids_${eventId}_day_$dayIndex"

    suspend fun saveDay(eventId: Long, dayIndex: Int, day: ScheduleDay) {
        offlineDataStorage.save(dayKey(eventId, dayIndex), AppJson.encodeToString(ScheduleDay.serializer(), day))
    }

    suspend fun loadDay(eventId: Long, dayIndex: Int): ScheduleDay? {
        val raw = try { offlineDataStorage.load(dayKey(eventId, dayIndex)) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "loadDay failed: ${e.message}")
            null
        } ?: return null
        return try { parseScheduleDay(raw) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "parseScheduleDay failed: ${e.message}")
            null
        }
    }

    suspend fun listStoredDayIndexes(eventId: Long): Set<Int> {
        val prefix = "camp_schedule_${eventId}_day_"
        return try {
            offlineDataStorage.allKeys()
                .filter { it.startsWith(prefix) }
                .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
                .toSet()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "listStoredDayIndexes failed: ${e.message}")
            emptySet()
        }
    }

    suspend fun saveGroupNumber(eventId: Long, dayIndex: Int, groupNumber: Int) {
        offlineDataStorage.save(groupNumberKey(eventId, dayIndex), groupNumber.toString())
    }

    suspend fun loadGroupNumber(eventId: Long, dayIndex: Int): Int? {
        val raw = try { offlineDataStorage.load(groupNumberKey(eventId, dayIndex)) } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        return raw?.toIntOrNull()
    }

    suspend fun getReminderMinutes(eventId: Long, default: Int = 30): Int {
        val raw = try { offlineDataStorage.load(reminderMinutesKey(eventId)) } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        return raw?.toIntOrNull() ?: default
    }

    suspend fun setReminderMinutes(eventId: Long, minutes: Int) {
        offlineDataStorage.save(reminderMinutesKey(eventId), minutes.toString())
    }

    /** Manual override for "my table name", e.g. for testing against a name the logged-in account doesn't resolve to. */
    suspend fun saveMyNameOverride(eventId: Long, name: String) {
        offlineDataStorage.save(myNameOverrideKey(eventId), name)
    }

    suspend fun loadMyNameOverride(eventId: Long): String? {
        val raw = try { offlineDataStorage.load(myNameOverrideKey(eventId)) } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        return raw?.takeIf { it.isNotBlank() }
    }
}
