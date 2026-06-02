package com.github.arthurkun.koo

import com.github.arthurkun.koo.recognition.RecognitionModel

public expect class PaddleOcrRecognitionService : RecognitionApi {

    public override suspend fun recognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel,
    ): RecognitionResult

    public override fun close()
}
