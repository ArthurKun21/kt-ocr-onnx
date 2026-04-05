package com.github.arthurkun.koo

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Interface for Optical Character Recognition (OCR) operations.
 *
 * This interface provides methods to perform text detection and recognition on image data
 * provided as [ByteArray], [Source], file path [String], or [Path] instances.
 * As it extends [AutoCloseable], implementations should release any native resources
 * or models when closed.
 */
public interface OcrApi : AutoCloseable {

    /**
     * Detects text regions in the provided image.
     *
     * @param byteArray The raw image bytes to be processed.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(byteArray: ByteArray): List<DetectedResults>

    /**
     * Recognizes text from a single image, typically a cropped text-line region.
     *
     * @param byteArray The raw image bytes to be recognized.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(byteArray: ByteArray): RecognitionResult

    /**
     * Detects text regions and recognizes text in each region.
     *
     * @param byteArray The raw image bytes to be processed.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(byteArray: ByteArray): List<OcrResult>

    /**
     * Detects text regions in the provided image read from a [Source].
     *
     * @param source The [Source] to read image bytes from.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(source: Source): List<DetectedResults> =
        detectText(source.readByteArray())

    /**
     * Recognizes text from a single image read from a [Source].
     *
     * @param source The [Source] to read image bytes from.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(source: Source): RecognitionResult =
        recognizeText(source.readByteArray())

    /**
     * Detects text regions and recognizes text in each region from a [Source].
     *
     * @param source The [Source] to read image bytes from.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(source: Source): List<OcrResult> =
        detectAndRecognizeText(source.readByteArray())

    /**
     * Detects text regions in the image at the specified file path.
     *
     * @param path The file path string of the image to be processed.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(path: String): List<DetectedResults> =
        detectText(Path(path))

    /**
     * Recognizes text from the image at the specified file path.
     *
     * @param path The file path string of the image to be recognized.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(path: String): RecognitionResult =
        recognizeText(Path(path))

    /**
     * Detects text regions and recognizes text in each region from the image at the specified file path.
     *
     * @param path The file path string of the image to be processed.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(path: String): List<OcrResult> =
        detectAndRecognizeText(Path(path))

    /**
     * Detects text regions in the image at the specified [Path].
     *
     * @param path The [Path] of the image to be processed.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(path: Path): List<DetectedResults> {
        val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        return detectText(bytes)
    }

    /**
     * Recognizes text from the image at the specified [Path].
     *
     * @param path The [Path] of the image to be recognized.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(path: Path): RecognitionResult {
        val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        return recognizeText(bytes)
    }

    /**
     * Detects text regions and recognizes text in each region from the image at the specified [Path].
     *
     * @param path The [Path] of the image to be processed.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(path: Path): List<OcrResult> {
        val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        return detectAndRecognizeText(bytes)
    }
}
