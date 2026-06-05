package com.tkolymp.shared.payments

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.sync.OfflineKeys

class PaymentsViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _items = MutableStateFlow<List<PaymentDebtorItem>>(emptyList())
    val items: StateFlow<List<PaymentDebtorItem>> = _items

    init {
        load()
    }

    fun load() {
        scope.launch {
            _isLoading.value = true
            try {
                val pid = withContext(Dispatchers.IO) { com.tkolymp.shared.ServiceLocator.userService.getCachedPersonId() }

                val list = try {
                    withContext(Dispatchers.IO) { com.tkolymp.shared.ServiceLocator.paymentService.fetchDebtorsForPerson(pid) }
                } catch (_: Exception) {
                    emptyList<com.tkolymp.shared.payments.PaymentDebtorItem>()
                }

                if (list.isNotEmpty()) {
                    _items.value = list.sortedByDescending { it.payment?.dueAt }
                } else {
                    // fallback: try offline cache (per-person then global)
                    val offlineJson = withContext(Dispatchers.IO) {
                        val storage = com.tkolymp.shared.ServiceLocator.offlineDataStorage
                        if (!pid.isNullOrBlank()) {
                            try { storage.load(OfflineKeys.paymentsForPerson(pid)) } catch (_: Exception) { null }
                                ?: try { storage.load(OfflineKeys.PAYMENTS) } catch (_: Exception) { null }
                        } else {
                            try { storage.load(OfflineKeys.PAYMENTS) } catch (_: Exception) { null }
                        }
                    }
                    if (!offlineJson.isNullOrBlank()) {
                        _items.value = parseOfflinePayments(offlineJson, pid).sortedByDescending { it.payment?.dueAt }
                    } else {
                        _items.value = list.sortedByDescending { it.payment?.dueAt }
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                _items.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun parseOfflinePayments(jsonStr: String, pid: String?): List<PaymentDebtorItem> {
        return try {
            val el = AppJson.parseToJsonElement(jsonStr)
            val arr = when (el) {
                is JsonArray -> el
                is JsonObject -> JsonArray(listOf(el))
                else -> JsonArray(emptyList())
            }
            arr.mapNotNull { itemEl ->
                try {
                    val obj = itemEl as? JsonObject ?: return@mapNotNull null
                    val priceObj = obj["price"] as? JsonObject
                    val price = priceObj?.let {
                        val amountStr = it["amount"]?.jsonPrimitive?.contentOrNull
                        val amountMinor = amountStr?.toLongOrNull()
                            ?: amountStr?.toDoubleOrNull()?.let { d -> (d * 100).roundToLong() }
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

                    val personId = obj["personId"]?.jsonPrimitive?.contentOrNull
                    if (pid != null && pid.isNotBlank() && !personId.isNullOrBlank() && personId != pid) return@mapNotNull null

                    PaymentDebtorItem(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull,
                        isUnpaid = obj["isUnpaid"]?.jsonPrimitive?.contentOrNull?.let { it == "true" },
                        price = price,
                        paymentId = obj["paymentId"]?.jsonPrimitive?.contentOrNull,
                        personId = personId,
                        payment = payment,
                        person = person,
                        raw = obj
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }
}
