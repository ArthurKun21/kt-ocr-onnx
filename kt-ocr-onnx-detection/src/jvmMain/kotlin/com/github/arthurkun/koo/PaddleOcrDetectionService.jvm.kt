package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

public actual class PaddleOcrDetectionService public constructor() : JvmDetectionApi {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)
    private val detection = PaddleOcrDetection(scope)

    public actual override suspend fun detectText(byteArray: ByteArray): List<DetectedResults> =
        withByteArrayImage(byteArray) { detectTextInternal(it) }

    public override suspend fun detectText(mat: Mat): List<DetectedResults> =
        withMatImage(mat) { detectTextInternal(it) }

    private suspend fun detectTextInternal(image: CvImage): List<DetectedResults> =
        detection.detect(image)

    private suspend fun <T> withByteArrayImage(byteArray: ByteArray, block: suspend (CvImage) -> T): T {
        val image = CvImage.fromByteArray(byteArray, isColor = true, tag = "ocr_input")
        return try {
            val rgbImage = image.toRgbCvImage()
            try {
                block(rgbImage)
            } finally {
                rgbImage.close()
            }
        } finally {
            image.close()
        }
    }

    private suspend fun <T> withMatImage(mat: Mat, block: suspend (CvImage) -> T): T {
        val image = NativeMat(mat, "ocr_input")
        val rgbImage = image.toRgbCvImage()
        return try {
            block(rgbImage)
        } finally {
            rgbImage.close()
        }
    }

    public actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        detection.close()
        scope.cancel()
    }
}
