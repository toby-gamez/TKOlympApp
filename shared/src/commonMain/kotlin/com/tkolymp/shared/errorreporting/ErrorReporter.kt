package com.tkolymp.shared.errorreporting

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.device.DeviceInfo
import com.tkolymp.shared.feedback.FeedbackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sends error and crash reports to the club's feedback backend
 * ([com.tkolymp.shared.feedback.IFeedbackService]), automatically and without any
 * user interaction. Active in release builds only ([Logger.isDebug] gates every entry
 * point below) so debug/dev builds never spam the backend with local errors.
 *
 * Privacy rules:
 *  - User PII (email, name, or any identifying data) is NEVER included in reports; messages and
 *    stack traces are additionally run through [scrub].
 *  - Reports are sent only while [enabled] (privacy policy accepted and the user did not opt out).
 *  - Device info (OS version, device model, app version) is included ONLY when the
 *    user has accepted the privacy policy; call [setDeviceInfoConsented] at startup
 *    and whenever consent changes.
 *
 * Deliberately fire-and-forget: reporting failures are swallowed so they never
 * cascade into a second, user-visible error.
 */
object ErrorReporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dedupeMutex = Mutex()
    private val seenSignatures = mutableSetOf<String>()
    private var reportCount = 0

    private const val MAX_REPORTS_PER_SESSION = 40
    private const val REPORT_EMAIL = "autoreport@tkolymp.app"

    /** Set to true when the user has accepted the privacy policy and consented to diagnostic data. */
    @Volatile var deviceInfoConsented: Boolean = false
        private set

    /** False when the user opted out of error reports (or has not yet accepted the privacy policy). */
    @Volatile var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** Call once the user's consent state is known (app startup and on first accept). */
    fun setDeviceInfoConsented(consented: Boolean) {
        deviceInfoConsented = consented
    }

    /** Reports a handled (non-fatal) error. Safe to call from anywhere, any thread. No-op in debug builds. */
    fun report(context: String, message: String, throwable: Throwable? = null) {
        if (Logger.isDebug || !enabled) return
        val signature = buildString {
            append(context)
            append('|')
            append(message.take(160))
            throwable?.let { append('|'); append(it::class.simpleName) }
        }
        scope.launch {
            if (!shouldSend(signature)) return@launch
            val body = buildReportBody(
                title = "Automatic error report",
                context = context,
                message = message,
                throwable = throwable,
            )
            send(body)
        }
    }

    /** Builds the persisted text for a fatal/uncaught exception; called synchronously from the crash handler. */
    fun buildCrashReport(context: String, throwable: Throwable): String =
        buildReportBody(
            title = "Automatic crash report",
            context = context,
            message = throwable.message ?: throwable::class.simpleName ?: "Uncaught exception",
            throwable = throwable,
        )

    /** Call once at startup, after [ServiceLocator] is initialized, to flush a crash captured on the previous run. No-op in debug builds. */
    fun flushPendingCrashReport(storage: CrashReportStorage) {
        val pending = storage.getPendingCrash() ?: return
        storage.clearPendingCrash()
        if (Logger.isDebug || !enabled) return
        scope.launch { send(pending) }
    }

    private suspend fun shouldSend(signature: String): Boolean = dedupeMutex.withLock {
        if (reportCount >= MAX_REPORTS_PER_SESSION || !seenSignatures.add(signature)) {
            false
        } else {
            reportCount++
            true
        }
    }

    private val scrubPatterns = listOf(
        Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*") to "<jwt>",
        Regex("(?i)bearer\\s+\\S+") to "Bearer <redacted>",
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "<email>",
        Regex("\\d{5,}") to "<num>",
    )

    /** Strips tokens, e-mail addresses and long numeric ids (person ids, phone numbers, accounts) from free text. */
    internal fun scrub(text: String): String = scrubPatterns.fold(text) { acc, (re, rep) -> re.replace(acc, rep) }

    private fun buildReportBody(title: String, context: String, message: String, throwable: Throwable?): String = buildString {
        appendLine(title)
        appendLine("Context: $context")
        appendLine("Message: ${scrub(message)}")
        appendLine("Time: ${kotlin.time.Clock.System.now()}")
        if (deviceInfoConsented) {
            appendLine("App version: ${DeviceInfo.appVersion}")
            appendLine("OS: ${DeviceInfo.osVersion}")
            appendLine("Device: ${DeviceInfo.deviceModel}")
        }
        appendLine()
        appendLine("Stack trace:")
        appendLine(throwable?.stackTraceToString()?.let(::scrub)?.take(6000) ?: "(no exception attached)")
    }

    private suspend fun send(body: String) {
        if (!ServiceLocator.isInitialized) return
        try {
            ServiceLocator.feedbackService.submit(
                type = FeedbackType.BUG_REPORT,
                name = "Auto report (${DeviceInfo.platformName})",
                email = REPORT_EMAIL,
                message = body,
            )
        } catch (_: Throwable) {
            // Never let reporting itself fail user-visibly.
        }
    }
}
