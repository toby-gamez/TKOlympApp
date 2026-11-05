package com.tkolymp.tkolympapp

import com.tkolymp.shared.html.HtmlSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val out = HtmlSanitizer.sanitize("<img src=\"https://tkolymp.cz/f/i.png\" onerror=\"alert(1)\"><iframe src=\"x\"></iframe><custom>t</custom>")
        assertEquals("<img src=\"https://tkolymp.cz/f/i.png\">t", out)
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

    @Test fun untrustedImagesBecomeLinks() {
        assertEquals("<a href=\"https://evil.cz/p.gif\">Plakát</a>",
            HtmlSanitizer.sanitize("<img src=\"https://evil.cz/p.gif\" alt=\"Plakát\">"))
        assertEquals("<a href=\"https://a.cz/i.png\">https://a.cz/i.png</a>",
            HtmlSanitizer.sanitize("<img src=\"https://a.cz/i.png\">"))
        assertEquals("<img src=\"https://cdn.tkolymp.cz/i.png\">",
            HtmlSanitizer.sanitize("<img src=\"https://cdn.tkolymp.cz/i.png\">"))
        // http images are no longer loaded at all (mixed content), and look-alike hosts are not trusted.
        assertEquals("<img>", HtmlSanitizer.sanitize("<img src=\"http://tkolymp.cz/i.png\">"))
    }

    @Test fun trustedHostCheckRejectsLookalikes() {
        assertTrue(HtmlSanitizer.isTrustedImageHost("https://tkolymp.cz/f/1"))
        assertTrue(HtmlSanitizer.isTrustedImageHost("https://www.tkolymp.cz:443/f/1"))
        assertFalse(HtmlSanitizer.isTrustedImageHost("https://tkolymp.cz.evil.com/x"))
        assertFalse(HtmlSanitizer.isTrustedImageHost("https://eviltkolymp.cz/x"))
        assertFalse(HtmlSanitizer.isTrustedImageHost("https://tkolymp.cz@evil.com/x"))
    }
}
