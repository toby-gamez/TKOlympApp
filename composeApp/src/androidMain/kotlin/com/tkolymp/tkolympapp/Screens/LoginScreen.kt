package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.viewmodels.LoginViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSuccess: () -> Unit = {}) {
    val viewModel = remember { LoginViewModel() }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Přihlášení", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text("Login") },
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text("Heslo") },
                modifier = Modifier.fillMaxWidth(0.85f),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Button(onClick = {
                if (state.isLoading) return@Button
                scope.launch {
                    val ok = viewModel.login()
                    if (ok) onSuccess()
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading) {
                Text(if (state.isLoading) "Probíhá..." else "Přihlásit")
            }

            if (state.error != null) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
