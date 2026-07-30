package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.feedback.FeedbackType
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@Composable
fun FeedbackDialog(
    type: FeedbackType,
    onDismiss: () -> Unit,
) {
    val strings = AppStrings.current.feedback
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    val title = if (type == FeedbackType.BUG_REPORT) strings.reportBugLabel else strings.suggestFeatureLabel
    val messageHint = if (type == FeedbackType.BUG_REPORT) strings.bugMessageHint else strings.featureMessageHint

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                if (success) {
                    Text(strings.successMessage)
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.nameLabel) },
                        singleLine = true,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(strings.emailLabel) },
                        singleLine = true,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(messageHint) },
                        enabled = !sending,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorText != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorText!!, color = MaterialTheme.colorScheme.error)
                    }
                    if (sending) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator()
                    }
                }
            }
        },
        confirmButton = {
            if (success) {
                TextButton(onClick = onDismiss) { Text(AppStrings.current.commonActions.ok) }
            } else {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        if (name.isBlank() || email.isBlank() || message.isBlank()) {
                            errorText = strings.validationError
                            return@TextButton
                        }
                        if (!EMAIL_REGEX.matches(email.trim())) {
                            errorText = AppStrings.current.invalidEmail
                            return@TextButton
                        }
                        errorText = null
                        sending = true
                        scope.launch {
                            val result = ServiceLocator.feedbackService.submit(type, name, email, message)
                            sending = false
                            result.onSuccess { success = true }
                                .onFailure { errorText = strings.errorMessage }
                        }
                    }
                ) { Text(if (sending) strings.sending else strings.submit) }
            }
        },
        dismissButton = {
            if (!success) {
                TextButton(enabled = !sending, onClick = onDismiss) { Text(AppStrings.current.commonActions.cancel) }
            }
        }
    )
}
