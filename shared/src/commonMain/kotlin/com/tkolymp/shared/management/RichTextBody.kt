package com.tkolymp.shared.management

/**
 * Converts between the HTML stored by the backend (announcement bodies, event descriptions)
 * and the plain text edited in the app. Only "simple" HTML (paragraphs and line breaks) is
 * round-tripped as plain text; anything richer is edited as raw HTML so no formatting made
 * in the web administration is silently lost.
 */
object RichTextBody {
    private val tagRegex = Regex("<\\s*(/?)\\s*([a-zA-Z0-9]+)([^>]*)>")
    private val simpleTags = setOf("p", "br")

    data class Editable(val text: String, val isRawHtml: Boolean)

    fun isSimpleHtml(html: String): Boolean =
        tagRegex.findAll(html).all { m ->
            val tag = m.groupValues[2].lowercase()
            val attrs = m.groupValues[3].trim().removeSuffix("/").trim()
            tag in simpleTags && attrs.isEmpty()
        }

    fun forEditing(html: String?): Editable {
        val source = html.orEmpty()
        if (source.isBlank()) return Editable("", isRawHtml = false)
        return if (isSimpleHtml(source)) Editable(htmlToPlainText(source), isRawHtml = false)
        else Editable(source, isRawHtml = true)
    }

    fun forSaving(text: String, isRawHtml: Boolean): String =
        if (isRawHtml) text.trim() else plainTextToHtml(text)

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

    fun htmlToPlainText(html: String): String {
        var s = html.replace("\r\n", "\n")
        s = s.replace(Regex("\\s*<\\s*/\\s*p\\s*>\\s*<\\s*p\\s*>\\s*", RegexOption.IGNORE_CASE), "\n\n")
        s = s.replace(Regex("<\\s*br\\s*/?\\s*>\\n?", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<\\s*/?\\s*p\\s*>", RegexOption.IGNORE_CASE), "")
        return unescape(s).trim()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
