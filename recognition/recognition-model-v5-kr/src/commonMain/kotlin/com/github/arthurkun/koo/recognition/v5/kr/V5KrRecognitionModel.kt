package com.github.arthurkun.koo.recognition.v5.kr

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.v5.kr.resources.Res

/**
 * PaddleOCR v5 mobile recognition model for Korean language.
 */
public object V5KrRecognitionModel : RecognitionModel {
    override val id: String = "pp-ocrv5-mobile-kr"

    override suspend fun loadModelBytes(): ByteArray {
        return Res.readBytes(MODEL_PATH)
    }

    override suspend fun loadDictionaryBytes(): ByteArray {
        return Res.readBytes(DICT_PATH)
    }
}

private const val MODEL_PATH = "files/korean_PP-OCRv5_mobile_rec.onnx"
private const val DICT_PATH = "files/ppocrv5_korean_dict.txt"
