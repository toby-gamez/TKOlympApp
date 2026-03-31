package com.tkolymp.shared.notification

interface ITopicManager {
    suspend fun updateSubscriptions(subscribe: Set<String>, unsubscribe: Set<String>)
}
