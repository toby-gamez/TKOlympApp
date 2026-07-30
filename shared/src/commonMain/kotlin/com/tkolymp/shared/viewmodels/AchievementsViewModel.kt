package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.achievements.AchievementService
import com.tkolymp.shared.achievements.BadgeDefinition
import com.tkolymp.shared.achievements.BadgeRegistry
import com.tkolymp.shared.achievements.CampOccurrence
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.user.UserService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

@Immutable
data class BadgeUiState(
    val definition: BadgeDefinition,
    val earned: Boolean,
    val earnedOn: LocalDate?,
    val progress: Pair<Int, Int>?,
    val isNew: Boolean,
)

@Immutable
data class DiplomaUiState(
    val camp: CampOccurrence,
    val participantName: String,
)

@Immutable
data class AchievementsState(
    val badges: List<BadgeUiState> = emptyList(),
    val diplomas: List<DiplomaUiState> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null,
) : ViewModelState {
    val earnedCount: Int get() = badges.count { it.earned }
}

class AchievementsViewModel(
    private val achievementService: AchievementService = AchievementService(),
    private val peopleService: PeopleService = ServiceLocator.peopleService,
    private val userService: UserService = ServiceLocator.userService,
) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val result = achievementService.evaluateAndPersist()
            val earnedById = result.earnedBadges.associateBy { it.id }

            val badges = BadgeRegistry.all.map { definition ->
                val earned = earnedById[definition.id]
                BadgeUiState(
                    definition = definition,
                    earned = earned != null,
                    earnedOn = earned?.earnedOn,
                    progress = com.tkolymp.shared.achievements.AchievementEngine.progressFor(definition.id, result.context),
                    isNew = definition.id in result.newlyEarnedIds,
                )
            }

            val participantName = try {
                val personId = userService.getCachedPersonId()
                if (!personId.isNullOrBlank()) peopleService.fetchPersonDisplayName(personId) else null
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: ""

            val diplomas = result.context.completedCamps
                .sortedByDescending { it.startDate }
                .map { camp -> DiplomaUiState(camp = camp, participantName = participantName) }

            _state.value = _state.value.copy(badges = badges, diplomas = diplomas, isLoading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoadingEvents)
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
