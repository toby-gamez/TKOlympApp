package com.tkolymp.shared.achievements

import com.tkolymp.shared.language.AchievementStrings

enum class BadgeCategory { CAMP, MEMBERSHIP, ATTENDANCE, COMPETITIONS, REPERTOIRE, RHYTHM }

/** Stable string ids for each badge — persisted in [AchievementStorage], never renamed once shipped. */
object BadgeIds {
    const val CAMP_FIRST = "camp_first"
    const val CAMP_3 = "camp_3"
    const val CAMP_5 = "camp_5"
    const val CAMP_10 = "camp_10"
    const val CAMP_20 = "camp_20"
    const val CAMP_PERFECT = "camp_perfect"
    const val CAMP_MULTI_SEASON = "camp_multi_season"
    const val MEMBERSHIP_1 = "membership_1"
    const val MEMBERSHIP_2 = "membership_2"
    const val MEMBERSHIP_3 = "membership_3"
    const val MEMBERSHIP_5 = "membership_5"
    const val MEMBERSHIP_7 = "membership_7"
    const val MEMBERSHIP_10 = "membership_10"
    const val MEMBERSHIP_15 = "membership_15"
    const val STREAK_4 = "streak_4"
    const val STREAK_8 = "streak_8"
    const val STREAK_16 = "streak_16"
    const val LESSONS_50 = "lessons_50"
    const val LESSONS_100 = "lessons_100"
    const val LESSONS_250 = "lessons_250"
    const val COMPETITION_FIRST = "competition_first"
    const val COMPETITION_10 = "competition_10"
    const val COMPETITION_25 = "competition_25"
    const val COMPETITION_FINAL_FIRST = "competition_final_first"
    const val COMPETITION_PODIUM = "competition_podium"
    const val COMPETITION_CHAMPION = "competition_champion"
    const val COMPETITION_VERSATILE = "competition_versatile"
    const val TRAINERS_3 = "trainers_3"
    const val TRAINERS_5 = "trainers_5"
    const val TRAINERS_10 = "trainers_10"
    const val TYPES_EXPLORER = "types_explorer"
    const val EARLY_BIRD = "early_bird"
    const val NIGHT_OWL = "night_owl"
    const val WEEKEND_WARRIOR = "weekend_warrior"
    const val COMEBACK = "comeback"
    const val PARTNERSHIP_3 = "partnership_3"
    const val PARTNERSHIP_5 = "partnership_5"
}

data class BadgeDefinition(
    val id: String,
    val category: BadgeCategory,
    val icon: String,
    val title: (AchievementStrings) -> String,
    val description: (AchievementStrings) -> String,
)

