package com.tkolymp.shared.payments

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                val list = withContext(Dispatchers.IO) { com.tkolymp.shared.ServiceLocator.paymentService.fetchDebtorsForPerson(pid) }
                _items.value = list
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                _items.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun clear() {
        scope.cancel()
    }
}
