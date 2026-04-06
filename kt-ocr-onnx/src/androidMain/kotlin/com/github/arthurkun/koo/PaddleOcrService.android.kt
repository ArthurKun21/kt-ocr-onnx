package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.imaging.cropPerspective
import com.github.arthurkun.koo.imaging.cvImageFromBitmap
import com.github.arthurkun.koo.imaging.initOpenCV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
public actual class PaddleOcrService actual constructor(
    platformContext: Any?,
) : AndroidOcrApi {

    private val context: Context = requireNotNull(platformContext as? Context) {
        "Android PaddleOcrService requires a non-null Context as platformContext"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isClosed = AtomicBoolean(false)

    init {
        initOpenCV()
    }

    private val detection = PaddleOcrDetection(scope, DET_MODEL_PATH)
    private val recognition = PaddleOcrRecognition(scope, MODEL_PATH, DICT_PATH)

    actual override suspend fun detectText(byteArray: ByteArray): List<DetectedResults> {
        return withByteArrayImage(byteArray) { detectTextInternal(it) }
    }

    actual override suspend fun recognizeText(byteArray: ByteArray): RecognitionResult {
        return withByteArrayImage(byteArray) { recognizeTextInternal(it) }
    }

    actual override suspend fun detectAndRecognizeText(byteArray: ByteArray): List<OcrResult> {
        return withByteArrayImage(byteArray) { detectAndRecognizeTextInternal(it) }
    }

    override suspend fun detectText(bitmap: Bitmap): List<DetectedResults> {
        return withBitmapImage(bitmap) { detectTextInternal(it) }
    }

    override suspend fun recognizeText(bitmap: Bitmap): RecognitionResult {
        return withBitmapImage(bitmap) { recognizeTextInternal(it) }
    }

    override suspend fun detectAndRecognizeText(bitmap: Bitmap): List<OcrResult> {
        return withBitmapImage(bitmap) { detectAndRecognizeTextInternal(it) }
    }

    override suspend fun detectText(uri: Uri): List<DetectedResults> {
        return detectText(readUriBytes(uri))
    }

    override suspend fun recognizeText(uri: Uri): RecognitionResult {
        return recognizeText(readUriBytes(uri))
    }

    override suspend fun detectAndRecognizeText(uri: Uri): List<OcrResult> {
        return detectAndRecognizeText(readUriBytes(uri))
    }

    override suspend fun detectText(mat: Mat): List<DetectedResults> {
        return withMatImage(mat) { detectTextInternal(it) }
    }

    override suspend fun recognizeText(mat: Mat): RecognitionResult {
        return withMatImage(mat) { recognizeTextInternal(it) }
    }

    override suspend fun detectAndRecognizeText(mat: Mat): List<OcrResult> {
        return withMatImage(mat) { detectAndRecognizeTextInternal(it) }
    }

    private suspend fun detectTextInternal(image: CvImage): List<DetectedResults> {
        return detection.detect(image)
    }

    private suspend fun recognizeTextInternal(image: CvImage): RecognitionResult {
        return recognition.detectText(image)
    }

    private suspend fun detectAndRecognizeTextInternal(image: CvImage): List<OcrResult> {
        val nativeMat = image as NativeMat
        return runDetectAndRecognizePipeline(
            image = image,
            detectText = ::detectTextInternal,
            recognizeText = ::recognizeTextInternal,
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
                IllegalStateException("Failed to open input stream for URI: $uri"),
            )
    }

    actual override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        detection.close()
        recognition.close()
        scope.cancel()
    }
}

private const val TAG = "PaddleOcrService"
