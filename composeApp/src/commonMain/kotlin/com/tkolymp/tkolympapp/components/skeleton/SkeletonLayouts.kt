package com.tkolymp.tkolympapp.components.skeleton

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.tkolympapp.components.cardLineBorder
import com.tkolymp.tkolympapp.components.surfaceCardColors
import com.tkolymp.tkolympapp.ui.theme.Spacing

// Geometry below mirrors the real screens (card shapes, paddings, avatar sizes, row heights);
// keep in sync with PrototypeComponents.kt / CalendarComponents.kt when those change.

private val CardShape = RoundedCornerShape(16.dp)
private val HeroShape = RoundedCornerShape(20.dp)
private val RowCardShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(8.dp)
private val FieldShape = RoundedCornerShape(14.dp)
private const val LineH = 14
private const val SmallH = 10

/** Card with the app's surface colour, 1dp hairline and 16dp corners, as used by nearly every real card. */
@Composable
fun SkeletonSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = surfaceCardColors(),
        border = cardLineBorder(),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).shimmer())
}

/** Person / trainer / participant row: 40dp avatar, two text lines, 16dp card, 8dp gap between cards. */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier, avatar: Dp = 40.dp) {
    Box(modifier.padding(horizontal = Spacing.default, vertical = Spacing.xs)) {
        SkeletonSurface {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                SkeletonCircle(size = avatar)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    SkeletonLine(widthFraction = 0.55f, height = LineH.dp)
                    SkeletonLine(widthFraction = 0.25f, height = SmallH.dp)
                }
            }
        }
    }
}

/** Generic content card (stats / info cards): 16dp, padding 16, a title line and [lines] text lines. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, lines: Int = 3) {
    SkeletonSurface(modifier) {
        Column(Modifier.padding(Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SkeletonLine(widthFraction = 0.4f, height = LineH.dp)
            repeat(lines) { i -> SkeletonLine(widthFraction = if (i == lines - 1) 0.7f else 1f, height = SmallH.dp) }
        }
    }
}

@Composable
fun SkeletonList(modifier: Modifier = Modifier, count: Int = 8, item: @Composable () -> Unit = { SkeletonListItem() }) {
    SkeletonContainer(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(top = Spacing.small)) { repeat(count) { item() } }
    }
}

/** Wrap-height variant of [SkeletonList] for use inside lazy list items. */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 4) {
    SkeletonContainer(modifier = modifier.fillMaxWidth()) {
        Column { repeat(count) { SkeletonListItem() } }
    }
}

@Composable
private fun SkeletonToggle(modifier: Modifier = Modifier) {
    SkeletonBox(modifier = modifier.fillMaxWidth().height(36.dp), shape = RoundedCornerShape(10.dp))
}

@Composable
private fun PaidPaymentCard() {
    SkeletonSurface {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            SkeletonCircle(size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(widthFraction = 0.55f, height = LineH.dp)
                SkeletonLine(widthFraction = 0.35f, height = SmallH.dp)
            }
            SkeletonText(width = 70.dp)
        }
    }
}

@Composable
private fun PendingPaymentCard() {
    SkeletonSurface(shape = HeroShape) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                SkeletonLine(Modifier.weight(1f), height = LineH.dp)
                SkeletonBox(Modifier.width(72.dp).height(22.dp), shape = PillShape)
            }
            SkeletonLine(Modifier.padding(top = Spacing.small), widthFraction = 0.4f, height = 30.dp)
            SkeletonLine(Modifier.padding(top = Spacing.small), widthFraction = 0.3f, height = SmallH.dp)
            Spacer12()
            Divider()
            Spacer12()
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                SkeletonBox(Modifier.width(120.dp).height(40.dp), shape = CircleShape)
                SkeletonBox(Modifier.width(120.dp).height(40.dp), shape = CircleShape)
            }
        }
    }
}

@Composable
private fun Spacer12() = Box(Modifier.height(Spacing.medium))

/** Payments: segmented toggle, one big pending card and compact paid cards (spacing 10). */
@Composable
fun SkeletonPayments(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxSize()) {
        Column {
            SkeletonToggle(Modifier.padding(start = Spacing.default, end = Spacing.default, top = Spacing.small))
            Column(
                Modifier.padding(start = Spacing.default, end = Spacing.default, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PendingPaymentCard()
                repeat(3) { PaidPaymentCard() }
            }
        }
    }
}

