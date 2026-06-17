package com.tkolymp.shared.competitions

import kotlinx.serialization.Serializable

@Serializable
data class CompetitionCategory(
    val id: Long?,
    val name: String?,
    val series: String?,
    val discipline: String?,
    val ageGroup: String?,
    val genderGroup: String?,
    val competitorClass: String?,
    val competitorType: String?,
    val baseDanceProgramId: Long?
)

@Serializable
data class CstsProgress(
    val points: String?,
    val finals: Int?,
    val competitorName: String?,
    val category: CompetitionCategory?
)

@Serializable
data class Competition(
    val competitionId: Long?,
    val competitionDate: String,
    val checkInEnd: String?,
    val competitionType: String?,
    val isFinal: Boolean,
    val hasResult: Boolean,
    val eventName: String?,
    val eventLocation: String?,
    val eventId: Long?,
    val federation: String?,
    val federatedPersonId: String?,
    val personName: String?,
    val personId: Long?,
    val competitorName: String?,
    val competitorId: String?,
    val competitorType: String?,
    val dances: List<String>,
    val participants: Int?,
    val ranking: Int?,
    val rankingTo: Int?,
    val pointGain: String?,
    val category: CompetitionCategory?
)
