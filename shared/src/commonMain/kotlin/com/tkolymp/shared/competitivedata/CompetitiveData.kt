package com.tkolymp.shared.competitivedata

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
    val competitions: List<FederationCompetitionSummary>
)

@Serializable
data class FederationResultRow(
    val ranking: Int,
    val rankingTo: Int?,
    val pointGain: String?,
    val competitorName: String?,
    /** The API's `isFinal` flag (a ČSTS "final" credit), shown as "F" like on the old competition screens. */
    val isFinal: Boolean = false
)

@Serializable
data class FederationCompetitionResults(
    val id: Long,
    val startDate: String,
    val competitionType: String?,
    val status: String,
    val participants: Int?,
    val categoryName: String?,
    val results: List<FederationResultRow>
)

/** [data] plus whether it is the saved copy served because the live request failed. */
data class LoadedData<T>(val data: T, val fromOffline: Boolean = false)
