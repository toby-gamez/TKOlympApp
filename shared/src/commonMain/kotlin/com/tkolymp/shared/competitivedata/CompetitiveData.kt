package com.tkolymp.shared.competitivedata

import com.tkolymp.shared.competitions.CompetitionOfficial
import kotlinx.serialization.Serializable

@Serializable
data class DanceFederation(val code: String, val name: String)

@Serializable
data class FederationYear(val year: Int, val eventCount: Int, val mappedEventCount: Int)

@Serializable
data class FederationEventSummary(
    val id: Long,
    val name: String?,
    val startDate: String,
    val endDate: String?,
    val city: String?,
    val location: String?,
    val country: String?,
    val lat: Double?,
    val lng: Double?,
    val status: String,
    val competitionCount: Int,
    val hasResults: Boolean,
    /** CompetitionType names (CUP, RANKING, ...) of the competitions held at this event. */
    val types: Set<String> = emptySet()
) {
    val place: String? get() = city?.takeIf { it.isNotBlank() } ?: location?.takeIf { it.isNotBlank() }
    val isCancelled: Boolean get() = status == "CANCELLED"
    val hasCoordinates: Boolean get() = lat != null && lng != null
    /** 1..12, parsed from the ISO start date. */
    val month: Int get() = startDate.substring(5, 7).toInt()
}

@Serializable
data class FederationCompetitionSummary(
    val id: Long,
    val startDate: String,
    val competitionType: String?,
    val status: String,
    val participants: Int?,
    val categoryName: String?,
    val discipline: String?,
    val ageGroup: String?,
    val competitorClass: String?
)

@Serializable
data class FederationEventDetail(
    val id: Long,
    /** The federation's own event id and secondary id; ČSTS sign-up links are built from both. */
    val externalId: String? = null,
    val extraId: String? = null,
    val federation: String,
    val name: String?,
    val startDate: String,
    val endDate: String?,
    val city: String?,
    val location: String?,
    val country: String?,
    val streetAddress: String?,
    val postalCode: String?,
    val addressNote: String?,
    val contactName: String?,
    val contactPhone: String?,
    val contactEmail: String?,
    val websiteUrl: String?,
    val lat: Double?,
    val lng: Double?,
    val status: String,
    val organizer: String?,
    val competitions: List<FederationCompetitionSummary>,
    val officials: List<CompetitionOfficial> = emptyList()
)

@Serializable
data class FederationResultRow(
    val ranking: Int,
    val rankingTo: Int?,
    val pointGain: String?,
    val competitorName: String?,
    /** The API's `isFinal` flag (a ČSTS "final" credit), shown as "F" like on the old competition screens. */
    val isFinal: Boolean = false,
    /** True when the competitor (or a member of the pair) is a person from our club's people list. */
    val isClub: Boolean = false
)

@Serializable
data class FederationCompetitionResults(
    val id: Long,
    val startDate: String,
    val competitionType: String?,
    val status: String,
    val participants: Int?,
    val categoryName: String?,
    val results: List<FederationResultRow>,
    val officials: List<CompetitionOfficial> = emptyList(),
    /** Names of the non-cancelled entries, sorted by name. */
    val entries: List<String> = emptyList(),
    /** The subset of [entries] that belongs to our club. */
    val clubEntries: Set<String> = emptySet()
)

/** The club result lists of the "Soutěžní data" screen. */
enum class HighlightKind { YEAR_ACHIEVEMENTS, HALL_OF_CHAMPIONS, LAST_WEEKEND }

/** One placement of a club competitor: [competitorName] finished [ranking]. in [categoryName] at [eventName]. */
@Serializable
data class FederationHighlight(
    val competitionId: Long,
    val eventId: Long?,
    val eventName: String?,
    val startDate: String,
    val competitionType: String?,
    val categoryName: String?,
    val ranking: Int,
    val rankingTo: Int?,
    val competitorId: String?,
    /** CompetitorType name: COUPLE, SOLO, DUO, TRIO, TEAM, GROUP, FORMATION. */
    val competitorType: String?,
    val competitorName: String?
)

/** [rows] of one list plus the covered range, [since] to [until] both inclusive (ISO dates). */
data class ClubResults(val rows: List<FederationHighlight>, val since: String?, val until: String?)

/** [data] plus whether it is the saved copy served because the live request failed. */
data class LoadedData<T>(val data: T, val fromOffline: Boolean = false)
