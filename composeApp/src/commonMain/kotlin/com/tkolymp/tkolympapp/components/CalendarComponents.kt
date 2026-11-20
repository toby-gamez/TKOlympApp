package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import com.tkolymp.tkolympapp.util.Motion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import com.tkolymp.tkolympapp.ui.theme.Spacing

/** One cell of the [WeekStrip]. */
data class WeekDayCell(val abbr: String, val number: Int, val count: Int, val selected: Boolean, val isToday: Boolean)

/** Seven equal day columns: weekday, date number and event count; the selected day gets a red pill. */
@Composable
fun WeekStrip(days: List<WeekDayCell>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.medium, end = Spacing.medium, top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        days.forEachIndexed { i, d ->
            val onPill = Color.White
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (d.selected) BrandPrimary() else Color.Transparent)
                    .clickable(role = Role.Button) { onSelect(i) }
                    .padding(top = 8.dp, bottom = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(d.abbr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (d.selected) onPill else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    d.number.toString(),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        d.selected -> onPill
                        d.isToday -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    if (d.count > 0) d.count.toString() else "",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (d.selected) onPill else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 12.dp)
                )
            }
        }
    }
}

/** The prototype's `--prf` red, identical in both themes. */
@Composable
internal fun BrandPrimary(): Color = com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary

/**
 * iOS-style segmented control: grey track, white (surface) selected segment.
 * With [fillWidth] the segments split the available width equally (needs a bounded width, so not inside a scrolling row).
 */
@Composable
fun SegmentedToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selectedIndex
            // Pill colour and shadow fade as one layer; a separate Modifier.shadow would show through the fading pill as a grey rectangle.
            val pillAlpha by animateFloatAsState(if (on) 1f else 0f, tween(Motion.FadeMs), label = "segmentPill")
            val pillColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 32.dp)
                    // No ripple: it flashes as a rectangle over the pill; the pill fade is the feedback.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = pillAlpha }
                        .drawBehind {
                            // Hand-drawn soft lift: a platform shadow on a fading layer went missing on the first segment.
                            val radius = CornerRadius(8.dp.toPx())
                            val spread = 1.dp.toPx()
                            drawRoundRect(Color.Black.copy(alpha = 0.05f), Offset(-spread, 0f), Size(size.width + 2 * spread, size.height + 2 * spread), radius)
                            drawRoundRect(Color.Black.copy(alpha = 0.07f), Offset(-spread / 2, 0f), Size(size.width + spread, size.height + spread), radius)
                            drawRoundRect(pillColor, cornerRadius = radius)
                        }
                )
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = Spacing.medium, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = animateColorAsState(
                        if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(Motion.FadeMs), label = "segmentText"
                    ).value
                )
            }
        }
    }
}

