package com.github.arthurkun.koo

import kotlinx.coroutines.CoroutineScope

/**
 * An internal expected class that provides OCR (Optical Character Recognition) capabilities
 * using the PaddleOCR engine.
 *
 * This service combines text detection ([PaddleOcrDetection]) and text recognition
 * ([PaddleOcrRecognition]) to perform full OCR on image data.
 *
 * As an `expect` class, its actual implementation is provided
 * in the respective platform-specific modules.
 *
 * @param scope The [CoroutineScope] for asynchronous operations.
 * @param platformContext Platform-specific context. On Android, this should be a [android.content.Context].
 *                        On JVM, this parameter is ignored.
 * @param detModelPath Path to the detection model resource.
 * @param recModelPath Path to the recognition model resource.
 * @param dictPath Path to the dictionary resource.
 */
internal expect class PaddleOcrService(
    scope: CoroutineScope,
    platformContext: Any? = null,
    detModelPath: String = DET_MODEL_PATH,
    recModelPath: String = MODEL_PATH,
    dictPath: String = DICT_PATH,
) : OcrApi {

    override suspend fun detectText(byteArray: ByteArray): List<DetectedResults>

    override suspend fun recognizeText(byteArray: ByteArray): RecognitionResult

    override suspend fun detectAndRecognizeText(byteArray: ByteArray): List<OcrResult>

    override fun close()
}
