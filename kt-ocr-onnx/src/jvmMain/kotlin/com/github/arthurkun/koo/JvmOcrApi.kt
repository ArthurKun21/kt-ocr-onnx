package com.github.arthurkun.koo

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import org.bytedeco.opencv.opencv_core.Mat

/**
 * JVM-specific extension of [OcrApi] providing bytedeco OpenCV [Mat] overloads
 * for OCR operations.
 */
public interface JvmOcrApi : OcrApi {

    /**
     * Detects text regions in the provided bytedeco OpenCV [mat].
     *
     * @param mat The bytedeco OpenCV Mat image to detect text regions in.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(mat: Mat): List<DetectedResults>

    /**
     * Recognizes text from the provided bytedeco OpenCV [mat].
     *
     * @param mat The bytedeco OpenCV Mat image to recognize text from.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(mat: Mat): RecognitionResult =
        recognizeText(mat, BaseRecognitionModel)

    public suspend fun recognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel,
    ): RecognitionResult

    /**
     * Detects text regions and recognizes text in each region of the provided bytedeco OpenCV [mat].
     *
     * @param mat The bytedeco OpenCV Mat image to perform full OCR on.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(mat: Mat): List<OcrResult> =
        detectAndRecognizeText(mat, BaseRecognitionModel)

    public suspend fun detectAndRecognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel,
    ): List<OcrResult>
}
