package com.tkolymp.shared.viewmodels

sealed class AppError {
    abstract val message: String

    data class Generic(override val message: String) : AppError()
    data class Network(override val message: String) : AppError()
    data class NotFound(override val message: String) : AppError()

    companion object {
        fun generic(message: String?): AppError = Generic(message ?: "Neznámá chyba")
        fun network(message: String?): AppError = Network(message ?: "Chyba sítě")
        fun notFound(message: String?): AppError = NotFound(message ?: "Nenalezeno")
    }
}
