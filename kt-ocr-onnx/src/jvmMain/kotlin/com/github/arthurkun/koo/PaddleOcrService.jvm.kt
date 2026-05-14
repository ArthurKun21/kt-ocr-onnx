package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.imaging.cropPerspective
import com.github.arthurkun.koo.imaging.initOpenCV
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import com.github.arthurkun.koo.recognition.RecognitionModelLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat
import org.bytedeco.opencv.opencv_core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * JVM OCR Service that combines [PaddleOcrDetection] and [PaddleOcrRecognition]
 * to perform full text detection and recognition.
 *
 * This service acts as the public entry point for OCR operations, delegating
 * the actual detection and recognition work to the respective engines.
 */
public actual class PaddleOcrService actual constructor(
    @Suppress("UNUSED_PARAMETER") platformContext: Any?,
    private val recognitionModel: RecognitionModel,
    private val recognitionModelCachePolicy: RecognitionModelCachePolicy,
) : JvmOcrApi {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)
    private val recognitionMutex = Mutex()
    private val cachedRecognitions = mutableMapOf<String, PaddleOcrRecognition>()

    init {
        initOpenCV()
    }

    private val detection = PaddleOcrDetection(scope, DET_MODEL_PATH)

    actual override suspend fun detectText(byteArray: ByteArray): List<DetectedResults> {
        return withByteArrayImage(byteArray) { detectTextInternal(it) }
    }

    actual override suspend fun recognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): RecognitionResult {
        return withRecognition(recognitionModel, recognitionModelCachePolicy) { recognition ->
            withByteArrayImage(byteArray) { recognizeTextInternal(it, recognition) }
        }
    }

    actual override suspend fun detectAndRecognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): List<OcrResult> {
        return withRecognition(recognitionModel, recognitionModelCachePolicy) { recognition ->
            withByteArrayImage(byteArray) { detectAndRecognizeTextInternal(it, recognition) }
        }
    }

    override suspend fun detectText(mat: Mat): List<DetectedResults> {
        return withMatImage(mat) { detectTextInternal(it) }
    }

    override suspend fun recognizeText(mat: Mat): RecognitionResult {
        return withRecognition(recognitionModel, recognitionModelCachePolicy) { recognition ->
            withMatImage(mat) { recognizeTextInternal(it, recognition) }
        }
    }

    override suspend fun detectAndRecognizeText(mat: Mat): List<OcrResult> {
        return withRecognition(recognitionModel, recognitionModelCachePolicy) { recognition ->
            withMatImage(mat) { detectAndRecognizeTextInternal(it, recognition) }
        }
    }

    private suspend fun detectTextInternal(image: CvImage): List<DetectedResults> {
        return detection.detect(image)
    }

    private suspend fun recognizeTextInternal(image: CvImage, recognition: PaddleOcrRecognition): RecognitionResult {
        return recognition.detectText(image)
    }

    private suspend fun detectAndRecognizeTextInternal(
        image: CvImage,
        recognition: PaddleOcrRecognition,
    ): List<OcrResult> {
        val nativeMat = image as NativeMat
        return runDetectAndRecognizePipeline(
            image = image,
            detectText = ::detectTextInternal,
            recognizeText = { croppedImage -> recognizeTextInternal(croppedImage, recognition) },
            cropFromBox = { box -> nativeMat.cropPerspective(box) },
            log = { message -> logcat(TAG) { message } },
        )
    }

    private suspend fun <T> withRecognition(
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
        block: suspend (PaddleOcrRecognition) -> T,
    ): T {
        if (recognitionModelCachePolicy == RecognitionModelCachePolicy.LOAD_EACH_TIME) {
            val recognition = createRecognition(recognitionModel, recognitionModelCachePolicy)
            return try {
                block(recognition)
            } finally {
                recognition.close()
            }
        }

        val recognition = recognitionMutex.withLock {
            cachedRecognitions.getOrPut(recognitionModel.id) {
                createRecognition(recognitionModel, recognitionModelCachePolicy)
            }
        }
        return block(recognition)
    }

    private fun createRecognition(
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): PaddleOcrRecognition {
        return PaddleOcrRecognition(
            scope,
            RecognitionModelLoader(recognitionModel, recognitionModelCachePolicy),
        )
    }

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
            // Do not close the original NativeMat — the caller owns the Mat
        }
    }

    actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        detection.close()
        cachedRecognitions.values.forEach { it.close() }
        cachedRecognitions.clear()
        scope.cancel()
    }
}

private const val TAG = "PaddleOcrService"
