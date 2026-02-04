package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSuccess: () -> Unit = {}) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Přihlášení", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Login") },
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Heslo") },
                modifier = Modifier.fillMaxWidth(0.85f),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Button(onClick = {
                if (loading) return@Button
                loading = true
                error = null
                scope.launch {
                    try {
                        val ok = ServiceLocator.authService.login(username, password)
                        if (ok) onSuccess() else error = "Přihlášení selhalo"
                    } catch (ex: Throwable) {
                        error = ex.message ?: "Chyba při přihlášení"
                    } finally {
                        loading = false
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Probíhá..." else "Přihlásit")
            }

            if (error != null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
