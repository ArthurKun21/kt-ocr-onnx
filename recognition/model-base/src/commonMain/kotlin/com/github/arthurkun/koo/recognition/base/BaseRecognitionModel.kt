package com.github.arthurkun.koo.recognition.base

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.base.resources.Res

/** PaddleOCR v6 small recognition model bundled with the library. */
public object BaseRecognitionModel : RecognitionModel {
    override val id: String = "pp-ocrv6-small-base"

    override suspend fun loadModelBytes(): ByteArray {
        return Res.readBytes(MODEL_PATH)
    }

    override suspend fun loadDictionaryBytes(): ByteArray {
        return Res.readBytes(DICT_PATH)
    }
}

private const val MODEL_PATH = "files/PP-OCRv6_small_rec.onnx"
private const val DICT_PATH = "files/ppocrv6_dict.txt"
