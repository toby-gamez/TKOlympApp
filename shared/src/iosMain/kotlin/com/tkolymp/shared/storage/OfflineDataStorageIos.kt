package com.tkolymp.shared.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import platform.darwin.UInt8
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
class OfflineDataStorageIos : OfflineDataStorage {
    private val offlineDir: String by lazy {
        val appSupport = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: ""
        val dir = "$appSupport/offline"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null
        )
        dir
    }

    private val dirMutex = Mutex()
    private val fileMutexes = mutableMapOf<String, Mutex>()
    private val fileMutexLock = Mutex()

    private suspend fun mutexFor(path: String): Mutex {
        fileMutexLock.withLock {
            return fileMutexes.getOrPut(path) { Mutex() }
        }
    }

    private fun sha256Hex(input: String): String {
        val data = (input as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return input.hashCode().toString()
        val length = data.length.convert<Int>()
        return memScoped {
            val digest = allocArray<UInt8>(CC_SHA256_DIGEST_LENGTH)
            CC_SHA256(data.bytes, data.length.convert(), digest)
            (0 until CC_SHA256_DIGEST_LENGTH).joinToString("") {
                "%02x".format(digest[it].toInt() and 0xFF)
            }
        }
    }

    private fun fileForKey(key: String): String {
        val hash = sha256Hex(key)
        return "$offlineDir/$hash"
    }

    private fun indexFile(): String = "$offlineDir/offline_index.tsv"

    private fun readIndex(): MutableMap<String, String> {
        val idx = mutableMapOf<String, String>()
        val path = indexFile()
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return idx
        try {
            val content = NSString.create(contentsOfFile = path, encoding = NSUTF8StringEncoding)?.toString() ?: return idx
            content.lines().forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) idx[parts[0]] = parts[1]
            }
        } catch (_: Exception) {}
        return idx
    }

    private fun writeIndex(map: Map<String, String>) {
        val path = indexFile()
        val tmpPath = "$path.tmp"
        try {
            val content = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }
            val nsString = NSString.create(string = content)
            nsString.writeToFile(tmpPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            NSFileManager.defaultManager.moveItemAtPath(tmpPath, toPath = path, error = null)
        } catch (_: Exception) {}
    }

    override suspend fun save(key: String, json: String) {
        withContext(Dispatchers.Default) {
            val path = fileForKey(key)
            val m = mutexFor(path)
            dirMutex.withLock {
                m.withLock {
                    try {
                        val tmpPath = "$path.tmp"
                        (json as NSString).writeToFile(tmpPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
                        NSFileManager.defaultManager.moveItemAtPath(tmpPath, toPath = path, error = null)
                        val hashName = path.substringAfterLast("/")
                        val idx = readIndex()
                        idx[hashName] = key
                        writeIndex(idx)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override suspend fun load(key: String): String? {
        return withContext(Dispatchers.Default) {
            val path = fileForKey(key)
            val m = mutexFor(path)
            m.withLock {
                if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@withContext null
                try {
                    NSString.create(contentsOfFile = path, encoding = NSUTF8StringEncoding)?.toString()
                } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun deleteByPrefix(prefix: String) {
        withContext(Dispatchers.Default) {
            dirMutex.withLock {
                try {
                    val idx = readIndex()
                    val toRemove = idx.filterValues { it.startsWith(prefix) }.keys.toList()
                    toRemove.forEach { hash ->
                        val path = "$offlineDir/$hash"
                        val m = mutexFor(path)
                        m.withLock {
                            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                            idx.remove(hash)
                        }
                    }
                    writeIndex(idx)
                } catch (_: Exception) {}
            }
        }
    }

    override suspend fun allKeys(): Set<String> {
        return withContext(Dispatchers.Default) {
            try {
                readIndex().values.toSet()
            } catch (_: Exception) { emptySet() }
        }
    }
}
