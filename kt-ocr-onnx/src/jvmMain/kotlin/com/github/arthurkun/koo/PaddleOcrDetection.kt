package com.github.arthurkun.koo

import kotlinx.coroutines.CoroutineScope
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacpp.indexer.IntIndexer
import org.bytedeco.javacpp.indexer.UByteIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_core.CV_32FC1
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE
import org.bytedeco.opencv.global.opencv_imgproc.RETR_LIST
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Point2f
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Scalar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * JVM implementation of text detection using PaddleOCR v5 DB ONNX model.
 *
 * Extends [PaddleOcrDetectionBase] with JavaCPP OpenCV (`org.bytedeco.opencv.*`) for
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
        val probMat = Mat(mapH, mapW, CV_32FC1)
        val binaryMat = Mat(mapH, mapW, CV_8UC1)

        val probIndexer = probMat.createIndexer<FloatIndexer>()
        val binaryIndexer = binaryMat.createIndexer<UByteIndexer>()

        for (y in 0 until mapH) {
            for (x in 0 until mapW) {
                probIndexer.put(y.toLong(), x.toLong(), probMap[y][x])
                val binaryVal = if (probMap[y][x] > DET_THRESH) 255 else 0
                binaryIndexer.put(y.toLong(), x.toLong(), binaryVal)
            }
        }
        probIndexer.close()
        binaryIndexer.close()

        // Find contours on binary mask
        val contours = MatVector()
        val hierarchy = Mat()
        opencv_imgproc.findContours(binaryMat, contours, hierarchy, RETR_LIST, CHAIN_APPROX_SIMPLE)
        hierarchy.close()

        val numContours = min(contours.size().toInt(), DET_MAX_CANDIDATES)
        val boxes = mutableListOf<DetectedResults>()

        for (i in 0 until numContours) {
            val contour = contours.get(i.toLong())
            try {
                // Get minimum area rotated rectangle
                val contour2f = Mat()
                contour.convertTo(contour2f, opencv_core.CV_32F)
                val rotatedRect = opencv_imgproc.minAreaRect(contour2f)
                contour2f.close()

                val sside = min(rotatedRect.size().width(), rotatedRect.size().height())
                if (sside < DET_MIN_SIZE) continue

                // Get 4 corner points
                val pointsMat = Point2f(4)
                rotatedRect.points(pointsMat)
                val rectPoints = (0 until 4).map {
                    doubleArrayOf(
                        pointsMat.position(it.toLong()).x().toDouble(),
                        pointsMat.position(it.toLong()).y().toDouble(),
                    )
                }
                pointsMat.close()

                // Sort points clockwise: top-left, top-right, bottom-right, bottom-left
                val sortedPoints = getMiniBoxPoints(rectPoints)

                // Compute score (box_score_fast)
                val score = boxScoreFast(probMat, sortedPoints, mapH, mapW)
                if (score < DET_BOX_THRESH) continue

                // Unclip (expand) the polygon
                val expanded = unclipPolygon(sortedPoints)
                if (expanded.isEmpty()) continue

                // Re-fit minimum area rect on expanded polygon
                val expandedMat = Mat(expanded.size, 1, opencv_core.CV_32FC2)
                val expandedIndexer = expandedMat.createIndexer<FloatIndexer>()
                for (j in expanded.indices) {
                    expandedIndexer.put(j.toLong(), 0L, 0L, expanded[j][0].toFloat())
                    expandedIndexer.put(j.toLong(), 0L, 1L, expanded[j][1].toFloat())
                }
                expandedIndexer.close()

                val expandedRect = opencv_imgproc.minAreaRect(expandedMat)
                expandedMat.close()

                val expandedSside = min(expandedRect.size().width(), expandedRect.size().height())
                if (expandedSside < DET_MIN_SIZE + 2) continue

                val expandedPointsMat = Point2f(4)
                expandedRect.points(expandedPointsMat)
                val expandedRectPoints = (0 until 4).map {
                    doubleArrayOf(
                        expandedPointsMat.position(it.toLong()).x().toDouble(),
                        expandedPointsMat.position(it.toLong()).y().toDouble(),
                    )
                }
                expandedPointsMat.close()
                val finalPoints = getMiniBoxPoints(expandedRectPoints)

                // Scale back to original image coordinates
                val scaledPoints = finalPoints.map { pt ->
                    BoxPoint(
                        (pt[0] / mapW * srcW).roundToInt().coerceIn(0, srcW),
                        (pt[1] / mapH * srcH).roundToInt().coerceIn(0, srcH),
                    )
                }

                boxes.add(DetectedResults(scaledPoints, score))
            } finally {
                contour.close()
            }
        }

        // Cleanup
        probMat.close()
        binaryMat.close()
        contours.close()

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
        val mask = Mat(roiH, roiW, CV_8UC1, Scalar(0.0))

        // Shift points to ROI coordinates and fill polygon
        val shiftedPointsMat = Mat(points.size, 1, opencv_core.CV_32SC2)
        val shiftedIndexer = shiftedPointsMat.createIndexer<IntIndexer>()
        for (j in points.indices) {
            shiftedIndexer.put(j.toLong(), 0L, 0L, (points[j][0] - xmin).roundToInt().coerceIn(0, roiW - 1))
            shiftedIndexer.put(j.toLong(), 0L, 1L, (points[j][1] - ymin).roundToInt().coerceIn(0, roiH - 1))
        }
        shiftedIndexer.close()

        val contourVec = MatVector(shiftedPointsMat)
        opencv_imgproc.fillPoly(mask, contourVec, Scalar(255.0))
        contourVec.close()
        shiftedPointsMat.close()

        // Extract ROI from probability map and compute mean with mask
        val roi = probMat.apply(
            Rect(xmin, ymin, roiW, roiH),
        )
        val meanVal = opencv_core.mean(roi, mask)

        roi.close()
        mask.close()

        return meanVal.get(0).toFloat()
    }
}
