package com.github.arthurkun.koo

public expect class PaddleOcrDetectionService : DetectionApi {

    public override suspend fun detectText(byteArray: ByteArray): List<DetectedResults>

    public override fun close()
}
