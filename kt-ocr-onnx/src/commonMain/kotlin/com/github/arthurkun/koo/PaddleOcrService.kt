package com.github.arthurkun.koo

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.recognition.RecognitionModel

/**
 * An expected class that provides OCR (Optical Character Recognition) capabilities
 * using the PaddleOCR engine.
 *
 * This service combines text detection ([PaddleOcrDetection]) and text recognition
 * ([PaddleOcrRecognition]) to perform full OCR on image data.
 *
 * As an `expect` class, its actual implementation is provided
 * in the respective platform-specific modules.
 *
 * Construct this service from platform code. JVM constructors do not require platform context;
 * Android constructors require an Android [android.content.Context].
 */
public expect class PaddleOcrService : OcrApi {

    override suspend fun detectText(
        byteArray: ByteArray,
        detectionModel: DetectionModel,
    ): List<DetectedResults>

    override suspend fun recognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
    ): RecognitionResult

    override suspend fun detectAndRecognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
        detectionModel: DetectionModel,
    ): List<OcrResult>

    override fun close()
}
