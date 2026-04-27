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
    val freeLessons: FreeLessonsStrings = FreeLessonsStrings(),
    val dialogs: DialogStrings,
    val errorMessages: ErrorMessageStrings,
    val about: AboutStrings,
    val privacy: PrivacyStrings,
    val stats: StatsStrings,
    val settings: SettingsStrings,
    val personalEvents: PersonalEventStrings = PersonalEventStrings(),
) {
    // Backwards-compatible flat accessors for code that still expects
    // `AppStrings.current.someKey` instead of grouped access like
    // `AppStrings.current.group.someKey`.
    // Add mappings here as needed when migrating usages to grouped structs.

    // Onboarding
    val onboardingTitle1: String get() = onboarding.onboardingTitle1
    val onboardingDesc1: String get() = onboarding.onboardingDesc1
    val onboardingTitle2: String get() = onboarding.onboardingTitle2
    val onboardingDesc2: String get() = onboarding.onboardingDesc2
    val onboardingTitle3: String get() = onboarding.onboardingTitle3
    val onboardingDesc3: String get() = onboarding.onboardingDesc3
    val start: String get() = onboarding.start

    // Common actions (confirm/cancel/ok/save/back...)
    val confirm: String get() = commonActions.confirm
    val cancel: String get() = commonActions.cancel
    val ok: String get() = commonActions.ok
    val save: String get() = commonActions.save
    val back: String get() = commonActions.back

    // Dialogs / auth related
    val passwordMinLength: String get() = dialogs.passwordMinLength
    val passwordsMismatch: String get() = dialogs.passwordsMismatch
    val changePasswordFailed: String get() = dialogs.changePasswordFailed
    val invalidEmail: String get() = dialogs.invalidEmail
    val saveFailed: String get() = dialogs.saveFailed
    val cannotDetermineUserId: String get() = dialogs.cannotDetermineUserId
    val selectDate: String get() = dialogs.selectDate
    val passwordTooShort: String get() = auth.passwordTooShort
    val changePassword: String get() = auth.changePassword
    // Auth (flat access)
    val emailOrUsername: String get() = auth.emailOrUsername
    val password: String get() = auth.password
    val newPassword: String get() = auth.newPassword
    val confirmPassword: String get() = auth.confirmPassword
    val login: String get() = auth.login
    val forgotPassword: String get() = auth.forgotPassword
    val loginSubtitle: String get() = auth.loginSubtitle

    // Profile-related (flattened)
    val editPersonalData: String get() = profile.editPersonalData
    val firstName: String get() = profile.firstName
    val lastName: String get() = profile.lastName
    val prefixTitle: String get() = profile.prefixTitle
    val suffixTitle: String get() = profile.suffixTitle
    val aboutMe: String get() = profile.aboutMe
    val email: String get() = profile.email
    val cstsId: String get() = profile.cstsId
    val wdsfId: String get() = profile.wdsfId
    val personalId: String get() = profile.personalId
    val nationality: String get() = profile.nationality
    val contacts: String get() = profile.contacts
    val phone: String get() = profile.phone
    val mobile: String get() = profile.mobile
    val birthDate: String get() = profile.birthDate

    // Address
    val street: String get() = address.street
    val city: String get() = address.city
    val region: String get() = address.region
    val district: String get() = address.district
    val zip: String get() = address.zip
    val addressLabel: String get() = address.address
    // Extended profile fields
    val conscriptionNumber: String get() = extendedProfile.conscriptionNumber
    val orientationNumber: String get() = extendedProfile.orientationNumber

    // Gender
    val genderMale: String get() = gender.genderMale
    val genderFemale: String get() = gender.genderFemale
    val genderUnspecified: String get() = gender.genderUnspecified

    // Calendar view
    val next: String get() = calendarView.next
    val previous: String get() = calendarView.previous
    companion object {
        lateinit var current: Strings
    }
}
