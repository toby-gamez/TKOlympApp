package com.tkolymp.tkolympapp.components.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos

/** Which part of the globe an [EventMap] shows. */
enum class MapRegion(
    val minLon: Double, val maxLon: Double, val minLat: Double, val maxLat: Double
) {
    WORLD(-180.0, 180.0, -58.0, 84.0),
    CZECHIA(11.8, 19.0, 48.4, 51.2),
    SLOVAKIA(16.7, 22.7, 47.6, 49.8);

    /** Horizontal stretch so a degree of longitude is as wide as it is on the ground (World stays plain equirectangular). */
    val lonScale: Double get() = if (this == WORLD) 1.0 else cos((minLat + maxLat) / 2 * PI / 180)

    val width: Float get() = MAP_UNITS
    val height: Float get() = (MAP_UNITS * (maxLat - minLat) / ((maxLon - minLon) * lonScale)).toFloat()

    /** Projects a coordinate into map units (0..[width], 0..[height]); y grows downwards. */
    fun project(lon: Double, lat: Double): Offset = Offset(
        ((lon - minLon) / (maxLon - minLon) * width).toFloat(),
        ((maxLat - lat) / (maxLat - minLat) * height).toFloat()
    )

    companion object { const val MAP_UNITS = 1000f }
}

/** Parsed outline rings as flat lon/lat pairs. */
internal class MapRings(val rings: List<DoubleArray>)

internal object MapGeometry {
    private fun decode(data: String, scale: Double): MapRings = MapRings(
        data.split(';').filter { it.isNotBlank() }.map { ring ->
            val pts = ring.trim().split(' ')
            val out = DoubleArray(pts.size * 2)
            var x = 0L; var y = 0L
            pts.forEachIndexed { i, p ->
                val c = p.indexOf(',')
                x += p.substring(0, c).toLong(); y += p.substring(c + 1).toLong()
                out[i * 2] = x / scale; out[i * 2 + 1] = y / scale
            }
            out
        }
    )

    private val world by lazy { decode(MapGeometryData.WORLD, 100.0) }
    private val czechia by lazy { decode(MapGeometryData.CZECHIA, 1000.0) }
    private val slovakia by lazy { decode(MapGeometryData.SLOVAKIA, 1000.0) }
    private val czechiaRegions by lazy { decode(MapGeometryData.CZECHIA_REGIONS, 1000.0) }
    private val slovakiaRegions by lazy { decode(MapGeometryData.SLOVAKIA_REGIONS, 1000.0) }

    /** Filled country shapes (land) and inner borders drawn as outlines. */
    fun land(region: MapRegion): List<MapRings> = when (region) {
        MapRegion.WORLD -> listOf(world)
        // Neighbouring countries give context around the focused one.
        MapRegion.CZECHIA -> listOf(czechia, slovakia, world)
        MapRegion.SLOVAKIA -> listOf(slovakia, czechia, world)
    }

    fun borders(region: MapRegion): MapRings? = when (region) {
        MapRegion.CZECHIA -> czechiaRegions
        MapRegion.SLOVAKIA -> slovakiaRegions
        MapRegion.WORLD -> null
    }

    fun toPath(rings: MapRings, region: MapRegion): Path = Path().apply {
        for (r in rings.rings) {
            var i = 0
            while (i < r.size) {
                val p = region.project(r[i], r[i + 1])
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                i += 2
            }
            close()
        }
    }
}
