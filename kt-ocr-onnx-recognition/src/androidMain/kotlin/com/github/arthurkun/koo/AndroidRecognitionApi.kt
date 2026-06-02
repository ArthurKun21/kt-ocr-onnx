package com.github.arthurkun.koo

import android.graphics.Bitmap
import android.net.Uri
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import org.opencv.core.Mat

public interface AndroidRecognitionApi : RecognitionApi {

    public suspend fun recognizeText(
        bitmap: Bitmap,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult

    public suspend fun recognizeText(
        uri: Uri,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult

    public suspend fun recognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult
}
