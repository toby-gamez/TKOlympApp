package com.tkolymp.shared.errorreporting

/**
 * Synchronous (non-suspend) single-slot storage for a crash report captured by an
 * uncaught-exception handler. Writes must be synchronous because the process may be
 * killed immediately after an uncaught exception, before any coroutine gets to run.
 * The pending report (if any) is sent on the next app launch and then cleared.
 */
expect class CrashReportStorage(platformContext: Any) {
    fun savePendingCrash(report: String)
    fun getPendingCrash(): String?
    fun clearPendingCrash()
}
