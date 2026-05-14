package com.github.arthurkun.koo.recognition.base

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.base.resources.Res

/** PaddleOCR v5 mobile recognition model bundled with the library. */
public object BaseRecognitionModel : RecognitionModel {
    override val id: String = "pp-ocrv5-mobile-base"

    override suspend fun loadModelBytes(): ByteArray {
        return Res.readBytes(MODEL_PATH)
    }

    override suspend fun loadDictionaryBytes(): ByteArray {
        return Res.readBytes(DICT_PATH)
    }
}

private const val MODEL_PATH = "files/PP-OCRv5_mobile_rec.onnx"
private const val DICT_PATH = "files/ppocrv5_dict.txt"
