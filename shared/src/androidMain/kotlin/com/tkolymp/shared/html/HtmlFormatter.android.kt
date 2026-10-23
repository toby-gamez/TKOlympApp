package com.tkolymp.shared.html

import android.text.Html

actual class HtmlFormatter actual constructor() {
    actual fun format(html: String): Any? = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
}
