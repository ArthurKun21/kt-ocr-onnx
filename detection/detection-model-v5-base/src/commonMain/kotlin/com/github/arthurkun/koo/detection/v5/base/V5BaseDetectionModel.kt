package com.github.arthurkun.koo.detection.v5.base

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.v5.base.resources.Res

/**
 * PaddleOCR v5 mobile detection model bundled with the library.
 *
 * DB postprocess values follow `configs/det/PP-OCRv5/PP-OCRv5_mobile_det.yml`: [detBoxThresh] and
 * [detUnclipRatio] are overridden; every other parameter matches the interface defaults.
 */
public object V5BaseDetectionModel : DetectionModel {
    override val id: String = "pp-ocrv5-mobile-base"

    override val detBoxThresh: Float = 0.6f

    override val detUnclipRatio: Float = 1.5f

    override suspend fun loadModelBytes(): ByteArray {
        return Res.readBytes(MODEL_PATH)
    }
}

private const val MODEL_PATH = "files/PP-OCRv5_mobile_det.onnx"
