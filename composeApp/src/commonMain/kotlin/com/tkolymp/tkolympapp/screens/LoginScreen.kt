package com.tkolymp.tkolympapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
// removed TextFieldDefaults import — using Modifier background for inputs
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.viewmodels.LoginViewModel
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.tkolympapp.platform.AppLogo
import com.tkolymp.tkolympapp.ui.brandLightPrimary
import kotlinx.coroutines.launch

@Composable
fun BackgroundPluses(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    rows: Int = 8,
    cols: Int = 5
) {
    Canvas(modifier = modifier) {
        val spacingX = size.width / cols.toFloat()
        val spacingY = size.height / rows.toFloat()
        val plusHalf = minOf(spacingX, spacingY) * 0.12f
        val stroke = plusHalf * 0.45f
        val drawColor = color.copy(alpha = 0.12f)

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val cx = spacingX * (i + 0.5f)
                val cy = spacingY * (j + 0.5f)
                // draw a small gap in the arms so the center isn't darkened by overlap
                val centerRadius = stroke * 0.55f
                val gap = centerRadius * 1.1f

                // horizontal arms (left and right) with rounded caps
                drawLine(
                    color = drawColor,
                    start = Offset(cx - plusHalf, cy),
                    end = Offset(cx - gap, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = drawColor,
                    start = Offset(cx + gap, cy),
                    end = Offset(cx + plusHalf, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                // vertical arms (top and bottom) with rounded caps
                drawLine(
                    color = drawColor,
                    start = Offset(cx, cy - plusHalf),
                    end = Offset(cx, cy - gap),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = drawColor,
                    start = Offset(cx, cy + gap),
                    end = Offset(cx, cy + plusHalf),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                // final center (same visible color as arms) — drawn once so it doesn't darken
                drawCircle(color = drawColor, radius = centerRadius, center = Offset(cx, cy))
            }
        }
    }
}

@Composable
fun LoginScreen(onSuccess: () -> Unit = {}) {
    val viewModel = viewModel<LoginViewModel>()
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        BackgroundPluses(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand image
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(brandLightPrimary())
            ) {
                AppLogo(size = 100.dp)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "TK Olymp",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                AppStrings.current.auth.loginSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text(AppStrings.current.auth.emailOrUsername) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text(AppStrings.current.auth.password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    if (state.isLoading) return@Button
                    scope.launch {
                        val ok = viewModel.login()
                        if (ok) onSuccess()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(AppStrings.current.auth.login, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

                val uriHandler = LocalUriHandler.current
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://tkolymp.cz/zapomenute-heslo")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = AppStrings.current.auth.forgotPassword,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

            if (state.error != null) {
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
