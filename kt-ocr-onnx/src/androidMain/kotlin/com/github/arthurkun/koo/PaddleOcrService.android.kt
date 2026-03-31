package com.github.arthurkun.koo

import android.graphics.Bitmap
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.imaging.cropPerspective
import com.github.arthurkun.koo.imaging.cvImageFromBitmap
import com.github.arthurkun.koo.imaging.initOpenCV
import kotlinx.coroutines.CoroutineScope
import logcat.logcat

/**
 * OCR Service that combines [PaddleOcrDetection] and [PaddleOcrRecognition]
 * to perform full text detection and recognition.
 *
 * This service acts as the public entry point for OCR operations, handling
 * Android-specific concerns like [Bitmap] conversion while delegating
 * the actual detection and recognition work to the respective engines.
 */
internal actual class PaddleOcrService actual constructor(
    scope: CoroutineScope,
    detModelPath: String,
    recModelPath: String,
    dictPath: String,
) : AndroidOcrApi {

    init {
        initOpenCV()
    }

    private val detection = PaddleOcrDetection(scope, detModelPath)
    private val recognition = PaddleOcrRecognition(scope, recModelPath, dictPath)

    actual override suspend fun detectText(image: CvImage): List<DetectedResults> {
        return detection.detect(image)
    }

    actual override suspend fun recognizeText(image: CvImage): RecognitionResult {
        return recognition.detectText(image)
    }

    actual override suspend fun detectAndRecognizeText(image: CvImage): List<OcrResult> {
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

    override suspend fun detectText(bitmap: Bitmap): List<DetectedResults> {
        return withBitmapImage(bitmap) { detectText(it) }
    }

    override suspend fun recognizeText(bitmap: Bitmap): RecognitionResult {
        return withBitmapImage(bitmap) { recognizeText(it) }
    }

    override suspend fun detectAndRecognizeText(bitmap: Bitmap): List<OcrResult> {
        return withBitmapImage(bitmap) { detectAndRecognizeText(it) }
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
