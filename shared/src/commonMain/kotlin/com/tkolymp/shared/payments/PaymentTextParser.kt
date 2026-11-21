package com.tkolymp.shared.payments

import kotlinx.datetime.LocalDate

/**
 * Fallback for events that carry no ready-made payment QR: pulls payment details out of the
 * free text a trainer wrote. Deterministic, offline, no network. Amounts are kept in minor units
 * (like [QrPayment.amountMinor]) because commonMain has no BigDecimal.
 */

/** Who a price is meant for. The app pays as a club member, so [CLUB] wins over [OTHER] (see [applicableOptions]). */
enum class PriceAudience(val label: String) { CLUB("klubová"), OTHER("ostatní") }

data class AmountOption(
    val amountMinor: Long,
    val until: LocalDate? = null,
    val after: LocalDate? = null,
    val audience: PriceAudience? = null,
    /** A price for one day ("800 Kč / den"): the user picks how many days to pay for. */
    val perDay: Boolean = false
)

/** One price block of the text (a program/course); [title] is the line above it, when there is one. */
data class PriceProgram(val title: String?, val options: List<AmountOption>)

sealed interface VsSource {
    data class Literal(val digits: String) : VsSource
    /** Birth number of the signed-in user, taken from the profile and used without the slash. */
    data object BirthNumberFromProfile : VsSource
}

/**
 * A deposit ("záloha 2000 Kč do 30. 6."): the user may pay only this, or the whole price. [remainderUntil] and
 * [remainderSs] come from the "Doplatek … do 15. 7., SS: …" line.
 */
data class Deposit(
    val amountMinor: Long,
    val until: LocalDate? = null,
    val remainderUntil: LocalDate? = null,
    val remainderSs: String? = null
)

/** What is left to pay after the deposit, or null when the price is unknown or not higher than the deposit. */
fun remainderAmount(priceMinor: Long?, deposit: Deposit): Long? =
    priceMinor?.minus(deposit.amountMinor)?.takeIf { it > 0 }

enum class MissingField { ACCOUNT, AMOUNT, VS, NAME }

data class ParsedPayment(
    /** Czech account `[prefix-]number/bank`, no spaces. */
    val account: String? = null,
    /** IBAN found in the text, an alternative to [account]. */
    val iban: String? = null,
    /** One entry per price block; several when the text offers more than one program. */
    val programs: List<PriceProgram> = emptyList(),
    val ss: String? = null,
    val vs: VsSource? = null,
    /** Message with any placeholder removed or replaced; safe to put into the QR. */
    val message: String? = null,
    /** Message with `{NAME}` where the payer's name belongs; null when the text had no placeholder. */
    val messageTemplate: String? = null,
    val dueDate: LocalDate? = null,
    val missing: List<MissingField> = emptyList(),
    /** Cancellation conditions: the lines of the "Storno" section, if the text has one. */
    val storno: List<String> = emptyList(),
    /** Set when the text offers a deposit next to the full price. */
    val deposit: Deposit? = null
) {
    /** Price levels of the first (usually only) program. */
    val amounts: List<AmountOption> get() = applicableOptions(programs.firstOrNull()?.options.orEmpty())
}

/** What the parser needs from the user's profile. Never logged: [toString] hides the birth number. */
class PaymentProfile(val nationalId: String?, val firstName: String?, val lastName: String?) {
    val fullName: String?
        get() = listOfNotNull(firstName, lastName).map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ").takeIf { it.isNotEmpty() }

    override fun toString(): String = "PaymentProfile"
}

data class PriceHint(
    val currentAmountMinor: Long,
    val lastDayAtThisPrice: LocalDate,
    val nextAmountMinor: Long,
    val daysLeft: Int
)

/** The price-rise warning is shown when at most this many days (inclusive) remain. */
const val PRICE_HINT_DAYS = 7

private enum class Field { ACCOUNT, AMOUNT, VS, SS, MESSAGE, DUE, STORNO }

