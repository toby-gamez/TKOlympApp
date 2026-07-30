package com.tkolymp.shared

import com.tkolymp.shared.announcements.AnnouncementServiceImpl
import com.tkolymp.shared.auth.AuthService
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.campschedule.CampScheduleReminderService
import com.tkolymp.shared.campschedule.CampScheduleService
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.competitions.CompetitionService
import com.tkolymp.shared.feedback.FeedbackService
import com.tkolymp.shared.html.HtmlFormatter
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.network.GraphQlClientImpl
import com.tkolymp.shared.network.NetworkMonitorIos
import com.tkolymp.shared.notification.NotificationSchedulerIos
import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.shared.notification.NotificationStorage
import com.tkolymp.shared.payments.PaymentService
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.personalevents.PersonalEventService
import com.tkolymp.shared.storage.AnnouncementBadgeStorage
import com.tkolymp.shared.storage.CalendarPreferenceStorage
import com.tkolymp.shared.storage.LanguageStorage
import com.tkolymp.shared.storage.OfflineDataStorageIos
import com.tkolymp.shared.storage.OnboardingStorage
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.storage.UserStorage
import com.tkolymp.shared.sync.OfflineSyncManager
import com.tkolymp.shared.systemcalendar.SystemCalendarService
import com.tkolymp.shared.user.UserService
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.certificates.CertificatePinner
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

// Certificate pins for api.rozpisovnik.cz (same as Android OkHttp pins)
private val certPinner: CertificatePinner = CertificatePinner.Builder()
    .add("api.rozpisovnik.cz", "sha256/Q5SDlsyebwSuLU2EROPHxw0YP4+HhbPYfRZBMLFAqNo=")
    .add("api.rozpisovnik.cz", "sha256/s/tdAOmUzd8syaTuqfgGvFcn6DzA5Cmb+Vby1ST+U3Y=")
    .build()

// Feedback ("Report a bug" / "Suggest a feature") posts to Tobiso.Web, a separate backend from the club GraphQL API.
private const val FEEDBACK_BASE_URL = "https://www.tobiso.com/api"

suspend fun initNetworking(baseUrl: String, tenantId: String = "1") {
    val storage = TokenStorage("")

    val client = HttpClient(Darwin) {
        engine {
            handleChallenge { session, task, challenge, completionHandler ->
                certPinner.invoke(session, task, challenge, completionHandler)
            }
        }
        install(ContentNegotiation) {
            json(AppJson)
        }
    }

    var authRef: AuthService? = null
    val gql = GraphQlClientImpl(client, baseUrl, tenantId, tokenProvider = { authRef?.getToken() })
    val auth = AuthService(storage, gql)
    authRef = auth

    val cache = CacheService()
    val eventSvc = com.tkolymp.shared.event.EventService(gql, cache)
    val announcementSvc = AnnouncementServiceImpl(gql, cache)
    val userStorage = UserStorage("")
    val userSvc = UserService(gql, userStorage)
    val clubSvc = ClubService(gql, cache)
    val peopleSvc = PeopleService(gql, cache)
    val paymentSvc = PaymentService(gql, cache)
    val notificationStorage = NotificationStorage("")
    val notificationScheduler = NotificationSchedulerIos()
    val notificationSvc = NotificationService(notificationStorage, notificationScheduler, eventSvc)

    val offlineDataStorage = OfflineDataStorageIos()
    val networkMonitor = NetworkMonitorIos()
    val personalEventService = PersonalEventService(offlineDataStorage, notificationScheduler, notificationStorage)
    val competitionSvc = CompetitionService(gql, cache)
    val offlineSyncManager = OfflineSyncManager(
        eventSvc, announcementSvc, peopleSvc, offlineDataStorage,
        networkMonitor, userSvc, notificationSvc, clubSvc, paymentSvc, competitionSvc
    )
    val campScheduleSvc = CampScheduleService(offlineDataStorage)
    val campScheduleReminderSvc = CampScheduleReminderService(offlineDataStorage, notificationScheduler, campScheduleSvc, notificationStorage)
    val feedbackSvc = FeedbackService(client, FEEDBACK_BASE_URL, platformLabel = "TKOlympApp iOS")

    val container = AppContainer(
        tokenStorage = storage,
        graphQlClient = gql,
        authService = auth,
        cacheService = cache,
        eventService = eventSvc,
        userStorage = userStorage,
        userService = userSvc,
        announcementService = announcementSvc,
        peopleService = peopleSvc,
        clubService = clubSvc,
        paymentService = paymentSvc,
        notificationStorage = notificationStorage,
        notificationScheduler = notificationScheduler,
        notificationService = notificationSvc,
        onboardingStorage = OnboardingStorage(""),
        languageStorage = LanguageStorage(""),
        calendarPreferenceStorage = CalendarPreferenceStorage(""),
        systemCalendarService = SystemCalendarService(""),
        offlineDataStorage = offlineDataStorage,
        personalEventService = personalEventService,
        networkMonitor = networkMonitor,
        offlineSyncManager = offlineSyncManager,
        announcementBadgeStorage = AnnouncementBadgeStorage(""),
        competitionService = competitionSvc,
        campScheduleService = campScheduleSvc,
        campScheduleReminderService = campScheduleReminderSvc,
        feedbackService = feedbackSvc,
    )

    ServiceLocator.init(container)
    auth.initialize()
}
