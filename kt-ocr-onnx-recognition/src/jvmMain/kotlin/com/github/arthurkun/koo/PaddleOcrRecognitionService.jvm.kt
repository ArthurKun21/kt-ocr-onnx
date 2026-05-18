package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
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
        withByteArrayImage(byteArray) { recognizeTextInternal(it, recognition) }
    }

    public suspend fun recognizeText(mat: Mat): RecognitionResult = recognizeText(mat, recognitionModel)

    public override suspend fun recognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel,
    ): RecognitionResult = recognitions.withRecognition(recognitionModel) { recognition ->
        withMatImage(mat) { recognizeTextInternal(it, recognition) }
    }

    private suspend fun recognizeTextInternal(image: CvImage, recognition: PaddleOcrRecognition): RecognitionResult =
        recognition.detectText(image)

    private suspend fun <T> withByteArrayImage(byteArray: ByteArray, block: suspend (CvImage) -> T): T {
        val image = CvImage.fromByteArray(byteArray, isColor = true, tag = "ocr_input")
        return try {
            val rgbImage = image.toRgbCvImage()
            try {
                block(rgbImage)
            } finally {
                rgbImage.close()
            }
        } finally {
            image.close()
        }
    }

    private suspend fun <T> withMatImage(mat: Mat, block: suspend (CvImage) -> T): T {
        val image = NativeMat(mat, "ocr_input")
        val rgbImage = image.toRgbCvImage()
        return try {
            block(rgbImage)
        } finally {
            rgbImage.close()
        }
    }

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
