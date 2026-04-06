package com.github.arthurkun.koo

// --- Recognition model defaults ---

internal const val MODEL_PATH = "files/base/PP-OCRv5_mobile_rec.onnx"
internal const val DICT_PATH = "files/base/ppocrv5_dict.txt"

/**
 * Target height for PP-OCRv5 recognition model.
 * The model expects images with height of 48 pixels.
 */
internal const val TARGET_HEIGHT = 48

/**
 * Target width for PP-OCRv5 recognition model.
 * Images are padded to this width.
 */
internal const val TARGET_WIDTH = 320

/**
 * Number of color channels (RGB).
 */
internal const val CHANNELS = 3

// --- Detection model defaults ---

internal const val DET_MODEL_PATH = "files/base/PP-OCRv5_mobile_det.onnx"

/**
 * Minimum side length for resize. Images with min(h,w) < this value are scaled up.
 * Both dimensions are then rounded to multiples of [DET_ROUND_TO].
 *
 * Reference: ppocr/data/imaug/operators.py DetResizeForTest (limit_type="min")
 */
internal const val DET_LIMIT_SIDE_LEN = 736
internal const val DET_ROUND_TO = 32

/**
 * ImageNet normalization parameters applied in RGB channel order.
 *
 * Reference: PP-OCRv5_mobile_det.yml NormalizeImage transform
 */
internal val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
internal val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

/**
 * DB postprocess parameters.
 *
 * Reference: PP-OCRv5_mobile_det.yml PostProcess section
 */
internal const val DET_THRESH = 0.3f
internal const val DET_BOX_THRESH = 0.6f
internal const val DET_MAX_CANDIDATES = 1000
internal const val DET_UNCLIP_RATIO = 1.5f
internal const val DET_MIN_SIZE = 3

// --- Service pipeline defaults ---

/**
 * Minimum crop height (in pixels) for a detected region to be passed to the recognition model.
 * Crops smaller than this are skipped as they are unlikely to contain recognizable text.
 */
internal const val MIN_CROP_HEIGHT = 10
