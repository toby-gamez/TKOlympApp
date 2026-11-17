package com.tkolymp.tkolympapp.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

/** Clickable that shrinks slightly while pressed (prototype `style-active: scale(.98)`). */
fun Modifier.pressClickable(pressedScale: Float = 0.98f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, label = "pressScale")
    this.scale(scale).clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}

/** Green used for "free"/"ok" states in the prototype (`--ok`); lighter in dark theme. */
@Composable
fun successColor(): Color =
    if (MaterialTheme.colorScheme.background.red < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)

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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().pressClickable(0.985f, onClick),
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) {
        Row(Modifier.fillMaxWidth().height(6.dp)) {
            val colors = cohortColors.ifEmpty { listOf(MaterialTheme.colorScheme.primary) }
            colors.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it)) }
        }
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dateBig, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Text(dateSmall, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onAction: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = 20.dp, bottom = 6.dp),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strikeThrough: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth().pressClickable(0.985f, onClick),
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
