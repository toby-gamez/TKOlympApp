package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.errorreporting.ErrorReporter
import com.tkolymp.shared.language.AppStrings

sealed class AppError {
    abstract val message: String

    data class Generic(override val message: String) : AppError()
    data class Network(override val message: String) : AppError()
    data class NotFound(override val message: String) : AppError()

    companion object {
        // Every AppError shown (or, per the silent-failure audit, sometimes NOT shown) to a user
        // is also automatically forwarded to the club's bug-report backend, in debug and release
        // alike — this is the single choke point through which nearly all user-facing failures flow.
        fun generic(message: String?, cause: Throwable? = null): AppError {
            val resolved = message ?: AppStrings.current.errorMessages.unknownError
            ErrorReporter.report("AppError.generic", resolved, cause)
            return Generic(resolved)
        }

        fun network(message: String?, cause: Throwable? = null): AppError {
            val resolved = message ?: AppStrings.current.errorMessages.networkError
            // Timeouts / unreachable host are the user's connectivity (or a server blip), not an app bug.
            if (!isTransientNetworkFailure(message, cause)) ErrorReporter.report("AppError.network", resolved, cause)
            return Network(resolved)
        }

        private val transientMarkers = listOf(
            "timeout", "timed out", "unable to resolve host", "unknownhost", "unresolved address",
            "failed to connect", "connection reset", "connection refused", "network is unreachable",
            "software caused connection abort", "broken pipe", "unexpected end of stream",
        )

        internal fun isTransientNetworkFailure(message: String?, cause: Throwable?): Boolean {
            val text = buildString {
                append(message.orEmpty())
                var t = cause
                while (t != null) { append(' ').append(t::class.simpleName).append(' ').append(t.message.orEmpty()); t = t.cause }
            }.lowercase()
            return transientMarkers.any { it in text }
        }

        fun notFound(message: String?, cause: Throwable? = null): AppError {
            val resolved = message ?: AppStrings.current.errorMessages.notFoundError
            ErrorReporter.report("AppError.notFound", resolved, cause)
            return NotFound(resolved)
        }
    }
}
