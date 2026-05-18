package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.imaging.cropPerspective
import com.github.arthurkun.koo.imaging.cvImageFromBitmap
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.opencv.core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * OCR Service that combines [PaddleOcrDetection] and [PaddleOcrRecognition]
 * to perform full text detection and recognition.
 *
 * This service acts as the public entry point for OCR operations, handling
 * Android-specific concerns like [Bitmap] and [Uri] conversion while delegating
 * the actual detection and recognition work to the respective engines.
 */
public actual class PaddleOcrService public constructor(
    platformContext: Context,
    private val recognitionModel: RecognitionModel = BaseRecognitionModel,
    private val recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
) : AndroidOcrApi {

    private val context: Context = platformContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)

    private val detection = PaddleOcrDetection(scope)
    private val recognitions = RecognitionModelManager(
        scope = scope,
        cachePolicy = recognitionModelCachePolicy,
        isOpen = { !isClosed.load() },
    )

    actual override suspend fun detectText(byteArray: ByteArray): List<DetectedResults> {
        return withByteArrayImage(byteArray) { detectTextInternal(it) }
    }

    actual override suspend fun recognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
    ): RecognitionResult {
        return recognitions.withRecognition(recognitionModel) { recognition ->
            withByteArrayImage(byteArray) { recognizeTextInternal(it, recognition) }
        }
    }

    actual override suspend fun detectAndRecognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
    ): List<OcrResult> {
        return recognitions.withRecognition(recognitionModel) { recognition ->
            withByteArrayImage(byteArray) { detectAndRecognizeTextInternal(it, recognition) }
        }
    }

    override suspend fun detectText(bitmap: Bitmap): List<DetectedResults> {
        return withBitmapImage(bitmap) { detectTextInternal(it) }
    }

    public suspend fun recognizeText(bitmap: Bitmap): RecognitionResult {
        return recognizeText(bitmap, recognitionModel)
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        recognitionModel: RecognitionModel,
    ): RecognitionResult {
        return recognitions.withRecognition(recognitionModel) { recognition ->
            withBitmapImage(bitmap) { recognizeTextInternal(it, recognition) }
        }
    }

    override suspend fun detectAndRecognizeText(
        bitmap: Bitmap,
        recognitionModel: RecognitionModel,
    ): List<OcrResult> {
        return recognitions.withRecognition(recognitionModel) { recognition ->
            withBitmapImage(bitmap) { detectAndRecognizeTextInternal(it, recognition) }
        }
    }

    public suspend fun detectAndRecognizeText(bitmap: Bitmap): List<OcrResult> {
        return detectAndRecognizeText(bitmap, recognitionModel)
    }

    override suspend fun detectText(uri: Uri): List<DetectedResults> {
        return detectText(readUriBytes(uri))
    }

    public suspend fun recognizeText(uri: Uri): RecognitionResult {
        return recognizeText(uri, recognitionModel)
    }

    override suspend fun recognizeText(
        uri: Uri,
        recognitionModel: RecognitionModel,
    ): RecognitionResult {
        return recognizeText(readUriBytes(uri), recognitionModel)
    }

    override suspend fun detectAndRecognizeText(
        uri: Uri,
        recognitionModel: RecognitionModel,
    ): List<OcrResult> {
        return detectAndRecognizeText(readUriBytes(uri), recognitionModel)
    }

    public suspend fun detectAndRecognizeText(uri: Uri): List<OcrResult> {
        return detectAndRecognizeText(uri, recognitionModel)
    }

    override suspend fun detectText(mat: Mat): List<DetectedResults> {
        return withMatImage(mat) { detectTextInternal(it) }
    }

    public suspend fun recognizeText(mat: Mat): RecognitionResult {
        return recognizeText(mat, recognitionModel)
    }

    override suspend fun recognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel,
    ): RecognitionResult {
        return recognitions.withRecognition(recognitionModel) { recognition ->
            withMatImage(mat) { recognizeTextInternal(it, recognition) }
        }
    }

    override suspend fun detectAndRecognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel,
    ): List<OcrResult> {
        return recognitions.withRecognition(recognitionModel) { recognition ->
            withMatImage(mat) { detectAndRecognizeTextInternal(it, recognition) }
        }
    }

    public suspend fun detectAndRecognizeText(mat: Mat): List<OcrResult> {
        return detectAndRecognizeText(mat, recognitionModel)
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

    private suspend fun <T> withBitmapImage(bitmap: Bitmap, block: suspend (CvImage) -> T): T {
        val image = cvImageFromBitmap(bitmap, "ocr_input")
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

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw OCRIOException(
                "Failed to open input stream for URI: $uri",
            )
    }

    actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        detection.close()
        runBlocking {
            recognitions.close()
        }
        scope.cancel()
    }
}

private const val TAG = "PaddleOcrService"
