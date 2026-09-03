package com.github.arthurkun.koo

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.DetectionModelCachePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Creates and caches [PaddleOcrDetectionBase] instances per [DetectionModel].
 *
 * [createDetection] is supplied by the platform service because the concrete
 * `PaddleOcrDetection` lives in the platform source sets while this manager is shared.
 */
@InternalKtOcrONNXApi
public class DetectionModelManager public constructor(
    private val scope: CoroutineScope,
    private val cachePolicy: DetectionModelCachePolicy,
    private val isOpen: () -> Boolean,
    private val createDetection: (DetectionModel) -> PaddleOcrDetectionBase,
) {
    private val mutex = Mutex()
    private val cachedDetections = mutableMapOf<String, PaddleOcrDetectionBase>()

    public suspend fun <T> withDetection(
        detectionModel: DetectionModel,
        block: suspend (PaddleOcrDetectionBase) -> T,
    ): T {
        if (cachePolicy == DetectionModelCachePolicy.LOAD_EACH_TIME) {
            val detection = mutex.withLock {
                checkOpen()
                createDetection(detectionModel)
            }
            return try {
                block(detection)
            } finally {
                detection.closeSuspending()
            }
        }

        val detection = mutex.withLock {
            checkOpen()
            cachedDetections.getOrPut(detectionModel.id) {
                createDetection(detectionModel)
            }
        }
        return block(detection)
    }

    public suspend fun close() {
        mutex.withLock {
            cachedDetections.values.forEach { it.closeSuspending() }
            cachedDetections.clear()
        }
    }

    private fun checkOpen() {
        if (!isOpen()) {
            throw OCRClosedException("OCR service is already closed")
        }
    }
}
