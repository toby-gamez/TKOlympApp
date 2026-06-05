package com.tkolymp.tkolympapp

import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.payments.Money
import com.tkolymp.shared.payments.PaymentService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun makeClient(response: JsonElement): IGraphQlClient =
    object : IGraphQlClient {
        override suspend fun post(query: String, variables: JsonObject?): JsonElement = response
    }

private fun makeThrowingClient(): IGraphQlClient =
    object : IGraphQlClient {
        override suspend fun post(query: String, variables: JsonObject?): JsonElement =
            throw RuntimeException("Connection refused")
    }

class PaymentServiceTest {

    @Test
    fun `fetchPaymentDebtors parses amount in minor units`() = runTest {
        val response = buildJsonObject {
            put("data", buildJsonObject {
                put("paymentDebtorsList", buildJsonArray {
                    add(buildJsonObject {
                        put("id", JsonPrimitive("1"))
                        put("isUnpaid", JsonPrimitive(true))
                        put("paymentId", JsonPrimitive("p1"))
                        put("personId", JsonPrimitive("42"))
                        put("price", buildJsonObject {
                            put("amount", JsonPrimitive(1500.50))
                            put("currency", JsonPrimitive("CZK"))
                        })
                        put("payment", buildJsonObject { put("id", JsonPrimitive("p1")) })
                        put("person", buildJsonObject {
                            put("id", JsonPrimitive("42"))
                            put("firstName", JsonPrimitive("Jan"))
                            put("lastName", JsonPrimitive("Novák"))
                        })
                    })
                })
            })
        }

        val service = PaymentService(client = makeClient(response), cache = CacheService())
        val items = service.fetchPaymentDebtors()

        assertEquals(1, items.size)
        val price = items[0].price!!
        // 1500.50 CZK → 150050 haléřů (minor units)
        assertEquals(150050L, price.amount)
        assertEquals("CZK", price.currency)
    }

    @Test
    fun `fetchPaymentDebtors parses whole-number amount correctly`() = runTest {
        val response = buildJsonObject {
            put("data", buildJsonObject {
                put("paymentDebtorsList", buildJsonArray {
                    add(buildJsonObject {
                        put("id", JsonPrimitive("2"))
                        put("isUnpaid", JsonPrimitive(false))
                        put("paymentId", JsonPrimitive("p2"))
                        put("personId", JsonPrimitive("5"))
                        put("price", buildJsonObject {
                            put("amount", JsonPrimitive(200.0))
                            put("currency", JsonPrimitive("EUR"))
                        })
                        put("payment", buildJsonObject {})
                        put("person", buildJsonObject {})
                    })
                })
            })
        }

        val service = PaymentService(client = makeClient(response), cache = CacheService())
        val items = service.fetchPaymentDebtors()
        assertEquals(20000L, items[0].price?.amount)
    }

    @Test
    fun `fetchPaymentDebtors throws on network failure`() = runTest {
        val service = PaymentService(client = makeThrowingClient(), cache = CacheService())
        assertFailsWith<RuntimeException> {
            service.fetchPaymentDebtors()
        }
    }

    @Test
    fun `fetchPaymentDebtors returns empty list when paymentDebtorsList is absent`() = runTest {
        val response = buildJsonObject {
            put("data", buildJsonObject { })
        }
        val service = PaymentService(client = makeClient(response), cache = CacheService())
        assertTrue(service.fetchPaymentDebtors().isEmpty())
    }

    @Test
    fun `fetchDebtorsForPerson filters by personId`() = runTest {
        val response = buildJsonObject {
            put("data", buildJsonObject {
                put("paymentDebtorsList", buildJsonArray {
                    add(buildJsonObject {
                        put("id", JsonPrimitive("1")); put("isUnpaid", JsonPrimitive(true))
                        put("paymentId", JsonPrimitive("p1")); put("personId", JsonPrimitive("10"))
                        put("price", buildJsonObject { put("amount", JsonPrimitive(100.0)); put("currency", JsonPrimitive("CZK")) })
                        put("payment", buildJsonObject {}); put("person", buildJsonObject {})
                    })
                    add(buildJsonObject {
                        put("id", JsonPrimitive("2")); put("isUnpaid", JsonPrimitive(false))
                        put("paymentId", JsonPrimitive("p2")); put("personId", JsonPrimitive("20"))
                        put("price", buildJsonObject { put("amount", JsonPrimitive(50.0)); put("currency", JsonPrimitive("CZK")) })
                        put("payment", buildJsonObject {}); put("person", buildJsonObject {})
                    })
                })
            })
        }
        val service = PaymentService(client = makeClient(response), cache = CacheService())
        val forPerson10 = service.fetchDebtorsForPerson("10")
        assertEquals(1, forPerson10.size)
        assertEquals("1", forPerson10[0].id)
    }

    @Test
    fun `Money amount stores minor units and round-trips`() {
        val m = Money(amount = 99999L, currency = "CZK")
        assertEquals(99999L, m.amount)
        assertEquals(999.99, m.amount!! / 100.0, absoluteTolerance = 0.001)
    }
}
