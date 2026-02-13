package com.tkolymp.shared.html

import android.os.Build
import android.text.Html
import android.text.Spanned

actual class HtmlFormatter actual constructor() {
    actual fun format(html: String): Any? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }
}
