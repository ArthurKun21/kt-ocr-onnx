package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.withRgbCvImageFromByteArray
import com.github.arthurkun.koo.imaging.withRgbCvImageFromMat
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

@OptIn(InternalKtOcrONNXApi::class)
public actual class PaddleOcrRecognitionService public constructor(
    private val recognitionModel: RecognitionModel = BaseRecognitionModel,
    private val recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
) : JvmRecognitionApi {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)
    private val recognitions = RecognitionModelManager(
        scope = scope,
        cachePolicy = recognitionModelCachePolicy,
        isOpen = { !isClosed.load() },
    )

    public actual override suspend fun recognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
    ): RecognitionResult = recognitions.withRecognition(recognitionModel) { recognition ->
        withRgbCvImageFromByteArray(byteArray) { recognizeTextInternal(it, recognition) }
    }

    public suspend fun recognizeText(mat: Mat): RecognitionResult = recognizeText(mat, recognitionModel)

    public override suspend fun recognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel,
    ): RecognitionResult = recognitions.withRecognition(recognitionModel) { recognition ->
        withRgbCvImageFromMat(mat) { recognizeTextInternal(it, recognition) }
    }

    private suspend fun recognizeTextInternal(image: CvImage, recognition: PaddleOcrRecognition): RecognitionResult =
        recognition.detectText(image)

    public actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        runBlocking {
            recognitions.close()
        }
        scope.cancel()
    }
}
