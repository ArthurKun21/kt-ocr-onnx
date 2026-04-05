package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.imaging.cropPerspective
import com.github.arthurkun.koo.imaging.cvImageFromBitmap
import com.github.arthurkun.koo.imaging.initOpenCV
import kotlinx.coroutines.CoroutineScope
import logcat.logcat
import org.opencv.core.Mat

/**
 * OCR Service that combines [PaddleOcrDetection] and [PaddleOcrRecognition]
 * to perform full text detection and recognition.
 *
 * This service acts as the public entry point for OCR operations, handling
 * Android-specific concerns like [Bitmap] and [Uri] conversion while delegating
 * the actual detection and recognition work to the respective engines.
 */
internal actual class PaddleOcrService actual constructor(
    scope: CoroutineScope,
    platformContext: Any?,
    detModelPath: String,
    recModelPath: String,
    dictPath: String,
) : AndroidOcrApi {

    private val context: Context = requireNotNull(platformContext as? Context) {
        "Android PaddleOcrService requires a non-null Context as platformContext"
    }

    init {
        initOpenCV()
    }

    private val detection = PaddleOcrDetection(scope, detModelPath)
    private val recognition = PaddleOcrRecognition(scope, recModelPath, dictPath)

    // region ByteArray overloads

    actual override suspend fun detectText(byteArray: ByteArray): List<DetectedResults> {
        return withByteArrayImage(byteArray) { detectTextInternal(it) }
    }

    actual override suspend fun recognizeText(byteArray: ByteArray): RecognitionResult {
        return withByteArrayImage(byteArray) { recognizeTextInternal(it) }
    }

    actual override suspend fun detectAndRecognizeText(byteArray: ByteArray): List<OcrResult> {
        return withByteArrayImage(byteArray) { detectAndRecognizeTextInternal(it) }
    }

    // endregion

    // region Bitmap overloads

    override suspend fun detectText(bitmap: Bitmap): List<DetectedResults> {
        return withBitmapImage(bitmap) { detectTextInternal(it) }
    }

    override suspend fun recognizeText(bitmap: Bitmap): RecognitionResult {
        return withBitmapImage(bitmap) { recognizeTextInternal(it) }
    }

    override suspend fun detectAndRecognizeText(bitmap: Bitmap): List<OcrResult> {
        return withBitmapImage(bitmap) { detectAndRecognizeTextInternal(it) }
    }

    // endregion

    // region Uri overloads

    override suspend fun detectText(uri: Uri): List<DetectedResults> {
        return detectText(readUriBytes(uri))
    }

    override suspend fun recognizeText(uri: Uri): RecognitionResult {
        return recognizeText(readUriBytes(uri))
    }

    override suspend fun detectAndRecognizeText(uri: Uri): List<OcrResult> {
        return detectAndRecognizeText(readUriBytes(uri))
    }

    // endregion

    // region Mat overloads

    override suspend fun detectText(mat: Mat): List<DetectedResults> {
        return withMatImage(mat) { detectTextInternal(it) }
    }

    override suspend fun recognizeText(mat: Mat): RecognitionResult {
        return withMatImage(mat) { recognizeTextInternal(it) }
    }

    override suspend fun detectAndRecognizeText(mat: Mat): List<OcrResult> {
        return withMatImage(mat) { detectAndRecognizeTextInternal(it) }
    }

    // endregion

    // region Internal CvImage-based implementations

    private suspend fun detectTextInternal(image: CvImage): List<DetectedResults> {
        return detection.detect(image)
    }

    private suspend fun recognizeTextInternal(image: CvImage): RecognitionResult {
        return recognition.detectText(image)
    }

    private suspend fun detectAndRecognizeTextInternal(image: CvImage): List<OcrResult> {
        val nativeMat = image as NativeMat

        // Skip detection for images that are already text-line sized;
        // detection is designed for larger images with multiple text regions.
        if (image.height <= TARGET_HEIGHT * 2) {
            val result = recognition.detectText(image)
            logcat(TAG) { "detectAndRecognizeText (whole-image recognition): ${result.text}" }
            return if (result.text.isBlank()) {
                emptyList()
            } else {
                listOf(
                    OcrResult(wholeImageBox(image), result.text, result.score),
                )
            }
        }

        val boxes = detection.detect(image)

        if (boxes.isEmpty()) {
            val result = recognition.detectText(image)
            logcat(TAG) { "detectAndRecognizeText (whole-image fallback): ${result.text}" }
            return if (result.text.isBlank()) {
                emptyList()
            } else {
                listOf(
                    OcrResult(wholeImageBox(image), result.text, result.score),
                )
            }
        }

        val orderedBoxes = boxes.sortedForReadingOrder()
        val results = mutableListOf<OcrResult>()

        for (box in orderedBoxes) {
            val crop = nativeMat.cropPerspective(box)
            try {
                if (crop.isEmpty() || crop.height < MIN_CROP_HEIGHT) continue
                val recResult = recognition.detectText(crop)
                val text = recResult.text.trim()
                if (text.isNotEmpty()) {
                    results.add(OcrResult(box, text, recResult.score))
                }
            } finally {
                crop.close()
            }
        }

        if (results.isEmpty()) {
            val result = recognition.detectText(image)
            logcat(TAG) { "detectAndRecognizeText (whole-image last resort): ${result.text}" }
            return if (result.text.isBlank()) {
                emptyList()
            } else {
                listOf(
                    OcrResult(wholeImageBox(image), result.text, result.score),
                )
            }
        }

        logcat(TAG) { "detectAndRecognizeText: ${results.size} results" }
        return results
    }

    // endregion

    // region Helpers

    private suspend fun <T> withByteArrayImage(byteArray: ByteArray, block: suspend (CvImage) -> T): T {
        val image = CvImage.fromByteArray(byteArray, isColor = true, tag = "ocr_input")
        val rgbImage = image.toRgbCvImage()
        return try {
            block(rgbImage)
        } finally {
            image.close()
            rgbImage.close()
        }
    }

    private suspend fun <T> withBitmapImage(bitmap: Bitmap, block: suspend (CvImage) -> T): T {
        val image = cvImageFromBitmap(bitmap, "ocr_input")
        val rgbImage = image.toRgbCvImage()
        return try {
            block(rgbImage)
        } finally {
            image.close()
            rgbImage.close()
        }
    }

    private suspend fun <T> withMatImage(mat: Mat, block: suspend (CvImage) -> T): T {
        val image = NativeMat(mat, "ocr_input")
        val rgbImage = image.toRgbCvImage()
        return try {
            block(rgbImage)
        } finally {
            rgbImage.close()
            // Do not close the original NativeMat — the caller owns the Mat
        }
    }

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw OCRException(
                OCRReason.LoadingError,
                IllegalStateException("Failed to open input stream for URI: $uri"),
            )
    }

    // endregion

    actual override fun close() {
        detection.close()
        recognition.close()
    }
}

/**
 * Sorts detected boxes in reading order: top-to-bottom, left-to-right.
 */
private fun List<DetectedResults>.sortedForReadingOrder(): List<DetectedResults> {
    return sortedWith(
        compareBy<DetectedResults> { box ->
            box.points.minOf { it.y }
        }.thenBy { box ->
            box.points.minOf { it.x }
        },
    )
}

/**
 * Creates a [DetectedResults] representing the whole image as a single detected region.
 */
private fun wholeImageBox(image: CvImage): DetectedResults {
    return DetectedResults(
        points = listOf(
            BoxPoint(0, 0),
            BoxPoint(image.width, 0),
            BoxPoint(image.width, image.height),
            BoxPoint(0, image.height),
        ),
        score = 1.0f,
    )
}

private const val TAG = "PaddleOcrService"
