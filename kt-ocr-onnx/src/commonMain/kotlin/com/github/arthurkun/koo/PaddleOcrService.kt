package com.github.arthurkun.koo

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
 * @param platformContext Platform-specific context. On Android, this should be a [android.content.Context].
 *                        On JVM, this parameter is ignored.
 */
public expect class PaddleOcrService(
    platformContext: Any? = null,
) : OcrApi {

    override suspend fun detectText(byteArray: ByteArray): List<DetectedResults>

    override suspend fun recognizeText(byteArray: ByteArray): RecognitionResult

    override suspend fun detectAndRecognizeText(byteArray: ByteArray): List<OcrResult>

    override fun close()
}
