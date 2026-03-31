package com.tkolymp.tkolympapp

import com.tkolymp.shared.notification.ITopicManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidTopicManager : ITopicManager {
    private suspend fun awaitTask(action: (String) -> com.google.android.gms.tasks.Task<Void>, topic: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val task = action(topic)
            task.addOnSuccessListener { cont.resume(Unit) }
            task.addOnFailureListener { cont.resumeWithException(it) }
        }

    override suspend fun updateSubscriptions(subscribe: Set<String>, unsubscribe: Set<String>) {
        val fm = FirebaseMessaging.getInstance()
        for (t in subscribe) {
            try { awaitTask({ topic -> fm.subscribeToTopic(topic) }, t) } catch (_: Exception) {}
        }
        for (t in unsubscribe) {
            try { awaitTask({ topic -> fm.unsubscribeFromTopic(topic) }, t) } catch (_: Exception) {}
        }
    }
}