/** Row labels, already normalized (no diacritics, lowercase). The only place to add a synonym. */
private val LABELS: Map<Field, List<String>> = mapOf(
    Field.ACCOUNT to listOf("cu", "ucet", "cislo uctu", "platba na ucet", "platba na ucet klubu", "bankovni spojeni", "iban"),
    Field.AMOUNT to listOf("castka", "cena", "k uhrade", "suma", "celkem", "platba"),
    Field.VS to listOf("vs", "variabilni symbol"),
    Field.SS to listOf("ss", "specificky symbol"),
    Field.MESSAGE to listOf("poznamka", "zprava", "zprava pro prijemce", "pro prijemce", "popis", "ucel", "do poznamky", "do poznamky napiste", "napiste do poznamky"),
    Field.DUE to listOf("termin", "termin platby", "termin uhrady", "termin splatnosti", "splatnost", "splatnost do", "zaplatit do", "uhrada do", "platba do", "uzavirka prihlasek", "uzavirka", "uzaverka prihlasek", "uzaverka", "do"),
    Field.STORNO to listOf("storno", "storno podminky", "podminky storna", "stornovaci podminky", "odhlaseni", "zruseni")
)

/** Audience words, already normalized and matched as word prefixes. The only place to add a synonym. First hit in the text wins. */
private val AUDIENCE_WORDS: List<Pair<PriceAudience, List<String>>> = listOf(
    PriceAudience.OTHER to listOf("ostatn", "externi", "neclen", "host", "verejnost", "cizi"),
    PriceAudience.CLUB to listOf("olymp", "tkolymp", "klub", "clen", "clenov", "clensk", "vlastni")
)

private val LABEL_TABLE: Map<String, Field> = buildMap {
    for ((field, labels) in LABELS) for (label in labels) put(label.replace(" ", ""), field)
}

private val MD_LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val WHITESPACE = Regex("""\s+""")
private val ACCOUNT_REGEX = Regex("""(?<!\d)(?:\d{1,6}-)?\d{2,10}/\d{4}(?!\d)""")
private val IBAN_REGEX = Regex("""CZ\d{2}(?: ?\d){20}""", RegexOption.IGNORE_CASE)
private val AMOUNT_REGEX = Regex("""(?<!\d)(\d{1,3}(?: \d{3})+|\d+)([.,]\d{1,2})?\s*(?:Kč|CZK|,-)""", RegexOption.IGNORE_CASE)
private val KEYWORD_DATE = Regex("""\b(do|po)\s+(\d{1,2})\.\s*(\d{1,2})\.(?:\s*(20\d{2}))?""", RegexOption.IGNORE_CASE)
private val PLAIN_DATE = Regex("""(\d{1,2})\.\s*(\d{1,2})\.(?:\s*(20\d{2}))?""")

private val NAME_PAIR = Regex("""jm[eé]no\s*(?:a|,|/|i)?\s*p[rř][ií]jmen[ií]""", RegexOption.IGNORE_CASE)
private val NAME_SINGLE = Regex("""jm[eé]no|p[rř][ií]jmen[ií]""", RegexOption.IGNORE_CASE)
private val FIELD_SEPARATOR = Regex("""[,;]\s*""")

/** Labels so unmistakable that "…, VS 123" may start a new field even without a colon. */
private val STRONG_DUE_LABELS = setOf("termin", "splatnost", "uzavirkaprihlasek", "uzavirka", "uzaverkaprihlasek", "uzaverka")

private val DEPOSIT = Regex("""\bzaloh\w*""")
private val REMAINDER = Regex("""\b(?:doplat\w*|zbytek|zbytku)""")
private val SS_IN_TEXT = Regex("""\bss\s*:?\s*(\d{1,10})""", RegexOption.IGNORE_CASE)

private val PAREN = Regex("""\([^)]*\)""")
private val PLACEHOLDER_HINTS = listOf("doplnit", "vyplnit", "nutno", "napis", "uved", "instruk", "jmeno", "prijmeni")
private const val NAME_MARK = "{NAME}"

private val NUMBERED_TITLE = Regex("""^\s*\d+\s*[.)]\s*(.+)$""")
private val BULLET = Regex("""^[\s\-•*–]+""")

private class Line(val raw: String, val field: Field?, val value: String)

fun preprocessPaymentText(text: String): String =
    text.replace(MD_LINK, "$1")
        .replace(' ', ' ').replace(' ', ' ').replace('\t', ' ')
        .replace("\r\n", "\n").replace('\r', '\n')

/**
 * [eventEnd] is the last day of the event: a date written without a year gets the event's year, or the year
 * before when that would be after the event ended. Without it such a date is the nearest one from [today].
 */
