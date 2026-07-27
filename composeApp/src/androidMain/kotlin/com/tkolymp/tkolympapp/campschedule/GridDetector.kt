package com.tkolymp.tkolympapp.campschedule

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Detects the black grid of a photographed schedule table and returns, for each row,
 * the cell bounding boxes in image pixel coordinates. Row 0 is the header row (time
 * corner + one cell per person column). A merged/full-width row (e.g. "Oběd") is
 * detected by the absence of internal vertical grid lines and is returned with only
 * 2 cells: the time cell and one cell spanning the rest of the row's width.
 */
object GridDetector {

    // Tuning constants. Modern phone photos are commonly 3000-4000px+ per side, so
    // thresholds are expressed relative to image size rather than as fixed pixel
    // counts — a fixed 12px merge distance (say) is generous for a scanned document
    // but far too tight for a handheld photo, where a "straight" printed line can
    // drift tens of pixels across a 4000px-wide frame from minor camera rotation.
    private const val ADAPTIVE_BLOCK_SIZE = 15
    private const val ADAPTIVE_C = 8.0
    private const val LINE_MERGE_FRACTION = 0.006
    private const val MIN_LINE_MERGE_DISTANCE_PX = 12
    private const val LINE_KERNEL_DIVISOR = 25.0
    private const val LINE_KERNEL_THICKNESS_PX = 2.0
    private const val HOUGH_THRESHOLD = 60
    private const val HOUGH_MAX_LINE_GAP_FRACTION = 0.01
    private const val MAX_LINE_ANGLE_DEG = 5.0

    private var openCvReady = false

    private fun ensureOpenCvLoaded() {
        if (!openCvReady) {
            openCvReady = OpenCVLoader.initLocal()
        }
    }

