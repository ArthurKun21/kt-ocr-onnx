package com.github.arthurkun.koo

import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.DetectionModelCachePolicy
import com.github.arthurkun.koo.detection.base.BaseDetectionModel
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.withRgbCvImageFromByteArray
import com.github.arthurkun.koo.imaging.withRgbCvImageFromMat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

@OptIn(InternalKtOcrONNXApi::class)
public actual class PaddleOcrDetectionService public constructor(
    private val detectionModel: DetectionModel = BaseDetectionModel,
    private val detectionModelCachePolicy: DetectionModelCachePolicy = DetectionModelCachePolicy.KEEP_IN_MEMORY,
) : JvmDetectionApi {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)
    private val detections = DetectionModelManager(
        scope = scope,
        cachePolicy = detectionModelCachePolicy,
        isOpen = { !isClosed.load() },
    ) { model ->
        PaddleOcrDetection(scope, model)
    }

    public actual override suspend fun detectText(
        byteArray: ByteArray,
        detectionModel: DetectionModel,
    ): List<DetectedResults> = detections.withDetection(detectionModel) { detection ->
        withRgbCvImageFromByteArray(byteArray) { detectTextInternal(it, detection) }
    }

    public suspend fun detectText(mat: Mat): List<DetectedResults> = detectText(mat, detectionModel)

    public override suspend fun detectText(
        mat: Mat,
        detectionModel: DetectionModel,
    ): List<DetectedResults> = detections.withDetection(detectionModel) { detection ->
        withRgbCvImageFromMat(mat) { detectTextInternal(it, detection) }
    }

    private suspend fun detectTextInternal(image: CvImage, detection: PaddleOcrDetectionBase): List<DetectedResults> =
        detection.detect(image)

    public actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        runBlocking {
            detections.close()
        }
        scope.cancel()
    }
}
