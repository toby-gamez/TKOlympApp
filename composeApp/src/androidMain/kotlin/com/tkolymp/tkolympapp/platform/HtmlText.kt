package com.tkolymp.tkolympapp.platform

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.DrawableCompat

@Composable
actual fun HtmlText(
    html: String,
    modifier: Modifier,
    textColor: Color,
    linkColor: Color,
    textSizeSp: Float,
    selectable: Boolean
) {
    val textArgb = if (textColor == Color.Unspecified) null else textColor.toArgb()
    val linkArgb = if (linkColor == Color.Unspecified) null else linkColor.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                if (selectable) setTextIsSelectable(true)
                if (selectable) {
                    this.linksClickable = true
                    this.movementMethod = LinkMovementMethod.getInstance()
                }
            }
        },
        update = { tv ->
            tv.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            if (textArgb != null) tv.setTextColor(textArgb)
            if (linkArgb != null) tv.setLinkTextColor(linkArgb)
            if (selectable && linkArgb != null) {
                val selectionHighlight = (linkArgb and 0x00FFFFFF) or (0x33 shl 24)
                tv.setHighlightColor(selectionHighlight)
                tintTextViewHandles(tv, linkArgb)
            }
        }
    )
}

private fun tintTextViewHandles(tv: TextView, color: Int) {
    try {
        val editorField = TextView::class.java.getDeclaredField("mEditor")
        editorField.isAccessible = true
        val editor = editorField.get(tv) ?: return
        val handleNames = arrayOf("mSelectHandleLeft", "mSelectHandleRight", "mSelectHandleCenter")
        for (name in handleNames) {
            try {
                val f = editor.javaClass.getDeclaredField(name)
                f.isAccessible = true
                val d = f.get(editor) as? Drawable
                if (d != null) {
                    val wrapped = DrawableCompat.wrap(d.mutate())
                    DrawableCompat.setTint(wrapped, color)
                    DrawableCompat.setTintMode(wrapped, PorterDuff.Mode.SRC_IN)
                    f.set(editor, wrapped)
                }
            } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {}
}
