package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.viewinterop.UIKitView
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.darwin.NSObject
import platform.UIKit.UIApplication
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
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
    var contentHeightDp by remember { mutableStateOf(200) }

    fun colorToHex(color: Color, fallback: String): String {
        if (color == Color.Unspecified) return fallback
        val argb = color.toArgb()
        return "#%06X".format(0xFFFFFF and argb)
    }

    val textHex = colorToHex(textColor, "#000000")
    val linkHex = colorToHex(linkColor, "#0088CC")
    val htmlNoInline = html.replace(Regex("<([a-zA-Z0-9]+)([^>]*?) style=\\\".*?\\\"([^>]*?)>", RegexOption.IGNORE_CASE)) {
        "<" + it.groupValues[1] + it.groupValues[2] + it.groupValues[3] + ">"
    }
    val styledHtml = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
body { margin:0; padding:0; font-size:${textSizeSp}px; color:${textHex}; background:transparent; word-wrap:break-word; }
a { color:${linkHex}; }
img, picture, video { width:100% !important; max-width:100% !important; height:auto !important; display:block; margin:0 auto; border-radius:10px; }
</style></head><body>$htmlNoInline</body></html>"""

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            if (onImageClick != null) {
                val imageClickHandler = object : NSObject(), WKScriptMessageHandlerProtocol {
                    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
                        val url = didReceiveScriptMessage.body as? String ?: return
                        onImageClick(url)
                    }
                }
                config.userContentController.addScriptMessageHandler(imageClickHandler, "imageClick")
            }
            WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                scrollView.scrollEnabled = selectable
                scrollView.bounces = false
                opaque = false
                backgroundColor = platform.UIKit.UIColor.clearColor

                val navDelegate = object : NSObject(), WKNavigationDelegateProtocol {
                    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                        webView.evaluateJavaScript("document.body.scrollHeight") { result, _ ->
                            val cssHeight = (result as? Double)?.toInt() ?: return@evaluateJavaScript
                            if (cssHeight > 0) contentHeightDp = cssHeight
                        }
                    }

                    override fun webView(
                        webView: WKWebView,
                        decidePolicyForNavigationAction: WKNavigationAction,
                        decisionHandler: (WKNavigationActionPolicy) -> Unit
                    ) {
                        val url = decidePolicyForNavigationAction.request.URL
                        if (decidePolicyForNavigationAction.navigationType ==
                            platform.WebKit.WKNavigationTypeLinkActivated && url != null
                        ) {
                            UIApplication.sharedApplication.openURL(url)
                            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                        } else {
                            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                        }
                    }
                }
                navigationDelegate = navDelegate
                loadHTMLString(styledHtml, baseURL = null)
            }
        },
        update = { wv ->
            wv.loadHTMLString(styledHtml, baseURL = null)
        },
        modifier = modifier.heightIn(min = contentHeightDp.dp)
    )
}
