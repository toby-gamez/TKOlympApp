package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.payments.AmountOption
import com.tkolymp.shared.payments.ParsedPayment
import com.tkolymp.shared.payments.PaymentProfile
import com.tkolymp.shared.payments.PriceProgram
import com.tkolymp.shared.payments.VsSource
import com.tkolymp.shared.payments.buildSpdPayload
import com.tkolymp.shared.payments.fillName
import com.tkolymp.shared.payments.amountOptionLabel
import com.tkolymp.shared.payments.formatCzDate
import com.tkolymp.shared.payments.SavedPayment
import com.tkolymp.shared.payments.formatCzk
import com.tkolymp.shared.payments.remainderAmount
import com.tkolymp.shared.payments.isPastDue
import com.tkolymp.shared.payments.applicableOptions
import com.tkolymp.shared.payments.expiredPricesNote
import com.tkolymp.shared.payments.isExpired
import com.tkolymp.shared.payments.needsUserChoice
import com.tkolymp.shared.payments.programsDiffer
import com.tkolymp.shared.payments.formatMinorUnits
import com.tkolymp.shared.payments.isValidAccountChecksum
import com.tkolymp.shared.payments.parseAmountInput
import com.tkolymp.shared.payments.priceHint
import com.tkolymp.shared.payments.priceHintMessage
import com.tkolymp.shared.payments.resolveVariableSymbol
import com.tkolymp.shared.payments.preselectedAmount
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import kotlinx.datetime.LocalDate

private enum class PayMode { FULL, DEPOSIT, REMAINDER }

/** Temporarily off while testing with past dates; set back to true to block payment after the "Termín". */
private const val ENFORCE_PAYMENT_DEADLINE = false

/**
 * Fallback payment card for events whose text names an account but carries no ready QR. When nothing
 * has to be chosen the QR is shown straight away (with an "edit" button); otherwise the prefilled,
 * editable form comes first and the QR appears after the user confirms it.
 */