@Composable
private fun NoticeCard(featured: Boolean) {
    SkeletonSurface(shape = if (featured) HeroShape else CardShape) {
        Column(
            if (featured) Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp)
            else Modifier.padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                SkeletonCircle(size = if (featured) 28.dp else 22.dp)
                SkeletonLine(Modifier.weight(1f, fill = false), widthFraction = 0.5f, height = SmallH.dp)
                if (featured) SkeletonBox(Modifier.width(44.dp).height(18.dp), shape = RoundedCornerShape(6.dp))
            }
            Column(
                Modifier.padding(top = if (featured) Spacing.medium else Spacing.small),
                verticalArrangement = Arrangement.spacedBy(if (featured) 8.dp else 6.dp),
            ) {
                SkeletonLine(height = if (featured) 18.dp else LineH.dp)
                SkeletonLine(widthFraction = 0.6f, height = if (featured) 18.dp else LineH.dp)
            }
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(if (featured) 3 else 2) { i -> SkeletonLine(widthFraction = if (i == (if (featured) 2 else 1)) 0.7f else 1f, height = SmallH.dp) }
            }
        }
    }
}

/** Board: one featured card (20dp) followed by compact notice cards (16dp), spacing 10. */
@Composable
fun SkeletonNoticeList(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = Spacing.default, end = Spacing.default, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NoticeCard(featured = true)
            repeat(3) { NoticeCard(featured = false) }
        }
    }
}

/** Overview announcement tile: 262dp wide horizontal card. */
@Composable
fun SkeletonAnnouncementTiles(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState(), enabled = false).padding(horizontal = Spacing.default),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) {
                SkeletonSurface(Modifier.width(262.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                            SkeletonCircle(size = 22.dp)
                            SkeletonText(width = 90.dp, height = SmallH.dp)
                        }
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonLine(height = LineH.dp)
                            SkeletonLine(widthFraction = 0.6f, height = LineH.dp)
                        }
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonLine(height = SmallH.dp)
                            SkeletonLine(height = SmallH.dp)
                            SkeletonLine(widthFraction = 0.5f, height = SmallH.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Camp card: 6dp cohort stripe, big day number + month, name, place. */
@Composable
fun SkeletonCampCard(modifier: Modifier = Modifier) {
    SkeletonSurface(modifier) {
        SkeletonBox(Modifier.fillMaxWidth().height(6.dp), shape = RoundedCornerShape(0.dp))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                SkeletonText(width = 70.dp, height = 26.dp)
                SkeletonText(width = 60.dp, height = LineH.dp)
            }
            SkeletonLine(Modifier.padding(top = 10.dp), widthFraction = 0.7f, height = LineH.dp)
            Row(Modifier.padding(top = Spacing.small), horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                SkeletonLine(Modifier.weight(1f), widthFraction = 0.5f, height = SmallH.dp)
            }
        }
    }
}

@Composable
fun SkeletonCampList(modifier: Modifier = Modifier, count: Int = 4) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Spacing.default, end = Spacing.default, top = 14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            repeat(count) { SkeletonCampCard() }
        }
    }
}

/** One schedule-timeline row: 44dp time, dot with connector, 14dp card with two lines. */
@Composable
private fun TimelineRowSkeleton() {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonText(width = 36.dp, height = LineH.dp, modifier = Modifier.padding(top = 13.dp))
        Box(Modifier.width(12.dp).fillMaxHeight()) {
            Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().shimmer())
            SkeletonCircle(Modifier.align(Alignment.TopCenter).padding(top = 15.dp), size = 10.dp)
        }
        SkeletonSurface(Modifier.weight(1f).padding(vertical = 4.dp), shape = RowCardShape) {
            Column(Modifier.padding(horizontal = Spacing.medium, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(widthFraction = 0.6f, height = LineH.dp)
                SkeletonLine(widthFraction = 0.7f, height = SmallH.dp)
            }
        }
    }
}

@Composable
fun SkeletonTimelineRows(modifier: Modifier = Modifier, count: Int = 3) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) { repeat(count) { TimelineRowSkeleton() } }
    }
}

