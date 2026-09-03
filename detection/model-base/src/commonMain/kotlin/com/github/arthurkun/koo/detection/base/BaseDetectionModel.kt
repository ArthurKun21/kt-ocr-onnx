package com.github.arthurkun.koo.detection.base

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.base.resources.Res

public object BaseDetectionModel : DetectionModel {
    override val id: String = "pp-ocrv6-small-base"

    override suspend fun loadModelBytes(): ByteArray {
        return Res.readBytes(MODEL_PATH)
    }
}

private const val MODEL_PATH = "files/PP-OCRv6_small_det.onnx"
