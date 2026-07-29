package com.tkolymp.tkolympapp.campschedule

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Detects the black grid of a photographed schedule table and returns, for each row,
 * the cell bounding boxes in image pixel coordinates. Row/column boundaries are found
 * via projection profiles (summing "on" pixels per row/column after adaptive
 * thresholding) rather than Hough line detection — for a table's long, straight grid
 * lines this aggregates evidence across the whole line at once, which is far less
 * sensitive to the per-pixel noise introduced by JPEG re-encoding/decoding than
 * fragile line-continuity-based detection.
 *
 * A merged/full-width row (e.g. a title bar like "PONDĚLÍ" above the real header, or a
 * note row like "Oběd") is detected by the absence of internal column dividers within
 * that row's own y-range, and is returned with only 2 cells: the time/leading cell and
 * one cell spanning the rest of the row's width. The real header row (time corner +
 * one cell per person column) is therefore not necessarily row 0 — callers must find
 * the first non-merged row themselves.
 */
object GridDetector {

    private const val ADAPTIVE_BLOCK_SIZE = 15
    private const val ADAPTIVE_C = 8.0
    // A boundary line is wherever the row/column "on"-pixel count is at least this
    // fraction of the profile's peak — grid lines are the strongest, most consistent
    // features in the image, so a fairly high fraction cleanly separates them from text.
    private const val PEAK_THRESHOLD_FRACTION = 0.5
    private const val MIN_PEAK_GAP_FRACTION = 0.02
    private const val MIN_PEAK_GAP_FLOOR_PX = 5
    private const val EDGE_MARGIN_FRACTION = 0.01
    private const val EDGE_MARGIN_FLOOR_PX = 5
    // A row counts as having an internal column divider only if some x-window near a
    // column boundary is "on" for almost this row's entire height. A real grid line
    // spans (close to) 100% of the row height; a tall text stroke that merely happens
    // to fall near a column boundary (e.g. a "d"/"l" ascender in a merged note row's
    // text) reaches at most ~60% in practice — measured against real sample photos,
    // where genuine dividers sit at 1.00 and coincidental strokes cap around 0.56-0.61.
    private const val ROW_INTERNAL_THRESHOLD_FRACTION = 0.85
    private const val ROW_INTERNAL_WINDOW_PX = 3
    // Row/column boundary pairs closer together than this are almost certainly noise
    // (e.g. a table border line detected a second time a few px off) and are merged
    // into the previous segment rather than kept as their own tiny sliver.
    private const val MIN_SEGMENT_HEIGHT_PX = 8
    // A merged/note row's OCR crop is tightened from the full table width down to just
    // the horizontal span of actual ink, plus this margin — ML Kit's text recognizer
    // reliably fails on the full-width crop (~40:1 width:height for a short row) but
    // succeeds once the crop is closer to a normal cell's aspect ratio.
    private const val MERGED_TEXT_MARGIN_PX = 10
    private const val MERGED_INK_THRESHOLD_FRACTION = 0.15
    private const val MERGED_BORDER_EXCLUSION_PX = 4

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
        // Row separators are naturally much closer together (one per table row) than
        // column separators (one per person column spanning the full width), so each
        // axis's minimum peak gap must be relative to ITS OWN extent, not the other's —
        // using max(width, height) for both made the row gap far too large for a dense
        // table and silently merged distinct row lines together.
        val rowMinPeakGap = (height * MIN_PEAK_GAP_FRACTION).toInt().coerceAtLeast(MIN_PEAK_GAP_FLOOR_PX)
        val colMinPeakGap = (width * MIN_PEAK_GAP_FRACTION).toInt().coerceAtLeast(MIN_PEAK_GAP_FLOOR_PX)
        val rowEdgeMargin = (height * EDGE_MARGIN_FRACTION).toInt().coerceAtLeast(EDGE_MARGIN_FLOOR_PX)
        val colEdgeMargin = (width * EDGE_MARGIN_FRACTION).toInt().coerceAtLeast(EDGE_MARGIN_FLOOR_PX)

        val rowBoundaries = dropThinSegments(
            addEdges(findPeaks(rowSums(binary), PEAK_THRESHOLD_FRACTION, rowMinPeakGap), height, rowEdgeMargin)
        )
        val colBoundaries = dropThinSegments(
            addEdges(findPeaks(colSums(binary), PEAK_THRESHOLD_FRACTION, colMinPeakGap), width, colEdgeMargin)
        )

        if (rowBoundaries.size < 2 || colBoundaries.size < 2) return emptyList()

        val internalCols = colBoundaries.drop(2).dropLast(1)

