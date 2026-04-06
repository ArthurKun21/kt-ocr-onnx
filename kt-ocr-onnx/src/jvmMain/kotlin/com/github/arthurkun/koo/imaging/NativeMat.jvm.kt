package com.github.arthurkun.koo.imaging

import com.github.arthurkun.koo.DetectedResults
import com.github.arthurkun.koo.OCRException
import com.github.arthurkun.koo.OCRReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacpp.indexer.UByteIndexer
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_32FC2
import org.bytedeco.opencv.global.opencv_core.CV_32FC3
import org.bytedeco.opencv.global.opencv_core.CV_8U
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR
import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_GRAYSCALE
import org.bytedeco.opencv.global.opencv_imgcodecs.imdecode
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2RGB
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2RGB
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_imgproc.getPerspectiveTransform
import org.bytedeco.opencv.global.opencv_imgproc.warpPerspective
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.math.max
import kotlin.math.sqrt
import org.bytedeco.opencv.global.opencv_imgproc.resize as cvResize
import org.bytedeco.opencv.opencv_core.Size as CvSize

private const val TAG = "NativeMat"

private val dispatcher = Dispatchers.Default

/**
 * JVM implementation of [com.github.arthurkun.koo.imaging.NativeMat] using bytedeco OpenCV [Mat].
 *
 * Internal to the module. External code should use [CvImage] instead.
 */
internal actual class NativeMat(
    @PublishedApi internal val mat: Mat = Mat(),
    actual override val tag: String,
) : CvImage {

    constructor(mat: Mat = Mat()) : this(mat, "")

    actual override val width: Int
        get() = if (mat.empty()) 0 else mat.cols()

    actual override val height: Int
        get() = if (mat.empty()) 0 else mat.rows()

    actual override fun isEmpty(): Boolean = mat.empty()

    actual override fun close() {
        try {
            mat.release()
            mat.close()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) {
                "Failed to release mat: $tag ${e.asLog()}"
            }
        }
    }

    actual override fun toRgbCvImage(): NativeMat {
        if (mat.empty()) {
            throw OCRException(
                OCRReason.LoadingError,
                cause = IllegalArgumentException("Cannot convert an empty image to RGB: $tag"),
            )
        }

        val rgbMat = Mat()
        return try {
            when (mat.channels()) {
                1 -> cvtColor(mat, rgbMat, COLOR_GRAY2RGB)

                3 -> cvtColor(mat, rgbMat, COLOR_BGR2RGB)

                4 -> cvtColor(mat, rgbMat, COLOR_BGRA2RGB)

                else -> throw OCRException(
                    OCRReason.LoadingError,
                    cause = IllegalArgumentException("Unsupported channel count ${mat.channels()} for image: $tag"),
                )
            }
            NativeMat(rgbMat, "$tag[rgb]")
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) {
                "Failed to convert mat to RGB: $tag ${e.asLog()}"
            }
            rgbMat.close()
            throw if (e is OCRException) {
                e
            } else {
                OCRException(OCRReason.LoadingError, cause = e)
            }
        }
    }

    actual override fun getPixel(y: Int, x: Int): DoubleArray {
        if (mat.empty()) {
            throw OCRException(
                OCRReason.LoadingError,
                cause = IllegalArgumentException("Cannot read pixels from an empty image: $tag"),
            )
        }
        if (y !in 0 until height || x !in 0 until width) {
            throw OCRException(
                OCRReason.LoadingError,
                cause = IndexOutOfBoundsException("Pixel ($x, $y) is outside image bounds ${width}x$height: $tag"),
            )
        }

        return try {
            val channels = mat.channels()
            val result = DoubleArray(channels)
            when (mat.depth()) {
                CV_8U -> {
                    mat.createIndexer<UByteIndexer>().use { indexer ->
                        for (c in 0 until channels) {
                            result[c] = indexer.get(y.toLong(), x.toLong(), c.toLong()).toDouble()
                        }
                    }
                }

                CV_32F -> {
                    mat.createIndexer<FloatIndexer>().use { indexer ->
                        for (c in 0 until channels) {
                            result[c] = indexer.get(y.toLong(), x.toLong(), c.toLong()).toDouble()
                        }
                    }
                }

                else -> throw OCRException(
                    OCRReason.LoadingError,
                    cause = IllegalStateException("Unsupported mat depth ${mat.depth()} for image: $tag"),
                )
            }
            result
        } catch (e: Exception) {
            throw if (e is OCRException) {
                e
            } else {
                OCRException(OCRReason.LoadingError, cause = e)
            }
        }
    }

    private fun convertTo(outputType: Int): NativeMat {
        val result = Mat()
        mat.convertTo(result, outputType)
        return NativeMat(result, "$tag[converted]")
    }

    actual override fun resizeTo(targetHeight: Int, targetWidth: Int): NativeMat {
        if (mat.empty()) {
            throw OCRException(
                OCRReason.LoadingError,
                cause = IllegalArgumentException("Cannot resize an empty image: $tag"),
            )
        }

        val result = Mat()
        try {
            cvResize(
                mat,
                result,
                CvSize(targetWidth, targetHeight),
            )
            if (result.empty()) {
                throw OCRException(
                    OCRReason.LoadingError,
                    cause = IllegalStateException("Resize produced an empty image: $tag"),
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) {
                "Failed to resize mat: $tag ${e.asLog()}"
            }
            result.release()
            result.close()
            throw if (e is OCRException) {
                e
            } else {
                OCRException(OCRReason.LoadingError, cause = e)
            }
        }
        return NativeMat(result, "$tag[resized]")
    }

    actual override fun convertToFloat(): NativeMat {
        return convertTo(CV_32FC3)
    }

    override fun toString(): String = tag.ifBlank { "NativeMat(${width}x$height)" }

    actual companion object {
        actual suspend fun fromByteArray(
            byteArray: ByteArray,
            isColor: Boolean,
            tag: String,
        ): NativeMat = withContext(dispatcher) {
            val bp = BytePointer(*byteArray)
            try {
                val buf = Mat(1, byteArray.size, CV_8UC1, bp)
                val decoded = imdecode(buf, if (isColor) IMREAD_COLOR else IMREAD_GRAYSCALE)
                buf.close()
                if (decoded.empty()) {
                    decoded.close()
                    throw OCRException(
                        OCRReason.LoadingError,
                        cause = IllegalArgumentException("Decoded image is empty for tag: $tag"),
                    )
                }
                NativeMat(decoded, tag)
            } finally {
                bp.close()
            }
        }
    }
}

