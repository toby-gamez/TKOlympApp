package com.tkolymp.shared.payments

import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.json.AppJsonWithDefaults
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * A payment the user saved from an event's payment card to settle later (e.g. the balance after a deposit).
 * Local only: it is shown in Payments next to the ones from the server.
 */
@Serializable
data class SavedPayment(
    val id: String,
    val eventId: Long? = null,
    val title: String,
    val amountMinor: Long,
    /** Last day to pay, ISO `yyyy-MM-dd`. */
    val dueDate: String? = null,
    val account: String,
    val variableSymbol: String? = null,
    val specificSymbol: String? = null,
    val message: String? = null,
    /** The deposit that was already paid, when this is the balance after it. */
    val depositMinor: Long? = null,
    val savedAt: String = "",
    val paid: Boolean = false
)

/** Same event, amount and deadline give the same id, so saving twice replaces instead of duplicating. */
fun savedPaymentId(eventId: Long?, title: String, amountMinor: Long, dueDate: String?): String =
    "${eventId ?: title.hashCode()}_${amountMinor}_${dueDate ?: "-"}"

/** Stored in [OfflineDataStorage] under a per-person key, outside the `offline_` prefix the sync manager wipes. */
class SavedPaymentsStore(private val storage: OfflineDataStorage) {
    private fun key(personId: String?) = "$KEY_PREFIX${personId?.takeIf { it.isNotBlank() } ?: "me"}"

    /** Unpaid first, soonest deadline first; items without a deadline last. */
    suspend fun list(personId: String?): List<SavedPayment> = read(personId)
        .sortedWith(compareBy<SavedPayment>({ it.paid }, { it.dueDate ?: "9999-12-31" }, { it.savedAt }))

    suspend fun upsert(personId: String?, payment: SavedPayment) =
        write(personId, read(personId).filterNot { it.id == payment.id } + payment)

    suspend fun setPaid(personId: String?, id: String, paid: Boolean = true) =
        write(personId, read(personId).map { if (it.id == id) it.copy(paid = paid) else it })

    suspend fun remove(personId: String?, id: String) =
        write(personId, read(personId).filterNot { it.id == id })

    private suspend fun read(personId: String?): List<SavedPayment> {
        val json = try { storage.load(key(personId)) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (json.isNullOrBlank()) return emptyList()
        return try { AppJson.decodeFromString(ListSerializer(SavedPayment.serializer()), json) } catch (_: Exception) { emptyList() }
    }

    private suspend fun write(personId: String?, list: List<SavedPayment>) =
        storage.save(key(personId), AppJsonWithDefaults.encodeToString(ListSerializer(SavedPayment.serializer()), list))

    private companion object {
        const val KEY_PREFIX = "saved_payments_"
    }
}