enum class CalendarSkeletonMode { Timeline, Time, Trainers }

/** Schedule row: 40dp time, 4x30dp mark, two lines, trailing tag; 54dp tall. */
@Composable
private fun ScheduleRowSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonText(width = 36.dp, height = LineH.dp)
        SkeletonBox(Modifier.width(4.dp).height(30.dp), shape = RoundedCornerShape(2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(widthFraction = 0.55f, height = LineH.dp)
            SkeletonLine(widthFraction = 0.65f, height = SmallH.dp)
        }
        SkeletonText(width = 40.dp, height = SmallH.dp)
    }
}

/** Day content below the header for the active calendar tab: timeline rows, hour groups or trainer cards. */
@Composable
fun SkeletonCalendarBody(mode: CalendarSkeletonMode, modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        when (mode) {
            CalendarSkeletonMode.Timeline ->
                Column(Modifier.padding(horizontal = Spacing.default)) { repeat(5) { TimelineRowSkeleton() } }
            CalendarSkeletonMode.Time -> Column {
                SkeletonToggle(Modifier.padding(horizontal = Spacing.default, vertical = Spacing.xs))
                repeat(3) { group ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = 10.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        SkeletonText(width = 48.dp, height = LineH.dp)
                        SkeletonText(width = 60.dp, height = SmallH.dp)
                    }
                    SkeletonSurface(Modifier.padding(start = Spacing.default, end = Spacing.default, bottom = Spacing.xs)) {
                        repeat(if (group == 0) 3 else 2) { i ->
                            if (i > 0) Divider()
                            ScheduleRowSkeleton()
                        }
                    }
                }
            }
            CalendarSkeletonMode.Trainers -> Column {
                SkeletonToggle(Modifier.padding(horizontal = Spacing.default, vertical = Spacing.xs))
                Column(Modifier.padding(horizontal = Spacing.default), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) {
                        SkeletonSurface {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = Spacing.medium),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SkeletonCircle(size = 32.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SkeletonLine(widthFraction = 0.45f, height = LineH.dp)
                                    SkeletonLine(widthFraction = 0.6f, height = SmallH.dp)
                                }
                                SkeletonCircle(size = 22.dp)
                            }
                            repeat(2) {
                                Divider()
                                ScheduleRowSkeleton()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Calendar day when nothing is known yet: day header (label + summary) plus the tab body. */
@Composable
fun SkeletonCalendarDay(mode: CalendarSkeletonMode = CalendarSkeletonMode.Timeline, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        SkeletonContainer {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.default, end = Spacing.default, top = Spacing.medium, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonText(width = 130.dp, height = LineH.dp)
                SkeletonText(width = 80.dp, height = SmallH.dp)
            }
        }
        SkeletonCalendarBody(mode)
    }
}

/** Competition countdown card: big day number, divider, three text lines. */
@Composable
fun SkeletonCountdownCard(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        SkeletonSurface {
            Row(
                Modifier.padding(horizontal = Spacing.default, vertical = 14.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.default),
            ) {
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonText(width = 48.dp, height = 34.dp)
                    SkeletonText(width = 32.dp, height = SmallH.dp)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().shimmer())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    SkeletonLine(widthFraction = 0.8f, height = LineH.dp)
                    SkeletonLine(widthFraction = 0.6f, height = SmallH.dp)
                    SkeletonLine(widthFraction = 0.4f, height = SmallH.dp)
                }
            }
        }
    }
}

/** Cohort card: 44dp rounded colour tile + name, 16dp card, padding 14. */
@Composable
fun SkeletonCohortList(modifier: Modifier = Modifier, count: Int = 5) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Spacing.default, end = Spacing.default, top = Spacing.medium), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(count) {
                SkeletonSurface {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp))
                        SkeletonLine(Modifier.weight(1f), widthFraction = 0.5f, height = LineH.dp)
                    }
                }
            }
        }
    }
}