        return (0 until rowBoundaries.size - 1).map { row ->
            val rowTop = rowBoundaries[row]
            val rowBottom = rowBoundaries[row + 1]
            if (!hasInternalColumnDividers(binary, internalCols, rowTop, rowBottom)) {
                listOf(
                    Rect(colBoundaries[0], rowTop, colBoundaries[1], rowBottom),
                    tightenMergedCellRect(binary, rowTop, rowBottom, colBoundaries[1], colBoundaries.last())
                )
            } else {
                (0 until colBoundaries.size - 1).map { col ->
                    Rect(colBoundaries[col], rowTop, colBoundaries[col + 1], rowBottom)
                }
            }
        }
    }

    /** Count of "on" (thresholded) pixels in each row, i.e. a vertical profile of horizontal line strength. */
    private fun rowSums(binary: Mat): IntArray {
        val reduced = Mat()
        Core.reduce(binary, reduced, 1, Core.REDUCE_SUM, CvType.CV_32S)
        val result = IntArray(binary.rows())
        val buf = IntArray(1)
        for (i in result.indices) {
            reduced.get(i, 0, buf)
            result[i] = buf[0] / 255
        }
        return result
    }

    /** Count of "on" pixels in each column, i.e. a horizontal profile of vertical line strength. */
    private fun colSums(binary: Mat): IntArray {
        val reduced = Mat()
        Core.reduce(binary, reduced, 0, Core.REDUCE_SUM, CvType.CV_32S)
        val result = IntArray(binary.cols())
        val buf = IntArray(1)
        for (j in result.indices) {
            reduced.get(0, j, buf)
            result[j] = buf[0] / 255
        }
        return result
    }

    /** Finds contiguous runs at/above [thresholdFraction] of the profile's peak, collapsing each to its center. */
    private fun findPeaks(profile: IntArray, thresholdFraction: Double, minGap: Int): List<Int> {
        val maxVal = profile.maxOrNull() ?: return emptyList()
        if (maxVal == 0) return emptyList()
        val threshold = maxVal * thresholdFraction
        val rawPeaks = mutableListOf<Int>()
        var start = -1
        for (i in profile.indices) {
            val above = profile[i] >= threshold
            if (above && start < 0) {
                start = i
            } else if (!above && start >= 0) {
                rawPeaks += (start + i - 1) / 2
                start = -1
            }
        }
        if (start >= 0) rawPeaks += (start + profile.size - 1) / 2

        val merged = mutableListOf<Int>()
        for (p in rawPeaks) {
            if (merged.isNotEmpty() && p - merged.last() < minGap) {
                merged[merged.size - 1] = (merged.last() + p) / 2
            } else {
                merged += p
            }
        }
        return merged
    }

    private fun addEdges(peaks: List<Int>, extent: Int, edgeMargin: Int): List<Int> {
        if (peaks.isEmpty()) return emptyList()
        val result = peaks.toMutableList()
        if (result.first() > edgeMargin) result.add(0, 0)
        if (result.last() < extent - edgeMargin) result.add(extent)
        return result
    }

    /** Merges boundary pairs closer than [MIN_SEGMENT_HEIGHT_PX] into the previous segment (drops noise slivers). */
    private fun dropThinSegments(boundaries: List<Int>): List<Int> {
        if (boundaries.size < 2) return boundaries
        val result = mutableListOf(boundaries.first())
        for (i in 1 until boundaries.size) {
            val b = boundaries[i]
            if (b - result.last() >= MIN_SEGMENT_HEIGHT_PX) {
                result += b
            } else if (i == boundaries.size - 1) {
                // The final boundary is too close to the previous one to be a real
                // division (e.g. a few px of margin below the last content row) —
                // replace rather than append, so it doesn't invent an empty sliver
                // "row" out of pure whitespace at the image edge.
                result[result.size - 1] = b
            }
        }
        return result
    }

    /**
     * Shrinks a merged/note row's full-width cell down to the horizontal span of its
     * actual ink (plus [MERGED_TEXT_MARGIN_PX] margin on each side), so the OCR crop's
     * aspect ratio is close to a normal cell's instead of the full table width — the
     * latter reliably fails on-device text recognition for a short row. Falls back to
     * the untightened full width if no ink is found (e.g. a genuinely blank row).
     */
    private fun tightenMergedCellRect(binary: Mat, rowTop: Int, rowBottom: Int, left: Int, right: Int): Rect {
        val rowHeight = rowBottom - rowTop
        val fullWidthRect = Rect(left, rowTop, right, rowBottom)
        if (rowHeight <= 0 || right <= left) return fullWidthRect

        val rowSlice = Mat(binary, org.opencv.core.Rect(left, rowTop, right - left, rowHeight))
        val profile = colSums(rowSlice)
        val threshold = rowHeight * MERGED_INK_THRESHOLD_FRACTION
        val searchStart = MERGED_BORDER_EXCLUSION_PX.coerceAtMost(profile.size)
        val searchEnd = (profile.size - MERGED_BORDER_EXCLUSION_PX).coerceAtLeast(searchStart)

        var minInk = -1
        var maxInk = -1
        for (i in searchStart until searchEnd) {
            if (profile[i] >= threshold) {
                if (minInk < 0) minInk = i
                maxInk = i
            }
        }
        if (minInk < 0) return fullWidthRect

        val tightLeft = (left + minInk - MERGED_TEXT_MARGIN_PX).coerceAtLeast(left)
        val tightRight = (left + maxInk + MERGED_TEXT_MARGIN_PX).coerceAtMost(right)
        return Rect(tightLeft, rowTop, tightRight, rowBottom)
    }

    /** True if some x-window near an internal column boundary is "on" for a real majority of this row's height. */
    private fun hasInternalColumnDividers(binary: Mat, internalCols: List<Int>, rowTop: Int, rowBottom: Int): Boolean {
        if (internalCols.isEmpty()) return true
        val rowHeight = rowBottom - rowTop
        if (rowHeight <= 0) return false
        val rowSlice = Mat(binary, org.opencv.core.Rect(0, rowTop, binary.cols(), rowHeight))
        val colProfile = colSums(rowSlice)
        val threshold = rowHeight * ROW_INTERNAL_THRESHOLD_FRACTION
        return internalCols.any { colX ->
            val left = (colX - ROW_INTERNAL_WINDOW_PX).coerceAtLeast(0)
            val right = (colX + ROW_INTERNAL_WINDOW_PX).coerceAtMost(colProfile.size - 1)
            (left..right).any { colProfile[it] >= threshold }
        }
    }
}