fun parsePayment(text: String, profile: PaymentProfile? = null, today: LocalDate, eventEnd: LocalDate? = null): ParsedPayment {
    val clean = preprocessPaymentText(text)
    val lines = clean.split('\n').flatMap(::splitFields).map(::classify)

    var ss: String? = null
    var vs: VsSource? = null
    var message: String? = null
    var dueDate: LocalDate? = null
    val programs = mutableListOf<PriceProgram>()
    var current = LinkedHashSet<AmountOption>()
    var currentTitle: String? = null
    var nextTitle: String? = null
    var boundary = false // a title line or blank line was seen after the current block's prices
    var titleIsNumbered = false
    val storno = mutableListOf<String>()
    var stornoOpen = false
    var depositAmount: Long? = null
    var depositUntil: LocalDate? = null
    var remainderUntil: LocalDate? = null
    var remainderSs: String? = null
    var inRemainderBlock = false // a "Doplatek:" heading was seen; its SS and Termín lines belong to the remainder


    for (line in lines) {
        if (stornoOpen) {
            if (line.field == null && line.raw.isNotBlank()) { storno += line.raw.replace(BULLET, "").trim(); continue }
            stornoOpen = false
        }
        if (inRemainderBlock && line.field == null && line.raw.isBlank()) inRemainderBlock = false
        if (isRemainderLine(line.raw)) {
            inRemainderBlock = true
            if (remainderUntil == null) remainderUntil = keywordDate(line.raw, "do", today, eventEnd)
                ?: PLAIN_DATE.find(line.raw)?.let { dateOf(it.groupValues[1], it.groupValues[2], it.groupValues[3], today, eventEnd) }
            if (remainderSs == null) remainderSs = SS_IN_TEXT.find(line.raw)?.groupValues?.get(1)
            continue
        }
        when (line.field) {
            Field.STORNO -> {
                stornoOpen = true
                line.value.replace(BULLET, "").trim().takeIf { it.isNotEmpty() }?.let { storno += it }
            }
            Field.SS -> if (inRemainderBlock && depositAmount != null) { if (remainderSs == null) remainderSs = parseSymbol(line.value) }
                else if (ss == null) ss = parseSymbol(line.value)
            Field.VS -> if (vs == null) vs = parseVs(line.value)
            Field.MESSAGE -> if (message == null && line.value.isNotBlank()) message = line.value.trim()
            Field.ACCOUNT -> Unit
            else -> {
                val all = pricesOf(line.raw)
                val deposits = all.filter { it.deposit }
                if (deposits.isNotEmpty() && depositAmount == null) {
                    depositAmount = deposits.first().amountMinor
                    depositUntil = keywordDate(line.raw, "do", today, eventEnd)
                }
                val priced = all.filterNot { it.deposit }
                val found = priced.map { it.amountMinor }
                if (deposits.isNotEmpty() && found.isEmpty()) continue
                if (found.isEmpty()) {
                    if (line.field == Field.DUE && inRemainderBlock && depositAmount != null) {
                        if (remainderUntil == null) {
                            remainderUntil = PLAIN_DATE.find(line.value)?.let { dueDateOf(it.groupValues[1], it.groupValues[2], it.groupValues[3], today, eventEnd) }
                        }
                    } else if (line.field == Field.DUE) {
                        if (dueDate == null) {
                            dueDate = PLAIN_DATE.find(line.value)?.let { dueDateOf(it.groupValues[1], it.groupValues[2], it.groupValues[3], today, eventEnd) }
                        }
                    } else {
                        if (line.field == null && line.raw.isNotBlank()) {
                            val numbered = NUMBERED_TITLE.find(line.raw)?.groupValues?.get(1)
                            if (numbered != null) { nextTitle = numbered.trim(); titleIsNumbered = true }
                            else if (!titleIsNumbered) nextTitle = line.raw.trim().trimEnd(':').trim()
                        }
                        boundary = true
                    }
                } else {
                    if (boundary && current.isNotEmpty()) {
                        programs += PriceProgram(currentTitle, current.toList())
                        current = LinkedHashSet()
                    }
                    boundary = false
                    if (current.isEmpty()) { currentTitle = nextTitle; nextTitle = null; titleIsNumbered = false }
                    val until = if (found.size == 1) keywordDate(line.raw, "do", today, eventEnd) else null
                    val after = if (found.size == 1) keywordDate(line.raw, "po", today, eventEnd) else null
                    priced.forEach { current += AmountOption(it.amountMinor, until, after, it.audience, it.perDay) }
                }
            }
        }
    }
    if (current.isNotEmpty()) programs += PriceProgram(currentTitle, current.toList())
    val priceBlocks = mergePerDayPrograms(programs)

    // Without an explicit VS the trainer expects the birth number, so that is the default.
    val variableSymbol = vs ?: VsSource.BirthNumberFromProfile

    val account = pickAccount(lines)
    val iban = IBAN_REGEX.find(clean)?.value?.filterNot { it == ' ' }?.uppercase()

    val template = message?.let(::messageTemplateOf)
    val name = profile?.fullName
    val finalMessage = template?.let { fillName(it, name) } ?: message?.trim()?.takeIf { it.isNotEmpty() }

    val missing = buildList {
        if (account == null && iban == null) add(MissingField.ACCOUNT)
        if (priceBlocks.isEmpty() && depositAmount == null) add(MissingField.AMOUNT)
        if (resolveVariableSymbol(variableSymbol, profile) == null) add(MissingField.VS)
        if (template != null && name == null) add(MissingField.NAME)
    }

    return ParsedPayment(
        account = account,
        iban = iban,
        programs = priceBlocks,
        ss = ss,
        vs = variableSymbol,
        message = finalMessage,
        messageTemplate = template,
        dueDate = dueDate,
        missing = missing,
        storno = storno,
        deposit = depositAmount?.let { Deposit(it, depositUntil, remainderUntil, remainderSs) }
    )
}

