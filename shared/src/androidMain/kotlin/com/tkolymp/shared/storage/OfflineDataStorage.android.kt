package com.tkolymp.shared.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

class OfflineDataStorageAndroid(private val context: Context) : OfflineDataStorage {
    private fun fileForKey(key: String): File {
        val safe = URLEncoder.encode(key, "UTF-8")
        return File(context.filesDir, safe)
    }

    override suspend fun save(key: String, json: String): Unit = withContext(Dispatchers.IO) {
        val f = fileForKey(key)
        f.parentFile?.mkdirs()
        f.writeText(json)
    }

    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        val f = fileForKey(key)
        if (f.exists()) try { f.readText() } catch (_: Exception) { null } else null
    }

    override suspend fun deleteByPrefix(prefix: String): Unit = withContext(Dispatchers.IO) {
        val dir = context.filesDir
        dir.listFiles()?.forEach { f ->
            try {
                val original = URLDecoder.decode(f.name, "UTF-8")
                if (original.startsWith(prefix)) f.delete()
            } catch (_: Exception) { /* ignore malformed names */ }
        }
        Unit
    }

    override suspend fun allKeys(): Set<String> = withContext(Dispatchers.IO) {
        val dir = context.filesDir
        val keys = mutableSetOf<String>()
        dir.listFiles()?.forEach { f ->
            try { keys.add(URLDecoder.decode(f.name, "UTF-8")) } catch (_: Exception) {}
        }
        keys
    }
}
