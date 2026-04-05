package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.imaging.cropPerspective
import com.github.arthurkun.koo.imaging.initOpenCV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import logcat.logcat
import org.bytedeco.opencv.opencv_core.Mat

/**
 * JVM OCR Service that combines [PaddleOcrDetection] and [PaddleOcrRecognition]
 * to perform full text detection and recognition.
 *
 * This service acts as the public entry point for OCR operations, delegating
 * the actual detection and recognition work to the respective engines.
 */
public actual class PaddleOcrService actual constructor(
    @Suppress("UNUSED_PARAMETER") platformContext: Any?,
) : JvmOcrApi {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        initOpenCV()
    }

    private val detection = PaddleOcrDetection(scope, DET_MODEL_PATH)
    private val recognition = PaddleOcrRecognition(scope, MODEL_PATH, DICT_PATH)

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

    // endregion

    actual override fun close() {
        detection.close()
        recognition.close()
        scope.cancel()
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