/**
 * Per-day prices are price levels, not programs: a block made only of per-day prices (set apart by a title or
 * blank line) joins the full-price program before it, or the first one when it comes first.
 */
private fun mergePerDayPrograms(programs: List<PriceProgram>): List<PriceProgram> {
    fun dayOnly(p: PriceProgram) = p.options.isNotEmpty() && p.options.all { it.perDay }
    if (programs.none(::dayOnly) || programs.all(::dayOnly)) return programs
    val result = mutableListOf<PriceProgram>()
    val early = mutableListOf<AmountOption>()
    for (p in programs) {
        when {
            !dayOnly(p) -> { result += p.copy(options = (p.options + early).distinct()); early.clear() }
            result.isEmpty() -> early += p.options
            else -> result[result.lastIndex] = result.last().let { it.copy(options = (it.options + p.options).distinct()) }
        }
    }
    return result
}

/** Variable symbol to put into the payment, or null when it is unknown (e.g. no birth number in the profile). */
fun resolveVariableSymbol(vs: VsSource?, profile: PaymentProfile?): String? = when (vs) {
    is VsSource.Literal -> vs.digits
    VsSource.BirthNumberFromProfile -> profile?.nationalId?.filter { it.isDigit() }?.take(10)?.takeIf { it.isNotEmpty() }
    null -> null
}

/**
 * Replaces name placeholders in a message ("JMÉNO a PŘÍJMENÍ", "(nutno doplnit)") with [name]. Without a
 * name the placeholder is dropped, so it never reaches a QR code. Messages without a placeholder are unchanged.
 */
fun replaceNamePlaceholder(message: String, name: String?): String? =
    messageTemplateOf(message)?.let { fillName(it, name) } ?: message

/** Substitutes the payer's name into [ParsedPayment.messageTemplate]; an unknown name just drops the marker. */
fun fillName(template: String, name: String?): String? =
    template.replace(NAME_MARK, name.orEmpty()).replace(WHITESPACE, " ").trim().takeIf { it.isNotEmpty() }

/** Checks the Czech account checksum (modulo 11) of the prefix and the number. */
fun isValidAccountChecksum(account: String): Boolean {
    val acc = account.filterNot { it.isWhitespace() }.substringBefore('/')
    val prefix = if ('-' in acc) acc.substringBefore('-') else ""
    val number = acc.substringAfter('-')
    if (prefix.length > 6 || number.length > 10 || number.isEmpty()) return false
    if (!(prefix + number).all { it.isDigit() }) return false
    fun weighted(digits: String, weights: IntArray) = digits.indices.sumOf { (digits[it] - '0') * weights[it] }
    return weighted(prefix.padStart(6, '0'), intArrayOf(10, 5, 8, 4, 2, 1)) % 11 == 0 &&
        weighted(number.padStart(10, '0'), intArrayOf(6, 3, 7, 9, 10, 5, 8, 4, 2, 1)) % 11 == 0
}

/** The price level valid on [today]: the nearest `until` not yet passed, else the `after` level. */
fun selectAmount(payment: ParsedPayment, today: LocalDate): AmountOption? = selectAmount(payment.amounts, today)

