package com.tkolymp.tkolympapp.components.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.shared.competitivedata.FederationEventSummary
import com.tkolymp.shared.language.AppStrings
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

private const val MAX_ZOOM = 60f
private const val CLUSTER_CELL_DP = 34f

/** Hue per month (1..12) - the same colour keys the month chips of the event list. */
fun monthColor(month: Int): Color = Color.hsv(((month - 1) * 30f + 355f) % 360f, 0.8f, 0.95f)

private class Cluster(val events: List<FederationEventSummary>, val pos: Offset)

/**
 * Vector map with one dot per event that has coordinates; dots that would overlap on screen merge into a
 * numbered cluster that splits when zooming in. Pan, pinch-zoom, double-tap and tap are supported.
 * [onSelect] receives every event under the tapped dot/cluster (empty when the tap hit nothing).
 */
@Composable
fun EventMap(
    events: List<FederationEventSummary>,
    region: MapRegion,
    selectedIds: Set<Long>,
    onSelect: (List<FederationEventSummary>) -> Unit,
    modifier: Modifier = Modifier,
    overlayTop: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val ocean = colors.surfaceContainerLow
    val landColor = colors.surfaceContainerHighest
    val outline = colors.outlineVariant
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val clusterColor = colors.primary
    val onCluster = colors.onPrimary

    val land = remember(region) { MapGeometry.land(region).map { MapGeometry.toPath(it, region) } }
    val borders = remember(region) { MapGeometry.borders(region)?.let { MapGeometry.toPath(it, region) } }
    val points = remember(events, region) {
        events.filter { it.hasCoordinates }.map { it to region.project(it.lng!!, it.lat!!) }
    }

    var zoom by remember(region) { mutableFloatStateOf(1f) }
    var pan by remember(region) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // screen = (pt - mapCentre) * s + canvasCentre + pan, with s = fit * zoom.
    fun fit(): Float = if (canvasSize.width == 0) 1f else min(canvasSize.width / region.width, canvasSize.height / region.height)
    fun toScreen(p: Offset): Offset {
        val s = fit() * zoom
        return Offset((p.x - region.width / 2) * s + canvasSize.width / 2 + pan.x, (p.y - region.height / 2) * s + canvasSize.height / 2 + pan.y)
    }
    fun clampPan(p: Offset): Offset {
        // The map may be dragged until its edge reaches the middle of the view, never further.
        val s = fit() * zoom
        val mx = region.width * s / 2
        val my = region.height * s / 2
        return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
    }
    fun zoomBy(factor: Float, focus: Offset) {
        val newZoom = (zoom * factor).coerceIn(1f, MAX_ZOOM)
        val k = newZoom / zoom
        val centre = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        zoom = newZoom
        pan = clampPan(focus - centre - (focus - centre - pan) * k)
    }

    // Greedy grid clustering in screen space; recomputed when the view changes (a few hundred points at most).
    val clusters = run {
        val cell = with(density) { CLUSTER_CELL_DP.dp.toPx() }
        if (canvasSize.width == 0) emptyList() else points
            .groupBy { (_, p) -> toScreen(p).let { floor(it.x / cell).toInt() to floor(it.y / cell).toInt() } }
            .values.map { group ->
                val screen = group.map { toScreen(it.second) }
                Cluster(group.map { it.first }, Offset(screen.map { it.x }.average().toFloat(), screen.map { it.y }.average().toFloat()))
            }
    }

    Box(modifier.clip(RoundedCornerShape(16.dp)).background(ocean)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(region) {
                    detectTransformGestures { centroid, dragged, gestureZoom, _ ->
                        pan = clampPan(pan + dragged)
                        zoomBy(gestureZoom, centroid)
                    }
                }
                .pointerInput(region, clusters) {
                    detectTapGestures(
                        onDoubleTap = { zoomBy(2f, it) },
                        onTap = { tap ->
                            val reach = with(density) { 28.dp.toPx() }
                            val hit = clusters.minByOrNull { (it.pos - tap).getDistance() }?.takeIf { (it.pos - tap).getDistance() <= reach }
                            when {
                                hit == null -> onSelect(emptyList())
                                // Several events: zoom in on them first; once zoomed far enough (same venue) list them.
                                hit.events.size > 1 && zoom < MAX_ZOOM / 4 -> { zoomBy(2.4f, hit.pos); onSelect(emptyList()) }
                                else -> onSelect(hit.events)
                            }
                        }
                    )
                }
        ) {
            val s = fit() * zoom
            val origin = toScreen(Offset.Zero)
            val hair = 0.7.dp.toPx() / s
            withTransform({
                translate(origin.x, origin.y)
                scale(s, s, pivot = Offset.Zero)
            }) {
                land.forEach { drawPath(it, landColor) }
                land.forEach { drawPath(it, outline, style = Stroke(width = hair * 1.2f)) }
                borders?.let { drawPath(it, outline.copy(alpha = 0.7f), style = Stroke(width = hair)) }
            }
            for (c in clusters) {
                val selected = c.events.any { it.id in selectedIds }
                if (c.events.size == 1) {
                    val e = c.events.first()
                    val r = 6.dp.toPx()
                    drawCircle(Color.Black.copy(alpha = 0.25f), r + 2.dp.toPx(), c.pos)
                    drawCircle(monthColor(e.month), r, c.pos)
                    if (selected) drawCircle(colors.onSurface, r + 3.5.dp.toPx(), c.pos, style = Stroke(2.dp.toPx()))
                } else {
                    val r = (12 + sqrt(c.events.size.toFloat()) * 2.2f).dp.toPx()
                    drawCircle(clusterColor.copy(alpha = 0.25f), r + 5.dp.toPx(), c.pos)
                    drawCircle(clusterColor, r, c.pos)
                    if (selected) drawCircle(colors.onSurface, r + 3.dp.toPx(), c.pos, style = Stroke(2.dp.toPx()))
                    val label = measurer.measure(c.events.size.toString(), TextStyle(color = onCluster, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    drawText(label, topLeft = c.pos - Offset(label.size.width / 2f, label.size.height / 2f))
                }
            }
        }

        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { overlayTop() }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            val centre = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            MapButton(Icons.Filled.Add, AppStrings.current.danceOrg.zoomIn) { zoomBy(1.8f, centre) }
            MapButton(Icons.Filled.Remove, AppStrings.current.danceOrg.zoomOut) { zoomBy(1 / 1.8f, centre) }
            MapButton(Icons.Filled.Refresh, AppStrings.current.danceOrg.resetMap) { zoom = 1f; pan = Offset.Zero }
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    // 48dp touch target around a 36dp visual button.
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shadowElevation = 2.dp, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp)) }
        }
    }
}
