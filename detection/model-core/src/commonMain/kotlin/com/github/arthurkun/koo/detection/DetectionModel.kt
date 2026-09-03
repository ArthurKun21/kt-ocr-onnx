package com.github.arthurkun.koo.detection

/**
 * Provides the bytes required to create a PaddleOCR text detection model, plus the detection
 * parameters that model was configured with.
 *
 * Property defaults mirror the Python reference defaults so a custom model only needs to override
 * what its config changes:
 * - DB postprocess: `ppocr/postprocess/db_postprocess.py::DBPostProcess.__init__`
 * - Resize: `ppocr/data/imaug/operators.py::DetResizeForTest` (as used by
 *   `tools/infer/predict_det.py`)
 * - Normalization: `NormalizeImage` in the model's config yml (ImageNet statistics)
 *
 * Implementations returning [FloatArray] values should be aware that the default getters allocate
 * a fresh array per access; the runtime hoists them out of per-pixel loops.
 */
public interface DetectionModel {

    /** Stable identifier used to distinguish this model from other detection models. */
    public val id: String

    /** Loads the ONNX detection model bytes. */
    public suspend fun loadModelBytes(): ByteArray

    /**
     * Probability-map binarization threshold for DB postprocess.
     *
     * Reference: `DBPostProcess.__init__` `thresh` (default 0.3).
     */
    public val detThresh: Float
        get() = 0.3f

    /**
     * Minimum average probability for a detected box to be kept.
     *
     * Reference: `DBPostProcess.__init__` `box_thresh` (default 0.7).
     */
    public val detBoxThresh: Float
        get() = 0.7f

    /**
     * Maximum number of contours considered by DB postprocess.
     *
     * Reference: `DBPostProcess.__init__` `max_candidates` (default 1000).
     */
    public val detMaxCandidates: Int
        get() = 1000

    /**
     * Polygon expansion (unclip) ratio applied to detected boxes.
     *
     * Reference: `DBPostProcess.__init__` `unclip_ratio` (default 2.0).
     */
    public val detUnclipRatio: Float
        get() = 2.0f

    /**
     * Minimum side length, in probability-map pixels, for a candidate contour.
     *
     * Reference: `DBPostProcess.__init__` `min_size` (fixed at 3).
     */
    public val detMinSize: Int
        get() = 3

    /**
     * Resize cap for `DetResizeForTest` with `limit_type="max"` semantics: when max(h, w) exceeds
     * this value the image is scaled down so its max side equals it; smaller images pass through.
     *
     * Mirrors the Python inference driver default (`tools/infer/utility.py`
     * `det_limit_side_len=960, det_limit_type="max"`).
     */
    public val detLimitSideLen: Int
        get() = 960

    /**
     * Both resized dimensions are rounded to the nearest multiple of this value (minimum itself).
     *
     * Reference: `DetResizeForTest.resize_image_type0` (32, required by the network).
     */
    public val detRoundTo: Int
        get() = 32

    /**
     * Per-channel normalization means applied as `(x / 255 - mean) / std` in RGB channel order.
     *
     * Reference: `NormalizeImage` in the detection config ymls (ImageNet statistics).
     */
    public val detMean: FloatArray
        get() = floatArrayOf(0.485f, 0.456f, 0.406f)

    /**
     * Per-channel normalization standard deviations applied as `(x / 255 - mean) / std` in RGB
     * channel order.
     *
     * Reference: `NormalizeImage` in the detection config ymls (ImageNet statistics).
     */
    public val detStd: FloatArray
        get() = floatArrayOf(0.229f, 0.224f, 0.225f)
}
