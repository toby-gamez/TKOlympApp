package com.tkolymp.tkolympapp.platform

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "CredentialUtils"
private const val GET_CREDENTIAL_TIMEOUT_MS = 8000L

@Composable
actual fun rememberSaveCredentialsCallback(): suspend (username: String, password: String) -> Unit {
    val context = LocalContext.current
    return { username, password ->
        Log.d(TAG, "rememberSaveCredentialsCallback invoked, usernameBlank=${username.isBlank()} passwordBlank=${password.isBlank()} context=$context")
        if (username.isNotBlank() && password.isNotBlank()) {
            try {
                Log.d(TAG, "Calling CredentialManager.createCredential with CreatePasswordRequest")
                val response = CredentialManager.create(context).createCredential(
                    context = context,
                    request = CreatePasswordRequest(username, password)
                )
                Log.d(TAG, "createCredential succeeded: type=${response.type} data=${response.data}")
            } catch (e: CreateCredentialCancellationException) {
                Log.d(TAG, "User dismissed the save-password prompt: ${e.message}")
            } catch (e: CreateCredentialException) {
                Log.w(TAG, "createCredential failed: type=${e.type} class=${e::class.simpleName} message=${e.message} errorMessage=${e.errorMessage}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while offering credential save: ${e::class.qualifiedName}: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Skipping credential save — blank username or password")
        }
    }
}

@Composable
actual fun rememberGetSavedCredentialCallback(): suspend () -> Pair<String, String>? {
    val context = LocalContext.current
    return {
        Log.d(TAG, "rememberGetSavedCredentialCallback invoked, context=$context")
        try {
            val request = GetCredentialRequest(listOf(GetPasswordOption()))
            Log.d(TAG, "Calling CredentialManager.getCredential with GetPasswordOption (timeout=${GET_CREDENTIAL_TIMEOUT_MS}ms)")
            val response = withTimeoutOrNull(GET_CREDENTIAL_TIMEOUT_MS) {
                CredentialManager.create(context).getCredential(context, request)
            }
            if (response == null) {
                Log.w(TAG, "getCredential timed out after ${GET_CREDENTIAL_TIMEOUT_MS}ms with no response — likely stuck showing/resolving the picker")
                null
            } else {
                val credential = response.credential as? PasswordCredential
                if (credential != null) {
                    Log.d(TAG, "getCredential succeeded for id=${credential.id}")
                    credential.id to credential.password
                } else {
                    Log.w(TAG, "getCredential returned unexpected credential type: ${response.credential.type}")
                    null
                }
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User dismissed the sign-in picker: ${e.message}")
            null
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No saved credential available: ${e.message}")
            null
        } catch (e: GetCredentialException) {
            Log.w(TAG, "getCredential failed: type=${e.type} class=${e::class.simpleName} message=${e.message}")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching saved credential: ${e::class.qualifiedName}: ${e.message}", e)
            null
        }
    }
}
