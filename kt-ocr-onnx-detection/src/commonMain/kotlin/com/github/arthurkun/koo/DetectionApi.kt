package com.github.arthurkun.koo

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.base.BaseDetectionModel
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

public interface DetectionApi : AutoCloseable {

    public suspend fun detectText(
        byteArray: ByteArray,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults>

    public suspend fun detectText(
        source: Source,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults> = detectText(source.readByteArray(), detectionModel)

    public suspend fun detectText(
        path: String,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults> = detectText(Path(path), detectionModel)

    public suspend fun detectText(
        path: Path,
        detectionModel: DetectionModel = BaseDetectionModel,
    ): List<DetectedResults> {
        val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        return detectText(bytes, detectionModel)
    }
}
