package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.RichTextBody
import com.tkolymp.tkolympapp.ui.theme.AppTheme

/**
 * State of a [RichHtmlEditor]: the WYSIWYG document plus an optional HTML-source mode for
 * content the rich editor cannot represent (images, tables, embeds from the web admin).
 */
@Stable
class RichHtmlEditorState internal constructor(
    val rich: RichTextState,
    private val sourceModeState: MutableState<Boolean>,
    private val sourceState: MutableState<String>,
    private val loadedState: MutableState<Boolean>,
) {
    var isSourceMode: Boolean
        get() = sourceModeState.value
        private set(value) { sourceModeState.value = value }

    var source: String
        get() = sourceState.value
        set(value) { sourceState.value = value }

    val isLoaded: Boolean get() = loadedState.value

    /** Loads stored HTML once; later calls (e.g. after recomposition) are ignored. */
    fun loadOnce(storedHtml: String?) {
        if (loadedState.value) return
        loadedState.value = true
        val html = RichTextBody.toEditorHtml(storedHtml)
        if (RichTextBody.isEditorCompatible(html)) {
            rich.setHtml(html)
            isSourceMode = false
        } else {
            source = html
            isSourceMode = true
        }
    }

    fun toggleSourceMode() {
        if (isSourceMode) {
            rich.setHtml(source)
            isSourceMode = false
        } else {
            source = richHtml()
            isSourceMode = true
        }
    }

    /** HTML to store; an empty editor yields "". */
    fun currentHtml(): String = if (isSourceMode) source.trim() else richHtml()

    private fun richHtml(): String = RichTextBody.normalizeForStorage(rich.toHtml(), rich.toText())
}

@Composable
fun rememberRichHtmlEditorState(): RichHtmlEditorState {
    val rich = rememberRichTextState()
    val sourceMode = rememberSaveable { mutableStateOf(false) }
    val source = rememberSaveable { mutableStateOf("") }
    val loaded = rememberSaveable { mutableStateOf(false) }
    return remember(rich) { RichHtmlEditorState(rich, sourceMode, source, loaded) }
}

/** Text colors offered by the toolbar. They are content colors saved into the HTML, not theme colors. */
private data class ContentColor(val color: Color, val name: () -> String)

private val textColors = listOf(
    ContentColor(Color(0xFFD32F2F)) { AppStrings.current.management.colorRed },
    ContentColor(Color(0xFFF57C00)) { AppStrings.current.management.colorOrange },
    ContentColor(Color(0xFF388E3C)) { AppStrings.current.management.colorGreen },
    ContentColor(Color(0xFF1976D2)) { AppStrings.current.management.colorBlue },
    ContentColor(Color(0xFF7B1FA2)) { AppStrings.current.management.colorPurple },
)

private val highlightColor = Color(0xFFFFF176)

