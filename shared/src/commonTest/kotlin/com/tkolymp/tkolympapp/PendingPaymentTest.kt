package com.tkolymp.tkolympapp

import com.tkolymp.shared.payments.PaymentDebtorItem
import com.tkolymp.shared.payments.SavedPayment
import com.tkolymp.shared.payments.hasPendingPayment
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingPaymentTest {
    private fun item(isUnpaid: Boolean?) = PaymentDebtorItem(null, isUnpaid, null, null, null, null, null, null)
    private fun saved(paid: Boolean) = SavedPayment(id = "1", title = "T", amountMinor = 100, account = "1/0800", paid = paid)

    @Test
    fun nothingPendingWhenAllPaid() = assertFalse(hasPendingPayment(listOf(item(false)), listOf(saved(true))))

    @Test
    fun unpaidServerItemIsPending() = assertTrue(hasPendingPayment(listOf(item(true)), emptyList()))

    @Test
    fun unpaidSavedPaymentIsPending() = assertTrue(hasPendingPayment(emptyList(), listOf(saved(false))))

    @Test
    fun emptyIsNotPending() = assertFalse(hasPendingPayment(emptyList(), emptyList()))
}
