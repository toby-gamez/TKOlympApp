package com.tkolymp.shared.language

/**
 * All UI strings for the application.
 * Each language provides an instance of this class.
 * Usage: AppStrings.current.overview
 */
data class Strings(
    // --- Navigation / bottom bar ---
    val overview: String,
    val calendar: String,
    val board: String,
    val events: String,
    val other: String,

    // --- OtherScreen sections ---
    val membersAndClub: String,
    val appSection: String,
    val people: String,
    val trainersAndSpaces: String,
    val trainingGroups: String,
    val leaderboard: String,
    val languages: String,
    val aboutApp: String,
    val notificationSettings: String,
    val privacyPolicy: String,
    val myAccount: String,

    // --- Common actions ---
    val back: String,
    val save: String,
    val saveChanges: String,
    val cancel: String,
    val delete: String,
    val confirm: String,
    val ok: String,
    val retry: String,
    val logout: String,
    val confirmLogoutText: String,
    val edit: String,
    val showLess: String,
    val showAll: String,
    val noData: String,
    val loading: String,
    val error: String,
    val search: String,
    val all: String,
    val yes: String,
    val no: String,

    // --- Auth / Login ---
    val emailOrUsername: String,
    val password: String,
    val newPassword: String,
    val confirmPassword: String,
    val passwordTooShort: String,
    val login: String,
    val changePassword: String,

    // --- Profile / Person ---
    val myProfile: String,
    val editPersonalData: String,
    val changePersonalData: String,
    val firstName: String,
    val lastName: String,
    val birthDate: String,
    val gender: String,
    val nationality: String,
    val phone: String,
    val mobile: String,
    val email: String,
    val cstsId: String,
    val wdsfId: String,
    val personalId: String,
    val prefixTitle: String,
    val suffixTitle: String,
    val aboutMe: String,
    val basicInfo: String,
    val personalData: String,
    val otherDetails: String,
    val person: String,
    val personNotFound: String,
    val activeCouple: String,
    val noCouples: String,
    val trainer: String,
    val trainers: String,

    // --- Address ---
    val address: String,
    val addressNotAvailable: String,
    val street: String,
    val city: String,
    val zip: String,
    val district: String,
    val region: String,

    // --- Events ---
    val event: String,
    val eventDates: String,
    val aboutEvent: String,
    val upcomingEvents: String,
    val noEventsPlanned: String,
    val noPastEvents: String,
    val noEventToShow: String,
    val errorLoadingEvents: String,
    val registrationOpen: String,
    val registrationClosed: String,
    val registeredCount: String,
    val capacity: String,
    val notesAllowed: String,
    val isPublic: String,
    val isVisible: String,
    val eventType: String,
    val dateCount: String,

    // --- Registration ---
    val registration: String,
    val confirmRegistration: String,
    val deleteRegistration: String,
    val deleteSelectedRegistration: String,
    val confirmDeleteRegistration: String,
    val confirmDeleteRule: String,
    val registerAnother: String,
    val register: String,
    val selectRegistration: String,
    val selectWhomToRegister: String,
    val registrationNote: String,
    val noteOptional: String,
    val noRegistrationSelected: String,
    val editRegistration: String,
    val participants: String,
    val noParticipants: String,

    // --- Announcements / Board ---
    val announcements: String,
    val noAnnouncementsToShow: String,
    val errorLoadingAnnouncements: String,

    // --- Notifications ---
    val notificationsGloballyEnabled: String,
    val timeBeforeEvent: String,
    val ruleName: String,
    val deleteRule: String,
    val noRules: String,
    val orPickFromValues: String,

    // --- People / Groups ---
    val searchByName: String,
    val alphabetically: String,
    val mine: String,
    val noGroupsToShow: String,
    val noPeopleToShow: String,
    val noTrainers: String,
    val noTrainingSpaces: String,
    val trainingSpaces: String,

    // --- Timeline ---
    val timeline: String,
    val today: String,
    val nothingPlanned: String,

    // --- Import / Export ---
    val exportJson: String,
    val importJson: String,

    // --- Leaderboard ---
    val leaderboardTitle: String,

    // --- Language screen ---
    val selectLanguage: String,

    // --- Dates ---
    val tomorrow: String,

    // --- Overview ---
    val upcomingTrainings: String,
    val upcomingCamps: String,
    val fromTheBoard: String,
    val more: String,
    val browseOthers: String,

    // --- Board tabs ---
    val news: String,
    val permanentBoard: String,

    // --- Events / Calendar tabs ---
    val planned: String,
    val past: String,
    val lessonLabel: String,

    // --- Auth ---
    val forgotPassword: String,

    // --- People ---
    val birthdays: String,

    // --- Event details ---
    val place: String,
    val term: String,
    val targetGroups: String,

    // --- Announcements ---
    val announcement: String,
    val noAnnouncementToShow: String,

    // --- Profile ---
    val contacts: String,
    val bioNotAvailable: String,
    val noActiveCouples: String,
    val externalIdSection: String,
    val dateFrom: String,
    val dateTo: String,

    // --- Notifications settings ---
    val addRule: String,
    val editRuleTitle: String,
    val globallyEnabled: String,
    val noRulesDescription: String,
    val allEventsFilter: String,
    val filterTypeLabel: String,
    val ruleNameLabel: String,
    val timeAheadLabel: String,
    val minutesUnit: String,
    val hoursUnit: String,
    val exportSuccessful: String,
    val exportFailed: String,
    val importSuccessful: String,
    val importFailed: String,
    val deleteRuleConfirmTitle: String,
    val deleteRuleConfirmText: String,
    val allLocations: String,
    val allTrainers: String,
    val allTypes: String,
    val minutesBefore: String,

    // --- Misc ---
    val createdAt: String,
    val remainingLessons: String,
    val editLessonClaims: String,
    val lessonDemand: String,
    val appVersion: String,
    val licenseInfo: String,

    // --- Event types ---
    val eventTypeLesson: String,
    val eventTypeCamp: String,
    val eventTypeGroup: String,
    val eventTypeReservation: String,
    val eventTypeHoliday: String,
    val errorPrefix: String,
    val selectRegistrant: String,
    val selectTrainersAndLessons: String,
    val confirmRegistrationTitle: String,
    val selectToDelete: String,
    val deleteRegistrationConfirmTitle: String,
    val deleteRegistrationConfirmText: String,
    val noRegistrationOwned: String,

    // --- Gender ---
    val genderMale: String,
    val genderFemale: String,
    val genderUnspecified: String,

    // --- Extended profile fields ---
    val workPhone: String,
    val username: String,
    val passportNumber: String,
    val idNumber: String,
    val conscriptionNumber: String,
    val orientationNumber: String,

    // --- Misc 2 ---
    val author: String,
    val updatedAt: String,
    val rule: String,

    // --- Calendar view ---
    val next: String,
    val previous: String,
    val viewModeDay: String,
    val viewModeThreeDays: String,
    val viewModeWeek: String,
    val freeLesson: String,

    // --- Onboarding ---
    val onboardingTitle1: String,
    val onboardingDesc1: String,
    val onboardingTitle2: String,
    val onboardingDesc2: String,
    val onboardingTitle3: String,
    val onboardingDesc3: String,
    val start: String,

    // --- Dialogs / Validation ---
    val passwordMinLength: String,
    val passwordsMismatch: String,
    val changePasswordFailed: String,
    val invalidEmail: String,
    val saveFailed: String,
    val dataSaved: String,
    val cannotDetermineUserId: String,
    val selectDate: String,
    val selected: String,

    // --- Error messages (ViewModels) ---
    val errorLogin: String,
    val errorLoginPersonId: String,
    val errorLoadingLogin: String,
    val errorLoadingOverview: String,
    val errorLoading: String,
    val errorLoadingData: String,
    val errorLoadingEvent: String,
    val errorLoadingLeaderboard: String,
    val errorAnnouncementNotFound: String,
    val errorLoadingSettings: String,
    val errorUpdating: String,
    val errorLoadingPeople: String,
    val errorLoadingPerson: String,
    val errorLoadingProfile: String,
    val errorLoadingNames: String,
    val errorLoadingClub: String,
    val couple: String,

    // --- About screen ---
    val about: AboutStrings,

    // --- Privacy policy screen ---
    val privacy: PrivacyStrings,
)
