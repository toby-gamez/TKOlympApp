package com.tkolymp.shared.payments

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

data class Money(val amount: Long?, val currency: String?)

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

        val resp = try { client.post(query, null) } catch (e: CancellationException) { throw e }
        val root = (resp as? JsonObject)?.get("data")?.jsonObject ?: return emptyList()
        val arr = (root["paymentDebtorsList"] as? JsonArray) ?: JsonArray(emptyList())

        val list = arr.mapNotNull { el ->
            try {
                val obj = el as? JsonObject ?: return@mapNotNull null
                val priceObj = obj["price"] as? JsonObject
                val price = priceObj?.let {
                    val amountMinor = it["amount"]?.jsonPrimitive?.contentOrNull?.let(::parseDecimalToMinorUnits)
                    Money(amountMinor, it["currency"]?.jsonPrimitive?.contentOrNull)
                }

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
                    isUnpaid = obj["isUnpaid"]?.jsonPrimitive?.booleanOrNull,
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

// Parses a decimal string (e.g. "1500.5") to minor currency units (e.g. 150050) without
// going through Double, which would introduce floating-point error for certain values.
internal fun parseDecimalToMinorUnits(raw: String): Long? {
    val negative = raw.startsWith('-')
    val abs = if (negative) raw.substring(1) else raw
    val dotIdx = abs.indexOf('.')
    val intPart = if (dotIdx < 0) abs else abs.substring(0, dotIdx)
    val fracPart = if (dotIdx < 0) "" else abs.substring(dotIdx + 1)
    val major = intPart.toLongOrNull() ?: return null
    val minor = fracPart.padEnd(2, '0').take(2).toLongOrNull() ?: return null
    val result = major * 100 + minor
    return if (negative) -result else result
}
