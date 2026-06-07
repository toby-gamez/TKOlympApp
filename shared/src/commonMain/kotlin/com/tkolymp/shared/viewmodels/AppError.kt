package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.language.AppStrings

sealed class AppError {
    abstract val message: String

    data class Generic(override val message: String) : AppError()
    data class Network(override val message: String) : AppError()
    data class NotFound(override val message: String) : AppError()

    companion object {
        fun generic(message: String?): AppError = Generic(message ?: AppStrings.current.errorMessages.unknownError)
        fun network(message: String?): AppError = Network(message ?: AppStrings.current.errorMessages.networkError)
        fun notFound(message: String?): AppError = NotFound(message ?: AppStrings.current.errorMessages.notFoundError)
    }
}
