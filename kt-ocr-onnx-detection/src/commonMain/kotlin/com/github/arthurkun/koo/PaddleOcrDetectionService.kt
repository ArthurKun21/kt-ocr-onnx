package com.github.arthurkun.koo

import com.github.arthurkun.koo.detection.DetectionModel

public expect class PaddleOcrDetectionService : DetectionApi {

    public override suspend fun detectText(
        byteArray: ByteArray,
        detectionModel: DetectionModel,
    ): List<DetectedResults>

    public override fun close()
}
