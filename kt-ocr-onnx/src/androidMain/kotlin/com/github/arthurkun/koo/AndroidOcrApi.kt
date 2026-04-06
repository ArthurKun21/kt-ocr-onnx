package com.github.arthurkun.koo

import android.graphics.Bitmap
import android.net.Uri
import org.opencv.core.Mat

/**
 * Android-specific extension of [OcrApi] providing [Bitmap], [Uri], and [Mat] overloads
 * for OCR operations.
 */
public interface AndroidOcrApi : OcrApi {

    /**
     * Detects text regions in the provided [bitmap].
     *
     * @param bitmap The image to detect text regions in.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(bitmap: Bitmap): List<DetectedResults>

    /**
     * Recognizes text from a single [bitmap], typically a cropped text-line region.
     *
     * @param bitmap The image to recognize text from.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(bitmap: Bitmap): RecognitionResult

    /**
     * Detects text regions and recognizes text in each region of the provided [bitmap].
     *
     * @param bitmap The image to perform full OCR on.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(bitmap: Bitmap): List<OcrResult>

    /**
     * Detects text regions in the image at the specified [uri].
     *
     * @param uri The content URI of the image to be processed.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(uri: Uri): List<DetectedResults>

    /**
     * Recognizes text from the image at the specified [uri].
     *
     * @param uri The content URI of the image to be recognized.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(uri: Uri): RecognitionResult

    /**
     * Detects text regions and recognizes text in each region from the image at the specified [uri].
     *
     * @param uri The content URI of the image to perform full OCR on.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(uri: Uri): List<OcrResult>

    /**
     * Detects text regions in the provided OpenCV [mat].
     *
     * @param mat The OpenCV Mat image to detect text regions in.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(mat: Mat): List<DetectedResults>

    /**
     * Recognizes text from the provided OpenCV [mat].
     *
     * @param mat The OpenCV Mat image to recognize text from.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(mat: Mat): RecognitionResult

    /**
     * Detects text regions and recognizes text in each region of the provided OpenCV [mat].
     *
     * @param mat The OpenCV Mat image to perform full OCR on.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(mat: Mat): List<OcrResult>
}
