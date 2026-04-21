package com.tkolymp.shared.payments

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

data class Money(val amount: Double?, val currency: String?)

data class PaymentSummary(
    val id: String?,
    val variableSymbol: String?,
    val specificSymbol: String?,
    val dueAt: String?,
    val status: String?
)

data class PersonSummary(val id: String?, val firstName: String?, val lastName: String?)

data class PaymentDebtorItem(
    val id: String?,
    val isUnpaid: Boolean?,
    val price: Money?,
    val paymentId: String?,
    val personId: String?,
    val payment: PaymentSummary?,
    val person: PersonSummary?,
    val raw: JsonElement?
)

class PaymentService(
    private val client: com.tkolymp.shared.network.IGraphQlClient = ServiceLocator.graphQlClient,
    private val cache: CacheService = ServiceLocator.cacheService
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchPaymentDebtors(): List<PaymentDebtorItem> {
        val cacheKey = "payment_debtors"
        try {
            val cached: List<PaymentDebtorItem>? = cache.get(cacheKey)
            if (cached != null) return cached
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}

        val query = """
            query {
              paymentDebtorsList {
                id
                isUnpaid
                price { amount currency }
                paymentId
                personId
                payment { id variableSymbol specificSymbol dueAt status }
                person { id firstName lastName }
              }
            }
        """.trimIndent()

        val resp = try { client.post(query, null) } catch (e: CancellationException) { throw e } catch (_: Exception) { return emptyList() }
        val root = (resp as? JsonObject)?.get("data")?.jsonObject ?: return emptyList()
        val arr = (root["paymentDebtorsList"] as? JsonArray) ?: JsonArray(emptyList())

        val list = arr.mapNotNull { el ->
            try {
                val obj = el as? JsonObject ?: return@mapNotNull null
                val priceObj = obj["price"] as? JsonObject
                val price = priceObj?.let { Money(it["amount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(), it["currency"]?.jsonPrimitive?.contentOrNull) }

                val paymentObj = obj["payment"] as? JsonObject
                val payment = paymentObj?.let {
                    PaymentSummary(
                        id = it["id"]?.jsonPrimitive?.contentOrNull,
                        variableSymbol = it["variableSymbol"]?.jsonPrimitive?.contentOrNull,
                        specificSymbol = it["specificSymbol"]?.jsonPrimitive?.contentOrNull,
                        dueAt = it["dueAt"]?.jsonPrimitive?.contentOrNull,
                        status = it["status"]?.jsonPrimitive?.contentOrNull
                    )
                }

                val personObj = obj["person"] as? JsonObject
                val person = personObj?.let { PersonSummary(it["id"]?.jsonPrimitive?.contentOrNull, it["firstName"]?.jsonPrimitive?.contentOrNull, it["lastName"]?.jsonPrimitive?.contentOrNull) }

                PaymentDebtorItem(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull,
                    isUnpaid = obj["isUnpaid"]?.jsonPrimitive?.contentOrNull?.let { it == "true" },
                    price = price,
                    paymentId = obj["paymentId"]?.jsonPrimitive?.contentOrNull,
                    personId = obj["personId"]?.jsonPrimitive?.contentOrNull,
                    payment = payment,
                    person = person,
                    raw = obj
                )
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }

        try { cache.put(cacheKey, list) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return list
    }

    suspend fun fetchDebtorsForPerson(personId: String?): List<PaymentDebtorItem> {
        if (personId == null) return emptyList()
        val all = fetchPaymentDebtors()
        return all.filter { it.personId != null && it.personId == personId }
    }
}
