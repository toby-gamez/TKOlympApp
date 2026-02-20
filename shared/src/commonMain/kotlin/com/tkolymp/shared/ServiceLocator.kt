package com.tkolymp.shared

import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.storage.UserStorage
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.announcements.IAnnouncementService
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.notification.NotificationStorage
import com.tkolymp.shared.notification.INotificationScheduler
import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.storage.OnboardingStorage

object ServiceLocator {
    lateinit var graphQlClient: IGraphQlClient
    lateinit var authService: IAuthService
    lateinit var tokenStorage: TokenStorage
    lateinit var eventService: IEventService
    lateinit var userStorage: UserStorage
    lateinit var userService: UserService
    lateinit var announcementService: IAnnouncementService
    lateinit var peopleService: PeopleService
    lateinit var clubService: ClubService
    lateinit var cacheService: CacheService

    // Notification-related plumbing (initialized on platform)
    lateinit var notificationStorage: NotificationStorage
    lateinit var notificationScheduler: INotificationScheduler
    lateinit var notificationService: NotificationService

    // Onboarding
    lateinit var onboardingStorage: OnboardingStorage
}
