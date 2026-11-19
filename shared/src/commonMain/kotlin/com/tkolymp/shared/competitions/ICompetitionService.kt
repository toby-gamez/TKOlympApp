package com.tkolymp.shared.competitions

interface ICompetitionService {
    suspend fun getUpcomingCompetitions(
        pSince: String? = null,
        pUntil: String? = null,
        first: Int = 50,
        pPersonIds: List<Long>? = null
    ): List<Competition>

    suspend fun getPastCompetitions(
        pSince: String? = null,
        pUntil: String? = null,
        first: Int = 50,
        pPersonIds: List<Long>? = null
    ): List<Competition>

    /** Competitions of the last offline sync inside the range, for showing something while a fetch runs. */
    suspend fun getStoredUpcoming(pSince: String, pUntil: String, pPersonIds: List<Long>? = null): List<Competition> = emptyList()

    suspend fun getNearestUpcoming(pPersonIds: List<Long>? = null): Competition?
}
