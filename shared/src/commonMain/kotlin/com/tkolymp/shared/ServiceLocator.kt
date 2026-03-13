package com.tkolymp.shared

import com.tkolymp.shared.announcements.IAnnouncementService
import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.notification.INotificationScheduler
import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.shared.notification.NotificationStorage
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.storage.LanguageStorage
import com.tkolymp.shared.storage.OnboardingStorage
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.storage.UserStorage
import com.tkolymp.shared.user.UserService

/**
 * Thin read-only facade over [AppContainer]. All mutable state lives in a single
 * @Volatile reference; no individual lateinit vars. Thread-safe: the volatile
 * write in [init] is visible to all threads that read [container] afterwards.
 *
 * Call [init] exactly once (in the platform initializer) before any ViewModel
 * or service accesses the properties below.
 */
object ServiceLocator {
    @Volatile private var _container: AppContainer? = null

    val container: AppContainer
        get() = _container ?: error(
            "AppContainer is not initialized. Call ServiceLocator.init() in the platform initializer."
        )

    fun init(container: AppContainer) {
        _container = container
    }

    val graphQlClient: IGraphQlClient get() = container.graphQlClient
    val authService: IAuthService get() = container.authService
    val tokenStorage: TokenStorage get() = container.tokenStorage
    val eventService: IEventService get() = container.eventService
    val userStorage: UserStorage get() = container.userStorage
    val userService: UserService get() = container.userService
    val announcementService: IAnnouncementService get() = container.announcementService
    val peopleService: PeopleService get() = container.peopleService
    val clubService: ClubService get() = container.clubService
    val cacheService: CacheService get() = container.cacheService
    val notificationStorage: NotificationStorage get() = container.notificationStorage
    val notificationScheduler: INotificationScheduler get() = container.notificationScheduler
    val notificationService: NotificationService get() = container.notificationService
    val onboardingStorage: OnboardingStorage get() = container.onboardingStorage
    val languageStorage: LanguageStorage get() = container.languageStorage
}
