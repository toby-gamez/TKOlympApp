package com.tkolymp.shared.html

import platform.Foundation.NSAttributedString
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSHTMLTextDocumentType
import platform.Foundation.NSDocumentTypeDocumentAttribute
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual class HtmlFormatter actual constructor() {
    actual fun format(html: String): Any? {
        val data = (html as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return null
        val options = mapOf<Any?, Any?>(NSDocumentTypeDocumentAttribute to NSHTMLTextDocumentType)
        return try {
            NSAttributedString(data, options, null)
        } catch (_: Exception) { null }
    }
}
