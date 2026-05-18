package com.github.arthurkun.koo

import org.bytedeco.opencv.opencv_core.Mat

public interface JvmDetectionApi : DetectionApi {

    public suspend fun detectText(mat: Mat): List<DetectedResults>
}
