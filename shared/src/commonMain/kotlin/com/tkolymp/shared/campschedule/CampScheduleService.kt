package com.tkolymp.shared.campschedule

import com.tkolymp.shared.Logger
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Local-only persistence for camp schedule days: the transcribed JSON per day, the
 * uploaded photo per day, the user's chosen group number and name (both camp-wide,
 * not per-day), and the shared reminder-minutes preference for the camp. Nothing here
 * is synced or uploaded anywhere.
 */
class CampScheduleService(
    private val offlineDataStorage: OfflineDataStorage
) {
    private companion object {
        const val TAG = "CampScheduleService"
    }

    private fun dayKey(eventId: Long, dayIndex: Int) = "camp_schedule_${eventId}_day_$dayIndex"
    private fun photoKey(eventId: Long, dayIndex: Int) = "camp_schedule_photo_${eventId}_day_$dayIndex"
    private fun groupNumberKey(eventId: Long) = "camp_schedule_groupnum_$eventId"
    private fun reminderMinutesKey(eventId: Long) = "camp_schedule_reminder_minutes_$eventId"
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

    /** The group number applies to the whole camp, chosen once, not per day. */
    suspend fun saveGroupNumber(eventId: Long, groupNumber: Int) {
        offlineDataStorage.save(groupNumberKey(eventId), groupNumber.toString())
    }

    suspend fun loadGroupNumber(eventId: Long): Int? {
        val raw = try { offlineDataStorage.load(groupNumberKey(eventId)) } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        return raw?.toIntOrNull()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun savePhoto(eventId: Long, dayIndex: Int, bytes: ByteArray) {
        offlineDataStorage.save(photoKey(eventId, dayIndex), Base64.encode(bytes))
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun loadPhoto(eventId: Long, dayIndex: Int): ByteArray? {
        val raw = try { offlineDataStorage.load(photoKey(eventId, dayIndex)) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "loadPhoto failed: ${e.message}")
            null
        } ?: return null
        return try { Base64.decode(raw) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "photo base64 decode failed: ${e.message}")
            null
        }
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
}
