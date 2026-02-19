package com.tkolymp.shared

import android.content.Context
import com.tkolymp.shared.auth.AuthService
import com.tkolymp.shared.network.GraphQlClientImpl
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.notification.NotificationStorage
import com.tkolymp.shared.notification.NotificationSchedulerAndroid
import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.shared.event.EventService
import com.tkolymp.shared.storage.UserStorage
import com.tkolymp.shared.storage.OnboardingStorage
import com.tkolymp.shared.user.UserService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.*

suspend fun initNetworking(context: Context, baseUrl: String) {
    val storage = TokenStorage(context)

    val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
            }
        }

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val gql = GraphQlClientImpl(client, baseUrl)
    val auth = AuthService(storage, gql)
    val cache = com.tkolymp.shared.cache.CacheService()

    // register cache early so default parameters that reference ServiceLocator.cacheService
    // do not throw UninitializedPropertyAccessException
    ServiceLocator.cacheService = cache

    val eventSvc = EventService(gql, cache)
    val announcementSvc = com.tkolymp.shared.announcements.AnnouncementServiceImpl(cache)
    val userStorage = UserStorage(context)
    val userSvc = UserService(gql, userStorage)
    val clubSvc = com.tkolymp.shared.club.ClubService(gql)
    val notificationStorage = NotificationStorage(context)
    val notificationScheduler = NotificationSchedulerAndroid(context)
    val notificationSvc = NotificationService(notificationStorage, notificationScheduler, eventSvc)

    ServiceLocator.graphQlClient = gql
    // ensure peopleService is available like other services
    ServiceLocator.peopleService = com.tkolymp.tkolympapp.PeopleService()
    ServiceLocator.authService = auth
    ServiceLocator.tokenStorage = storage
    ServiceLocator.eventService = eventSvc
    ServiceLocator.cacheService = cache
    ServiceLocator.notificationStorage = notificationStorage
    ServiceLocator.notificationScheduler = notificationScheduler
    ServiceLocator.notificationService = notificationSvc
    ServiceLocator.announcementService = announcementSvc
    ServiceLocator.userStorage = userStorage
    ServiceLocator.userService = userSvc
    ServiceLocator.clubService = clubSvc
    ServiceLocator.onboardingStorage = OnboardingStorage(context)

    auth.initialize()
}
