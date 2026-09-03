package com.github.arthurkun.koo

/**
 * Target height for PP-OCRv6 recognition model.
 * The model expects images with height of 48 pixels.
 */
internal const val TARGET_HEIGHT = 48

/**
 * Target width for PP-OCRv6 recognition model.
 * Images are padded to this width.
 */
internal const val TARGET_WIDTH = 320

/**
 * Number of color channels (RGB).
 */
internal const val CHANNELS = 3

// --- Service pipeline defaults ---

/**
 * Minimum crop height (in pixels) for a detected region to be passed to the recognition model.
 * Crops smaller than this are skipped as they are unlikely to contain recognizable text.
 */
internal const val MIN_CROP_HEIGHT = 10