/** Podium (58/72/58dp avatars on 56/96/72dp blocks) followed by rank rows. */
@Composable
fun SkeletonLeaderboard(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.default),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(58.dp to 72.dp, 72.dp to 96.dp, 58.dp to 56.dp).forEach { (avatar, block) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonCircle(size = avatar)
                        SkeletonText(width = 60.dp, height = SmallH.dp)
                        SkeletonBox(
                            Modifier.fillMaxWidth().height(block),
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        )
                    }
                }
            }
            Box(Modifier.height(Spacing.small))
            repeat(5) {
                Box(Modifier.padding(horizontal = Spacing.default)) {
                    SkeletonSurface {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                        ) {
                            SkeletonText(width = 24.dp, height = LineH.dp)
                            SkeletonCircle(size = 34.dp)
                            SkeletonLine(Modifier.weight(1f), widthFraction = 0.5f, height = LineH.dp)
                            SkeletonText(width = 30.dp, height = LineH.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Changelog release card: title + tag pill, author line, divider, body lines. */
@Composable
fun SkeletonReleaseList(modifier: Modifier = Modifier, count: Int = 3) {
    SkeletonContainer(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.default).padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            repeat(count) {
                SkeletonSurface {
                    Column(Modifier.padding(Spacing.default)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            SkeletonText(width = 150.dp, height = LineH.dp)
                            SkeletonBox(Modifier.width(50.dp).height(20.dp), shape = PillShape)
                        }
                        SkeletonText(width = 120.dp, height = SmallH.dp, modifier = Modifier.padding(top = 6.dp))
                        Box(Modifier.padding(vertical = 10.dp)) { Divider() }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonLine(height = SmallH.dp)
                            SkeletonLine(height = SmallH.dp)
                            SkeletonLine(widthFraction = 0.6f, height = SmallH.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Info rows in a 16dp card separated by dividers (ProfileValueRow / DetailInfoRow geometry). */
@Composable
private fun SkeletonInfoCard(rows: Int, rowHeight: Dp = 48.dp) {
    SkeletonSurface {
        repeat(rows) { i ->
            if (i > 0) Divider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = rowHeight).padding(horizontal = 14.dp, vertical = Spacing.medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonText(width = 90.dp, height = SmallH.dp)
                SkeletonText(width = 120.dp, height = LineH.dp)
            }
        }
    }
}

/** Person / profile: 80dp avatar header (name, login, details), then labelled info cards. */
@Composable
fun SkeletonProfile(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.default, vertical = Spacing.small)) {
            Row(Modifier.padding(vertical = Spacing.small), horizontalArrangement = Arrangement.spacedBy(Spacing.default), verticalAlignment = Alignment.CenterVertically) {
                SkeletonCircle(size = 80.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonLine(widthFraction = 0.7f, height = 20.dp)
                    SkeletonLine(widthFraction = 0.4f, height = LineH.dp)
                    SkeletonLine(widthFraction = 0.5f, height = LineH.dp)
                }
            }
            repeat(2) {
                SkeletonText(width = 90.dp, height = SmallH.dp, modifier = Modifier.padding(top = 20.dp, bottom = Spacing.small))
                SkeletonInfoCard(rows = 3)
            }
        }
    }
}

/** HeaderWithImage layout (title lines + 120x88 rounded image) followed by body text. */
@Composable
fun SkeletonDetail(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.default)) {
            Row(Modifier.padding(top = Spacing.medium, bottom = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.default), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    SkeletonLine(height = 24.dp)
                    SkeletonLine(widthFraction = 0.6f, height = 24.dp)
                    SkeletonBox(Modifier.width(90.dp).height(24.dp), shape = PillShape)
                }
                SkeletonBox(Modifier.width(120.dp).height(88.dp), shape = CardShape)
            }
            Column(Modifier.padding(top = Spacing.default), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonLine(height = LineH.dp)
                SkeletonLine(height = LineH.dp)
                SkeletonLine(widthFraction = 0.9f, height = LineH.dp)
                SkeletonLine(widthFraction = 0.5f, height = LineH.dp)
            }
        }
    }
}

/** Event detail: full-bleed header block, segmented toggle, section label and a detail info card. */
@Composable
fun SkeletonEventDetail(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Column {
            SkeletonBox(Modifier.fillMaxWidth().height(150.dp), shape = RoundedCornerShape(0.dp))
            SkeletonToggle(Modifier.padding(start = Spacing.default, end = Spacing.default, top = 14.dp))
            SkeletonText(width = 80.dp, height = SmallH.dp, modifier = Modifier.padding(start = Spacing.default, top = 20.dp, bottom = Spacing.small))
            Box(Modifier.padding(horizontal = Spacing.default)) { SkeletonInfoCard(rows = 4, rowHeight = 64.dp) }
        }
    }
}

