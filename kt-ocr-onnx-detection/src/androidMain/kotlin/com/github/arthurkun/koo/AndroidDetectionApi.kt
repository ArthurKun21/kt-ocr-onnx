package com.github.arthurkun.koo

import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.base.BaseDetectionModel
import org.opencv.core.Mat

public interface AndroidDetectionApi : DetectionApi {

    public suspend fun detectText(
        bitmap: Bitmap,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults>

    public suspend fun detectText(
        uri: Uri,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults>

    public suspend fun detectText(
        mat: Mat,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults>
}
