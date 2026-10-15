package com.tkolymp.tkolympapp

import com.tkolymp.shared.html.HtmlSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HtmlSanitizerTest {
    @Test fun keepsBasicFormatting() {
        assertEquals("<p><b>Hi</b> <a href=\"https://a.cz/x?a=1&amp;b=2\">link</a></p>",
            HtmlSanitizer.sanitize("<p><b>Hi</b> <a href=\"https://a.cz/x?a=1&amp;b=2\" onclick=\"x()\" style=\"color:red\">link</a></p>"))
    }

    @Test fun removesScriptWithContent() {
        val out = HtmlSanitizer.sanitize("a<script>alert(1)</script>b<SCRIPT src=x></SCRIPT>c")
        assertEquals("abc", out)
    }

    @Test fun dropsEventHandlersAndUnknownTags() {
        val out = HtmlSanitizer.sanitize("<img src=\"https://a.cz/i.png\" onerror=\"alert(1)\"><iframe src=\"x\"></iframe><custom>t</custom>")
        assertEquals("<img src=\"https://a.cz/i.png\">t", out)
    }

    @Test fun blocksDangerousUrls() {
        assertEquals("<a>x</a>", HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>"))
        assertEquals("<a>x</a>", HtmlSanitizer.sanitize("<a href=\" java\tscript:alert(1)\">x</a>"))
        assertEquals("<a>x</a>", HtmlSanitizer.sanitize("<a href=\"java&#115;cript:alert(1)\">x</a>"))
        assertEquals("<img>", HtmlSanitizer.sanitize("<img src=\"data:text/html;base64,AAAA\">"))
    }

    @Test fun malformedTagsAreEscaped() {
        val out = HtmlSanitizer.sanitize("<a href=\"x>y<img src=x onerror=alert(1)>")
        assertFalse(out.contains("<img src=x"))
        assertFalse(out.contains("onerror=alert(1)>"))
    }

    @Test fun stripsComments() {
        assertEquals("ab", HtmlSanitizer.sanitize("a<!-- <script>x</script> -->b"))
    }
}
