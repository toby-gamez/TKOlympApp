package com.tkolymp.tkolympapp.platform

private val htmlTagRegex = Regex("<[^>]*>")
private val plainUrlRegex = Regex("""https?://[^\s<>"')]+""")
private val bankAccountRegex = Regex("""\b\d{2,10}/\d{4}\b""")
private val telAnchorRegex = Regex(
    """<a\b[^>]*href\s*=\s*["']tel:[^"']*["'][^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
// Some upstream auto-linkers only recognize the digits before the "/" as a phone number,
// leaving "/xxxx" (the bank code) dangling as plain text right after the closing </a>.
private val telAnchorWithBankSuffixRegex = Regex(
    """<a\b[^>]*href\s*=\s*["']tel:[^"']*["'][^>]*>\s*(\d{2,10})\s*</a>\s*/\s*(\d{4})\b""",
    RegexOption.IGNORE_CASE
)

/**
 * Wraps bare bank-account numbers ("xxxxxxxxxx/xxxx") in a tappable span and bare
 * "http(s)://" URLs (including ones inside parentheses) in an `<a>` tag, without touching
 * text that is already inside an existing tag (e.g. an `<a>`/`<img>` attribute or an
 * already-linked URL). Needed because the source HTML itself sometimes already contains
 * `<a href="tel:...">` around bank account numbers (produced upstream by whatever
 * auto-linked the event description), which otherwise stays a callable phone link forever
 * since it's already anchored; plain-text URLs are otherwise never made clickable.
 */
internal fun autoLinkHtmlBody(html: String): String {
    // Merge a tel: anchor that only wraps the digits before the "/" back together with the
    // dangling "/xxxx" bank code that follows it, into a single plain account number.
    val withMergedSplitAccounts = telAnchorWithBankSuffixRegex.replace(html) { m ->
        "${m.groupValues[1]}/${m.groupValues[2]}"
    }
    // Unwrap any remaining pre-existing tel: link whose visible text is actually a bank
    // account number, so the pass below can re-detect and re-wrap it as a copyable span.
    val withoutBankTelLinks = telAnchorRegex.replace(withMergedSplitAccounts) { m ->
        val innerText = m.groupValues[1].replace(htmlTagRegex, "").trim()
        if (bankAccountRegex.containsMatchIn(innerText)) innerText else m.value
    }

    val sb = StringBuilder()
    var lastIndex = 0
    var insideAnchor = false
    for (match in htmlTagRegex.findAll(withoutBankTelLinks)) {
        val textSegment = withoutBankTelLinks.substring(lastIndex, match.range.first)
        sb.append(if (insideAnchor) textSegment else transformPlainTextSegment(textSegment))
        sb.append(match.value)
        val tagLower = match.value.lowercase()
        if (tagLower.startsWith("<a ") || tagLower == "<a>") insideAnchor = true
        else if (tagLower.startsWith("</a")) insideAnchor = false
        lastIndex = match.range.last + 1
    }
    val tailSegment = withoutBankTelLinks.substring(lastIndex)
    sb.append(if (insideAnchor) tailSegment else transformPlainTextSegment(tailSegment))
    return sb.toString()
}

private fun transformPlainTextSegment(text: String): String {
    if (text.isBlank()) return text
    val withBankAccounts = bankAccountRegex.replace(text) { m ->
        "<span class=\"tko-bank-account\" data-account=\"${m.value}\">${m.value}</span>"
    }
    return plainUrlRegex.replace(withBankAccounts) { m ->
        val trimmed = m.value.trimEnd('.', ',', ';', ':', '!', '?')
        val trailingPunctuation = m.value.substring(trimmed.length)
        "<a href=\"$trimmed\">$trimmed</a>$trailingPunctuation"
    }
}
