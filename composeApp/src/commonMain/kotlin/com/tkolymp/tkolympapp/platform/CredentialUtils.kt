package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

/**
 * Returns a suspend callback that offers to save the given username/password with the
 * platform's credential manager (e.g. Google Password Manager on Android) so the OS can
 * offer to fill it in on the next login. Grab the callback once in a composable and call
 * it from a coroutine right after a successful login.
 */
@Composable
expect fun rememberSaveCredentialsCallback(): suspend (username: String, password: String) -> Unit

/**
 * Returns a suspend callback that asks the platform's credential manager for a previously
 * saved username/password, showing the native "Sign in as..." picker if one or more
 * credentials are available. Returns null if the user has none saved or dismisses the picker.
 */
@Composable
expect fun rememberGetSavedCredentialCallback(): suspend () -> Pair<String, String>?
