package com.tkolymp.shared.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineDataStorageAndroid(private val context: Context) : OfflineDataStorage {
    private val prefsName = "tkolymp_offline_cache"
    private val prefs by lazy { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }

    override suspend fun save(key: String, json: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, json).apply()
    }

    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun deleteByPrefix(prefix: String) = withContext(Dispatchers.IO) {
        val toRemove = prefs.all.keys.filter { it.startsWith(prefix) }
        if (toRemove.isNotEmpty()) {
            val editor = prefs.edit()
            toRemove.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    override suspend fun allKeys(): Set<String> = withContext(Dispatchers.IO) {
        prefs.all.keys
    }
}
