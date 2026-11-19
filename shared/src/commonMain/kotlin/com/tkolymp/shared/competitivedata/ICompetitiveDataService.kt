package com.tkolymp.shared.competitivedata

interface ICompetitiveDataService {
    suspend fun getFederations(): List<DanceFederation>
    suspend fun getYears(federation: String): List<FederationYear>
    suspend fun getEvents(federation: String, year: Int, forceRefresh: Boolean = false): LoadedData<List<FederationEventSummary>>
    suspend fun getEventDetail(eventId: Long): FederationEventDetail?
    /**
     * The club's placements for [kind]. [year] limits the two championship lists (YEAR_ACHIEVEMENTS defaults to the
     * current year, HALL_OF_CHAMPIONS to all years); LAST_WEEKEND ignores it.
     */
    suspend fun getClubResults(kind: HighlightKind, year: Int? = null): ClubResults
    suspend fun getCompetitionResults(competitionId: Long): FederationCompetitionResults?
}
