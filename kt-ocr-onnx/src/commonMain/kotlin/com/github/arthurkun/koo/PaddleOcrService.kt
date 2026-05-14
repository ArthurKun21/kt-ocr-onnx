package com.github.arthurkun.koo

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel

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
 * @param recognitionModel Default recognition model and dictionary to use.
 * @param recognitionModelCachePolicy Controls whether recognition model data is kept in memory.
 */
public expect class PaddleOcrService(
    platformContext: Any? = null,
    recognitionModel: RecognitionModel = BaseRecognitionModel,
    recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
) : OcrApi {

    override suspend fun detectText(byteArray: ByteArray): List<DetectedResults>

    override suspend fun recognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): RecognitionResult

    override suspend fun detectAndRecognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): List<OcrResult>

    override fun close()
}
