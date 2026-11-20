package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import com.tkolymp.tkolympapp.components.SheetPrimaryButton
import com.tkolymp.tkolympapp.components.accentColor
import com.tkolymp.tkolympapp.ui.theme.Spacing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tkolymp.tkolympapp.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.tkolymp.tkolympapp.components.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.FadeVisibility
// removed TextFieldDefaults import — using Modifier background for inputs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tkolymp.tkolympapp.components.BackgroundPluses
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.viewmodels.LoginViewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.platform.AppLogo
import com.tkolymp.tkolympapp.platform.rememberGetSavedCredentialCallback
import com.tkolymp.tkolympapp.platform.rememberSaveCredentialsCallback
import com.tkolymp.tkolympapp.ui.brandLightPrimary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSuccess: () -> Unit = {}) {
    val viewModel = viewModel<LoginViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val saveCredentials = rememberSaveCredentialsCallback()
    val getSavedCredential = rememberGetSavedCredentialCallback()
    var contentVisible by remember { mutableStateOf(false) }
    var credentialFromPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    LaunchedEffect(Unit) {
        val saved = getSavedCredential()
        if (saved != null) {
            val (username, password) = saved
            viewModel.updateUsername(username)
            viewModel.updatePassword(password)
            credentialFromPicker = true
            val ok = viewModel.login()
            if (ok) onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 }
            ) {
                Column {
                    // Brand row
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(brandLightPrimary())
                        ) { AppLogo(size = 44.dp) }
                        Text("TK Olymp", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Text(
                        AppStrings.current.auth.login,
                        modifier = Modifier.padding(top = 40.dp),
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        AppStrings.current.auth.loginSubtitle,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val hasError = state.error != null
                    var showPassword by remember { mutableStateOf(false) }

                    LoginFieldLabel(AppStrings.current.auth.emailOrUsername, topPadding = 28.dp)
                    LoginField(
                        value = state.username,
                        onValueChange = { credentialFromPicker = false; viewModel.updateUsername(it) },
                        icon = Icons.Filled.Person,
                        isError = hasError
                    )

                    LoginFieldLabel(AppStrings.current.auth.password, topPadding = 16.dp)
                    LoginField(
                        value = state.password,
                        onValueChange = { credentialFromPicker = false; viewModel.updatePassword(it) },
                        icon = Icons.Filled.Lock,
                        isError = hasError,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) AppStrings.current.commonActions.hidePassword else AppStrings.current.commonActions.showPassword,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    AnimatedVisibility(visible = hasError, enter = fadeIn(tween(200))) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = accentColor(), modifier = Modifier.size(16.dp))
                            Text(state.error?.message ?: "", style = MaterialTheme.typography.bodySmall, color = accentColor())
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SheetPrimaryButton(
                        text = if (state.isLoading) "" else AppStrings.current.auth.login,
                        onClick = {
                            if (state.isLoading) return@SheetPrimaryButton
                            val username = state.username
                            val password = state.password
                            val skipSave = credentialFromPicker
                            scope.launch {
                                val ok = viewModel.login()
                                if (ok) {
                                    if (!skipSave) saveCredentials(username, password)
                                    onSuccess()
                                }
                            }
                        }
                    )
                    FadeVisibility(state.isLoading) {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }

                    val uriHandler = LocalUriHandler.current
                    TextButton(
                        onClick = { uriHandler.openUri("https://tkolymp.cz/zapomenute-heslo") },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.small)
                    ) {
                        Text(
                            text = AppStrings.current.auth.forgotPassword,
                            color = accentColor(),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginFieldLabel(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        modifier = Modifier.padding(top = topPadding, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Filled 52dp field with a leading icon; the border turns red on error (prototype login). */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isError: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(1.dp, if (isError) brandLightPrimary() else Color.Transparent, shape),
        shape = shape,
        singleLine = true,
        visualTransformation = visualTransformation,
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        trailingIcon = trailing,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        )
    )
}