/** "Filters" button; turns dark and shows a red count badge while filters are active. */
@Composable
fun FilterButton(label: String, activeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = activeCount > 0
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val fg = if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
        Icon(Icons.Filled.Tune, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = fg)
        if (active) {
            Text(
                activeCount.toString(),
                modifier = Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).background(BrandPrimary(), CircleShape).padding(horizontal = 5.dp),
                fontSize = 11.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/** Outlined quick-filter chip; tinted with a check when [selected], optional trailing close icon. */
@Composable
fun QuickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    removable: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    val tint = MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(10.dp))
            .then(if (selected) Modifier.background(tint) else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(10.dp)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (selected && !removable) Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        else leading?.invoke()
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
        if (removable) Icon(Icons.Filled.Close, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
    }
}

/** Horizontally scrolling row used for the segmented control + filter button + quick chips. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = Spacing.default, end = Spacing.default, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** How a [ScheduleRow] ends on the right. */
sealed interface ScheduleTag {
    data class Free(val label: String) : ScheduleTag
    data class Cancelled(val label: String) : ScheduleTag
    data class Mine(val label: String) : ScheduleTag
    data class Duration(val text: String) : ScheduleTag
    data object None : ScheduleTag
}

/** Row of an hour group / trainer card: time, coloured mark, title + subtitle, trailing tag. */
@Composable
fun ScheduleRow(
    time: String,
    mark: Color,
    title: String,
    subtitle: String?,
    tag: ScheduleTag,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    strikeThrough: Boolean = false,
    emphasized: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .pressClickable(0.995f, onClick)
            .padding(horizontal = Spacing.medium, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(time, modifier = Modifier.width(40.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (strikeThrough) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        Box(Modifier.width(4.dp).height(30.dp).background(mark, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                color = titleColor,
                textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (tag) {
            is ScheduleTag.Free -> Text(
                tag.label,
                modifier = Modifier.background(successContainerColor(), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = successColor()
            )
            is ScheduleTag.Cancelled -> Row(
                modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                Text(tag.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            }
            is ScheduleTag.Mine -> Text(tag.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            is ScheduleTag.Duration -> Text(tag.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ScheduleTag.None -> Unit
        }
    }
}

/** Bordered surface card whose children are separated by hairline dividers. */
@Composable
fun ScheduleCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = surfaceCardColors(),
        border = cardLineBorder()
    ) { Column { content() } }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
}

/** Hour heading of the "Time" view: "15:00  3 events". */
@Composable
fun HourHeader(hour: String, count: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = Spacing.default, end = Spacing.default, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Text(hour, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Collapsible card per trainer: avatar, name, summary line, free badge, chevron, then rows. */
@Composable
fun TrainerCard(
    name: String,
    summary: String,
    freeBadge: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    rows: @Composable () -> Unit
) {
    ScheduleCard(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 14.dp, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            InitialsAvatar(name = name, size = 32.dp, fontSize = 12.sp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (freeBadge != null) Text(
                freeBadge,
                modifier = Modifier.background(successContainerColor(), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = successColor()
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(Motion.FadeMs)) + expandVertically(Motion.settleSpring()),
            exit = fadeOut(tween(Motion.FadeMs)) + shrinkVertically(tween(Motion.FadeMs))
        ) { Column { rows() } }
    }
}

/** Grey circle with an icon, a message and an optional reset pill (prototype empty day). */
@Composable
fun DayEmptyState(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.EventBusy, resetLabel: String? = null, onReset: () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (resetLabel != null) Text(
            resetLabel,
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).clickable(role = Role.Button, onClick = onReset).padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Preview
@Composable
private fun CalendarComponentsPreviewLight() { AppTheme(darkTheme = false) { CalendarComponentsPreviewBody() } }

@Preview
@Composable
private fun CalendarComponentsPreviewDark() { AppTheme(darkTheme = true) { CalendarComponentsPreviewBody() } }

@Composable
private fun CalendarComponentsPreviewBody() {
    Column(Modifier.padding(Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        WeekStrip(List(7) { WeekDayCell("Po", 16 + it, if (it % 2 == 0) it + 1 else 0, it == 2, it == 1) }, {})
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SegmentedToggle(listOf("Moje", "Vše"), 0, {})
            FilterButton("Filtry", 2, {})
            QuickChip("Jen volné", true, {})
        }
        ScheduleCard {
            ScheduleRow("15:45", MaterialTheme.colorScheme.primary, "Hana Kudrnová", "Pavel Grepl · SGO", ScheduleTag.Mine("VY"), {}, emphasized = true)
            RowDivider()
            ScheduleRow("16:30", successColor(), "Volná lekce", "Roman Pecha · SGO", ScheduleTag.Free("Přihlásit"), {}, titleColor = successColor())
        }
    }
}

/** Uppercase muted section label used inside the filter sheet. */
@Composable
fun SheetSectionLabel(text: String, modifier: Modifier = Modifier, note: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Layout option card (icon, label) used for "List / Trainers / Axis". */
@Composable
fun LayoutCard(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val fg = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)
            .border(1.5.dp, if (selected) BrandPrimary() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** Title + description with a trailing switch (prototype 52x32 track, white thumb). */
@Composable
fun SheetSwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onCheckedChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = BrandPrimary(),
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/** Filter-sheet chip: optional leading avatar/dot, label and a muted count. */
@Composable
fun SheetChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(10.dp)
    val fg = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer) else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), shape))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(start = if (leading != null && !selected) 6.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
        else leading?.invoke()
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
        Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = if (selected) fg else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-width red pill at the bottom of the sheet. */
@Composable
fun SheetPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(BrandPrimary())
            .pressClickable(0.98f, onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

/** Full-width grey pill button (prototype secondary action); [danger] turns the label red. */
@Composable
fun SecondaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, danger: Boolean = false) {
    Box(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pressClickable(0.98f, onClick),
        contentAlignment = Alignment.Center
    ) {
        val fg = if (danger) accentColor() else MaterialTheme.colorScheme.onSurface
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}
