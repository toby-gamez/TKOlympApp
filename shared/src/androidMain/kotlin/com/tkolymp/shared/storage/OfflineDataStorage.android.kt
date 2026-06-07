package com.tkolymp.shared.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class OfflineDataStorageAndroid(private val context: Context) : OfflineDataStorage {
    companion object {
        private const val TAG = "OfflineDataStorage"
        private val mutexes = ConcurrentHashMap<String, Mutex>()
        private val dirMutex = Mutex()

        private fun mutexFor(path: String): Mutex = mutexes.computeIfAbsent(path) { Mutex() }
    }

    private fun fileForKey(key: String): File {
        val hash = sha256Hex(key)
        return File(context.filesDir, hash)
    }

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun indexFile(): File = File(context.filesDir, "offline_index.tsv")

    private fun readIndex(): MutableMap<String, String> {
        val idx = mutableMapOf<String, String>()
        val f = indexFile()
        if (!f.exists()) return idx
        try {
            f.readLines().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) idx[parts[0]] = parts[1]
            }
        } catch (e: Exception) {
            Log.w(TAG, "readIndex failed", e)
        }
        return idx
    }

    private fun writeIndex(map: Map<String, String>) {
        val f = indexFile()
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(map.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeIndex failed", e)
        }
    }

    override suspend fun save(key: String, json: String) {
        withContext(Dispatchers.IO) {
            val f = fileForKey(key)
            val m = mutexFor(f.absolutePath)

            // Ensure directory-level ordering while writing
            dirMutex.withLock {
                m.withLock {
                    try {
                        f.parentFile?.mkdirs()
                        val tmp = File(f.parentFile, f.name + ".tmp")
                        tmp.writeText(json)

                        val renamed = try {
                            tmp.renameTo(f)
                        } catch (e: Exception) {
                            Log.w(TAG, "renameTo failed", e)
                            false
                        }

                        if (!renamed) {
                            try {
                                tmp.copyTo(f, overwrite = true)
                                tmp.delete()
                            } catch (e: Exception) {
                                Log.e(TAG, "failed to write file for key=$key", e)
                                throw e
                            }
                        }
                        // update index mapping (hash -> original key)
                        try {
                            val idx = readIndex()
                            idx[f.name] = key
                            writeIndex(idx)
                        } catch (e: Exception) {
                            Log.w(TAG, "failed to update index for key=$key", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "save failed for key=$key", e)
                        throw e
                    }
                }
            }
        }
    }

    override suspend fun load(key: String): String? {
        return withContext(Dispatchers.IO) {
            val f = fileForKey(key)
            val m = mutexFor(f.absolutePath)

            m.withLock {
                if (!f.exists()) return@withContext null
                try {
                    f.readText()
                } catch (e: Exception) {
                    Log.w(TAG, "load failed for key=$key", e)
                    null
                }
            }
        }
    }

    override suspend fun deleteByPrefix(prefix: String) {
        withContext(Dispatchers.IO) {
            dirMutex.withLock {
                try {
                    val idx = readIndex()
                    val toRemove = idx.filterValues { it.startsWith(prefix) }.keys.toList()
                    toRemove.forEach { hash ->
                        val f = File(context.filesDir, hash)
                        val m = mutexFor(f.absolutePath)
                        try {
                            m.withLock {
                                if (f.exists()) {
                                    if (!f.delete()) Log.w(TAG, "failed to delete ${f.absolutePath}")
                                }
                                idx.remove(hash)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "deleteByPrefix lock failed for $hash", e)
                        }
                    }
                    writeIndex(idx)
                } catch (e: Exception) {
                    Log.w(TAG, "deleteByPrefix failed", e)
                }
            }
        }
    }

    override suspend fun allKeys(): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val idx = readIndex()
                idx.values.toSet()
            } catch (e: Exception) {
                Log.w(TAG, "allKeys failed", e)
                emptySet()
            }
        }
    }
}
