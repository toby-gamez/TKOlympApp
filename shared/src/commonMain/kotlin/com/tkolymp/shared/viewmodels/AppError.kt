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
            ErrorReporter.report("AppError.network", resolved, cause)
            return Network(resolved)
        }

        fun notFound(message: String?, cause: Throwable? = null): AppError {
            val resolved = message ?: AppStrings.current.errorMessages.notFoundError
            ErrorReporter.report("AppError.notFound", resolved, cause)
            return NotFound(resolved)
        }
    }
}
