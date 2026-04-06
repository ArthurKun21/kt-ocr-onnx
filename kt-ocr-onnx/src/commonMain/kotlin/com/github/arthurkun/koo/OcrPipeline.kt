package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import kotlin.math.abs

private const val READING_ORDER_ROW_TOLERANCE_PX = 12

private data class ReadingRow(
    var anchorY: Int,
    val boxes: MutableList<DetectedResults>,
)

internal suspend fun runDetectAndRecognizePipeline(
    image: CvImage,
    detectText: suspend (CvImage) -> List<DetectedResults>,
    recognizeText: suspend (CvImage) -> RecognitionResult,
    cropFromBox: (DetectedResults) -> CvImage,
    log: (String) -> Unit,
): List<OcrResult> {
    if (image.height <= TARGET_HEIGHT * 2) {
        val result = recognizeText(image)
        log("detectAndRecognizeText (whole-image recognition): ${result.text}")
        return if (result.text.isBlank()) {
            emptyList()
        } else {
            listOf(OcrResult(wholeImageBox(image), result.text, result.score))
        }
    }

    val boxes = detectText(image)
    if (boxes.isEmpty()) {
        val result = recognizeText(image)
        log("detectAndRecognizeText (whole-image fallback): ${result.text}")
        return if (result.text.isBlank()) {
            emptyList()
        } else {
            listOf(OcrResult(wholeImageBox(image), result.text, result.score))
        }
    }

    val results = mutableListOf<OcrResult>()
    for (box in boxes.sortedForReadingOrder()) {
        val crop = cropFromBox(box)
        try {
            if (crop.isEmpty() || crop.height < MIN_CROP_HEIGHT) {
                continue
            }

            val recognized = recognizeText(crop)
            val text = recognized.text.trim()
            if (text.isNotEmpty()) {
                results.add(OcrResult(box, text, recognized.score))
            }
        } finally {
            crop.close()
        }
    }

    if (results.isEmpty()) {
        val result = recognizeText(image)
        log("detectAndRecognizeText (whole-image last resort): ${result.text}")
        return if (result.text.isBlank()) {
            emptyList()
        } else {
            listOf(OcrResult(wholeImageBox(image), result.text, result.score))
        }
    }

    log("detectAndRecognizeText: ${results.size} results")
    return results
}

internal fun List<DetectedResults>.sortedForReadingOrder(): List<DetectedResults> {
    if (isEmpty()) {
        return this
    }

    val rows = mutableListOf<ReadingRow>()
    for (box in sortedBy { it.minY() }) {
        val topY = box.minY()
        val row = rows.firstOrNull { abs(topY - it.anchorY) <= READING_ORDER_ROW_TOLERANCE_PX }
        if (row == null) {
            rows.add(ReadingRow(anchorY = topY, boxes = mutableListOf(box)))
            continue
        }

        row.boxes.add(box)
        row.anchorY = row.boxes.map { it.minY() }.average().toInt()
    }

    return rows
        .sortedBy { it.anchorY }
        .flatMap { row -> row.boxes.sortedBy { it.minX() } }
}

internal fun wholeImageBox(image: CvImage): DetectedResults {
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

private fun DetectedResults.minX(): Int = points.minOf { it.x }

private fun DetectedResults.minY(): Int = points.minOf { it.y }