    fun detectGrid(bitmap: Bitmap): List<List<Rect>> {
        ensureOpenCvLoaded()

        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

        val binary = Mat()
        Imgproc.adaptiveThreshold(
            gray, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV,
            ADAPTIVE_BLOCK_SIZE, ADAPTIVE_C
        )

        val width = binary.cols()
        val height = binary.rows()
        val diagonal = kotlin.math.hypot(width.toDouble(), height.toDouble())
        val lineMergeDistance = (diagonal * LINE_MERGE_FRACTION).toInt().coerceAtLeast(MIN_LINE_MERGE_DISTANCE_PX)
        val houghMaxLineGap = diagonal * HOUGH_MAX_LINE_GAP_FRACTION
        // The "thin" side of each kernel must stay close to the actual printed line's pixel
        // thickness (a few px, regardless of photo resolution) — erosion erases any line
        // thinner than the kernel, so this must NOT scale up with image size.
        val kernelThickness = LINE_KERNEL_THICKNESS_PX

        val horizontalSegments = detectLineSegments(
            binary,
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size((width / LINE_KERNEL_DIVISOR).coerceAtLeast(1.0), kernelThickness)),
            minLineLength = width * 0.4,
            maxLineGap = houghMaxLineGap,
            horizontal = true
        )
        val verticalSegments = detectLineSegments(
            binary,
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kernelThickness, (height / LINE_KERNEL_DIVISOR).coerceAtLeast(1.0))),
            minLineLength = height * 0.015,
            maxLineGap = houghMaxLineGap,
            horizontal = false
        )

        // Real photos sometimes have a locally faint/broken separator line (shadow, glare,
        // a crease) that Hough misses entirely, merging several real rows into one giant
        // cell. As a fallback, any row gap far larger than the typical row height is split
        // evenly rather than left as one oversized cell.
        val rowBoundaries = subdivideOutlierGaps(clusterPositions(horizontalSegments.map { it.midY() }, height, lineMergeDistance))
        val colBoundaries = clusterPositions(verticalSegments.map { it.midX() }, width, lineMergeDistance)

        if (rowBoundaries.size < 2 || colBoundaries.size < 2) return emptyList()

        return (0 until rowBoundaries.size - 1).map { row ->
            val rowTop = rowBoundaries[row]
            val rowBottom = rowBoundaries[row + 1]
            if (row > 0 && !hasInternalVerticalSegments(verticalSegments, colBoundaries, rowTop, rowBottom, lineMergeDistance)) {
                listOf(
                    Rect(colBoundaries[0], rowTop, colBoundaries[1], rowBottom),
                    Rect(colBoundaries[1], rowTop, colBoundaries.last(), rowBottom)
                )
            } else {
                (0 until colBoundaries.size - 1).map { col ->
                    Rect(colBoundaries[col], rowTop, colBoundaries[col + 1], rowBottom)
                }
            }
        }
    }

    private data class Segment(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        fun midY() = (y1 + y2) / 2
        fun midX() = (x1 + x2) / 2
    }

    private fun detectLineSegments(binary: Mat, kernel: Mat, minLineLength: Double, maxLineGap: Double, horizontal: Boolean): List<Segment> {
        val isolated = Mat()
        Imgproc.morphologyEx(binary, isolated, Imgproc.MORPH_OPEN, kernel)

        val lines = Mat()
        Imgproc.HoughLinesP(isolated, lines, 1.0, Math.PI / 180, HOUGH_THRESHOLD, minLineLength, maxLineGap)

        val segments = mutableListOf<Segment>()
        for (i in 0 until lines.rows()) {
            val v = lines.get(i, 0)
            val (x1, y1, x2, y2) = listOf(v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3].toInt())
            val angleDeg = Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()))
            val isRoughlyHorizontal = abs(angleDeg) < MAX_LINE_ANGLE_DEG || abs(abs(angleDeg) - 180) < MAX_LINE_ANGLE_DEG
            val isRoughlyVertical = abs(abs(angleDeg) - 90) < MAX_LINE_ANGLE_DEG
            if (horizontal && isRoughlyHorizontal) segments += Segment(x1, y1, x2, y2)
            if (!horizontal && isRoughlyVertical) segments += Segment(x1, y1, x2, y2)
        }
        return segments
    }

    /** Merges near-duplicate line positions and returns sorted boundary coordinates, including the image edges. */
    private fun clusterPositions(positions: List<Int>, imageExtent: Int, mergeDistance: Int): List<Int> {
        if (positions.isEmpty()) return emptyList()
        val sorted = positions.sorted()
        val clusters = mutableListOf(mutableListOf(sorted.first()))
        for (p in sorted.drop(1)) {
            if (p - clusters.last().last() <= mergeDistance) {
                clusters.last() += p
            } else {
                clusters += mutableListOf(p)
            }
        }
        val merged = clusters.map { it.sum() / it.size }.toMutableList()
        if (merged.first() > mergeDistance) merged.add(0, 0)
        if (merged.last() < imageExtent - mergeDistance) merged.add(imageExtent)
        return merged.sorted()
    }

    /** Splits any gap much larger than the median gap into evenly-sized sub-gaps. */
    private fun subdivideOutlierGaps(boundaries: List<Int>): List<Int> {
        if (boundaries.size < 3) return boundaries
        val gaps = boundaries.zipWithNext { a, b -> b - a }
        val median = gaps.sorted()[gaps.size / 2]
        if (median <= 0) return boundaries
        val result = mutableListOf(boundaries.first())
        for (i in boundaries.indices.drop(1)) {
            val start = boundaries[i - 1]
            val end = boundaries[i]
            val gap = end - start
            val subCount = (gap / median.toDouble()).toInt().coerceAtLeast(1)
            if (subCount > 1 && gap > median * 1.6) {
                for (k in 1 until subCount) result += start + (gap * k / subCount)
            }
            result += end
        }
        return result
    }

    /** True if a raw vertical segment spans this row's y-range at any internal (non-edge) column boundary. */
    private fun hasInternalVerticalSegments(verticalSegments: List<Segment>, colBoundaries: List<Int>, rowTop: Int, rowBottom: Int, mergeDistance: Int): Boolean {
        val internalCols = colBoundaries.drop(2).dropLast(1)
        if (internalCols.isEmpty()) return true
        return internalCols.any { colX ->
            verticalSegments.any { seg ->
                abs(seg.midX() - colX) <= mergeDistance &&
                    max(seg.y1, seg.y2) >= rowTop &&
                    min(seg.y1, seg.y2) <= rowBottom
            }
        }
    }
}
