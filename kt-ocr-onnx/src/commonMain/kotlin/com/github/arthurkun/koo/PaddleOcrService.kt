package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import kotlinx.coroutines.CoroutineScope

/**
 * An internal expected class that provides OCR (Optical Character Recognition) capabilities
 * using the PaddleOCR engine.
 *
 * This service combines text detection ([PaddleOcrDetection]) and text recognition
 * ([PaddleOcrRecognition]) to perform full OCR on [CvImage] image data.
 *
 * As an `expect` class, its actual implementation is provided
 * in the respective platform-specific modules.
 */
internal expect class PaddleOcrService(
    scope: CoroutineScope,
    detModelPath: String = DET_MODEL_PATH,
    recModelPath: String = MODEL_PATH,
    dictPath: String = DICT_PATH,
) : OcrApi {

    override suspend fun detectText(image: CvImage): List<DetectedResults>

    override suspend fun recognizeText(image: CvImage): RecognitionResult

    override suspend fun detectAndRecognizeText(image: CvImage): List<OcrResult>

    override fun close()
}