/** Stats: season chips, three summary cards (76dp), then bar-row cards. */
@Composable
fun SkeletonStats(modifier: Modifier = Modifier) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.default)) {
            Box(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                repeat(3) { SkeletonBox(Modifier.width(72.dp).height(34.dp), shape = RoundedCornerShape(10.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                repeat(3) {
                    SkeletonSurface(Modifier.weight(1f)) {
                        Column(Modifier.heightIn(min = 76.dp).padding(Spacing.medium), verticalArrangement = Arrangement.Center) {
                            SkeletonLine(widthFraction = 0.5f, height = 22.dp)
                            SkeletonLine(Modifier.padding(top = 6.dp), widthFraction = 0.8f, height = SmallH.dp)
                        }
                    }
                }
            }
            repeat(2) {
                SkeletonSurface {
                    Column(Modifier.padding(Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        SkeletonText(width = 110.dp, height = LineH.dp)
                        repeat(4) { i ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                SkeletonText(width = 90.dp, height = SmallH.dp)
                                SkeletonBox(Modifier.weight(1f).fillMaxWidth(1f - i * 0.15f).height(18.dp), shape = RoundedCornerShape(4.dp))
                                SkeletonText(width = 28.dp, height = SmallH.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Forms: section cards of 56dp rounded fields; with [avatar] a 112dp circle on top (profile edit). */
@Composable
fun SkeletonForm(modifier: Modifier = Modifier, avatar: Boolean = false, fields: Int = 5) {
    SkeletonContainer(modifier.fillMaxWidth()) {
        if (avatar) {
            Column(Modifier.padding(horizontal = Spacing.default), horizontalAlignment = Alignment.CenterHorizontally) {
                SkeletonCircle(Modifier.padding(top = Spacing.default), size = 112.dp)
                Column(Modifier.padding(top = Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    repeat(fields) { SkeletonBox(Modifier.fillMaxWidth().height(56.dp), shape = FieldShape) }
                }
            }
        } else {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    SkeletonSurface(shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonText(width = 100.dp, height = LineH.dp)
                            repeat((fields + 1) / 2) { SkeletonBox(Modifier.fillMaxWidth().height(56.dp), shape = FieldShape) }
                        }
                    }
                }
            }
        }
    }
}

/** Badge grid: three columns of square 16dp tiles. */
@Composable
fun SkeletonBadgeGrid(modifier: Modifier = Modifier, rows: Int = 3) {
    SkeletonContainer(modifier.fillMaxSize()) {
        Column(Modifier.padding(Spacing.default), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    repeat(3) { SkeletonBox(Modifier.weight(1f).aspectRatio(1f), shape = CardShape) }
                }
            }
        }
    }
}

/** Day timeline (60dp per hour, 60dp hour column) with a few event blocks (8dp shape). */
@Composable
fun SkeletonTimeline(modifier: Modifier = Modifier, hours: Int = 9) {
    SkeletonContainer(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                repeat(hours) {
                    Row(Modifier.fillMaxWidth().height(60.dp)) {
                        Box(Modifier.width(60.dp).height(60.dp)) {
                            SkeletonText(width = 30.dp, height = 11.dp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp))
                        }
                        Box(Modifier.weight(1f).height(1.dp).shimmer())
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 60.dp)) {
                SkeletonBox(Modifier.fillMaxWidth(0.5f).height(90.dp).offset(y = 40.dp).padding(horizontal = 3.dp, vertical = 1.dp), shape = PillShape)
                SkeletonBox(Modifier.fillMaxWidth(0.5f).height(60.dp).offset(y = 130.dp).align(Alignment.TopEnd).padding(horizontal = 3.dp, vertical = 1.dp), shape = PillShape)
                SkeletonBox(Modifier.fillMaxWidth().height(120.dp).offset(y = 260.dp).padding(horizontal = 3.dp, vertical = 1.dp), shape = PillShape)
            }
        }
    }
}
