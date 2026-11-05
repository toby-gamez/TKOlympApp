package com.tkolymp.tkolympapp.platform

import android.content.Context
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Single on-device copy of the logged-in account's photo (app-private storage, not the evictable
 * Coil cache). Keyed by the URL without its query, so any size variant (`?w=96`) maps to it.
 */
internal object OfflineAvatarStore {
    private const val IMAGE_FILE = "offline_avatar.img"
    private const val URL_FILE = "offline_avatar.url"

    private var loaded = false
    private var savedBase: String? = null

    private fun base(url: String) = url.substringBefore('?')

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        savedBase = try { File(context.filesDir, URL_FILE).takeIf { it.exists() }?.readText() } catch (_: Exception) { null }
        loaded = true
    }

    @Synchronized
    fun fileFor(context: Context, url: String): File? {
        ensureLoaded(context)
        if (savedBase != base(url)) return null
        return File(context.filesDir, IMAGE_FILE).takeIf { it.exists() }
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, IMAGE_FILE).delete()
        File(context.filesDir, URL_FILE).delete()
        savedBase = null
        loaded = true
    }

    /** Downloads the full-size photo; on any failure the previous copy is kept. */
    suspend fun save(context: Context, url: String) = withContext(Dispatchers.IO) {
        val full = base(url)
        if (!full.startsWith("https://tkolymp.cz/f/")) return@withContext
        try {
            val token = try { ServiceLocator.tokenStorage.getToken() } catch (_: Exception) { null }
            val request = Request.Builder().url(full).apply {
                if (!token.isNullOrEmpty()) {
                    addHeader("Authorization", "Bearer $token")
                    addHeader("Cookie", "rozpisovnik=$token")
                }
            }.build()
            val tmp = File(context.filesDir, "$IMAGE_FILE.tmp")
            com.tkolymp.shared.pinnedOkHttpClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                val body = resp.body ?: return@withContext
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            synchronized(this@OfflineAvatarStore) {
                if (tmp.length() == 0L) { tmp.delete(); return@withContext }
                val target = File(context.filesDir, IMAGE_FILE)
                target.delete()
                if (!tmp.renameTo(target)) { tmp.delete(); return@withContext }
                File(context.filesDir, URL_FILE).writeText(full)
                savedBase = full
                loaded = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // offline or server error: keep whatever copy we already have
        }
    }
}
