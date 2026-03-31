package com.github.arthurkun.koo

/**
 * Represents a combined detection and recognition result for a single text region.
 *
 * @property box The detected text region with its bounding quadrilateral and detection score.
 * @property text The recognized text string within the detected region.
 * @property score Average recognition confidence score across characters (0.0–1.0).
 */
public data class OcrResult(
    public val box: DetectedResults,
    public val text: String,
    public val score: Float,
)
