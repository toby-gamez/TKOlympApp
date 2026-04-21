package com.tkolymp.shared

import android.content.Context
import com.tkolymp.shared.Logger
import com.tkolymp.shared.AppContainer
import com.tkolymp.shared.auth.AuthService
import com.tkolymp.shared.network.GraphQlClientImpl
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.notification.NotificationStorage
import com.tkolymp.shared.notification.NotificationSchedulerAndroid
import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.shared.event.EventService
import com.tkolymp.shared.storage.UserStorage
import com.tkolymp.shared.storage.CalendarPreferenceStorage
import com.tkolymp.shared.storage.OnboardingStorage
import com.tkolymp.shared.systemcalendar.SystemCalendarService
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.storage.OfflineDataStorageAndroid
import com.tkolymp.shared.network.NetworkMonitorAndroid
import com.tkolymp.shared.sync.OfflineSyncManager
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.*
import okhttp3.CertificatePinner

// Certificate pins for api.rozpisovnik.cz
// Primary : leaf certificate public key (SHA-256/Base64)
// Backup  : Let's Encrypt E8 intermediate public key — use as fallback when the leaf is rotated
private val certificatePinner = CertificatePinner.Builder()
    .add("api.rozpisovnik.cz", "sha256/Im/lk9HBHiZ1ldzskHrfWIx2FVffonmmSwCb607rwP4=")  // leaf
    .add("api.rozpisovnik.cz", "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU=")  // Let's Encrypt E8 intermediate
    .build()

suspend fun initNetworking(context: Context, baseUrl: String, tenantId: String = "1") {
    val storage = TokenStorage(context)

    val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
                certificatePinner(certificatePinner)
            }
        }

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // Bootstrap: GraphQlClientImpl needs a token provider, but AuthService hasn't been
    // created yet. Capture AuthService reference via a mutable lambda that is closed
    // over after AuthService is constructed — avoids the circular ServiceLocator dependency.
    var authRef: AuthService? = null
    val gql = GraphQlClientImpl(client, baseUrl, tenantId, tokenProvider = { authRef?.getToken() })
    val auth = AuthService(storage, gql)
    authRef = auth  // close the loop

    val cache = com.tkolymp.shared.cache.CacheService()
    val eventSvc = EventService(gql, cache)
    val announcementSvc = com.tkolymp.shared.announcements.AnnouncementServiceImpl(cache)
    val userStorage = UserStorage(context)
    val userSvc = UserService(gql, userStorage)
    val clubSvc = com.tkolymp.shared.club.ClubService(gql, cache)
    val peopleSvc = com.tkolymp.shared.people.PeopleService(gql, cache)
    val paymentSvc = com.tkolymp.shared.payments.PaymentService(gql, cache)
    val notificationStorage = NotificationStorage(context)
    val notificationScheduler = NotificationSchedulerAndroid(context)
    val notificationSvc = NotificationService(notificationStorage, notificationScheduler, eventSvc)

    val offlineDataStorage = OfflineDataStorageAndroid(context)
    val networkMonitor = NetworkMonitorAndroid(context)
    val offlineSyncManager = OfflineSyncManager(eventSvc, announcementSvc, peopleSvc, offlineDataStorage, networkMonitor, userSvc, notificationSvc, clubSvc, paymentSvc)

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
        onboardingStorage = OnboardingStorage(context),
        languageStorage = com.tkolymp.shared.storage.LanguageStorage(context),
        calendarPreferenceStorage = CalendarPreferenceStorage(context),
        systemCalendarService = SystemCalendarService(context),
        offlineDataStorage = offlineDataStorage,
        networkMonitor = networkMonitor,
        offlineSyncManager = offlineSyncManager,
    )

    ServiceLocator.init(container)

    auth.initialize()
}
