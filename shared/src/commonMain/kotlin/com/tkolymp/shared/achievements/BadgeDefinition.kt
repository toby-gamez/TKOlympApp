package com.tkolymp.shared.achievements

import com.tkolymp.shared.language.AchievementStrings

enum class BadgeCategory { CAMP, MEMBERSHIP, ATTENDANCE }

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
    )

    fun byId(id: String): BadgeDefinition? = all.firstOrNull { it.id == id }
}
