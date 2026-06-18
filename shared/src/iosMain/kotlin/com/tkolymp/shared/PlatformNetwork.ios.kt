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
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecCertificateCopyKey
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyGetBlockSize
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustGetCertificateCount
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.SecKeyRef
import platform.Security.SecTrustRef
import platform.darwin.UInt8

// Certificate pins for api.rozpisovnik.cz (same as Android OkHttp pins)
// These are SHA-256/Base64 of the SubjectPublicKeyInfo (SPKI) DER encoding.
private val PINNED_SPKI_HASHES = setOf(
    "Im/lk9HBHiZ1ldzskHrfWIx2FVffonmmSwCb607rwP4=",  // leaf certificate
    "iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU="   // Let's Encrypt E8 intermediate
)

// SPKI ASN.1 DER header for RSA 2048
private val RSA2048_SPKI_HEADER = byteArrayOf(
    0x30.toByte(), 0x82.toByte(), 0x01.toByte(), 0x22.toByte(),
    0x30.toByte(), 0x0d.toByte(), 0x06.toByte(), 0x09.toByte(),
    0x2a.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(),
    0xf7.toByte(), 0x0d.toByte(), 0x01.toByte(), 0x01.toByte(),
    0x01.toByte(), 0x05.toByte(), 0x00.toByte(), 0x03.toByte(),
    0x82.toByte(), 0x01.toByte(), 0x0f.toByte(), 0x00.toByte()
)

// SPKI ASN.1 DER header for EC P-256
private val ECP256_SPKI_HEADER = byteArrayOf(
    0x30.toByte(), 0x59.toByte(), 0x30.toByte(), 0x13.toByte(),
    0x06.toByte(), 0x07.toByte(), 0x2a.toByte(), 0x86.toByte(),
    0x48.toByte(), 0xce.toByte(), 0x3d.toByte(), 0x02.toByte(),
    0x01.toByte(), 0x06.toByte(), 0x08.toByte(), 0x2a.toByte(),
    0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(),
    0x03.toByte(), 0x01.toByte(), 0x07.toByte(), 0x03.toByte(),
    0x42.toByte(), 0x00.toByte()
)

@OptIn(ExperimentalForeignApi::class)
private fun spkiHashBase64(key: SecKeyRef): String? {
    val keyData = SecKeyCopyExternalRepresentation(key, null) as? NSData ?: return null
    val blockSize = SecKeyGetBlockSize(key)

    // Choose header based on key size (RSA 2048 = 256 bytes, EC P-256 = 65 bytes uncompressed)
    val header = when (blockSize) {
        256UL -> RSA2048_SPKI_HEADER   // RSA 2048
        65UL  -> ECP256_SPKI_HEADER    // EC P-256 uncompressed point
        else  -> ECP256_SPKI_HEADER    // fallback to EC (Let's Encrypt uses EC)
    }

    // Build SPKI = header + raw key bytes
    val keyBytes = keyData.bytes?.let {
        val len = keyData.length.toInt()
        val ba = ByteArray(len)
        for (i in 0 until len) {
            ba[i] = (it as kotlinx.cinterop.CPointer<UInt8>)[i].toByte()
        }
        ba
    } ?: return null

    val spki = header + keyBytes

    // SHA-256 the SPKI bytes
    val digest = memScoped {
        val out = allocArray<UInt8>(CC_SHA256_DIGEST_LENGTH)
        val spkiNsData = NSMutableData.create(length = spki.size.toULong())!!
        spki.forEachIndexed { i, b ->
            (spkiNsData.mutableBytes as kotlinx.cinterop.CPointer<UInt8>)[i] = b.toUByte()
        }
        CC_SHA256(spkiNsData.bytes, spkiNsData.length.convert(), out)
        ByteArray(CC_SHA256_DIGEST_LENGTH) { out[it].toByte() }
    }

    // Base64-encode the digest
    val digestData = NSMutableData.create(length = digest.size.toULong())!!
    digest.forEachIndexed { i, b ->
        (digestData.mutableBytes as kotlinx.cinterop.CPointer<UInt8>)[i] = b.toUByte()
    }
    return (digestData as NSData).base64EncodedStringWithOptions(0u)
}

@OptIn(ExperimentalForeignApi::class)
private fun validateServerTrust(trust: SecTrustRef): Boolean {
    val certCount = SecTrustGetCertificateCount(trust)
    for (i in 0L until certCount) {
        val cert = SecTrustGetCertificateAtIndex(trust, i) ?: continue
        val key = SecCertificateCopyKey(cert) ?: continue
        val hash = spkiHashBase64(key) ?: continue
        if (hash in PINNED_SPKI_HASHES) return true
    }
    return false
}

@OptIn(ExperimentalForeignApi::class)
suspend fun initNetworking(baseUrl: String, tenantId: String = "1") {
    val storage = TokenStorage(null)

    val client = HttpClient(Darwin) {
        engine {
            handleChallenge { _, _, challenge, completionHandler ->
                if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
                    val trust = challenge.protectionSpace.serverTrust
                    if (trust != null && validateServerTrust(trust)) {
                        val credential = NSURLCredential.credentialForTrust(trust)
                        completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                    } else {
                        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                    }
                } else {
                    completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                }
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
    val announcementSvc = AnnouncementServiceImpl(cache)
    val userStorage = UserStorage(null)
    val userSvc = UserService(gql, userStorage)
    val clubSvc = ClubService(gql, cache)
    val peopleSvc = PeopleService(gql, cache)
    val paymentSvc = PaymentService(gql, cache)
    val notificationStorage = NotificationStorage(null)
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
        onboardingStorage = OnboardingStorage(null),
        languageStorage = LanguageStorage(null),
        calendarPreferenceStorage = CalendarPreferenceStorage(null),
        systemCalendarService = SystemCalendarService(null),
        offlineDataStorage = offlineDataStorage,
        personalEventService = personalEventService,
        networkMonitor = networkMonitor,
        offlineSyncManager = offlineSyncManager,
        announcementBadgeStorage = AnnouncementBadgeStorage(null),
        competitionService = competitionSvc,
    )

    ServiceLocator.init(container)
    auth.initialize()
}
