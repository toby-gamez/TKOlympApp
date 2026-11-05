package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.payments.buildSpdPayload
import com.tkolymp.shared.payments.formatMinorUnits
import com.tkolymp.tkolympapp.platform.rememberSaveImageToGalleryCallback
import com.tkolymp.tkolympapp.platform.rememberShareImageCallback
import kotlinx.coroutines.launch

/**
 * Copyable payment details plus "share QR" / "save QR to gallery" actions. The QR is generated
 * on the device, so nothing about the payer leaves the phone.
 */
@Composable
fun PaymentPayActions(
    bankAccount: String,
    amountMinor: Long?,
    currency: String,
    variableSymbol: String?,
    specificSymbol: String?,
    message: String?,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit
) {
    val misc = AppStrings.current.misc
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val fileName = "tkolymp_payment_${variableSymbol ?: specificSymbol ?: "qr"}"
    val shareQr = rememberShareImageCallback(fileBaseName = fileName, shareTitle = misc.payQrShare)
    val saveQr = rememberSaveImageToGalleryCallback(fileBaseName = fileName)
    val amount = amountMinor?.let(::formatMinorUnits)

    val qrBitmap = remember(bankAccount, amountMinor, currency, variableSymbol, specificSymbol, message) {
        buildSpdPayload(bankAccount, amountMinor, currency, variableSymbol, specificSymbol, message)
            ?.let { renderQrImageBitmap(it) }
    }

    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        CopyableDetail(misc.payAccountLabel, bankAccount, onCopy = { copy(bankAccount) })
        if (amount != null) {
            CopyableDetail(misc.payAmountLabel, "$amount $currency", onCopy = { copy(amount) })
        }
        if (!variableSymbol.isNullOrBlank()) {
            CopyableDetail(misc.payVariableSymbolLabel, variableSymbol, onCopy = { copy(variableSymbol) })
        }
        if (!specificSymbol.isNullOrBlank()) {
            CopyableDetail(misc.paySpecificSymbolLabel, specificSymbol, onCopy = { copy(specificSymbol) })
        }
        if (qrBitmap != null) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { scope.launch { shareQr(qrBitmap) } }) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(misc.payQrShare)
                }
                OutlinedButton(onClick = {
                    scope.launch { onMessage(if (saveQr(qrBitmap)) misc.payQrSaved else misc.payQrSaveFailed) }
                }) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(misc.payQrSave)
                }
            }
        }
    }
}

@Composable
private fun CopyableDetail(label: String, value: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = AppStrings.current.misc.payCopied)
        }
    }
}
