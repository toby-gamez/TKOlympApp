package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberSaveCredentialsCallback(): suspend (username: String, password: String) -> Unit {
    return { _, _ -> }
}

@Composable
actual fun rememberGetSavedCredentialCallback(): suspend () -> Pair<String, String>? {
    return { null }
}
