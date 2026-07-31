package com.tkolymp.shared.achievements

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
private data class EarnedBadgeRecord(val id: String, val earnedOnIso: String)

private const val STORAGE_KEY = "achievements_earned_badges"
private const val SEEN_DIPLOMAS_KEY = "achievements_seen_diploma_ids"

/**
 * Persists which badges have already been earned (on-device only, via the same
 * [OfflineDataStorage] key/value store used elsewhere for cached JSON blobs), so
 * re-evaluating badges never loses the date a badge was first earned.
 */
class AchievementStorage(
    private val offlineDataStorage: OfflineDataStorage = ServiceLocator.offlineDataStorage
) {
    /** False the very first time this device ever evaluates badges — used to seed the "seen" baseline silently instead of flagging pre-existing badges as new. */
    suspend fun hasStoredBadges(): Boolean =
        try { offlineDataStorage.load(STORAGE_KEY) != null } catch (_: Exception) { false }

    suspend fun loadEarnedBadges(): List<EarnedBadge> {
        val json = try { offlineDataStorage.load(STORAGE_KEY) } catch (_: Exception) { null } ?: return emptyList()
        return try {
            AppJson.decodeFromString(ListSerializer(EarnedBadgeRecord.serializer()), json)
                .mapNotNull { record ->
                    runCatching { LocalDate.parse(record.earnedOnIso) }.getOrNull()?.let { EarnedBadge(record.id, it) }
                }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun saveEarnedBadges(badges: List<EarnedBadge>) {
        val records = badges.map { EarnedBadgeRecord(it.id, it.earnedOn.toString()) }
        try {
            offlineDataStorage.save(STORAGE_KEY, AppJson.encodeToString(ListSerializer(EarnedBadgeRecord.serializer()), records))
        } catch (_: Exception) {}
    }

    /** False the very first time diploma "seen" state is ever persisted on this device — same seeding purpose as [hasStoredBadges]. */
    suspend fun hasStoredSeenDiplomaIds(): Boolean =
        try { offlineDataStorage.load(SEEN_DIPLOMAS_KEY) != null } catch (_: Exception) { false }

    suspend fun loadSeenDiplomaIds(): Set<Long> {
        val json = try { offlineDataStorage.load(SEEN_DIPLOMAS_KEY) } catch (_: Exception) { null } ?: return emptySet()
        return try {
            AppJson.decodeFromString(ListSerializer(Long.serializer()), json).toSet()
        } catch (_: Exception) { emptySet() }
    }

    suspend fun saveSeenDiplomaIds(ids: Set<Long>) {
        try {
            offlineDataStorage.save(SEEN_DIPLOMAS_KEY, AppJson.encodeToString(ListSerializer(Long.serializer()), ids.toList()))
        } catch (_: Exception) {}
    }
}
