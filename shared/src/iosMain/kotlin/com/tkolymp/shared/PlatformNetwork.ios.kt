package com.tkolymp.shared

import com.tkolymp.shared.announcements.AnnouncementServiceImpl
import com.tkolymp.shared.auth.AuthService
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.competitions.CompetitionService
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
    .add("api.rozpisovnik.cz", "sha256/Im/lk9HBHiZ1ldzskHrfWIx2FVffonmmSwCb607rwP4=")
    .add("api.rozpisovnik.cz", "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU=")
    .build()

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
    )

    ServiceLocator.init(container)
    auth.initialize()
}
