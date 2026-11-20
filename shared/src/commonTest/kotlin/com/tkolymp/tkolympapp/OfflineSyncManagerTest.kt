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
        competitionService: com.tkolymp.shared.competitions.ICompetitionService = FakeCompetitionService(),
        response: kotlinx.serialization.json.JsonObject = buildJsonObject { }
    ): OfflineSyncManager {
        val fakeClient = FakeGraphQlClient(response)
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

    @Test
    fun `persistCouples saves each distinct couple from the synced people and loadCouple reads it back`() = runTest {
        val response = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"data":{"couple":{"id":"7","name":"Novak - Novakova","ageGroup":"ADULT","since":"2024-09-01T00:00:00","until":null,"status":"ACTIVE",
            "man":{"id":"1","firstName":"Jan","lastName":"Novak"},"woman":{"id":"2","firstName":"Eva","lastName":"Novakova"},
            "federationCompetitors":{"nodes":[{"federation":"csts","externalId":"69899","name":"Novak - Novakova","ageGroup":"Adult","latestResultAt":"2026-10-24",
              "categoryProgressesList":[{"points":"66","domesticFinals":2,"foreignFinals":0,"category":{"id":"5","name":"Dospeli A LAT"}}],
              "ranklistEntries":{"nodes":[{"ranking":92,"rankingTo":null,"points":"110","snapshot":{"asOfDate":"2026-11-17","ranklist":{"name":"Dospeli STT","federation":"csts","category":{"name":"Dospeli STT"}}}}]},
              "competitionResultsList":[
                {"ranking":26,"rankingTo":27,"pointGain":"56","isFinal":false,"competition":{"id":"1","startDate":"2026-10-24","participantsTotal":32,"category":{"name":"STL Dospeli STT"},"event":{"id":"10","name":"Olymp GALA","city":"Prostejov","country":"CZ"}}},
                {"ranking":1,"rankingTo":null,"pointGain":"38","isFinal":true,"competition":{"id":"2","startDate":"2026-11-21","participantsTotal":20,"category":{"name":"Dospeli A STT"},"event":{"id":"11","name":"Gradace","city":"Kromeriz","country":"CZ"}}}]}]}}}}"""
        ) as kotlinx.serialization.json.JsonObject
        val storage = FakeOfflineDataStorage()
        val manager = makeManager(storage = storage, response = response)
        val people = kotlinx.serialization.json.Json.parseToJsonElement(
            """[{"activeCouplesList":[{"id":"7"}],"allCouplesList":[{"id":"7"},{"id":""}]},{"activeCouplesList":[],"allCouplesList":[{"id":"7"}]}]"""
        ) as kotlinx.serialization.json.JsonArray

        manager.persistCouples(people)

        val couple = assertNotNull(manager.loadCouple("7"))
        assertEquals("Novak - Novakova", couple.name)
        assertEquals("Jan Novak", couple.man?.name)
        assertEquals(setOf(OfflineKeys.couple("7")), storage.allKeys().filter { it.startsWith(OfflineKeys.COUPLE_PREFIX) }.toSet())
        assertEquals(listOf("csts"), couple.federations.map { it.federation })
        assertEquals("69899", couple.federations.single().externalId)
        assertEquals("66", couple.progress.single().points)
        assertEquals(92, couple.rankings.single().ranking)
        assertEquals(listOf("11", "10"), couple.results.map { it.eventId }, "results are newest first")
        assertEquals(27, couple.results.last().rankingTo)
        assertNull(manager.loadCouple("99"))
    }
}
