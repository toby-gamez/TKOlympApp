package com.tkolymp.tkolympapp

import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.shared.payments.PaymentService
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.sync.OfflineKeys
import com.tkolymp.shared.sync.OfflineSyncManager
import com.tkolymp.shared.user.UserService
import com.tkolymp.tkolympapp.fakes.FakeAnnouncementService
import com.tkolymp.tkolympapp.fakes.FakeCompetitionService
import com.tkolymp.tkolympapp.fakes.FakeEventService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeNetworkMonitor
import com.tkolymp.tkolympapp.fakes.FakeNotificationScheduler
import com.tkolymp.tkolympapp.fakes.FakeNotificationStorage
import com.tkolymp.tkolympapp.fakes.FakeOfflineDataStorage
import com.tkolymp.tkolympapp.fakes.FakeUserStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class OfflineSyncManagerTest {

    private fun makeManager(
        storage: FakeOfflineDataStorage = FakeOfflineDataStorage(),
        connected: Boolean = true,
        announcementService: com.tkolymp.shared.announcements.IAnnouncementService = FakeAnnouncementService(),
        eventService: com.tkolymp.shared.event.IEventService = FakeEventService(),
        competitionService: com.tkolymp.shared.competitions.ICompetitionService = FakeCompetitionService()
    ): OfflineSyncManager {
        val fakeClient = FakeGraphQlClient(buildJsonObject { })
        return OfflineSyncManager(
            eventService = eventService,
            announcementService = announcementService,
            peopleService = PeopleService(client = fakeClient, cache = CacheService()),
            offlineDataStorage = storage,
            networkMonitor = FakeNetworkMonitor(connected),
            userService = UserService(client = fakeClient, storage = FakeUserStorage()),
            notificationService = NotificationService(
                storage = FakeNotificationStorage(),
                scheduler = FakeNotificationScheduler(),
                eventService = eventService
            ),
            clubService = ClubService(client = fakeClient, cache = CacheService()),
            paymentService = PaymentService(client = fakeClient, cache = CacheService()),
            competitionService = competitionService
        )
    }

    @Test
    fun `syncAll is skipped when last sync is within debounce window`() = runTest {
        val storage = FakeOfflineDataStorage()
        storage.save(OfflineKeys.META_SCHEMA_VERSION, OfflineKeys.SCHEMA_VERSION.toString())
        storage.save(OfflineKeys.META_LAST_SYNC, Clock.System.now().minus(10.seconds).toString())

        makeManager(storage = storage).syncAll()

        val calendarKeys = storage.allKeys().filter { it.startsWith(OfflineKeys.CAL_PREFIX) }
        assertTrue(calendarKeys.isEmpty(), "Expected no calendar keys after debounced sync, found: $calendarKeys")
    }

    @Test
    fun `syncAll saves last sync time even when one domain service throws`() = runTest {
        val storage = FakeOfflineDataStorage()
        storage.save(OfflineKeys.META_SCHEMA_VERSION, OfflineKeys.SCHEMA_VERSION.toString())

        makeManager(
            storage = storage,
            announcementService = FakeAnnouncementService(throwOnFetch = true)
        ).syncAll()

        assertNotNull(storage.load(OfflineKeys.META_LAST_SYNC), "Last sync time should be saved despite announcement failure")
    }

    @Test
    fun `migrateIfNeeded clears stale offline data when schema version mismatches`() = runTest {
        val storage = FakeOfflineDataStorage()
        storage.save(OfflineKeys.META_SCHEMA_VERSION, "0")
        storage.save(OfflineKeys.PEOPLE, "[\"stale\"]")
        storage.save(OfflineKeys.PAYMENTS, "[\"stale\"]")

        makeManager(storage = storage, connected = false).syncAll()

        assertNull(storage.load(OfflineKeys.PEOPLE), "Stale people data should be cleared after migration")
        assertNull(storage.load(OfflineKeys.PAYMENTS), "Stale payments data should be cleared after migration")
        assertEquals(
            OfflineKeys.SCHEMA_VERSION.toString(),
            storage.load(OfflineKeys.META_SCHEMA_VERSION),
            "New schema version should be persisted"
        )
    }

    @Test
    fun `migrateIfNeeded is a no-op when schema version already matches`() = runTest {
        val storage = FakeOfflineDataStorage()
        storage.save(OfflineKeys.META_SCHEMA_VERSION, OfflineKeys.SCHEMA_VERSION.toString())
        storage.save(OfflineKeys.PEOPLE, "[\"valid\"]")

        makeManager(storage = storage, connected = false).syncAll()

        assertEquals("[\"valid\"]", storage.load(OfflineKeys.PEOPLE), "Existing data should not be cleared when schema matches")
    }

    @Test
    fun `downloadAll throws when network is unavailable`() = runTest {
        assertFailsWith<Exception> {
            makeManager(connected = false).downloadAll()
        }
    }
}
