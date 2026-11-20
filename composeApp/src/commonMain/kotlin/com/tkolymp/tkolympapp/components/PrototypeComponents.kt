package com.tkolymp.tkolympapp.components

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tkolymp.shared.language.AppStrings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tkolymp.tkolympapp.components.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.platform.rememberReduceMotion
import com.tkolymp.tkolympapp.util.Motion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import com.tkolymp.tkolympapp.ui.theme.Spacing

/**
 * Clickable that shrinks slightly and darkens while pressed (prototype `style-active: scale(.98)`).
 * No ripple: it would draw as a rectangle over rounded cards, so the press tint is drawn clipped to [shape].
 */
fun Modifier.pressClickable(pressedScale: Float = 0.98f, onClick: () -> Unit): Modifier =
    pressClickable(pressedScale, RoundedCornerShape(16.dp), onClick)

fun Modifier.pressClickable(pressedScale: Float, shape: Shape, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Keep the animated values as State and read them in the graphicsLayer/draw blocks so each frame only redraws.
    val reduceMotion = rememberReduceMotion()
    val scale = animateFloatAsState(if (pressed && !reduceMotion) pressedScale else 1f, Motion.pressSpring(), label = "pressScale")
    val tint = animateFloatAsState(if (pressed) 1f else 0f, label = "pressTint")
    val tintColor = MaterialTheme.colorScheme.onSurface
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.clickable(interactionSource = source, indication = null, onClick = onClick)
        .drawWithContent {
            drawContent()
            if (tint.value > 0f) {
                drawOutline(shape.createOutline(size, layoutDirection, this), tintColor.copy(alpha = 0.10f * tint.value))
            }
        }
}

/** Green used for "free"/"ok" states in the prototype (`--ok`); lighter in dark theme. */
@Composable
fun successColor(): Color =
    if (MaterialTheme.colorScheme.background.red < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)

/** The prototype's accent text/icon red (`--pr`): deep red in light theme, soft pink in dark. */
@Composable
fun accentColor(): Color =
    if (MaterialTheme.colorScheme.background.red < 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

/** Tinted background paired with [successColor] (`--oktint`). */
@Composable
fun successContainerColor(): Color =
    if (MaterialTheme.colorScheme.background.red < 0.5f) Color(0xFF4CAF50).copy(alpha = 0.16f) else Color(0xFFE8F5E9)

/** Left-aligned top bar with a 22sp bold title and optional muted subtitle (prototype Android app bar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.current.commonActions.back)
                }
            }
        },
        title = {
            Column {
                Text(title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** Rounded tinted strip with a leading icon and chevron, e.g. "3 free lessons today". */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    action: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.pressClickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = contentColor)
        if (action != null) {
            Text(action, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = contentColor)
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
    }
}

/** How the dot of a [TimelineRow] is drawn. */
sealed interface TimelineDot {
    data class Filled(val color: Color) : TimelineDot
    /** Hollow green ring: a free lesson. */
    data object Free : TimelineDot
}