@Composable
fun PaymentDraftCard(
    draft: ParsedPayment,
    profile: PaymentProfile?,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onSaveUnpaid: ((SavedPayment) -> Unit)? = null,
    onMessage: (String) -> Unit
) {
    val misc = AppStrings.current.misc
    val tooLate = ENFORCE_PAYMENT_DEADLINE && isPastDue(draft, today)
    val differ = remember(draft) { programsDiffer(draft) }
    val initialAmount = remember(draft, today) { if (differ) null else preselectedAmount(draft, today) }

    var programIdx by remember(draft) { mutableStateOf(if (differ) null else 0) }
    var account by remember(draft) { mutableStateOf(draft.account ?: draft.iban.orEmpty()) }
    var selected by remember(draft) { mutableStateOf(initialAmount) }
    var days by remember(draft) { mutableStateOf("1") }
    fun totalFor(option: AmountOption): String =
        formatMinorUnits(option.amountMinor * if (option.perDay) (days.toIntOrNull() ?: 1).coerceAtLeast(1) else 1)
    var payMode by remember(draft) { mutableStateOf<PayMode?>(null) }
    fun chosenPrice(): Long? =
        selected?.let { it.amountMinor * if (it.perDay) (days.toIntOrNull() ?: 1).coerceAtLeast(1) else 1 }
    // With a deposit on offer nothing is filled in until the user picks deposit, balance or the full amount.
    fun amountFor(): String = when {
        draft.deposit != null && payMode == null -> ""
        payMode == PayMode.DEPOSIT -> formatMinorUnits(draft.deposit?.amountMinor ?: 0)
        payMode == PayMode.REMAINDER ->
            draft.deposit?.let { remainderAmount(chosenPrice(), it) }?.let(::formatMinorUnits).orEmpty()
        else -> selected?.let(::totalFor).orEmpty()
    }
    var amountText by remember(draft) {
        mutableStateOf(if (draft.deposit != null) "" else initialAmount?.let { formatMinorUnits(it.amountMinor) }.orEmpty())
    }
    var vs by remember(draft, profile) { mutableStateOf(resolveVariableSymbol(draft.vs, profile).orEmpty()) }
    var ss by remember(draft) { mutableStateOf(draft.ss.orEmpty()) }
    var name by remember(draft, profile) { mutableStateOf(profile?.fullName.orEmpty()) }
    var message by remember(draft, profile) { mutableStateOf(draft.message.orEmpty()) }
    var confirmed by remember(draft, profile) { mutableStateOf(!needsUserChoice(draft) && draft.missing.isEmpty()) }

    val options = applicableOptions(programIdx?.let { draft.programs.getOrNull(it) }?.options.orEmpty())
    val amountMinor = parseAmountInput(amountText)
    val spd = buildSpdPayload(account, amountMinor, "CZK", vs.ifBlank { null }, ss.ifBlank { null }, message.ifBlank { null })
    val hint = priceHint(options, selected, today)?.let(::priceHintMessage)

    val warnings = buildList {
        if (programIdx == null) add(misc.payDraftWarnProgram)
        if (draft.deposit != null && payMode == null) add(misc.payDraftWarnMode)
        if ('/' in account && !isValidAccountChecksum(account)) add(misc.payDraftWarnAccount)
        if (amountMinor == null && programIdx != null) add(misc.payDraftWarnAmount)
        if (vs.isBlank()) {
            add(if (draft.vs == VsSource.BirthNumberFromProfile) misc.payDraftWarnVsProfile else misc.payDraftWarnVs)
        }
        if (draft.messageTemplate != null && name.isBlank()) add(misc.payDraftWarnName)
    }

    val showQr = confirmed && spd != null

    val priceLevels: @Composable () -> Unit = {
        if (options.size > 1 || options.any { it.perDay }) {
            GroupLabel(misc.payDraftPriceLevels)
            ChoiceList(
                items = options.map { Choice(amountOptionLabel(it), it == selected, !isExpired(it, today)) },
                onSelect = { selected = options[it]; amountText = amountFor() }
            )
            selected?.takeIf { it.perDay }?.let { option ->
                DraftField(misc.payDraftDays, days, {
                    days = it.filter(Char::isDigit).take(3)
                    amountText = amountFor()
                }, KeyboardType.Number)
            }
            expiredPricesNote(options, today)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(misc.payDraftTitle, horizontalPadding = 0.dp)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = surfaceCardColors(),
            border = cardLineBorder()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tooLate) {
                    PaymentNotice("${misc.payDraftTooLate} (${formatCzDate(draft.dueDate!!)})", isWarning = true)
                } else {
                    if (differ && !showQr) {
                        GroupLabel(misc.payDraftProgramLabel)
                        ChoiceList(
                            items = draft.programs.mapIndexed { index, program ->
                                Choice(program.title ?: "${misc.payDraftProgramLabel} ${index + 1}", index == programIdx)
                            },
                            onSelect = { index ->
                                programIdx = index
                                selected = preselectedAmount(draft.programs[index].options, today)
                                amountText = amountFor()
                            }
                        )
                    }
                    if (!showQr) priceLevels()
                    draft.deposit?.let { deposit ->
                        if (!showQr) GroupLabel(misc.payDraftModeLabel)
                        if (!showQr) ChoiceList(
                            items = listOf(
                                Choice(misc.payDraftModeFull, payMode == PayMode.FULL),
                                Choice(
                                    buildString {
                                        append(misc.payDraftModeDeposit).append(" · ").append(formatCzk(deposit.amountMinor))
                                        deposit.until?.let { append(" · do ").append(formatCzDate(it)) }
                                    },
                                    payMode == PayMode.DEPOSIT
                                ),
                                Choice(misc.payDraftModeRemainder, payMode == PayMode.REMAINDER)
                            ),
                            onSelect = { index ->
                                when (index) {
                                    0 -> {
                                        payMode = PayMode.FULL
                                        ss = draft.ss.orEmpty()
                                        if (selected == null) selected = preselectedAmount(options, today)
                                    }
                                    1 -> { payMode = PayMode.DEPOSIT; ss = draft.ss.orEmpty() }
                                    else -> {
                                        payMode = PayMode.REMAINDER
                                        ss = deposit.remainderSs ?: draft.ss.orEmpty()
                                        if (selected == null) selected = preselectedAmount(options, today)
                                    }
                                }
                                amountText = amountFor()
                            }
                        )
                        if (payMode == PayMode.REMAINDER) {
                            val due = deposit.remainderUntil?.let { " " + misc.payDraftRemainderDue.replace("{date}", formatCzDate(it)) }.orEmpty()
                            PaymentNotice(misc.payDraftRemainderAfterDeposit + due, isWarning = true)
                        }
                        if (payMode == PayMode.DEPOSIT) {
                            val rest = remainderAmount(chosenPrice(), deposit)
                            val date = deposit.remainderUntil?.let(::formatCzDate)
                            val note = when {
                                rest != null && date != null -> misc.payDraftRemainderAmountDate.replace("{amount}", formatCzk(rest)).replace("{date}", date)
                                date != null -> misc.payDraftRemainderDate.replace("{date}", date)
                                else -> misc.payDraftRemainderLater
                            } + (deposit.remainderSs?.let { " " + misc.payDraftRemainderSs.replace("{ss}", it) } ?: "")
                            PaymentNotice(note, isWarning = false)
                            if (onSaveUnpaid != null && rest != null && account.isNotBlank()) {
                                OutlinedButton(onClick = {
                                    onSaveUnpaid(
                                        SavedPayment(
                                            id = "",
                                            title = "",
                                            amountMinor = rest,
                                            depositMinor = deposit.amountMinor,
                                            dueDate = deposit.remainderUntil?.toString(),
                                            account = account,
                                            variableSymbol = vs.ifBlank { null },
                                            specificSymbol = (deposit.remainderSs ?: ss).ifBlank { null },
                                            message = message.ifBlank { null }
                                        )
                                    )
                                    onMessage(misc.payDraftSavedMsg)
                                }) { Text(misc.payDraftSaveUnpaid) }
                            }
                        }
                    }
                    if (hint != null) PaymentNotice(hint, isWarning = false)

                    if (showQr) {
                        warnings.forEach { PaymentNotice(it, isWarning = true) }
                        PaymentPayActions(
                            bankAccount = account,
                            amountMinor = amountMinor,
                            currency = "CZK",
                            variableSymbol = vs.ifBlank { null },
                            specificSymbol = ss.ifBlank { null },
                            message = message.ifBlank { null },
                            showQr = true,
                            style = PayActionsStyle.Inset,
                            onMessage = onMessage
                        )
                        TextButton(onClick = { confirmed = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(misc.payDraftEdit)
                        }
                    } else {
                        Text(misc.payDraftIntro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        warnings.forEach { PaymentNotice(it, isWarning = true) }

                        DraftField(misc.payAccountLabel, account, { account = it.filterNot(Char::isWhitespace) })
                        DraftField(misc.payAmountLabel, amountText, { amountText = it }, KeyboardType.Decimal)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DraftField(misc.payVariableSymbolLabel, vs, { vs = it.filter(Char::isDigit).take(10) }, KeyboardType.Number, Modifier.weight(1f))
                            DraftField(misc.paySpecificSymbolLabel, ss, { ss = it.filter(Char::isDigit).take(10) }, KeyboardType.Number, Modifier.weight(1f))
                        }
                        if (draft.messageTemplate != null) {
                            DraftField(misc.payDraftNameLabel, name, {
                                name = it
                                message = fillName(draft.messageTemplate.orEmpty(), it.trim().ifBlank { null }).orEmpty()
                            })
                        }
                        DraftField(misc.payDraftMessageLabel, message, { message = it })

                        Button(
                            onClick = { confirmed = true },
                            enabled = spd != null && amountMinor != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(misc.payDraftShowQr)
                        }
                    }
                }
                if (showQr && !tooLate && draft.storno.isNotEmpty()) {
                    Text(misc.payDraftStornoLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    draft.storno.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private data class Choice(val label: String, val selected: Boolean, val enabled: Boolean = true)

/** Small uppercase muted caption above a group, like the PersonScreen section labels. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Single-choice list: hairline-bordered rows with a check on the selected one (replaces filter chips). */
@Composable
private fun ChoiceList(items: List<Choice>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(cardLineBorder(), RoundedCornerShape(14.dp))
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) RowDivider()
            val tint = if (item.selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint)
                    .clickable(enabled = item.enabled) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .alpha(if (item.enabled) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (item.selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    item.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DraftField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun PaymentNotice(text: String, isWarning: Boolean, modifier: Modifier = Modifier) {
    val container = if (isWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(container).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (isWarning) Icons.Default.Warning else Icons.Default.Info,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = content, modifier = Modifier.weight(1f))
    }
}

private val previewDraft = ParsedPayment(
    account = "1806875329/0800",
    programs = listOf(
        PriceProgram(
            "Program A",
            listOf(
                AmountOption(489000, until = LocalDate(2026, 5, 31)),
                AmountOption(519000, until = LocalDate(2026, 6, 22)),
                AmountOption(549000, after = LocalDate(2026, 6, 22))
            )
        ),
        PriceProgram("Program B", listOf(AmountOption(34500)))
    ),
    ss = "20262707",
    vs = VsSource.BirthNumberFromProfile,
    messageTemplate = "ProAm kurz {NAME}"
)

@Preview(name = "PaymentDraftCard — Light")
@Composable
private fun PaymentDraftCardPreviewLight() {
    AppTheme(darkTheme = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            PaymentDraftCard(previewDraft, PaymentProfile("0601011234", "Jan", "Novák"), LocalDate(2026, 6, 19), onMessage = {})
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Preview(name = "PaymentDraftCard — Dark")
@Composable
private fun PaymentDraftCardPreviewDark() {
    AppTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            PaymentDraftCard(previewDraft, null, LocalDate(2026, 6, 19), onMessage = {})
        }
    }
}