/** Static registry of every badge the achievement engine knows about. Add a badge = add an entry here. */
object BadgeRegistry {
    val all: List<BadgeDefinition> = listOf(
        BadgeDefinition(BadgeIds.CAMP_FIRST, BadgeCategory.CAMP, "🏕️", { it.campFirstTitle }, { it.campFirstDesc }),
        BadgeDefinition(BadgeIds.CAMP_3, BadgeCategory.CAMP, "🏕️", { it.camp3Title }, { it.camp3Desc }),
        BadgeDefinition(BadgeIds.CAMP_5, BadgeCategory.CAMP, "🏕️", { it.camp5Title }, { it.camp5Desc }),
        BadgeDefinition(BadgeIds.CAMP_10, BadgeCategory.CAMP, "🏕️", { it.camp10Title }, { it.camp10Desc }),
        BadgeDefinition(BadgeIds.CAMP_20, BadgeCategory.CAMP, "🏕️", { it.camp20Title }, { it.camp20Desc }),
        BadgeDefinition(BadgeIds.CAMP_PERFECT, BadgeCategory.CAMP, "⭐", { it.campPerfectTitle }, { it.campPerfectDesc }),
        BadgeDefinition(BadgeIds.CAMP_MULTI_SEASON, BadgeCategory.CAMP, "🧭", { it.campMultiSeasonTitle }, { it.campMultiSeasonDesc }),
        BadgeDefinition(BadgeIds.MEMBERSHIP_1, BadgeCategory.MEMBERSHIP, "🎗️", { it.membership1Title }, { it.membership1Desc }),
        BadgeDefinition(BadgeIds.MEMBERSHIP_2, BadgeCategory.MEMBERSHIP, "🎗️", { it.membership2Title }, { it.membership2Desc }),
        BadgeDefinition(BadgeIds.MEMBERSHIP_3, BadgeCategory.MEMBERSHIP, "🎗️", { it.membership3Title }, { it.membership3Desc }),
        BadgeDefinition(BadgeIds.MEMBERSHIP_5, BadgeCategory.MEMBERSHIP, "🎗️", { it.membership5Title }, { it.membership5Desc }),
        BadgeDefinition(BadgeIds.MEMBERSHIP_7, BadgeCategory.MEMBERSHIP, "🎖️", { it.membership7Title }, { it.membership7Desc }),
        BadgeDefinition(BadgeIds.MEMBERSHIP_10, BadgeCategory.MEMBERSHIP, "🎖️", { it.membership10Title }, { it.membership10Desc }),
        BadgeDefinition(BadgeIds.MEMBERSHIP_15, BadgeCategory.MEMBERSHIP, "🏆", { it.membership15Title }, { it.membership15Desc }),
        BadgeDefinition(BadgeIds.STREAK_4, BadgeCategory.ATTENDANCE, "🔥", { it.streak4Title }, { it.streak4Desc }),
        BadgeDefinition(BadgeIds.STREAK_8, BadgeCategory.ATTENDANCE, "🔥", { it.streak8Title }, { it.streak8Desc }),
        BadgeDefinition(BadgeIds.STREAK_16, BadgeCategory.ATTENDANCE, "🔥", { it.streak16Title }, { it.streak16Desc }),
        BadgeDefinition(BadgeIds.LESSONS_50, BadgeCategory.ATTENDANCE, "💃", { it.lessons50Title }, { it.lessons50Desc }),
        BadgeDefinition(BadgeIds.LESSONS_100, BadgeCategory.ATTENDANCE, "💃", { it.lessons100Title }, { it.lessons100Desc }),
        BadgeDefinition(BadgeIds.LESSONS_250, BadgeCategory.ATTENDANCE, "💃", { it.lessons250Title }, { it.lessons250Desc }),
        BadgeDefinition(BadgeIds.COMPETITION_FIRST, BadgeCategory.COMPETITIONS, "🩰", { it.competitionFirstTitle }, { it.competitionFirstDesc }),
        BadgeDefinition(BadgeIds.COMPETITION_10, BadgeCategory.COMPETITIONS, "🩰", { it.competition10Title }, { it.competition10Desc }),
        BadgeDefinition(BadgeIds.COMPETITION_25, BadgeCategory.COMPETITIONS, "🩰", { it.competition25Title }, { it.competition25Desc }),
        BadgeDefinition(BadgeIds.COMPETITION_FINAL_FIRST, BadgeCategory.COMPETITIONS, "🎭", { it.competitionFinalFirstTitle }, { it.competitionFinalFirstDesc }),
        BadgeDefinition(BadgeIds.COMPETITION_PODIUM, BadgeCategory.COMPETITIONS, "🥉", { it.competitionPodiumTitle }, { it.competitionPodiumDesc }),
        BadgeDefinition(BadgeIds.COMPETITION_CHAMPION, BadgeCategory.COMPETITIONS, "🥇", { it.competitionChampionTitle }, { it.competitionChampionDesc }),
        BadgeDefinition(BadgeIds.COMPETITION_VERSATILE, BadgeCategory.COMPETITIONS, "🌈", { it.competitionVersatileTitle }, { it.competitionVersatileDesc }),
        BadgeDefinition(BadgeIds.PARTNERSHIP_3, BadgeCategory.COMPETITIONS, "👫", { it.partnership3Title }, { it.partnership3Desc }),
        BadgeDefinition(BadgeIds.PARTNERSHIP_5, BadgeCategory.COMPETITIONS, "💑", { it.partnership5Title }, { it.partnership5Desc }),
        BadgeDefinition(BadgeIds.TRAINERS_3, BadgeCategory.REPERTOIRE, "🧑‍🏫", { it.trainers3Title }, { it.trainers3Desc }),
        BadgeDefinition(BadgeIds.TRAINERS_5, BadgeCategory.REPERTOIRE, "🧑‍🏫", { it.trainers5Title }, { it.trainers5Desc }),
        BadgeDefinition(BadgeIds.TRAINERS_10, BadgeCategory.REPERTOIRE, "🧑‍🏫", { it.trainers10Title }, { it.trainers10Desc }),
        BadgeDefinition(BadgeIds.TYPES_EXPLORER, BadgeCategory.REPERTOIRE, "🗺️", { it.typesExplorerTitle }, { it.typesExplorerDesc }),
        BadgeDefinition(BadgeIds.EARLY_BIRD, BadgeCategory.RHYTHM, "☀️", { it.earlyBirdTitle }, { it.earlyBirdDesc }),
        BadgeDefinition(BadgeIds.NIGHT_OWL, BadgeCategory.RHYTHM, "🦉", { it.nightOwlTitle }, { it.nightOwlDesc }),
        BadgeDefinition(BadgeIds.WEEKEND_WARRIOR, BadgeCategory.RHYTHM, "🎉", { it.weekendWarriorTitle }, { it.weekendWarriorDesc }),
        BadgeDefinition(BadgeIds.COMEBACK, BadgeCategory.RHYTHM, "🔁", { it.comebackTitle }, { it.comebackDesc }),
    )

    fun byId(id: String): BadgeDefinition? = all.firstOrNull { it.id == id }
}