internal fun NativeMat.cropPerspective(box: DetectedResults): NativeMat {
    val pts = box.points.map { pt ->
        doubleArrayOf(
            pt.x.toDouble().coerceIn(0.0, width.toDouble()),
            pt.y.toDouble().coerceIn(0.0, height.toDouble()),
        )
    }

    val tl = pts[0]
    val tr = pts[1]
    val br = pts[2]
    val bl = pts[3]

    val widthTop = sqrt((tr[0] - tl[0]) * (tr[0] - tl[0]) + (tr[1] - tl[1]) * (tr[1] - tl[1]))
    val widthBot = sqrt((br[0] - bl[0]) * (br[0] - bl[0]) + (br[1] - bl[1]) * (br[1] - bl[1]))
    val dstW = max(widthTop, widthBot).toInt()

    val heightLeft = sqrt((bl[0] - tl[0]) * (bl[0] - tl[0]) + (bl[1] - tl[1]) * (bl[1] - tl[1]))
    val heightRight = sqrt((br[0] - tr[0]) * (br[0] - tr[0]) + (br[1] - tr[1]) * (br[1] - tr[1]))
    val dstH = max(heightLeft, heightRight).toInt()

    if (dstW <= 0 || dstH <= 0) return NativeMat(tag = "$tag[crop]")

    val srcPts = Mat(4, 1, CV_32FC2)
    val dstPts = Mat(4, 1, CV_32FC2)

    srcPts.createIndexer<FloatIndexer>().use { idx ->
        idx.put(0L, 0L, 0L, tl[0].toFloat())
        idx.put(0L, 0L, 1L, tl[1].toFloat())
        idx.put(1L, 0L, 0L, tr[0].toFloat())
        idx.put(1L, 0L, 1L, tr[1].toFloat())
        idx.put(2L, 0L, 0L, br[0].toFloat())
        idx.put(2L, 0L, 1L, br[1].toFloat())
        idx.put(3L, 0L, 0L, bl[0].toFloat())
        idx.put(3L, 0L, 1L, bl[1].toFloat())
    }
    dstPts.createIndexer<FloatIndexer>().use { idx ->
        idx.put(0L, 0L, 0L, 0f)
        idx.put(0L, 0L, 1L, 0f)
        idx.put(1L, 0L, 0L, (dstW - 1).toFloat())
        idx.put(1L, 0L, 1L, 0f)
        idx.put(2L, 0L, 0L, (dstW - 1).toFloat())
        idx.put(2L, 0L, 1L, (dstH - 1).toFloat())
        idx.put(3L, 0L, 0L, 0f)
        idx.put(3L, 0L, 1L, (dstH - 1).toFloat())
    }

    val transform = getPerspectiveTransform(srcPts, dstPts)
    val result = Mat()
    warpPerspective(mat, result, transform, CvSize(dstW, dstH))

    srcPts.close()
    dstPts.close()
    transform.close()

    return NativeMat(result, "$tag[crop]")
}