/** One timeline entry: time | dot on a connecting line | bordered card (prototype "Dále dnes"). */
@Composable
fun TimelineRow(
    time: String,
    dot: TimelineDot,
    title: String,
    subtitle: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    strikeThrough: Boolean = false
) {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            time,
            modifier = Modifier.width(44.dp).padding(top = 13.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Column(modifier = Modifier.width(12.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(15.dp).background(if (isFirst) Color.Transparent else line))
            val dotModifier = Modifier.size(10.dp)
            when (dot) {
                is TimelineDot.Filled -> Box(dotModifier.background(dot.color, CircleShape))
                TimelineDot.Free -> Box(dotModifier.border(2.dp, successColor(), CircleShape))
            }
            Box(Modifier.width(2.dp).weight(1f).background(if (isLast) Color.Transparent else line))
        }
        Card(
            modifier = Modifier.weight(1f).padding(vertical = Spacing.xs).pressClickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            colors = surfaceCardColors(),
            border = cardLineBorder()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = 9.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    textDecoration = if (strikeThrough) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Camp/event card: cohort-colour stripe on top, big date range, name and place (prototype "Nejbližší akce"). */
@Composable
fun CampCard(
    cohortColors: List<Color>,
    dateBig: String,
    dateSmall: String,
    name: String,
    place: String,
    registeredLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    whenLabel: String? = null,
    contentAlpha: Float = 1f
) {
    Card(
        modifier = modifier.fillMaxWidth().alpha(contentAlpha).pressClickable(0.985f, onClick),
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        if (cohortColors.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(6.dp)) {
                cohortColors.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it)) }
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dateBig, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Text(dateSmall, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (whenLabel != null) {
                    Spacer(Modifier.weight(1f))
                    Text(whenLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(place, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (registeredLabel != null) {
                    Text(
                        registeredLabel,
                        modifier = Modifier.background(successContainerColor(), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = successColor()
                    )
                }
            }
        }
    }
}

/** Small uppercase muted label with an optional right-aligned link ("DÁLE DNES  Kalendář ›"). */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    horizontalPadding: androidx.compose.ui.unit.Dp = Spacing.default
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            Row(modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onAction), verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Competition card: big day countdown, divider, then name / date · place / count (prototype "Nejbližší soutěž"). */
@Composable
fun CountdownCard(
    days: String,
    daysUnit: String,
    title: String,
    meta: String,
    footnote: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    strikeThrough: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.pressClickable(0.985f, onClick) else it },
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(days, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text(daysUnit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (strikeThrough) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (footnote != null) Text(footnote, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Small tinted pill with an optional leading icon, e.g. "Results" or "14 competitions". */
@Composable
fun InfoPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier.background(containerColor, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = contentColor, maxLines = 1)
    }
}

/** Rounded date square: big day number over a small month label. */
@Composable
fun DateTile(day: String, month: String, monthColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.size(52.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(month.uppercase(), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = monthColor)
    }
}

/** Placement badge: the winner is solid red, everyone else tinted. */
@Composable
fun RankBadge(text: String, winner: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(if (winner) BrandPrimary() else MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (winner) Color.White else MaterialTheme.colorScheme.onTertiaryContainer
    )
}

/**
 * Announcement card. [featured] is the large first card of the board (bigger title, optional [badge]);
 * otherwise the compact list card.
 */
@Composable
fun NoticeCard(
    authorName: String,
    meta: String,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    badge: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth().pressClickable(0.985f, onClick),
        shape = RoundedCornerShape(if (featured) 20.dp else 16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        Column(modifier = Modifier.padding(start = if (featured) 16.dp else 14.dp, end = if (featured) 16.dp else 14.dp, top = if (featured) 16.dp else 14.dp, bottom = if (featured) 18.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                if (authorName.isNotBlank()) {
                    InitialsAvatar(name = authorName, size = if (featured) 28.dp else 22.dp, fontSize = if (featured) 11.sp else 9.sp)
                }
                Text(
                    meta,
                    modifier = Modifier.weight(1f),
                    style = if (featured) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badge != null) {
                    Text(
                        badge,
                        modifier = Modifier.background(BrandPrimary(), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color.White
                    )
                }
            }
            Text(
                title,
                modifier = Modifier.padding(top = if (featured) 12.dp else 8.dp),
                fontSize = if (featured) 21.sp else 16.sp,
                lineHeight = if (featured) 27.sp else 21.sp,
                fontWeight = if (featured) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = if (featured) 3 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (body.isNotBlank()) {
                Text(
                    body,
                    modifier = Modifier.padding(top = if (featured) 6.dp else 4.dp),
                    fontSize = if (featured) 14.sp else 13.sp,
                    lineHeight = if (featured) 21.sp else 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (featured) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Rounded filled search field (44dp) with a trailing clear/close button, as in the prototype board search. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onTrailingClick: () -> Unit,
    trailingDescription: String,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(start = Spacing.medium, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            )
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onTrailingClick) {
            Icon(Icons.Filled.Close, contentDescription = trailingDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Menu row: tinted rounded icon tile, label, chevron (prototype "Členové a klub"). */
@Composable
fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().pressClickable(0.995f, onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor(), modifier = Modifier.size(20.dp))
        }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Square-ish shortcut card: accent icon over a short label. */
@Composable
fun QuickTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, showBadge: Boolean = false) {
    Card(
        modifier = modifier.pressClickable(0.95f, onClick),
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accentColor(), modifier = Modifier.size(24.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showBadge) Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp).size(8.dp).background(BrandPrimary(), CircleShape))
        }
    }
}

@Preview
@Composable
private fun TimelinePreviewLight() {
    AppTheme(darkTheme = false) { TimelinePreviewBody() }
}

@Preview
@Composable
private fun TimelinePreviewDark() {
    AppTheme(darkTheme = true) { TimelinePreviewBody() }
}

@Composable
private fun TimelinePreviewBody() {
    Column(Modifier.padding(Spacing.default)) {
        TimelineRow("15:45", TimelineDot.Filled(MaterialTheme.colorScheme.primary), "Hana Kudrnová", "Pavel Grepl · SGO · 45 min", true, false, {})
        TimelineRow("16:30", TimelineDot.Free, "Volná lekce", "Roman Pecha · SGO · 45 min", false, true, {}, titleColor = successColor())
        CampCard(listOf(Color(0xFFE53935), Color(0xFF1E88E5)), "6.–8.", "prosince", "Zimní soustředění Olomouc", "Sportovní hala Olomouc", "Přihlášen", {})
    }
}
