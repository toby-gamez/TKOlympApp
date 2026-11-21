package com.tkolymp.tkolympapp

import com.tkolymp.shared.payments.SavedPayment
import com.tkolymp.shared.payments.SavedPaymentsStore
import com.tkolymp.shared.payments.savedPaymentId
import com.tkolymp.tkolympapp.fakes.FakeOfflineDataStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedPaymentsStoreTest {
    private fun payment(due: String?, amount: Long = 320000, title: String = "Soustředění") = SavedPayment(
        id = savedPaymentId(7, title, amount, due), eventId = 7, title = title, amountMinor = amount,
        dueDate = due, depositMinor = 200000, account = "1806875329/0800", variableSymbol = "0601011234", specificSymbol = "20241507",
        savedAt = "2026-06-01T10:00:00Z"
    )

    @Test
    fun savedPaymentSurvivesReloadAndIsPerPerson() = runTest {
        val storage = FakeOfflineDataStorage()
        val p = payment("2026-07-15")
        SavedPaymentsStore(storage).upsert("1", p)
        assertEquals(listOf(p), SavedPaymentsStore(storage).list("1"))
        assertTrue(SavedPaymentsStore(storage).list("2").isEmpty())
    }

    @Test
    fun savingTheSamePaymentTwiceReplacesIt() = runTest {
        val store = SavedPaymentsStore(FakeOfflineDataStorage())
        store.upsert("1", payment("2026-07-15"))
        store.upsert("1", payment("2026-07-15").copy(message = "NOVE"))
        assertEquals(listOf("NOVE"), store.list("1").map { it.message })
    }

    @Test
    fun markPaidMovesItAfterUnpaidAndRemoveDeletes() = runTest {
        val store = SavedPaymentsStore(FakeOfflineDataStorage())
        val early = payment("2026-07-01", title = "A")
        val late = payment("2026-08-01", title = "B")
        store.upsert("1", late); store.upsert("1", early)
        assertEquals(listOf("A", "B"), store.list("1").map { it.title })
        store.setPaid("1", early.id)
        assertEquals(listOf("B", "A"), store.list("1").map { it.title })
        assertTrue(store.list("1").last().paid)
        store.remove("1", early.id)
        assertEquals(listOf("B"), store.list("1").map { it.title })
    }

    @Test
    fun corruptedStorageGivesEmptyList() = runTest {
        val storage = FakeOfflineDataStorage()
        storage.save("saved_payments_1", "{not json")
        assertTrue(SavedPaymentsStore(storage).list("1").isEmpty())
        assertEquals(savedPaymentId(7, "x", 100, null), savedPaymentId(7, "y", 100, null))
    }
}
