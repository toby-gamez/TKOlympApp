package com.tkolymp.shared.html

/**
 * Allowlist sanitizer for HTML that comes from the backend (announcements, event descriptions, profile text)
 * before it is rendered in a WebView. Anything not explicitly allowed — scripts, event handlers, iframes,
 * forms, `javascript:`/`data:` URLs, inline styles — is dropped. Text content of unknown tags is kept.
 *
 * Deliberately conservative and dependency-free; it tokenises tag by tag and escapes every `<` that is not
 * the start of a well-formed tag it understood, so malformed markup can't be re-interpreted by the browser.
 */
object HtmlSanitizer {
    private val allowedTags = setOf(
        "p", "br", "div", "span", "hr",
        "b", "strong", "i", "em", "u", "s", "strike", "del", "ins", "mark", "code", "pre", "sub", "sup", "small",
        "a", "img", "ul", "ol", "li", "blockquote",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption",
        "figure", "figcaption",
    )

    private val voidTags = setOf("br", "hr", "img")

    /** Tags whose whole content (not just the tag) must disappear. */
    private val droppedWithContent = Regex(
        "<\\s*(script|style|iframe|object|embed|noscript|template|svg|math|form|textarea|select|button|head|title)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val comments = Regex("<!--.*?(-->|$)", setOf(RegexOption.DOT_MATCHES_ALL))
    private val tagRegex = Regex("<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*)>")
    private val attrRegex = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?")

    private val allowedAttrs = mapOf(
        "a" to setOf("href"),
        "img" to setOf("src", "alt"),
        "td" to setOf("colspan", "rowspan"),
        "th" to setOf("colspan", "rowspan"),
    )

    private val linkSchemes = listOf("http://", "https://", "mailto:", "tel:")
    private val imageSchemes = listOf("https://")

    /**
     * Hosts (and their subdomains) whose images are loaded inline. Any other image would be fetched as soon as
     * the content is shown, telling that host the member's IP address and when they read it (tracking pixel),
     * so it is rendered as a plain link the member can choose to open instead.
     */
    val trustedImageHosts: Set<String> = setOf("tkolymp.cz")

    /** CSP for the WebViews that render sanitized HTML (Android + iOS); its img-src mirrors [trustedImageHosts]. */
    val CONTENT_SECURITY_POLICY: String =
        "default-src 'none'; img-src data: " + trustedImageHosts.joinToString(" ") { "https://$it https://*.$it" } +
            "; style-src 'unsafe-inline'; script-src 'none'; form-action 'none'; base-uri 'none'"

    fun sanitize(html: String): String {
        val source = comments.replace(droppedWithContent.replace(html, ""), "")
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c != '<') {
                out.append(c)
                i++
                continue
            }
            val m = tagRegex.matchAt(source, i)
            if (m == null) {
                out.append("&lt;")
                i++
                continue
            }
            i += m.value.length
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            if (name !in allowedTags) continue
            if (closing) {
                if (name !in voidTags) out.append("</").append(name).append('>')
                continue
            }
            val allowed = allowedAttrs[name].orEmpty()
            val kept = linkedMapOf<String, String>()
            val seen = mutableSetOf<String>()
            for (a in attrRegex.findAll(m.groupValues[3])) {
                val attr = a.groupValues[1].lowercase()
                if (attr !in allowed || !seen.add(attr)) continue
                val raw = a.groupValues[2].ifEmpty { a.groupValues[3].ifEmpty { a.groupValues[4] } }
                val value = when (attr) {
                    "href" -> safeUrl(raw, linkSchemes)
                    "src" -> safeUrl(raw, imageSchemes)
                    "colspan", "rowspan" -> raw.trim().takeIf { it.length in 1..2 && it.all(Char::isDigit) }
                    else -> raw
                } ?: continue
                kept[attr] = value
            }
            val src = kept["src"]
            if (name == "img" && src != null && !isTrustedImageHost(src)) {
                val label = kept["alt"]?.takeIf { it.isNotBlank() } ?: src
                out.append("<a href=\"").append(escapeAttr(src)).append("\">").append(escapeText(label)).append("</a>")
                continue
            }
            out.append('<').append(name)
            for ((attr, value) in kept) {
                out.append(' ').append(attr).append("=\"").append(escapeAttr(value)).append('"')
            }
            out.append('>')
        }
        return out.toString()
    }

    /** Returns [raw] when it is an absolute URL with an allowed scheme; entity/whitespace tricks are rejected. */
    private fun safeUrl(raw: String, schemes: List<String>): String? {
        val url = raw.trim()
        if (url.isEmpty() || url.any { it.isISOControl() || it.isWhitespace() }) return null
        val lower = url.lowercase()
        // Reject encoded scheme separators before the first path segment (e.g. "java&#x73;cript:").
        val head = lower.substringBefore('/')
        if ("&" in head) return null
        return url.takeIf { schemes.any { lower.startsWith(it) } }
    }

    /** True for an https URL whose host is one of [trustedImageHosts] or a subdomain of one. */
    internal fun isTrustedImageHost(url: String): Boolean {
        if (!url.lowercase().startsWith("https://")) return false
        val authority = url.substring("https://".length).takeWhile { it != '/' && it != '?' && it != '#' }
        if ('@' in authority || '\\' in authority) return false
        val host = authority.substringBefore(':').lowercase().trimEnd('.')
        return trustedImageHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun escapeText(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttr(value: String): String =
        value.replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