/**
 * Rich-text (WYSIWYG) HTML editor with a formatting toolbar: bold, italic, underline,
 * strikethrough, headings, lists, indentation, links, text color, highlight, alignment,
 * clear formatting, undo/redo and an HTML source mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichHtmlEditor(
    state: RichHtmlEditorState,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 6,
) {
    val strings = AppStrings.current.management
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RichTextToolbar(state = state)
        if (state.isSourceMode) {
            OutlinedTextField(
                value = state.source,
                onValueChange = { state.source = it },
                label = { Text("$label (HTML)") },
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(strings.htmlSourceHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedRichTextEditor(
                state = state.rich,
                label = { Text(label) },
                minLines = minLines,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RichTextToolbar(state: RichHtmlEditorState, modifier: Modifier = Modifier) {
    val strings = AppStrings.current.management
    val rich = state.rich
    val enabled = !state.isSourceMode
    val span = rich.currentSpanStyle
    val align = rich.currentParagraphStyle.textAlign
    var showLinkDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { rich.history.undo() }, enabled = enabled && rich.history.canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undo)
        }
        IconButton(onClick = { rich.history.redo() }, enabled = enabled && rich.history.canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redo)
        }
        ToolbarDivider()
        FormatToggle(Icons.Filled.FormatBold, strings.bold, span.fontWeight == FontWeight.Bold, enabled) {
            rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        }
        FormatToggle(Icons.Filled.FormatItalic, strings.italic, span.fontStyle == FontStyle.Italic, enabled) {
            rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
        FormatToggle(Icons.Filled.FormatUnderlined, strings.underline, span.textDecoration?.contains(TextDecoration.Underline) == true, enabled) {
            rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        }
        FormatToggle(Icons.Filled.FormatStrikethrough, strings.strikethrough, span.textDecoration?.contains(TextDecoration.LineThrough) == true, enabled) {
            rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        }
        HeadingMenu(rich = rich, enabled = enabled)
        ColorMenu(rich = rich, enabled = enabled)
        FormatToggle(Icons.Filled.FormatColorFill, strings.highlight, span.background == highlightColor, enabled) {
            rich.toggleSpanStyle(SpanStyle(background = highlightColor))
        }
        ToolbarDivider()
        FormatToggle(Icons.AutoMirrored.Filled.FormatListBulleted, strings.bulletList, rich.isUnorderedList, enabled) {
            rich.toggleUnorderedList()
        }
        FormatToggle(Icons.Filled.FormatListNumbered, strings.numberedList, rich.isOrderedList, enabled) {
            rich.toggleOrderedList()
        }
        IconButton(onClick = { rich.decreaseListLevel() }, enabled = enabled && rich.isList) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = strings.outdent)
        }
        IconButton(onClick = { rich.increaseListLevel() }, enabled = enabled && rich.isList) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = strings.indent)
        }
        ToolbarDivider()
        FormatToggle(Icons.AutoMirrored.Filled.FormatAlignLeft, strings.alignLeft, align == TextAlign.Start || align == TextAlign.Left, enabled) {
            rich.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Start))
        }
        FormatToggle(Icons.Filled.FormatAlignCenter, strings.alignCenter, align == TextAlign.Center, enabled) {
            rich.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center))
        }
        FormatToggle(Icons.AutoMirrored.Filled.FormatAlignRight, strings.alignRight, align == TextAlign.End || align == TextAlign.Right, enabled) {
            rich.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.End))
        }
        ToolbarDivider()
        if (rich.isLink) {
            IconButton(onClick = { rich.removeLink() }, enabled = enabled) {
                Icon(Icons.Filled.LinkOff, contentDescription = strings.removeLink)
            }
        }
        IconButton(onClick = { showLinkDialog = true }, enabled = enabled) {
            Icon(Icons.Filled.Link, contentDescription = strings.link)
        }
        IconButton(onClick = { rich.clearSpanStyles(); rich.setHeadingStyle(HeadingStyle.Normal) }, enabled = enabled) {
            Icon(Icons.Filled.FormatClear, contentDescription = strings.clearFormatting)
        }
        FormatToggle(Icons.Filled.Code, strings.htmlSource, state.isSourceMode, enabled = true) {
            state.toggleSourceMode()
        }
    }
    HorizontalDivider()

    if (showLinkDialog) {
        LinkDialog(
            initialUrl = rich.selectedLinkUrl.orEmpty(),
            needsText = rich.selection.collapsed && !rich.isLink,
            onDismiss = { showLinkDialog = false },
            onConfirm = { text, url ->
                showLinkDialog = false
                when {
                    rich.isLink -> rich.updateLink(url)
                    rich.selection.collapsed -> rich.addLink(text = text.ifBlank { url }, url = url)
                    else -> rich.addLinkToSelection(url)
                }
            },
        )
    }
}

@Composable
private fun FormatToggle(icon: ImageVector, label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
}

@Composable
private fun HeadingMenu(rich: RichTextState, enabled: Boolean) {
    val strings = AppStrings.current.management
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        HeadingStyle.Normal to strings.headingNormal,
        HeadingStyle.H1 to strings.heading1,
        HeadingStyle.H2 to strings.heading2,
        HeadingStyle.H3 to strings.heading3,
    )
    Box {
        IconToggleButton(
            checked = rich.currentHeadingStyle != HeadingStyle.Normal,
            onCheckedChange = { expanded = true },
            enabled = enabled,
        ) {
            Icon(Icons.Filled.Title, contentDescription = strings.heading)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (style, name) ->
                DropdownMenuItem(
                    text = { Text(name, style = headingPreviewStyle(style)) },
                    onClick = { expanded = false; rich.setHeadingStyle(style) },
                )
            }
        }
    }
}

@Composable
private fun headingPreviewStyle(style: HeadingStyle) = when (style) {
    HeadingStyle.H1 -> MaterialTheme.typography.titleLarge
    HeadingStyle.H2 -> MaterialTheme.typography.titleMedium
    HeadingStyle.H3 -> MaterialTheme.typography.titleSmall
    else -> MaterialTheme.typography.bodyLarge
}

@Composable
private fun ColorMenu(rich: RichTextState, enabled: Boolean) {
    val strings = AppStrings.current.management
    var expanded by remember { mutableStateOf(false) }
    val current = rich.currentSpanStyle.color
    val active = textColors.any { it.color == current }
    Box {
        IconToggleButton(checked = active, onCheckedChange = { expanded = true }, enabled = enabled) {
            Icon(Icons.Filled.FormatColorText, contentDescription = strings.textColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings.defaultColor) },
                onClick = {
                    expanded = false
                    if (active) rich.removeSpanStyle(SpanStyle(color = current))
                },
            )
            textColors.forEach { option ->
                val name = option.name()
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(20.dp)
                                .background(option.color, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .semantics { contentDescription = name }
                        )
                    },
                    onClick = {
                        expanded = false
                        if (active) rich.removeSpanStyle(SpanStyle(color = current))
                        rich.addSpanStyle(SpanStyle(color = option.color))
                    },
                )
            }
        }
    }
}

@Composable
private fun LinkDialog(
    initialUrl: String,
    needsText: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (text: String, url: String) -> Unit,
) {
    val strings = AppStrings.current.management
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.link) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsText) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(strings.linkText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; invalid = false },
                    label = { Text(strings.linkUrl) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(strings.invalidUrl) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = RichTextBody.normalizeUrl(url)
                if (normalized == null) invalid = true else onConfirm(text.trim(), normalized)
            }) { Text(AppStrings.current.commonActions.ok) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppStrings.current.commonActions.cancel) } },
    )
}

@Preview(name = "RichHtmlEditor — Light")
@Composable
private fun RichHtmlEditorPreviewLight() {
    AppTheme(darkTheme = false) { RichHtmlEditorPreviewContent() }
}

@Preview(name = "RichHtmlEditor — Dark")
@Composable
private fun RichHtmlEditorPreviewDark() {
    AppTheme(darkTheme = true) { RichHtmlEditorPreviewContent() }
}

@Composable
private fun RichHtmlEditorPreviewContent() {
    val state = rememberRichHtmlEditorState()
    state.loadOnce("<h2>Soustředění</h2><p><b>Sraz</b> v <i>8:00</i> před sálem.</p><ul><li>boty</li><li>pití</li></ul>")
    RichHtmlEditor(state = state, label = "Text", modifier = Modifier.padding(12.dp))
}
