package com.github.arthurkun.koo

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

public interface RecognitionApi : AutoCloseable {

    public suspend fun recognizeText(
        byteArray: ByteArray,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult

    public suspend fun recognizeText(
        source: Source,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult = recognizeText(source.readByteArray(), recognitionModel)

    public suspend fun recognizeText(
        path: String,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult = recognizeText(Path(path), recognitionModel)

    public suspend fun recognizeText(
        path: Path,
        recognitionModel: RecognitionModel = BaseRecognitionModel,
    ): RecognitionResult {
        val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        return recognizeText(bytes, recognitionModel)
    }
}
