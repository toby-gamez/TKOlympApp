package com.tkolymp.shared.management

/**
 * Helpers around the HTML stored by the backend (announcement bodies, event descriptions)
 * and edited in the app's rich-text editor.
 *
 * The editor understands inline formatting, headings, lists, links and alignment. HTML
 * using anything else (images, tables, embeds, …) is opened in HTML source mode instead,
 * so formatting made in the web administration is never silently dropped.
 */
object RichTextBody {
    private val tagRegex = Regex("<\\s*(/?)\\s*([a-zA-Z0-9]+)([^>]*)>")

    /** Tags the rich-text editor can load and save back without losing content. */
    private val editorTags = setOf(
        "p", "br", "div", "span",
        "b", "strong", "i", "em", "u", "s", "strike", "del", "mark", "code", "sub", "sup", "small",
        "a", "ul", "ol", "li",
        "h1", "h2", "h3", "h4", "h5", "h6",
    )

    fun hasTags(text: String): Boolean = tagRegex.containsMatchIn(text)

    fun isEditorCompatible(html: String): Boolean =
        tagRegex.findAll(html).all { it.groupValues[2].lowercase() in editorTags }

    /** HTML to load into the editor; legacy plain text keeps its line breaks. */
    fun toEditorHtml(stored: String?): String {
        val source = stored.orEmpty()
        if (source.isBlank()) return ""
        return if (hasTags(source)) source else plainTextToHtml(source)
    }

    /** Normalizes editor output: an editor with no visible text is stored as "". */
    fun normalizeForStorage(html: String, visibleText: String): String =
        if (visibleText.isBlank() && !html.contains("<img", ignoreCase = true)) "" else html.trim()

    fun plainTextToHtml(text: String): String {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return ""
        return normalized.split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("") { paragraph ->
                "<p>" + paragraph.lines().joinToString("<br>") { escape(it) } + "</p>"
            }
    }

    /** Accepts `example.com` as well as full URLs; returns null for anything unusable. */
    fun normalizeUrl(input: String): String? {
        val url = input.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        val lower = url.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") ||
                lower.startsWith("mailto:") || lower.startsWith("tel:") -> url
            lower.contains(":") -> null // javascript:, data:, … are not allowed
            url.contains("@") && !url.contains("/") -> "mailto:$url"
            url.contains(".") -> "https://$url"
            else -> null
        }
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
