package com.github.arthurkun.koo

/**
 * Represents a detected text region as a 4-point quadrilateral.
 *
 * Points are ordered clockwise: top-left, top-right, bottom-right, bottom-left.
 * Coordinates are in the original (pre-resize) image space.
 *
 * @property points 4 corner [BoxPoint]s in original image coordinates
 * @property score Average probability within the detected region (> [DET_BOX_THRESH])
 */
public data class DetectedResults(
    public val points: List<BoxPoint>,
    public val score: Float,
) {
    override fun toString(): String {
        val pts = points.joinToString(", ") { "(${it.x}, ${it.y})" }
        return "DetectedResults(score=${"%.3f".format(score)}, points=[$pts])"
    }
}
