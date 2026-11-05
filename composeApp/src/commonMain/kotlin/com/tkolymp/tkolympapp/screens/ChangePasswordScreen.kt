package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
) {
    val strings = AppStrings.current
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) strings.commonActions.hidePassword else strings.commonActions.showPassword
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

/** Changes the password; replaces the former dialog. [onSuccess] is expected to sign the user out, as before. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    val strings = AppStrings.current
    val vm = viewModel<ProfileViewModel>()
    val scope = rememberCoroutineScope()

    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading) return
        error = when {
            oldPass.isBlank() -> strings.oldPasswordRequired
            newPass.length < 8 -> strings.passwordMinLength
            newPass != confirm -> strings.passwordsMismatch
            else -> null
        }
        if (error != null) return
        scope.launch {
            loading = true
            val err = vm.changePassword(oldPass, newPass)
            loading = false
            if (err == null) onSuccess() else error = err
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.changePassword) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.commonActions.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(strings.passwordTooShort, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            PasswordField(oldPass, { oldPass = it }, strings.oldPassword, ImeAction.Next)
            PasswordField(newPass, { newPass = it }, strings.newPassword, ImeAction.Next)
            PasswordField(confirm, { confirm = it }, strings.confirmPassword, ImeAction.Done, onDone = ::submit)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
            Button(onClick = ::submit, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text(strings.confirm)
            }
        }
    }
}
