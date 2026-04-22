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
import com.tkolymp.shared.storage.CalendarPreferenceStorage
import com.tkolymp.shared.storage.LanguageStorage
import com.tkolymp.shared.storage.OnboardingStorage
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.storage.UserStorage
import com.tkolymp.shared.systemcalendar.SystemCalendarService
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.payments.PaymentService
import com.tkolymp.shared.storage.OfflineDataStorage
import com.tkolymp.shared.network.NetworkMonitor
import com.tkolymp.shared.sync.OfflineSyncManager
import com.tkolymp.shared.personalevents.PersonalEventService

/**
 * Holds all application-level service instances with explicit constructor injection.
 * Created once per app lifecycle in the platform initializer (e.g. PlatformNetwork.kt)
 * and registered via [ServiceLocator.init].
 */
class AppContainer(
    val tokenStorage: TokenStorage,
    val graphQlClient: IGraphQlClient,
    val authService: IAuthService,
    val cacheService: CacheService,
    val eventService: IEventService,
    val userStorage: UserStorage,
    val userService: UserService,
    val announcementService: IAnnouncementService,
    val peopleService: PeopleService,
    val clubService: ClubService,
    val paymentService: PaymentService,
    val notificationStorage: NotificationStorage,
    val notificationScheduler: INotificationScheduler,
    val notificationService: NotificationService,
    val onboardingStorage: OnboardingStorage,
    val languageStorage: LanguageStorage,
    val calendarPreferenceStorage: CalendarPreferenceStorage,
    val systemCalendarService: SystemCalendarService,
    val offlineDataStorage: OfflineDataStorage,
    val personalEventService: PersonalEventService,
    val networkMonitor: NetworkMonitor,
    val offlineSyncManager: OfflineSyncManager,
)
