package com.tkolymp.shared.competitivedata

interface ICompetitiveDataService {
    suspend fun getFederations(): List<DanceFederation>
    suspend fun getYears(federation: String): List<FederationYear>
    suspend fun getEvents(federation: String, year: Int, forceRefresh: Boolean = false): LoadedData<List<FederationEventSummary>>
    suspend fun getEventDetail(eventId: Long): FederationEventDetail?
    suspend fun getHighlights(kind: HighlightKind): List<FederationHighlight>
    suspend fun getCompetitionResults(competitionId: Long): FederationCompetitionResults?
}
