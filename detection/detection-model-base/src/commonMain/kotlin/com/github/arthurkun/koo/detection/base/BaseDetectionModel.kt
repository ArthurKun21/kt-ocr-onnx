package com.github.arthurkun.koo.detection.base

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.base.resources.Res

/**
 * PaddleOCR v6 small detection model bundled with the library.
 *
 * DB postprocess values follow `configs/det/PP-OCRv6/PP-OCRv6_small_det.yml`: [detThresh],
 * [detBoxThresh], [detMaxCandidates], and [detUnclipRatio] are overridden; every other parameter
 * matches the interface defaults.
 */
public object BaseDetectionModel : DetectionModel {
    override val id: String = "pp-ocrv6-small-base"

    override val detThresh: Float = 0.2f

    override val detBoxThresh: Float = 0.45f

    override val detMaxCandidates: Int = 3000

    override val detUnclipRatio: Float = 1.4f

    override suspend fun loadModelBytes(): ByteArray {
        return Res.readBytes(MODEL_PATH)
    }
}

private const val MODEL_PATH = "files/PP-OCRv6_small_det.onnx"
