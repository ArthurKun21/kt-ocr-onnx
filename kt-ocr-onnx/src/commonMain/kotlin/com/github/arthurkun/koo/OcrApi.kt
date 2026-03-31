package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage

/**
 * Interface for Optical Character Recognition (OCR) operations.
 *
 * This interface provides methods to perform text detection and recognition on image data
 * provided as [CvImage] instances. As it extends [AutoCloseable], implementations
 * should release any native resources or models when closed.
 */
public interface OcrApi : AutoCloseable {

    /**
     * Detects text regions in the provided image.
     *
     * Returns the bounding quadrilaterals and detection confidence scores for each
     * detected text region. No text recognition is performed.
     *
     * @param image The [CvImage] image data to be processed.
     * @return A list of [DetectedResults] describing the detected text regions.
     */
    public suspend fun detectText(image: CvImage): List<DetectedResults>

    /**
     * Recognizes text from a single image, typically a cropped text-line region.
     *
     * This method performs recognition only — it does not detect text regions.
     * For best results, pass a cropped image containing a single line of text.
     *
     * @param image The [CvImage] image data to be recognized.
     * @return A [RecognitionResult] containing the recognized text and confidence score.
     */
    public suspend fun recognizeText(image: CvImage): RecognitionResult

    /**
     * Detects text regions and recognizes text in each region.
     *
     * Combines detection and recognition in a single operation: first detects text
     * regions, then crops and recognizes text within each detected region.
     *
     * @param image The [CvImage] image data to be processed.
     * @return A list of [OcrResult] containing the detected region, recognized text, and score.
     */
    public suspend fun detectAndRecognizeText(image: CvImage): List<OcrResult>
}
