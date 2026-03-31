package com.github.arthurkun.koo

/**
 * Represents the result of text recognition on a single image or cropped text region.
 *
 * @property text The recognized text string.
 * @property score Average confidence score across recognized characters (0.0–1.0).
 */
public data class RecognitionResult(
    public val text: String,
    public val score: Float,
)