fun selectAmount(options: List<AmountOption>, today: LocalDate): AmountOption? {
    val all = applicableOptions(options)
    // Per-day prices need a number of days, so a full price is the default and the user switches to days.
    val amounts = all.filterNot { it.perDay }.ifEmpty { return all.firstOrNull() }
    if (amounts.size <= 1) return amounts.firstOrNull()
    amounts.filter { it.until != null && it.until >= today }.minByOrNull { it.until!! }?.let { return it }
    amounts.filter { it.after != null && it.after < today }.maxByOrNull { it.after!! }?.let { return it }
    return amounts.firstOrNull { it.after != null } ?: amounts.last()
}

fun priceHint(payment: ParsedPayment, today: LocalDate): PriceHint? =
    priceHint(payment.amounts, selectAmount(payment, today), today)

/** Same as above for a level the user picked by hand. */
fun priceHint(options: List<AmountOption>, selected: AmountOption?, today: LocalDate): PriceHint? {
    val amounts = applicableOptions(options).filterNot { it.perDay }
    if (selected == null || selected.perDay || amounts.size < 2) return null
    val until = selected.until ?: return null
    val daysLeft = (until.toEpochDays() - today.toEpochDays()).toInt()
    if (daysLeft < 0 || daysLeft > PRICE_HINT_DAYS) return null
    val ordered = amounts.sortedBy { it.until?.toEpochDays() ?: Long.MAX_VALUE }
    val next = ordered.getOrNull(ordered.indexOf(selected) + 1) ?: return null
    return PriceHint(selected.amountMinor, until, next.amountMinor, daysLeft)
}

/** Amount typed by the user ("450", "1 250,50"), in minor units; null when not a positive number. */
fun parseAmountInput(text: String): Long? =
    parseDecimalToMinorUnits(text.filterNot { it.isWhitespace() }.replace(',', '.'))?.takeIf { it > 0 }

/**
 * The levels the app should offer: the app pays as a club member, so when the text has club prices only those
 * count; when it has "others" next to unlabeled prices, the unlabeled ones count. Otherwise all levels.
 */
fun applicableOptions(options: List<AmountOption>): List<AmountOption> =
    forAudience(options.filterNot { it.perDay }) + forAudience(options.filter { it.perDay })

