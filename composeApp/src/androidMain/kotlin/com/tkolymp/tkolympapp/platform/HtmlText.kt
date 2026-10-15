package com.tkolymp.tkolympapp.platform

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun HtmlText(
    html: String,
    modifier: Modifier,
    textColor: Color,
    linkColor: Color,
    textSizeSp: Float,
    selectable: Boolean,
    onImageClick: ((String) -> Unit)?,
    onBankAccountCopy: ((String) -> Unit)?
) {
    var contentHeightPx by remember { mutableStateOf(0) }
    val pinnedClient = remember { com.tkolymp.shared.pinnedOkHttpClient() }
    val density = LocalDensity.current
    val sizedModifier = modifier
    var lastLoadedHtml by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = sizedModifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // JS needed only for height measurement; no addJavascriptInterface used
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                isScrollContainer = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (!selectable) {
                    // Disable all scrolling/touch events if not selectable
                    setOnTouchListener { _, _ -> true }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Only a real tap may leave the page, and only for plain web/mail/phone links;
                        // content must not be able to fire intent:/custom-scheme URLs via script redirects.
                        val scheme = request.url.scheme?.lowercase()
                        if (!request.hasGesture() || scheme !in setOf("http", "https", "mailto", "tel")) return true
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { ctx.startActivity(intent) } catch (_: Throwable) {}
                        return true
                    }

                    // Uploaded files (/f/...) may require the user's login; the WebView has no session,
                    // so fetch those from our own host with the Bearer token attached.
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                        val url = request.url
                        if (request.method != "GET" || url.scheme != "https" || url.host != "tkolymp.cz" ||
                            url.path?.startsWith("/f/") != true) return null
                        // Content is sanitised (no scripts), so this only fires for <img> tags; still require a
                        // pinned connection and never follow redirects so the token can't be sent elsewhere.
                        return try {
                            val token = kotlinx.coroutines.runBlocking { com.tkolymp.shared.ServiceLocator.tokenStorage.getToken() }
                                ?: return null
                            val req = okhttp3.Request.Builder()
                                .url(url.toString())
                                .header("Authorization", "Bearer $token")
                                // the site serves /f/* using its session cookie (value is the same JWT)
                                .header("Cookie", "rozpisovnik=$token")
                                .build()
                            val resp = pinnedClient.newCall(req).execute()
                            val body = resp.body
                            if (!resp.isSuccessful || body == null) { resp.close(); return null }
                            val mime = resp.header("Content-Type")?.substringBefore(';')?.trim() ?: "application/octet-stream"
                            android.webkit.WebResourceResponse(mime, null, body.byteStream())
                        } catch (_: Throwable) { null }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        // measure full document height and update Compose state
                        view.evaluateJavascript("document.body.scrollHeight") { result ->
                            val cssHeight = result?.replace("\"", "")?.toIntOrNull() ?: return@evaluateJavascript
                            if (cssHeight > 0) {
                                val px = (cssHeight * resources.displayMetrics.density).toInt()
                                contentHeightPx = px
                            }
                        }
                        // inject click handlers for images to call the Android bridge
                        if (onImageClick != null) {
                            try {
                                view.evaluateJavascript(
                                    "(function(){var imgs=document.getElementsByTagName('img');for(var i=0;i<imgs.length;i++){(function(){var s=imgs[i].src;imgs[i].onclick=function(){window.Android.onImageClick(s);};})();}})()"
                                ) { /* no-op */ }
                            } catch (_: Throwable) { }
                        }
                        // wire up tappable bank-account spans injected by autoLinkHtmlBody
                        try {
                            view.evaluateJavascript(
                                "(function(){var els=document.getElementsByClassName('tko-bank-account');for(var i=0;i<els.length;i++){(function(){var acc=els[i].getAttribute('data-account');els[i].onclick=function(){window.Android.onBankAccountClick(acc);};})();}})()"
                            ) { /* no-op */ }
                        } catch (_: Throwable) { }
                    }
                }

                // JS bridge for image clicks and bank-account copy
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onImageClick(url: String) {
                        if (onImageClick == null) return
                        if (!url.startsWith("https://") && !url.startsWith("http://")) return
                        try {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onImageClick(url)
                            }
                        } catch (_: Throwable) { }
                    }

                    @android.webkit.JavascriptInterface
                    fun onBankAccountClick(account: String) {
                        try {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("bank account", account))
                                onBankAccountCopy?.invoke(account)
                            }
                        } catch (_: Throwable) { }
                    }
                }, "Android")
            }
        },
        update = { wv ->
            val textHex = colorToHex(textColor, "#000000")
            val linkHex = colorToHex(linkColor, "#0088CC")
            // Remove all inline styles from HTML (for text)
            val htmlNoInline = html.replace(Regex("<([a-zA-Z0-9]+)([^>]*?) style=\\\".*?\\\"([^>]*?)>", RegexOption.IGNORE_CASE)) {
                "<" + it.groupValues[1] + it.groupValues[2] + it.groupValues[3] + ">"
            }
            val htmlAutoLinked = autoLinkHtmlBody(com.tkolymp.shared.html.HtmlSanitizer.sanitize(htmlNoInline))
                        val selectionColor = if (selectable) {
                                // Use a semi-transparent version of linkColor or fallback
                                val argb = linkColor.takeIf { it != Color.Unspecified }?.toArgb() ?: 0xFF0088CC.toInt()
                                val rgb = String.format("#%06X", 0xFFFFFF and argb)
                                val alpha = "40" // ~25% opacity
                                "::selection { background: $rgb$alpha; }"
                        } else ""

                        val styledHtml = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; script-src 'none'; form-action 'none'; base-uri 'none'\">
                                <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
                                <meta name=\"format-detection\" content=\"telephone=no, date=no, address=no, email=no\">
                                <style>
                                    body { margin:0; padding:0; font-size:${textSizeSp}px; color:${textHex}; background:transparent; word-wrap:break-word; }
                                    a { color:${linkHex}; }
                                    .tko-bank-account { text-decoration: underline dotted; cursor: pointer; }
                                    img, picture, video {
                                        width: 100% !important;
                                        max-width: 100% !important;
                                        height: auto !important;
                                        display: block;
                                        margin: 0 auto;
                                        border-radius: 10px;
                                    }
                                    $selectionColor
                                </style>
                                </head>
                                <body>$htmlAutoLinked</body>
                                </html>
                        """.trimIndent()
            if (styledHtml != lastLoadedHtml) {
                lastLoadedHtml = styledHtml
                wv.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun colorToHex(color: Color, fallback: String): String {
    if (color == Color.Unspecified) return fallback
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}
