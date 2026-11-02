package com.tkolymp.shared.update

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * On a network error, checks the newest GitHub release (tags look like `v2.2.1`) against the
 * installed version and exposes [updateAvailable] so the UI can offer a (dismissible) update dialog.
 * Checked at most once per session.
 */
object UpdateChecker {
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.tkolymp.tkolympapp"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    @Volatile private var currentVersion: String? = null
    @Volatile private var checked = false

    /** Call at startup with the installed version name (e.g. "2.2.1"). */
    fun setCurrentVersion(versionName: String?) {
        currentVersion = versionName
    }

    fun dismiss() {
        _updateAvailable.value = false
    }

    /** Safe to call from anywhere on every network error; does the GitHub lookup once per session. */
    fun onNetworkError() {
        val current = currentVersion ?: return
        if (checked) return
        checked = true
        scope.launch {
            try {
                val latest = ServiceLocator.changelogService.fetchReleases()
                    .filter { !it.isPrerelease }
                    .mapNotNull { parseVersion(it.tagName) }
                    .maxWithOrNull(::compareVersions)
                    ?: run { checked = false; return@launch }
                val installed = parseVersion(current) ?: return@launch
                if (compareVersions(latest, installed) > 0) _updateAvailable.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                checked = false
            }
        }
    }

    /** "v2.2.1" / "2.2.1" -> [2,2,1]; null when no numeric parts. */
    internal fun parseVersion(raw: String): List<Int>? {
        val parts = raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.').map { it.toIntOrNull() ?: return null }
        return parts.ifEmpty { null }
    }

    internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
