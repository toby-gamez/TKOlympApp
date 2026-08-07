package com.tkolymp.shared.errorreporting

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.device.DeviceInfo
import com.tkolymp.shared.feedback.FeedbackType
import com.tkolymp.shared.json.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sends every error the app encounters — handled or fatal — to the club's existing
 * feedback/bug-report backend ([com.tkolymp.shared.feedback.IFeedbackService]),
 * automatically and without any user interaction, in both debug and release builds.
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
    private const val AUTO_REPORT_EMAIL = "autoreport@tkolymp.app"

    /** Reports a handled (non-fatal) error. Safe to call from anywhere, any thread. */
    fun report(context: String, message: String, throwable: Throwable? = null) {
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

    /** Call once at startup, after [ServiceLocator] is initialized, to flush a crash captured on the previous run. */
    fun flushPendingCrashReport(storage: CrashReportStorage) {
        val pending = storage.getPendingCrash() ?: return
        storage.clearPendingCrash()
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

    private fun buildReportBody(title: String, context: String, message: String, throwable: Throwable?): String = buildString {
        appendLine(title)
        appendLine("Context: $context")
        appendLine("Message: $message")
        appendLine("Time: ${kotlin.time.Clock.System.now()}")
        appendLine("App version: ${DeviceInfo.appVersion}")
        appendLine("OS: ${DeviceInfo.osVersion}")
        appendLine("Device: ${DeviceInfo.deviceModel}")
        appendLine()
        appendLine("Stack trace:")
        appendLine(throwable?.stackTraceToString()?.take(6000) ?: "(no exception attached)")
    }

    private suspend fun send(body: String) {
        if (!ServiceLocator.isInitialized) return
        try {
            val email = currentUserEmail() ?: AUTO_REPORT_EMAIL
            ServiceLocator.feedbackService.submit(
                type = FeedbackType.BUG_REPORT,
                name = "Auto report (${DeviceInfo.platformName})",
                email = email,
                message = body,
            )
        } catch (_: Throwable) {
            // Never let reporting itself fail user-visibly.
        }
    }

    private suspend fun currentUserEmail(): String? {
        val json = try {
            ServiceLocator.userStorage.getCurrentUserJson()
        } catch (_: Throwable) {
            null
        } ?: return null
        return try {
            AppJson.parseToJsonElement(json).jsonObject["uEmail"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}
