package com.tkolymp.tkolympapp

import com.tkolymp.shared.user.UserService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeUserStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UserServiceLinkedPersonsTest {
    private val response = Json.parseToJsonElement(
        """{"data":{"getCurrentUser":{"userProxiesList":[
            {"person":{"id":"1","firstName":"Anna","lastName":"Nova"}},
            {"person":{"id":"2","firstName":"Petr","lastName":"Nova"}}]}}}"""
    )

    @Test
    fun storesAllLinkedPersonsAndDefaultsToFirst() = runTest {
        val service = UserService(FakeGraphQlClient(response), FakeUserStorage())
        assertEquals("1", service.fetchAndStorePersonId())
        assertEquals(listOf("1", "2"), service.getLinkedPersons().map { it.id })
        assertEquals("Petr Nova", service.getLinkedPersons()[1].name)
    }

    @Test
    fun refetchKeepsActivePerson() = runTest {
        val storage = FakeUserStorage().also { it.savePersonId("2") }
        val service = UserService(FakeGraphQlClient(response), storage)
        assertEquals("2", service.fetchAndStorePersonId())
    }

    @Test
    fun staleActivePersonFallsBackToFirst() = runTest {
        val storage = FakeUserStorage().also { it.savePersonId("99") }
        val service = UserService(FakeGraphQlClient(response), storage)
        assertEquals("1", service.fetchAndStorePersonId())
    }

    @Test
    fun switchToUnlinkedPersonIsRejected() = runTest {
        val service = UserService(FakeGraphQlClient(response), FakeUserStorage())
        service.fetchAndStorePersonId()
        assertFalse(service.switchPerson("42"))
        assertEquals("1", service.getCachedPersonId())
    }

    @Test
    fun inactiveProxiesAreIgnored() = runTest {
        val resp = Json.parseToJsonElement(
            """{"data":{"getCurrentUser":{"userProxiesList":[
                {"status":"EXPIRED","person":{"id":"1","firstName":"Old","lastName":"Link"}},
                {"status":"PENDING","person":{"id":"3","firstName":"Wait","lastName":"Ing"}},
                {"status":"ACTIVE","person":{"id":"2","firstName":"Petr","lastName":"Nova"}}]}}}"""
        )
        val service = UserService(FakeGraphQlClient(resp), FakeUserStorage())
        assertEquals("2", service.fetchAndStorePersonId())
        assertEquals(listOf("2"), service.getLinkedPersons().map { it.id })
    }
}
