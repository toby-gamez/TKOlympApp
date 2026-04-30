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
    onImageClick: ((String) -> Unit)?
) {
    var contentHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val sizedModifier = modifier

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
                        // open links in external browser
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { ctx.startActivity(intent) } catch (_: Throwable) {}
                        return true
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
                    }
                }

                // JS bridge for image clicks
                if (onImageClick != null) {
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onImageClick(url: String) {
                            try {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onImageClick(url)
                                }
                            } catch (_: Throwable) { }
                        }
                    }, "Android")
                }
            }
        },
        update = { wv ->
            val textHex = colorToHex(textColor, "#000000")
            val linkHex = colorToHex(linkColor, "#0088CC")
            // Remove all inline styles from HTML (for text)
            val htmlNoInline = html.replace(Regex("<([a-zA-Z0-9]+)([^>]*?) style=\\\".*?\\\"([^>]*?)>", RegexOption.IGNORE_CASE)) {
                "<" + it.groupValues[1] + it.groupValues[2] + it.groupValues[3] + ">"
            }
                        val selectionColor = if (selectable) {
                                // Use a semi-transparent version of linkColor or fallback
                                val argb = linkColor.takeIf { it != Color.Unspecified }?.toArgb() ?: 0xFF0088CC.toInt()
                                val rgb = String.format("#%06X", 0xFFFFFF and argb)
                                val alpha = "40" // ~25% opacity
                                "::selection { background: $rgb$alpha; }"
                        } else ""
                        val injectImageClickScript = if (onImageClick != null) {
                            // also attach click handlers on load in case some images are added later
                            "<script>document.addEventListener('DOMContentLoaded', function(){var imgs=document.getElementsByTagName('img');for(var i=0;i<imgs.length;i++){(function(){var s=imgs[i].src;imgs[i].onclick=function(){window.Android.onImageClick(s);};})();}});</script>"
                        } else ""

                        val styledHtml = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
                                <style>
                                    body { margin:0; padding:0; font-size:${textSizeSp}px; color:${textHex}; background:transparent; word-wrap:break-word; }
                                    a { color:${linkHex}; }
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
                                <body>$htmlNoInline</body>
                                $injectImageClickScript
                                </html>
                        """.trimIndent()
            wv.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
        }
    )
}

private fun colorToHex(color: Color, fallback: String): String {
    if (color == Color.Unspecified) return fallback
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}
