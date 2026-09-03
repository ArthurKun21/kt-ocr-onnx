package com.github.arthurkun.koo

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.base.BaseDetectionModel
import org.bytedeco.opencv.opencv_core.Mat

public interface JvmDetectionApi : DetectionApi {

    public suspend fun detectText(
        mat: Mat,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults>
}
