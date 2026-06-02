package com.github.arthurkun.koo

/**
 * Represents a 2D point in image coordinates.
 *
 * Used as a corner point of a detected text region quadrilateral.
 *
 * @property x The x-coordinate in original image space.
 * @property y The y-coordinate in original image space.
 */
public data class BoxPoint(
    public val x: Int,
    public val y: Int,
)
