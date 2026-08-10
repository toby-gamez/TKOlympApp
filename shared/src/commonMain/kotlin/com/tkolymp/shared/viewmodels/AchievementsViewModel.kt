package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.achievements.AchievementEngine
import com.tkolymp.shared.achievements.AchievementService
import com.tkolymp.shared.achievements.BadgeCategory
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
    val isNew: Boolean = false,
)

@Immutable
data class AchievementsState(
    val badges: List<BadgeUiState> = emptyList(),
    val diplomas: List<DiplomaUiState> = emptyList(),
    val isOffline: Boolean = false,
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

    /**
     * [personId] null (or equal to the current device user's own id) loads "my" achievements —
     * full category set, diplomas, and storage-backed "new" tracking. Any other personId loads a
     * read-only view of that member's achievements: only the categories backed by data sources
     * that accept an explicit personId (Camp, Membership, Competitions), no diplomas, and nothing
     * is ever persisted to [AchievementService]'s on-device storage — that storage represents only
     * the current device user's own state.
     */
    suspend fun load(personId: String? = null) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val myPersonId = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val isOffline = try { !ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { false }

            if (personId != null && personId != myPersonId) {
                val context = achievementService.loadContextForPerson(personId)
                val earnedById = AchievementEngine.evaluate(context).associateBy { it.id }
                val visibleCategories = setOf(BadgeCategory.CAMP, BadgeCategory.MEMBERSHIP, BadgeCategory.COMPETITIONS)

                val badges = BadgeRegistry.all
                    .filter { it.category in visibleCategories }
                    .map { definition ->
                        val earned = earnedById[definition.id]
                        BadgeUiState(
                            definition = definition,
                            earned = earned != null,
                            earnedOn = earned?.earnedOn,
                            progress = AchievementEngine.progressFor(definition.id, context),
                            isNew = false,
                        )
                    }

                _state.value = _state.value.copy(badges = badges, diplomas = emptyList(), isOffline = isOffline, isLoading = false)
                return
            }

            val result = achievementService.evaluateAndPersist()
            val earnedById = result.earnedBadges.associateBy { it.id }

            val badges = BadgeRegistry.all.map { definition ->
                val earned = earnedById[definition.id]
                BadgeUiState(
                    definition = definition,
                    earned = earned != null,
                    earnedOn = earned?.earnedOn,
                    progress = AchievementEngine.progressFor(definition.id, result.context),
                    isNew = definition.id in result.newlyEarnedIds,
                )
            }

            val participantName = try {
                if (!myPersonId.isNullOrBlank()) peopleService.fetchPersonDisplayName(myPersonId) else null
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: ""

            val diplomas = result.context.completedCamps
                .sortedByDescending { it.startDate }
                .map { camp ->
                    DiplomaUiState(
                        camp = camp,
                        participantName = participantName,
                        isNew = camp.eventId in result.newlyEarnedDiplomaEventIds,
                    )
                }

            _state.value = _state.value.copy(badges = badges, diplomas = diplomas, isOffline = isOffline, isLoading = false)
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
