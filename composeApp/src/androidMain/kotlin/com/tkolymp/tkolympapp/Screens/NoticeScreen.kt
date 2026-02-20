package com.tkolymp.tkolympapp

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.DrawableCompat
import com.tkolymp.shared.viewmodels.NoticeViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// Try to tint selection handles/cursor via reflection; best-effort and silently fails on unsupported platforms.
private fun tintTextViewSelectionHandles(tv: TextView, color: Int) {
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
            } catch (_: Throwable) {
            }
        }

        // Cursor drawable (array) in Editor
        try {
            val cursorField = editor.javaClass.getDeclaredField("mCursorDrawable")
            cursorField.isAccessible = true
            val c = cursorField.get(editor) as? Array<Drawable?>
            if (!c.isNullOrEmpty()) {
                for (i in c.indices) {
                    val d = c[i]
                    if (d != null) {
                        val wrapped = DrawableCompat.wrap(d.mutate())
                        DrawableCompat.setTint(wrapped, color)
                        DrawableCompat.setTintMode(wrapped, PorterDuff.Mode.SRC_IN)
                        c[i] = wrapped
                    }
                }
                cursorField.set(editor, c)
            }
        } catch (_: Throwable) {
        }
    } catch (_: Throwable) {
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(announcementId: Long, onBack: (() -> Unit)? = null) {
    val viewModel = remember { NoticeViewModel() }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(announcementId) {
        viewModel.load(announcementId)
    }

    // Keep content visible during loading; SwipeToReload handles the refresh indicator.
    val a = state.announcement

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oznámení") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zpět")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val scope = rememberCoroutineScope()
        SwipeToReload(
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { viewModel.load(announcementId) } },
            modifier = Modifier.padding(padding)
        ) {
            if (a == null) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Žádné oznámení k zobrazení", modifier = Modifier.padding(16.dp))
                }
            } else {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                ) {
                    Text(a.title ?: "(bez názvu)", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val authorName = listOfNotNull(a.author?.uJmeno, a.author?.uPrijmeni).joinToString(" ").trim()
                            if (authorName.isNotBlank()) {
                                Text("Autor: $authorName", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            fun formatIso(iso: String?): String? {
                                if (iso.isNullOrBlank()) return null
                                return try {
                                    val inst = Instant.parse(iso)
                                    val zdt = inst.atZone(ZoneId.systemDefault())
                                    val fmt = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm").withLocale(Locale("cs"))
                                    fmt.format(zdt)
                                } catch (ex: DateTimeParseException) {
                                    iso
                                }
                            }

                            formatIso(a.createdAt)?.let { f ->
                                Text("Vytvořeno: $f", style = MaterialTheme.typography.bodySmall)
                            }
                            formatIso(a.updatedAt)?.let { f ->
                                Text("Aktualizováno: $f", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (!a.body.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val bodySizeSp = MaterialTheme.typography.bodyMedium.fontSize.value
                                val onBackgroundArgb = MaterialTheme.colorScheme.onBackground.toArgb()
                                val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
                                val secondaryArgb = MaterialTheme.colorScheme.secondary.toArgb()
                                // make a translucent version of primary for selection highlight (approx 20% alpha)
                                val selectionHighlightArgb = (primaryArgb and 0x00FFFFFF) or (0x33 shl 24)

                                AndroidView(
                                    modifier = Modifier.fillMaxWidth(),
                                    factory = { ctx ->
                                        TextView(ctx).apply {
                                            setTextIsSelectable(true)
                                            linksClickable = true
                                            movementMethod = LinkMovementMethod.getInstance()
                                        }
                                    },
                                    update = { tv ->
                                        val sp = Html.fromHtml(a.body ?: "", Html.FROM_HTML_MODE_LEGACY)
                                        tv.text = sp
                                        tv.setTextSize(
                                            TypedValue.COMPLEX_UNIT_SP,
                                            bodySizeSp
                                        )
                                        tv.setTextColor(onBackgroundArgb)
                                        tv.setLinkTextColor(primaryArgb)
                                        tv.setHighlightColor(selectionHighlightArgb)
                                        tv.highlightColor = selectionHighlightArgb
                                        tintTextViewSelectionHandles(tv, primaryArgb)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
                title = { Text("Chyba") },
                text = { Text(err ?: "Neznámá chyba") }
            )
        }
    }
}
