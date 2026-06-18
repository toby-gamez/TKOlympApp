package com.tkolymp.shared.viewmodels

sealed class DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error(val error: AppError) : DataResult<Nothing>()
}
