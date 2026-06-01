package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.withBgrCvImageFromBitmap
import com.github.arthurkun.koo.imaging.withCvImageFromByteArray
import com.github.arthurkun.koo.imaging.withCvImageFromMat
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.opencv.core.Mat
import kotlin.concurrent.atomics.AtomicBoolean

@OptIn(InternalKtOcrONNXApi::class)
public actual class PaddleOcrRecognitionService public constructor(
    platformContext: Context,
    private val recognitionModel: RecognitionModel = BaseRecognitionModel,
    private val recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
) : AndroidRecognitionApi {

    private val context: Context = platformContext
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
        withCvImageFromByteArray(byteArray) { recognizeTextInternal(it, recognition) }
    }

    public suspend fun recognizeText(bitmap: Bitmap): RecognitionResult = recognizeText(bitmap, recognitionModel)

    public override suspend fun recognizeText(
        bitmap: Bitmap,
        recognitionModel: RecognitionModel,
    ): RecognitionResult = recognitions.withRecognition(recognitionModel) { recognition ->
        withBgrCvImageFromBitmap(bitmap) { recognizeTextInternal(it, recognition) }
    }

    public suspend fun recognizeText(uri: Uri): RecognitionResult = recognizeText(uri, recognitionModel)

    public override suspend fun recognizeText(
        uri: Uri,
        recognitionModel: RecognitionModel,
    ): RecognitionResult = recognizeText(readUriBytes(uri), recognitionModel)

    public suspend fun recognizeText(mat: Mat): RecognitionResult = recognizeText(mat, recognitionModel)

    public override suspend fun recognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel,
    ): RecognitionResult = recognitions.withRecognition(recognitionModel) { recognition ->
        withCvImageFromMat(mat) { recognizeTextInternal(it, recognition) }
    }

    private suspend fun recognizeTextInternal(image: CvImage, recognition: PaddleOcrRecognition): RecognitionResult =
        recognition.detectText(image)

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw OCRIOException("Failed to open input stream for URI: $uri")
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
