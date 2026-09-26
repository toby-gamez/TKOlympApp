package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.tkolympapp.ui.theme.AppTheme

/**
 * Consistent, reusable rendering of a [ViewModelState.error]. Every AppError is already
 * forwarded automatically to the bug-report backend (see [com.tkolymp.shared.errorreporting.ErrorReporter]);
 * this composable is only responsible for telling the user something went wrong.
 *
 * Use [ErrorState] for a full-page failure (e.g. initial load failed, nothing else to show),
 * and [ErrorBanner] for an inline notice on top of content that's still otherwise usable
 * (e.g. a stale cached list after a background refresh failed).
 * Both share the layout of [EmptyState] (icon above centered text).
 */
@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    ErrorState(message = error.message, modifier = modifier, onRetry = onRetry)
}

@Composable
fun ErrorState(
    message: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    fullPage: Boolean = true,
    onRetry: (() -> Unit)? = null,
) {
    StatusMessage(
        title = message?.takeIf { it.isNotBlank() } ?: AppStrings.current.commonActions.error,
        icon = icon,
        subtitle = null,
        fullPage = fullPage,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier,
        action = onRetry?.let { retry ->
            {
                TextButton(onClick = retry) {
                    Text(AppStrings.current.commonActions.retry)
                }
            }
        }
    )
}

@Composable
fun ErrorBanner(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    ErrorBanner(message = error.message, modifier = modifier, onRetry = onRetry)
}

@Composable
fun ErrorBanner(
    message: String?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    ErrorState(message = message, modifier = modifier, fullPage = false, onRetry = onRetry)
}

@Preview(name = "ErrorState — Light")
@Composable
private fun ErrorStatePreviewLight() {
    AppTheme(darkTheme = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            ErrorBanner(message = "Nepodařilo se načíst data", onRetry = {})
            ErrorState(message = "Nepodařilo se načíst data", onRetry = {})
        }
    }
}

@Preview(name = "ErrorState — Dark")
@Composable
private fun ErrorStatePreviewDark() {
    AppTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            ErrorBanner(message = "Nepodařilo se načíst data", onRetry = {})
            ErrorState(message = "Nepodařilo se načíst data", onRetry = {})
        }
    }
}
