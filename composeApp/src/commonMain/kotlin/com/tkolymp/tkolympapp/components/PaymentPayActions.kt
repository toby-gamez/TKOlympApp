package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.payments.buildSpdPayload
import com.tkolymp.shared.payments.isClubAccount
import com.tkolymp.shared.payments.formatMinorUnits
import com.tkolymp.tkolympapp.screens.ProfileValueRow
import com.tkolymp.tkolympapp.platform.rememberSaveImageToGalleryCallback
import com.tkolymp.tkolympapp.platform.rememberShareImageCallback
import kotlinx.coroutines.launch

/**
 * Payment summary (PersonScreen style; see [PayActionsStyle]): optional QR tile with the amount, tap-to-copy payment details and
 * "share QR" / "save QR to gallery" actions. The QR is generated on the device, so nothing
 * about the payer leaves the phone.
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
    showQr: Boolean = false,
    style: PayActionsStyle = PayActionsStyle.Plain,
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
        onMessage(misc.payCopied)
    }

    val details = buildList {
        add(Triple(misc.payAccountLabel, bankAccount, bankAccount))
        if (amount != null) add(Triple(misc.payAmountLabel, "$amount $currency", amount))
        if (!variableSymbol.isNullOrBlank()) add(Triple(misc.payVariableSymbolLabel, variableSymbol, variableSymbol))
        if (!specificSymbol.isNullOrBlank()) add(Triple(misc.paySpecificSymbolLabel, specificSymbol, specificSymbol))
        if (!message.isNullOrBlank()) add(Triple(misc.payDraftMessageLabel, message, message))
    }
    val rows: @Composable () -> Unit = {
        if (!isClubAccount(bankAccount)) {
            Text(
                misc.payAccountMismatchWarning,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp)
            )
        }
        details.forEachIndexed { index, (label, shown, copied) ->
            if (index > 0) RowDivider()
            ProfileValueRow(label, shown, onClick = { copy(copied) }, trailingIcon = Icons.Default.ContentCopy)
        }
    }
    val qrBlock: @Composable () -> Unit = {
        if (showQr && qrBitmap != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentQrImage(bitmap = qrBitmap, size = 200.dp)
                if (amount != null) {
                    Text("$amount $currency", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    val buttons: @Composable () -> Unit = {
        if (qrBitmap != null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { shareQr(qrBitmap) } },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(misc.payQrShare, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { scope.launch { onMessage(if (saveQr(qrBitmap)) misc.payQrSaved else misc.payQrSaveFailed) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(misc.payQrSave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    when (style) {
        PayActionsStyle.Card -> Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = surfaceCardColors(),
            border = cardLineBorder()
        ) {
            Column {
                if (showQr && qrBitmap != null) {
                    Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp)) { qrBlock() }
                    RowDivider()
                }
                rows()
                if (qrBitmap != null) {
                    RowDivider()
                    Box(Modifier.padding(14.dp)) { buttons() }
                }
            }
        }
        PayActionsStyle.Inset -> Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            qrBlock()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(cardLineBorder(), RoundedCornerShape(14.dp))) { rows() }
            buttons()
        }
        PayActionsStyle.Plain -> Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            qrBlock()
            Column { rows() }
            buttons()
        }
    }
}

/** [Card]: self-contained bordered card; [Inset]: for use inside another card; [Plain]: bare rows. */
enum class PayActionsStyle { Plain, Inset, Card }
