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

    suspend fun getNearestUpcoming(pPersonIds: List<Long>? = null): Competition?
}
