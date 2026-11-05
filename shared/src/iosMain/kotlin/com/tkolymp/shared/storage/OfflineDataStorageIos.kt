package com.tkolymp.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding

@OptIn(ExperimentalForeignApi::class)
class OfflineDataStorageIos : OfflineDataStorage {
    private val offlineDir: String by lazy {
        val appSupport = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: ""
        val dir = "$appSupport/offline"
        val fm = NSFileManager.defaultManager
        // Cached member/payment data: keep it unreadable while the device is locked (the default class only
        // protects it until the first unlock after boot). New files inherit the directory's class; files
        // written before this change are upgraded once here. The app never reads this cache in the background.
        val protection = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete)
        fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = protection, error = null)
        fm.setAttributes(protection, ofItemAtPath = dir, error = null)
        fm.contentsOfDirectoryAtPath(dir, error = null)?.forEach { name ->
            fm.setAttributes(protection, ofItemAtPath = "$dir/$name", error = null)
        }
        dir
    }

    private companion object {
        val PROTECTED_WRITE = NSDataWritingAtomic or NSDataWritingFileProtectionComplete
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
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { pinned ->
            CC_SHA256(data.bytes, data.length.convert(), pinned.addressOf(0).reinterpret<UByteVar>())
        }
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(CC_SHA256_DIGEST_LENGTH * 2)
        for (i in 0 until CC_SHA256_DIGEST_LENGTH) {
            val b = digest[i].toInt() and 0xFF
            sb.append(hexChars[(b ushr 4) and 0xF])
            sb.append(hexChars[b and 0xF])
        }
        return sb.toString()
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
            val content = NSString.create(contentsOfFile = path, encoding = NSUTF8StringEncoding, error = null)
                ?.toString() ?: return idx
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
            (content as NSString).dataUsingEncoding(NSUTF8StringEncoding)?.writeToFile(tmpPath, options = PROTECTED_WRITE, error = null)
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
                        (json as NSString).dataUsingEncoding(NSUTF8StringEncoding)?.writeToFile(tmpPath, options = PROTECTED_WRITE, error = null)
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
                    NSString.create(contentsOfFile = path, encoding = NSUTF8StringEncoding, error = null)?.toString()
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
