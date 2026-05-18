package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.imaging.cvImageFromBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.opencv.core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

public actual class PaddleOcrDetectionService public constructor(
    platformContext: Context,
) : AndroidDetectionApi {

    private val context: Context = platformContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)
    private val detection = PaddleOcrDetection(scope)

    public actual override suspend fun detectText(byteArray: ByteArray): List<DetectedResults> =
        withByteArrayImage(byteArray) { detectTextInternal(it) }

    public override suspend fun detectText(bitmap: Bitmap): List<DetectedResults> =
        withBitmapImage(bitmap) { detectTextInternal(it) }

    public override suspend fun detectText(uri: Uri): List<DetectedResults> =
        detectText(readUriBytes(uri))

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

    private suspend fun <T> withBitmapImage(bitmap: Bitmap, block: suspend (CvImage) -> T): T {
        val image = cvImageFromBitmap(bitmap, "ocr_input")
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

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw OCRIOException("Failed to open input stream for URI: $uri")
    }

    public actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        detection.close()
        scope.cancel()
    }
}
