package com.tkolymp.shared.language

/**
 * All UI strings for the application, divided into thematic groups.
 * Each language provides an instance of this class.
 * Usage: AppStrings.current.navigation.overview
 */
data class Strings(
    val navigation: NavigationStrings,
    val otherScreen: OtherScreenStrings,
    val filters: FilterStrings,
    val commonActions: CommonActionStrings,
    val auth: AuthStrings,
    val profile: ProfileStrings,
    val address: AddressStrings,
    val events: EventStrings,
    val registration: RegistrationStrings,
    val announcements: AnnouncementStrings,
    val notifications: NotificationStrings,
    val people: PeopleStrings,
    val timeline: TimelineStrings,
    val importExport: ImportExportStrings,
    val leaderboard: LeaderboardStrings,
    val languageScreen: LanguageScreenStrings,
    val overview: OverviewStrings,
    val boardTabs: BoardTabStrings,
    val eventCalendarTabs: EventCalendarTabStrings,
    val gender: GenderStrings,
    val extendedProfile: ExtendedProfileFieldStrings,
    val misc: MiscStrings,
    val calendarView: CalendarViewStrings,
    val onboarding: OnboardingStrings,
    val dialogs: DialogStrings,
    val errorMessages: ErrorMessageStrings,
    val about: AboutStrings,
    val privacy: PrivacyStrings,
) {
    companion object {
        lateinit var current: Strings
    }
}
