package com.tkolymp.tkolympapp

import androidx.lifecycle.ViewModel

class AndroidPaymentsViewModel : ViewModel() {
    val paymentsVm: com.tkolymp.shared.payments.PaymentsViewModel = com.tkolymp.shared.payments.PaymentsViewModel()

    val isLoading = paymentsVm.isLoading
    val items = paymentsVm.items

    fun load() = paymentsVm.load()

    override fun onCleared() {
        paymentsVm.clear()
        super.onCleared()
    }
}
