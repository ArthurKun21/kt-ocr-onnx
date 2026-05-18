package com.github.arthurkun.koo

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import org.bytedeco.opencv.opencv_core.Mat

public interface JvmRecognitionApi : RecognitionApi {

    public suspend fun recognizeText(
        mat: Mat,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult
}
