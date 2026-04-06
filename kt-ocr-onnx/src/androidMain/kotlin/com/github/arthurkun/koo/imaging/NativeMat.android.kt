package com.github.arthurkun.koo.imaging

import android.graphics.Bitmap
import com.github.arthurkun.koo.DetectedResults
import com.github.arthurkun.koo.OCRException
import com.github.arthurkun.koo.OCRReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import org.opencv.android.Utils
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "NativeMat"

private val dispatcher = Dispatchers.Default

/**
 * Android implementation of [com.github.arthurkun.koo.imaging.NativeMat] using OpenCV [Mat].
 *
 * Internal to the module. External code should use [CvImage] instead.
 */
internal actual class NativeMat(
    @PublishedApi internal val mat: Mat = Mat(),
    actual override val tag: String,
) : CvImage {

    constructor(mat: Mat = Mat()) : this(mat, "")

    actual override val width: Int
        get() = if (mat.empty()) 0 else mat.width()

    actual override val height: Int
        get() = if (mat.empty()) 0 else mat.height()

    actual override fun isEmpty(): Boolean = mat.empty()

    actual override fun close() {
        try {
            mat.release()
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
                1 -> Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_GRAY2RGB)
                3 -> Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_BGR2RGB)
                4 -> Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_BGRA2RGB)
                else -> throw OCRException(
                    OCRReason.LoadingError,
                    cause = IllegalArgumentException("Unsupported channel count ${mat.channels()} for image: $tag"),
                )
            }
            NativeMat(rgbMat, "$tag[rgb]")
        } catch (e: CvException) {
            logcat(LogPriority.ERROR, TAG) {
                "Failed to convert mat to RGB: $tag ${e.asLog()}"
            }
            rgbMat.release()
            throw OCRException(OCRReason.LoadingError, cause = e)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) {
                "Unexpected error converting mat to RGB: $tag ${e.asLog()}"
            }
            rgbMat.release()
            throw if (e is OCRException) {
                e
            } else {
                OCRException(OCRReason.LoadingError, cause = e)
            }
        }
    }

    actual override fun getPixel(y: Int, x: Int): DoubleArray? {
        return try {
            mat.get(y, x)
        } catch (e: Exception) {
            null
        }
    }

    private fun convertTo(outputType: Int): NativeMat {
        val result = Mat()
        mat.convertTo(result, outputType)
        return NativeMat(result, "$tag[converted]")
    }

    actual override fun resizeTo(targetHeight: Int, targetWidth: Int): NativeMat {
        val result = Mat()
        try {
            Imgproc.resize(
                mat,
                result,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
            )
        } catch (e: CvException) {
            logcat(LogPriority.ERROR, TAG) {
                "Failed to resize mat: $tag ${e.asLog()}"
            }
        }
        return NativeMat(result, "$tag[resized]")
    }

    actual override fun convertToFloat(): NativeMat {
        return convertTo(CvType.CV_32FC3)
    }

    override fun toString(): String = tag.ifBlank { "NativeMat(${width}x$height)" }

    actual companion object {
        actual suspend fun fromByteArray(
            byteArray: ByteArray,
            isColor: Boolean,
            tag: String,
        ): NativeMat {
            val mat = withContext(dispatcher) {
                MatOfByte(*byteArray).use { matOfByte ->
                    Imgcodecs.imdecode(
                        matOfByte,
                        if (isColor) Imgcodecs.IMREAD_COLOR else Imgcodecs.IMREAD_GRAYSCALE,
                    )
                }
            }
            if (mat.empty()) {
                mat.release()
                throw OCRException(
                    OCRReason.LoadingError,
                    cause = IllegalArgumentException("Decoded image is empty for tag: $tag"),
                )
            }
            return NativeMat(mat, tag)
        }

        fun fromBitmap(bitmap: Bitmap, tag: String = ""): NativeMat {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            return NativeMat(mat, tag)
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

    val srcPts = MatOfPoint2f(
        Point(tl[0], tl[1]),
        Point(tr[0], tr[1]),
        Point(br[0], br[1]),
        Point(bl[0], bl[1]),
    )
    val dstPts = MatOfPoint2f(
        Point(0.0, 0.0),
        Point((dstW - 1).toDouble(), 0.0),
        Point((dstW - 1).toDouble(), (dstH - 1).toDouble()),
        Point(0.0, (dstH - 1).toDouble()),
    )

    val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
    val result = Mat()
    Imgproc.warpPerspective(mat, result, transform, Size(dstW.toDouble(), dstH.toDouble()))

    srcPts.release()
    dstPts.release()
    transform.release()

    return NativeMat(result, "$tag[crop]")
}
