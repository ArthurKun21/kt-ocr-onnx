package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.withRgbCvImageFromByteArray
import com.github.arthurkun.koo.imaging.withRgbCvImageFromMat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

@OptIn(InternalKtOcrONNXApi::class)
public actual class PaddleOcrDetectionService public constructor() : JvmDetectionApi {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)
    private val detection = PaddleOcrDetection(scope)

    public actual override suspend fun detectText(byteArray: ByteArray): List<DetectedResults> =
        withRgbCvImageFromByteArray(byteArray) { detectTextInternal(it) }

    public override suspend fun detectText(mat: Mat): List<DetectedResults> =
        withRgbCvImageFromMat(mat) { detectTextInternal(it) }

    private suspend fun detectTextInternal(image: CvImage): List<DetectedResults> =
        detection.detect(image)

    public actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        detection.close()
        scope.cancel()
    }
}
