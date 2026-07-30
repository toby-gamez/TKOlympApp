package com.tkolymp.shared.storage

import android.content.Context
import android.util.Log
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class OfflineDataStorageAndroid(context: Context) : OfflineDataStorage {
    companion object {
        private const val TAG = "OfflineDataStorage"
        private const val INDEX_KEY = "__offline_index__"
        private val legacyHashFileName = Regex("^[0-9a-f]{64}$")
    }

    private val ksafe = KSafe(context, fileName = "offlinestore")
    private val indexMutex = Mutex()

    init {
        purgeLegacyPlaintextFiles(context)
    }

    // The previous implementation stored blobs as plaintext files named by sha256(key), plus a
    // plaintext offline_index.tsv. Both leaked cached member/payment data on disk. This removes
    // that residue so it doesn't sit there unencrypted forever after the switch to ksafe below.
    private fun purgeLegacyPlaintextFiles(context: Context) {
        try {
            context.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && (legacyHashFileName.matches(f.name) ||
                        f.name == "offline_index.tsv" || f.name == "offline_index.tsv.tmp")
                ) {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "purgeLegacyPlaintextFiles failed", e)
        }
    }

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun readIndex(): MutableMap<String, String> {
        val raw = ksafe.get(INDEX_KEY, "")
        if (raw.isEmpty()) return mutableMapOf()
        return try {
            Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw).toMutableMap()
        } catch (e: Exception) {
            Log.w(TAG, "readIndex failed", e)
            mutableMapOf()
        }
    }

    private suspend fun writeIndex(map: Map<String, String>) {
        try {
            ksafe.put(INDEX_KEY, Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map))
        } catch (e: Exception) {
            Log.w(TAG, "writeIndex failed", e)
        }
    }

    override suspend fun save(key: String, json: String) {
        val hash = sha256Hex(key)
        try {
            ksafe.put(hash, json)
        } catch (e: Exception) {
            Log.e(TAG, "save failed for key=$key", e)
            throw e
        }
        indexMutex.withLock {
            val idx = readIndex()
            idx[hash] = key
            writeIndex(idx)
        }
    }

    override suspend fun load(key: String): String? {
        val hash = sha256Hex(key)
        return try {
            ksafe.get(hash, "").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "load failed for key=$key", e)
            null
        }
    }

    override suspend fun deleteByPrefix(prefix: String) {
        indexMutex.withLock {
            try {
                val idx = readIndex()
                val toRemove = idx.filterValues { it.startsWith(prefix) }.keys.toList()
                toRemove.forEach { hash ->
                    try {
                        ksafe.delete(hash)
                    } catch (e: Exception) {
                        Log.w(TAG, "deleteByPrefix failed for hash=$hash", e)
                    }
                    idx.remove(hash)
                }
                writeIndex(idx)
            } catch (e: Exception) {
                Log.w(TAG, "deleteByPrefix failed", e)
            }
        }
    }

    override suspend fun allKeys(): Set<String> {
        return try {
            readIndex().values.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "allKeys failed", e)
            emptySet()
        }
    }
}
