package com.tkolymp.shared.html

/**
 * Cross-platform HTML formatter. Platform implementations should map HTML string
 * to a platform-specific styled text type (Android: Spanned, iOS: NSAttributedString)
 */
expect class HtmlFormatter() {
    fun format(html: String): Any?
}
