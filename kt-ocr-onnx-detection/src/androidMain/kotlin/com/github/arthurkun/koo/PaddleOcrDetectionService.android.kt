package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.DetectionModelCachePolicy
import com.github.arthurkun.koo.detection.base.BaseDetectionModel
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.withRgbCvImageFromBitmap
import com.github.arthurkun.koo.imaging.withRgbCvImageFromByteArray
import com.github.arthurkun.koo.imaging.withRgbCvImageFromMat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.opencv.core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

@OptIn(InternalKtOcrONNXApi::class)
public actual class PaddleOcrDetectionService public constructor(
    platformContext: Context,
    private val detectionModel: DetectionModel = BaseDetectionModel,
    private val detectionModelCachePolicy: DetectionModelCachePolicy = DetectionModelCachePolicy.KEEP_IN_MEMORY,
) : AndroidDetectionApi {

    private val context: Context = platformContext.applicationContext ?: platformContext
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

    public suspend fun detectText(bitmap: Bitmap): List<DetectedResults> = detectText(bitmap, detectionModel)

    public override suspend fun detectText(
        bitmap: Bitmap,
        detectionModel: DetectionModel,
    ): List<DetectedResults> = detections.withDetection(detectionModel) { detection ->
        withRgbCvImageFromBitmap(bitmap) { detectTextInternal(it, detection) }
    }

    public suspend fun detectText(uri: Uri): List<DetectedResults> = detectText(uri, detectionModel)

    public override suspend fun detectText(
        uri: Uri,
        detectionModel: DetectionModel,
    ): List<DetectedResults> = detectText(readUriBytes(uri), detectionModel)

    public suspend fun detectText(mat: Mat): List<DetectedResults> = detectText(mat, detectionModel)

    public override suspend fun detectText(
        mat: Mat,
        detectionModel: DetectionModel,
    ): List<DetectedResults> = detections.withDetection(detectionModel) { detection ->
        withRgbCvImageFromMat(mat) { detectTextInternal(it, detection) }
    }

    private suspend fun detectTextInternal(image: CvImage, detection: PaddleOcrDetectionBase): List<DetectedResults> =
        detection.detect(image)

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw OCRIOException("Failed to open input stream for URI: $uri")
    }

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
