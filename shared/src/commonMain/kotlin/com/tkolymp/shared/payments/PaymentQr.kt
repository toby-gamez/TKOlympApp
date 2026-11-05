package com.tkolymp.shared.payments

/**
 * Builds the Czech "QR Platba" (SPD 1.0) payload, which Czech/Slovak banking apps can read
 * from an image. Returns null when the account number cannot be converted to an IBAN.
 */
fun buildSpdPayload(
    account: String,
    amountMinor: Long?,
    currency: String?,
    variableSymbol: String?,
    specificSymbol: String?,
    message: String?
): String? {
    val iban = toIban(account) ?: return null
    val sb = StringBuilder("SPD*1.0*ACC:").append(iban)
    if (amountMinor != null && amountMinor > 0) {
        sb.append("*AM:").append(amountMinor / 100).append('.').append((amountMinor % 100).toString().padStart(2, '0'))
    }
    sb.append("*CC:").append(currency?.takeIf { it.length == 3 }?.uppercase() ?: "CZK")
    variableSymbol?.filter { it.isDigit() }?.take(10)?.takeIf { it.isNotEmpty() }?.let { sb.append("*X-VS:").append(it) }
    specificSymbol?.filter { it.isDigit() }?.take(10)?.takeIf { it.isNotEmpty() }?.let { sb.append("*X-SS:").append(it) }
    message?.let(::spdSafeText)?.takeIf { it.isNotEmpty() }?.let { sb.append("*MSG:").append(it) }
    return sb.toString()
}

/** Accepts an IBAN or a Czech `[prefix-]number/bankCode` account and returns an IBAN. */
internal fun toIban(raw: String): String? {
    val account = raw.filterNot { it.isWhitespace() }
    if (account.length >= 15 && account[0].isLetter() && account[1].isLetter()) {
        return account.uppercase()
    }
    val slash = account.indexOf('/')
    if (slash < 0) return null
    val bank = account.substring(slash + 1)
    val left = account.substring(0, slash)
    val dash = left.indexOf('-')
    val prefix = if (dash < 0) "" else left.substring(0, dash)
    val number = if (dash < 0) left else left.substring(dash + 1)
    if (bank.length != 4 || prefix.length > 6 || number.isEmpty() || number.length > 10) return null
    if (!(bank + prefix + number).all { it.isDigit() }) return null

    val bban = bank + prefix.padStart(6, '0') + number.padStart(10, '0')
    // "CZ00" rearranged: bban + C(12) Z(35) + 00
    val remainder = mod97(bban + "123500")
    val check = (98 - remainder).toString().padStart(2, '0')
    return "CZ$check$bban"
}

private fun mod97(digits: String): Int {
    var rem = 0
    for (ch in digits) rem = (rem * 10 + (ch - '0')) % 97
    return rem
}

private const val DIACRITICS_FROM = "áäčďéěëíĺľňóôöŕřšťúůüýžÁÄČĎÉĚËÍĹĽŇÓÔÖŔŘŠŤÚŮÜÝŽ"
private const val DIACRITICS_TO   = "aacdeeeillnooorrstuuuyzAACDEEEILLNOOORRSTUUUYZ"

// SPD message: upper-case ASCII, no '*', max 60 characters.
private fun spdSafeText(text: String): String =
    text.map { c -> DIACRITICS_FROM.indexOf(c).let { if (it >= 0) DIACRITICS_TO[it] else c } }
        .filter { it.code in 32..126 && it != '*' }
        .joinToString("")
        .trim()
        .uppercase()
        .take(60)
