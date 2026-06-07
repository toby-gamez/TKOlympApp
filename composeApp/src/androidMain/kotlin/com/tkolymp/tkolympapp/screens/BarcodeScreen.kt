package com.tkolymp.tkolympapp.screens

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings

private val L_CODES = arrayOf(
    "0001101", "0011001", "0010011", "0111101", "0100011",
    "0110001", "0101111", "0111011", "0110111", "0001011"
)
private val R_CODES = L_CODES.map { it.map { c -> if (c == '0') '1' else '0' }.joinToString("") }.toTypedArray()

private fun ean8CheckDigit(digits: String): Int {
    val sum = digits.mapIndexed { i, c ->
        c.digitToInt() * if (i % 2 == 0) 3 else 1
    }.sum()
    return (10 - (sum % 10)) % 10
}

private fun encodeEan8(input: String): Pair<String, String>? {
    val digits = input.filter { it.isDigit() }
    val full: String = when (digits.length) {
        7 -> digits + ean8CheckDigit(digits)
        8 -> digits
        else -> return null
    }
    val bits = buildString {
        append("101")
        for (i in 0..3) append(L_CODES[full[i].digitToInt()])
        append("01010")
        for (i in 4..7) append(R_CODES[full[i].digitToInt()])
        append("101")
    }
    return bits to full
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val attrs = activity?.window?.attributes
        val original = attrs?.screenBrightness ?: -1f
        if (attrs != null) {
            attrs.screenBrightness = 1.0f
            activity.window.attributes = attrs
        }
        onDispose {
            val a = activity?.window?.attributes
            if (a != null) {
                a.screenBrightness = original
                activity.window.attributes = a
            }
        }
    }

    var cstsId by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cstsId = try { ServiceLocator.userService.getCachedCstsId() } catch (_: Exception) { null }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(AppStrings.current.dialogs.barcodeInfoTitle) },
            text = { Text(AppStrings.current.dialogs.barcodeInfoText) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(AppStrings.current.ok) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.current.profile.cstsId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (cstsId == null) {
                Text(
                    text = AppStrings.current.profile.cstsNotAvailable,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            } else {
                val encoded = encodeEan8(cstsId)
                if (encoded == null) {
                    Text(
                        text = "${AppStrings.current.profile.cstsInvalidFormat}\n(${cstsId})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                } else {
                    val (bits, fullDigits) = encoded
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(67f / 50f)
                            ) {
                                val moduleWidth = size.width / bits.length
                                val path = Path()
                                bits.forEachIndexed { index, bit ->
                                    if (bit == '1') {
                                        val x = index * moduleWidth
                                        path.addRect(Rect(x, 0f, x + moduleWidth, size.height))
                                    }
                                }
                                drawPath(path, Color.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${fullDigits.take(4)}  ${fullDigits.drop(4)}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}
