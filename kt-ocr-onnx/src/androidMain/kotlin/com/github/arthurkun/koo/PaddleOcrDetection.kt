package com.github.arthurkun.koo

import kotlinx.coroutines.CoroutineScope
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Android implementation of text detection using PaddleOCR v5 DB ONNX model.
 *
 * Extends [PaddleOcrDetectionBase] with Android OpenCV (`org.opencv.core.*`) for
 * the DB postprocessing step (contour finding, box scoring, polygon operations).
 */
internal class PaddleOcrDetection(
    scope: CoroutineScope,
    modelPath: String = DET_MODEL_PATH,
) : PaddleOcrDetectionBase(scope, modelPath) {

    override fun dbPostProcess(
        probMap: Array<FloatArray>,
        mapH: Int,
        mapW: Int,
        srcH: Int,
        srcW: Int,
    ): List<DetectedResults> {
        // Create probability Mat and binary mask
        val probMat = Mat(mapH, mapW, CvType.CV_32FC1)
        val binaryMat = Mat(mapH, mapW, CvType.CV_8UC1)

        for (y in 0 until mapH) {
            for (x in 0 until mapW) {
                probMat.put(y, x, floatArrayOf(probMap[y][x]))
                val binaryVal: Byte = if (probMap[y][x] > DET_THRESH) 255.toByte() else 0
                binaryMat.put(y, x, byteArrayOf(binaryVal))
            }
        }

        // Find contours on binary mask
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val numContours = min(contours.size, DET_MAX_CANDIDATES)
        val boxes = mutableListOf<DetectedResults>()

        for (i in 0 until numContours) {
            val contour = contours[i]
            val contour2f = MatOfPoint2f(*contour.toArray())

            // Get minimum area rotated rectangle
            val rotatedRect = Imgproc.minAreaRect(contour2f)
            contour2f.release()

            val rectPoints = arrayOfNulls<Point>(4)
            rotatedRect.points(rectPoints)

            val sside = min(rotatedRect.size.width, rotatedRect.size.height).toFloat()
            if (sside < DET_MIN_SIZE) {
                contour.release()
                continue
            }

            // Convert Point objects to DoubleArrays for shared getMiniBoxPoints
            val pointArrays = rectPoints.filterNotNull().map { doubleArrayOf(it.x, it.y) }
            val sortedPoints = getMiniBoxPoints(pointArrays)

            // Compute score (box_score_fast)
            val score = boxScoreFast(probMat, sortedPoints, mapH, mapW)
            if (score < DET_BOX_THRESH) {
                contour.release()
                continue
            }

            // Unclip (expand) the polygon
            val expanded = unclipPolygon(sortedPoints)
            if (expanded.isEmpty()) {
                contour.release()
                continue
            }

            // Re-fit minimum area rect on expanded polygon
            val expandedMat = MatOfPoint2f(*expanded.map { Point(it[0], it[1]) }.toTypedArray())
            val expandedRect = Imgproc.minAreaRect(expandedMat)
            expandedMat.release()

            val expandedSside = min(expandedRect.size.width, expandedRect.size.height).toFloat()
            if (expandedSside < DET_MIN_SIZE + 2) {
                contour.release()
                continue
            }

            val expandedRectPoints = arrayOfNulls<Point>(4)
            expandedRect.points(expandedRectPoints)
            val expandedPointArrays = expandedRectPoints.filterNotNull().map { doubleArrayOf(it.x, it.y) }
            val finalPoints = getMiniBoxPoints(expandedPointArrays)

            // Scale back to original image coordinates
            val scaledPoints = finalPoints.map { pt ->
                BoxPoint(
                    (pt[0] / mapW * srcW).roundToInt().coerceIn(0, srcW),
                    (pt[1] / mapH * srcH).roundToInt().coerceIn(0, srcH),
                )
            }

            boxes.add(DetectedResults(scaledPoints, score))
            contour.release()
        }

        // Cleanup
        probMat.release()
        binaryMat.release()

        return boxes
    }

    /**
     * Computes the average probability score inside a box region.
     *
     * Reference: ppocr/postprocess/db_postprocess.py box_score_fast
     */
    private fun boxScoreFast(
        probMat: Mat,
        points: List<DoubleArray>,
        mapH: Int,
        mapW: Int,
    ): Float {
        val xCoords = points.map { it[0] }
        val yCoords = points.map { it[1] }

        val xmin = floor(xCoords.min()).toInt().coerceIn(0, mapW - 1)
        val xmax = ceil(xCoords.max()).toInt().coerceIn(0, mapW - 1)
        val ymin = floor(yCoords.min()).toInt().coerceIn(0, mapH - 1)
        val ymax = ceil(yCoords.max()).toInt().coerceIn(0, mapH - 1)

        if (xmin >= xmax || ymin >= ymax) return 0f

        // Create a mask for the polygon region
        val roiW = xmax - xmin + 1
        val roiH = ymax - ymin + 1
        val mask = Mat.zeros(roiH, roiW, CvType.CV_8UC1)

        // Shift points to ROI coordinates
        val shiftedPoints = points.map {
            Point(
                (it[0] - xmin).roundToInt().coerceIn(0, roiW - 1).toDouble(),
                (it[1] - ymin).roundToInt().coerceIn(0, roiH - 1).toDouble(),
            )
        }
        val contour = MatOfPoint(
            *shiftedPoints.map {
                Point(it.x, it.y)
            }.toTypedArray(),
        )

        Imgproc.fillPoly(mask, listOf(contour), Scalar(255.0))
        contour.release()

        // Extract ROI from probability map and compute mean with mask
        val roi = probMat.submat(ymin, ymax + 1, xmin, xmax + 1)
        val meanVal = Core.mean(roi, mask)

        roi.release()
        mask.release()

        return meanVal.`val`[0].toFloat()
    }
}
