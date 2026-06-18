package com.tkolymp.shared.viewmodels

/**
 * Base interface for ViewModel state — all state data classes should implement this.
 * Provides common fields for loading and error state.
 */
interface ViewModelState {
    val isLoading: Boolean
    val error: AppError?
}

/**
 * Type-safe sealed representation of async UI state.
 *
 * Prevents impossible states (e.g. isLoading=true with data present) that
 * can occur with the flat ViewModelState + isLoading boolean pattern.
 *
 * Usage:
 *   sealed interface MyState : UiState<MyState.Content> {
 *       data object Loading : MyState, UiState.Loading
 *       data class Content(val items: List<Item>) : MyState, UiState.Success<MyState.Content>
 *       data class Error(override val error: AppError) : MyState, UiState.Error
 *   }
 */
sealed interface UiState<out T : Any> {
    /** Initial or loading — no data available yet. */
    data object Loading : UiState<Nothing>

    /** Data successfully loaded and ready to display. */
    data class Success<T : Any>(val data: T) : UiState<T>

    /** An error occurred. */
    data class Error(val error: AppError) : UiState<Nothing>

    val isLoading: Boolean get() = this is Loading
    val errorMessage: AppError? get() = (this as? Error)?.error
}