private fun forAudience(options: List<AmountOption>): List<AmountOption> {
    options.filter { it.audience == PriceAudience.CLUB }.takeIf { it.isNotEmpty() }?.let { return it }
    if (options.any { it.audience == PriceAudience.OTHER }) {
        options.filter { it.audience == null }.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return options
}

/**
 * The level to preselect in the payment card. Null when the user has to choose: a per-day price next to a
 * full price (whichever comes first) is never applied automatically.
 */
fun preselectedAmount(options: List<AmountOption>, today: LocalDate): AmountOption? {
    val applicable = applicableOptions(options)
    if (applicable.any { it.perDay } && applicable.any { !it.perDay }) return null
    return selectAmount(options, today)
}

fun preselectedAmount(payment: ParsedPayment, today: LocalDate): AmountOption? =
    preselectedAmount(payment.programs.firstOrNull()?.options.orEmpty(), today)

/** True when the text names a payment deadline ("Termín:") that has passed: it is too late to pay. */
fun isPastDue(payment: ParsedPayment, today: LocalDate): Boolean = payment.dueDate?.let { it < today } == true

/** True when the level's last day has passed. */
fun isExpired(option: AmountOption, today: LocalDate): Boolean = option.until != null && option.until < today

/** Short Czech label for a price chip, e.g. "5 190 Kč · do 22. 6. 2026". */
fun amountOptionLabel(option: AmountOption): String = buildString {
    option.audience?.let { append(it.label).append(" · ") }
    append(formatCzk(option.amountMinor))
    option.until?.let { append(" · do ").append(formatCzDate(it)) }
    if (option.perDay) append(" / den")
    option.after?.let { append(" · po ").append(formatCzDate(it)) }
}

/** Explanation under greyed-out chips, or null when no level has expired. */
fun expiredPricesNote(options: List<AmountOption>, today: LocalDate): String? {
    val expired = options.filter { isExpired(it, today) }
    if (expired.isEmpty()) return null
    return expired.joinToString("\n") { "Cena ${formatCzk(it.amountMinor)} platila do ${formatCzDate(it.until!!)} a už není k dispozici." }
}

/** True when the programs have different prices; identical price blocks need no program choice. */
fun programsDiffer(payment: ParsedPayment): Boolean = payment.programs.map { applicableOptions(it.options) }.distinct().size > 1

/** Levels that must be picked by hand: several programs, or several prices that no date can tell apart. */
fun needsUserChoice(payment: ParsedPayment): Boolean =
    programsDiffer(payment) || payment.deposit != null || payment.amounts.any { it.perDay } ||
        payment.amounts.size > 1 && payment.amounts.any { it.until == null && it.after == null }

/** Czech warning text, e.g. "Cena 5 190 Kč platí ještě do 22. 6. 2026 (zbývají 3 dny), poté 5 490 Kč." */
fun priceHintMessage(hint: PriceHint): String {
    val price = formatCzk(hint.currentAmountMinor)
    val next = formatCzk(hint.nextAmountMinor)
    val date = formatCzDate(hint.lastDayAtThisPrice)
    return when (hint.daysLeft) {
        0 -> "Cena $price platí dnes naposledy ($date), poté $next."
        1 -> "Cena $price platí ještě zítra ($date), poté $next."
        else -> "Cena $price platí ještě do $date (${remainingDays(hint.daysLeft)}), poté $next."
    }
}

private fun remainingDays(n: Int): String = when (n) {
    1 -> "zbývá 1 den"
    in 2..4 -> "zbývají $n dny"
    else -> "zbývá $n dní"
}

/** "5 190 Kč" / "449,50 Kč". */
fun formatCzk(amountMinor: Long): String {
    val whole = (amountMinor / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val cents = amountMinor % 100
    return if (cents == 0L) "$whole Kč" else "$whole,${cents.toString().padStart(2, '0')} Kč"
}

fun formatCzDate(date: LocalDate): String = "${date.day}. ${date.month.ordinal + 1}. ${date.year}"

/** Turns an event description (HTML) into line-separated plain text for [parsePayment]. */
fun htmlToPlainText(html: String): String =
    html.replace(Regex("""<\s*br\s*/?\s*>|</\s*(p|div|li|h[1-6]|tr)\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]*>"""), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace("&amp;", "&")

private fun normalizeLabel(raw: String): String =
    removeDiacritics(raw).lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == ' ' }
        .replace(WHITESPACE, " ").trim()

private fun matchLabel(label: String): Field? {
    val key = normalizeLabel(label).replace(" ", "")
    if (key.isEmpty()) return null
    LABEL_TABLE[key]?.let { return it }
    if (key.length <= 3) return null
    return LABEL_TABLE.entries.firstOrNull { (k, _) -> k.length > 3 && levenshteinAtMostOne(k, key) }?.value
}

/**
 * "Účet: 123/0800, VS: 12, SS: 34" and "Účet 123/0800, VS 12, SS 34" become separate lines. A comma or semicolon
 * splits when a known label with a colon follows, or (without a colon) an account / VS / SS / deadline label,
 * so "449,50 Kč" and "poznámka: Novák, Jan" stay intact.
 */
private fun splitFields(raw: String): List<String> {
    if (isRemainderLine(raw)) return listOf(raw)
    val parts = mutableListOf<String>()
    var start = 0
    for (m in FIELD_SEPARATOR.findAll(raw)) {
        if (!startsField(raw.substring(m.range.last + 1))) continue
        parts += raw.substring(start, m.range.first)
        start = m.range.last + 1
    }
    parts += raw.substring(start)
    return parts
}

private fun isRemainderLine(raw: String): Boolean = REMAINDER.containsMatchIn(removeDiacritics(raw).lowercase())

private fun startsField(rest: String): Boolean {
    val colon = rest.indexOf(':')
    if (colon in 1..40) {
        val label = rest.substring(0, colon)
        if (',' !in label && ';' !in label && matchLabel(label) != null) return true
    }
    val tokens = rest.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    for (k in minOf(4, tokens.size) downTo 1) {
        val label = tokens.take(k).joinToString(" ")
        val field = matchLabel(label) ?: continue
        val key = normalizeLabel(label).replace(" ", "")
        return field == Field.ACCOUNT || field == Field.VS || field == Field.SS || key in STRONG_DUE_LABELS
    }
    return false
}

private fun classify(raw: String): Line {
    val colon = raw.indexOf(':')
    if (colon >= 0) {
        matchLabel(raw.substring(0, colon))?.let { return Line(raw, it, raw.substring(colon + 1).trim()) }
    }
    val tokens = raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    for (k in minOf(4, tokens.size) downTo 1) {
        val field = matchLabel(tokens.take(k).joinToString(" ")) ?: continue
        return Line(raw, field, tokens.drop(k).joinToString(" ").removePrefix(":").trim())
    }
    return Line(raw, null, raw.trim())
}

/** True when the edit distance between [a] and [b] is at most 1. */
private fun levenshteinAtMostOne(a: String, b: String): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return when {
        a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
        a.length > b.length -> a.substring(i + 1) == b.substring(i)
        else -> a.substring(i) == b.substring(i + 1)
    }
}

private fun pickAccount(lines: List<Line>): String? {
    class Candidate(val account: String, val labeled: Boolean) {
        val valid = isValidAccountChecksum(account)
    }
    val candidates = lines
        .filter { it.field != Field.VS && it.field != Field.SS && it.field != Field.MESSAGE }
        .flatMap { line ->
            ACCOUNT_REGEX.findAll(line.raw).map { Candidate(it.value.filterNot { c -> c.isWhitespace() }, line.field == Field.ACCOUNT) }.toList()
        }
    return (candidates.firstOrNull { it.labeled && it.valid }
        ?: candidates.firstOrNull { it.valid }
        ?: candidates.firstOrNull { it.labeled }
        ?: candidates.firstOrNull())?.account
}

private fun audienceOf(segment: String): PriceAudience? {
    for (word in normalizeLabel(segment).split(' ')) {
        if (word.isEmpty()) continue
        AUDIENCE_WORDS.firstOrNull { (_, prefixes) -> prefixes.any { word.startsWith(it) } }?.let { return it.first }
    }
    return null
}

private class Priced(val amountMinor: Long, val audience: PriceAudience?, val perDay: Boolean, val deposit: Boolean)

private val PER_DAY = Regex("""(?:/|\b(?:za|na|per|kazdy)\s)\s*(?:1\s*)?den(?:i|e)?\b|\bdenn[ie]\b|\bjednotliv\w*\s+dn[yi]\b""")

/** True for "/ den", "za den", "denně", "denní cena", "jednotlivé dny"; any position in the text. */
private fun isPerDay(segment: String): Boolean = PER_DAY.containsMatchIn(removeDiacritics(segment).lowercase())

private fun isDeposit(segment: String): Boolean = DEPOSIT.containsMatchIn(removeDiacritics(segment).lowercase())

private val SEPARATORS = charArrayOf(',', ';', '.')

/**
 * The label of each amount on a line, found by [find] in the text around it. Between two amounts the last
 * separator (, ; .) splits the text: what precedes it labels the amount before ("5 000 Kč klubová, 6 000 Kč"),
 * what follows it the amount after ("Cena: 5 990 Kč. Členové Olympu: 4 800 Kč"). Without a separator the text
 * goes to the amount after when the text before the first amount carries a label too, else to the one before.
 */
private fun <T : Any> labelsOf(raw: String, matches: List<MatchResult>, find: (String) -> T?): List<T?> {
    fun between(i: Int) = raw.substring(matches[i].range.last + 1, matches[i + 1].range.first)
    val head = raw.substring(0, matches.first().range.first)
    val labelBefore = find(head) != null
    return matches.indices.map { i ->
        val prefix = if (i == 0) head else between(i - 1).let { gap ->
            val k = gap.lastIndexOfAny(SEPARATORS)
            if (k >= 0) gap.substring(k + 1) else if (labelBefore) gap else ""
        }
        val suffix = if (i == matches.lastIndex) raw.substring(matches[i].range.last + 1) else between(i).let { gap ->
            val k = gap.lastIndexOfAny(SEPARATORS)
            if (k >= 0) gap.substring(0, k) else if (!labelBefore) gap else ""
        }
        find(prefix) ?: find(suffix)
    }
}

/** "/ 45 min", "/45min", "za hodinu", "/ lekci" right after an amount: a unit price, not a sum to pay. */
private val PER_TIME_UNIT = Regex("""^\s*(?:/|\b(?:za|na|per)\s)\s*\d*\s*(?:min|hod|h\b|lek|tren|hodin)""")

private fun isPerTimeUnit(raw: String, match: MatchResult): Boolean =
    PER_TIME_UNIT.containsMatchIn(removeDiacritics(raw.substring(match.range.last + 1)).lowercase())

/** Amounts of one line with their audience, per-day and deposit marks; never depends on the order of the amounts. */
private fun pricesOf(raw: String): List<Priced> {
    val matches = AMOUNT_REGEX.findAll(raw).toList()
    if (matches.isEmpty()) return emptyList()
    val audiences = labelsOf(raw, matches, ::audienceOf)
    val perDays = labelsOf(raw, matches) { if (isPerDay(it)) true else null }
    val deposits = labelsOf(raw, matches) { if (isDeposit(it)) true else null }
    return matches.indices.mapNotNull { i ->
        if (isPerTimeUnit(raw, matches[i])) return@mapNotNull null
        val amount = toMinorUnits(matches[i]) ?: return@mapNotNull null
        Priced(amount, audiences[i], perDays[i] == true, deposits[i] == true)
    }
}

private fun parseVs(value: String): VsSource? {
    val key = normalizeLabel(value)
    if ("rodn" in key || "kazdy svuj" in key || "profil" in key) return VsSource.BirthNumberFromProfile
    return parseSymbol(value)?.let { VsSource.Literal(it) }
}

private fun parseSymbol(value: String): String? {
    val compact = value.filterNot { it.isWhitespace() }
    val digits = if (compact.isNotEmpty() && compact.all { it.isDigit() }) compact
    else Regex("""\d+""").find(value)?.value
    return digits?.take(10)?.takeIf { it.isNotEmpty() }
}

private fun toMinorUnits(match: MatchResult): Long? {
    val whole = match.groupValues[1].replace(" ", "")
    if (whole.length > 9) return null
    val cents = match.groupValues[2].drop(1).padEnd(2, '0')
    return (whole.toLongOrNull() ?: return null) * 100 + (cents.toLongOrNull() ?: 0L)
}

private fun keywordDate(line: String, keyword: String, today: LocalDate, eventEnd: LocalDate?): LocalDate? =
    KEYWORD_DATE.findAll(line).firstOrNull { it.groupValues[1].equals(keyword, ignoreCase = true) }
        ?.let { dateOf(it.groupValues[2], it.groupValues[3], it.groupValues[4], today, eventEnd) }

/** A deadline without a year: this year unless that is more than half a year ago, then next year. */
private fun dueDateOf(day: String, month: String, year: String, today: LocalDate, eventEnd: LocalDate?): LocalDate? = try {
    if (year.isNotEmpty()) LocalDate(year.toInt(), month.toInt(), day.toInt())
    else if (eventEnd != null) eventYearDate(day.toInt(), month.toInt(), eventEnd)
    else LocalDate(today.year, month.toInt(), day.toInt()).let {
        if (today.toEpochDays() - it.toEpochDays() > 183) LocalDate(today.year + 1, it.month.ordinal + 1, it.day) else it
    }
} catch (_: IllegalArgumentException) {
    null
}

/** Year of the event; the year before when the date would then fall after the event's last day. */
private fun eventYearDate(day: Int, month: Int, eventEnd: LocalDate): LocalDate =
    LocalDate(eventEnd.year, month, day).let { if (it > eventEnd) LocalDate(eventEnd.year - 1, month, day) else it }

/** Builds a date; a missing year becomes the nearest one that is not in the past. */
private fun dateOf(day: String, month: String, year: String, today: LocalDate, eventEnd: LocalDate?): LocalDate? = try {
    val d = day.toInt()
    val m = month.toInt()
    if (year.isNotEmpty()) LocalDate(year.toInt(), m, d)
    else if (eventEnd != null) eventYearDate(d, m, eventEnd)
    else LocalDate(today.year, m, d).let { if (it < today) LocalDate(today.year + 1, m, d) else it }
} catch (_: IllegalArgumentException) {
    null
}

/** Replaces name placeholders and instruction brackets in [message] with [NAME_MARK]; null when there are none. */
private fun messageTemplateOf(message: String): String? {
    var placeholder = false
    var text = PAREN.replace(message) { m ->
        val key = normalizeLabel(m.value)
        if (PLACEHOLDER_HINTS.any { it in key }) { placeholder = true; "" } else m.value
    }
    if (NAME_SINGLE.containsMatchIn(text)) {
        placeholder = true
        text = NAME_PAIR.replace(text, NAME_MARK).let { NAME_SINGLE.replace(it, NAME_MARK) }
        while ("$NAME_MARK $NAME_MARK" in text) text = text.replace("$NAME_MARK $NAME_MARK", NAME_MARK)
    }
    if (!placeholder) return null
    if (NAME_MARK !in text) text = "$text $NAME_MARK"
    return text.replace(WHITESPACE, " ").trim()
}
